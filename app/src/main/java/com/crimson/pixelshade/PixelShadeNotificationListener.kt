package com.crimson.pixelshade

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.mutableStateListOf


data class ShadeNotificationAction(
    val title: String,
    val pendingIntent: PendingIntent?
)

data class ShadeNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val ongoing: Boolean,
    val isMedia: Boolean,
    val contentIntent: PendingIntent?,
    val actions: List<ShadeNotificationAction>,
    val artwork: Icon?
)

object PixelShadeNotificationStore {
    private const val TAG = "PixelShadeNotification"
    val items = mutableStateListOf<ShadeNotification>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var service: PixelShadeNotificationListener? = null

    internal fun attach(listener: PixelShadeNotificationListener) {
        service = listener
    }

    internal fun detach(listener: PixelShadeNotificationListener) {
        if (service === listener) service = null
    }

    internal fun replaceAll(newItems: List<ShadeNotification>) {
        main.post {
            items.clear()
            items.addAll(newItems.sortedByDescending { it.postTime })
        }
    }

    internal fun upsert(item: ShadeNotification) {
        main.post {
            val index = items.indexOfFirst { it.key == item.key }
            if (index >= 0) items[index] = item else items.add(item)
            val sorted = items.sortedByDescending { it.postTime }
            items.clear()
            items.addAll(sorted)
        }
    }

    internal fun remove(key: String) {
        main.post { items.removeAll { it.key == key } }
    }

    /**
     * A shade is an ephemeral surface. Dispatch first, then close on the next
     * main-loop turn so the destination activity can win task focus instead of
     * revealing Pixel Shade beneath the panel.
     */
    fun open(item: ShadeNotification, onDispatched: () -> Unit): Boolean {
        val pendingIntent = item.contentIntent
        if (pendingIntent == null) {
            Log.i(TAG, "contentIntent missing package=${item.packageName} key=${item.key}; keeping panel open")
            return false
        }
        Log.i(TAG, "contentIntent send package=${item.packageName} key=${item.key}")
        return try {
            pendingIntent.send()
            Log.i(TAG, "contentIntent dispatched package=${item.packageName} key=${item.key}; panel close queued")
            main.post { onDispatched() }
            true
        } catch (error: PendingIntent.CanceledException) {
            Log.w(TAG, "contentIntent cancelled package=${item.packageName} key=${item.key}; panel remains open", error)
            false
        } catch (error: Exception) {
            Log.e(TAG, "contentIntent failed package=${item.packageName} key=${item.key}; panel remains open", error)
            false
        }
    }

    /** Compatibility path for the legacy panel; V2 supplies the close callback above. */
    fun open(item: ShadeNotification): Boolean = open(item) {}

    fun runAction(item: ShadeNotification, action: ShadeNotificationAction): Boolean {
        val pendingIntent = action.pendingIntent
        if (pendingIntent == null) {
            Log.i(TAG, "action missing package=${item.packageName} key=${item.key} title=${action.title}")
            return false
        }
        Log.i(TAG, "action send package=${item.packageName} key=${item.key} title=${action.title}")
        return try {
            pendingIntent.send()
            Log.i(TAG, "action dispatched package=${item.packageName} key=${item.key} title=${action.title}")
            true
        } catch (error: PendingIntent.CanceledException) {
            Log.w(TAG, "action cancelled package=${item.packageName} key=${item.key} title=${action.title}", error)
            false
        } catch (error: Exception) {
            Log.e(TAG, "action failed package=${item.packageName} key=${item.key} title=${action.title}", error)
            false
        }
    }

    /** Compatibility path for the legacy panel, which does not retain the parent notification. */
    fun runAction(action: ShadeNotificationAction): Boolean {
        val pendingIntent = action.pendingIntent
        if (pendingIntent == null) {
            Log.i(TAG, "legacy action missing title=${action.title}")
            return false
        }
        return try {
            pendingIntent.send()
            Log.i(TAG, "legacy action dispatched title=${action.title}")
            true
        } catch (error: PendingIntent.CanceledException) {
            Log.w(TAG, "legacy action cancelled title=${action.title}", error)
            false
        } catch (error: Exception) {
            Log.e(TAG, "legacy action failed title=${action.title}", error)
            false
        }
    }

    fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }

    fun clearAll() {
        runCatching { service?.cancelAllNotifications() }
    }
}

class PixelShadeNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        PixelShadeNotificationStore.attach(this)
        refreshAll()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        PixelShadeNotificationStore.upsert(toShadeNotification(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        PixelShadeNotificationStore.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        PixelShadeNotificationStore.detach(this)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        PixelShadeNotificationStore.detach(this)
        super.onDestroy()
    }

    private fun refreshAll() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.packageName != packageName }
            .map { toShadeNotification(it) }
        PixelShadeNotificationStore.replaceAll(active)
    }

    private fun toShadeNotification(sbn: StatusBarNotification): ShadeNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        ).firstOrNull { !it.isNullOrBlank() }?.toString().orEmpty()
        val label = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)
        val actions = notification.actions?.mapNotNull { action ->
            val actionTitle = action.title?.toString()?.trim().orEmpty()
            if (actionTitle.isBlank()) null else ShadeNotificationAction(actionTitle, action.actionIntent)
        }.orEmpty()
        val isMedia = notification.category == Notification.CATEGORY_TRANSPORT || extras.containsKey(Notification.EXTRA_MEDIA_SESSION)

        return ShadeNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label,
            title = title.ifBlank { label },
            text = text,
            postTime = sbn.postTime,
            clearable = sbn.isClearable,
            ongoing = sbn.isOngoing,
            isMedia = isMedia,
            contentIntent = notification.contentIntent,
            actions = actions,
            artwork = runCatching { notification.getLargeIcon() }.getOrNull()
        )
    }
}
