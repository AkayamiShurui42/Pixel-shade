package com.crimson.pixelshade

import android.app.Notification
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
    val actions: List<ShadeNotificationAction>
)

object PixelShadeNotificationStore {
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

    fun open(item: ShadeNotification): Boolean = runCatching {
        item.contentIntent?.send() ?: return false
        true
    }.getOrDefault(false)

    fun runAction(action: ShadeNotificationAction): Boolean = runCatching {
        action.pendingIntent?.send() ?: return false
        true
    }.getOrDefault(false)

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
            actions = actions
        )
    }
}
