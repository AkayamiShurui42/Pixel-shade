package com.crimson.pixelshade

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import af.shizuku.Shizuku
import kotlin.math.abs
import kotlin.math.roundToInt

class PixelShadeTriggerService : Service() {
    companion object {
        const val ACTION_ENABLE_AND_SUPPRESS = "com.crimson.pixelshade.action.ENABLE_AND_SUPPRESS"
        const val ACTION_RECOVER_STOCK_SHADE = "com.crimson.pixelshade.action.RECOVER_STOCK_SHADE"
        const val EXTRA_ACTIVATION_RECEIVER = "activation_receiver"
        const val RESULT_ACTIVATION_OK = 1
        const val RESULT_ACTIVATION_FAILED = 0
        const val RESULT_DETAIL = "detail"

        @Volatile private var instance: PixelShadeTriggerService? = null
        @Volatile private var registeredTrigger = false

        fun hasWorkingTrigger(): Boolean =
            registeredTrigger || PixelShadeAccessibilityService.hasWorkingTrigger()

        fun requestTriggerRefresh(): Boolean {
            val service = instance ?: return false
            return service.rebuildTriggers()
        }

        /** Establish an application-overlay trigger before Accessibility removes its view. */
        fun requestRecoveryHandoff(): Boolean {
            val service = instance ?: return false
            service.rebuildTriggers(forceOverlayTop = true)
            return registeredTrigger
        }

        fun requestStockShadeRecovery(context: android.content.Context): Boolean = runCatching {
            context.startForegroundService(
                Intent(context, PixelShadeTriggerService::class.java)
                    .setAction(ACTION_RECOVER_STOCK_SHADE)
            )
        }.isSuccess
    }

    private lateinit var wm: WindowManager
    private val triggers = mutableListOf<View>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var rebuildingTriggers = false
    private val shizukuBinderReceived = Shizuku.OnBinderReceivedListener {
        mainHandler.post {
            when {
                !PixelShadeRuntime.isEnabled(this) && PixelShadeRuntime.statusBarWasDisabled(this) ->
                    recoverStockShade(null, "Shizuku reconnected while stock-shade recovery was pending")
                !PixelShadeRuntime.isEnabled(this) -> stopSelf()
                !PixelShadeConfig.triggersAllowedInCurrentConfiguration(this) ->
                    StatusBarSuppression.restoreTemporarily(this) { restored, _ ->
                        if (restored) rebuildTriggers()
                        else updateServiceNotification("Recovery needed - open Pixel Shade or use ADB")
                    }
                verifyTriggers() -> StatusBarSuppression.sync(this)
                else -> failActivation(null, "No working Pixel Shade trigger remained after Shizuku reconnected")
            }
        }
    }

    private enum class TriggerEdge { TOP, BOTTOM, LEFT, RIGHT }

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1717, serviceNotification("Gesture triggers are running"))
        runCatching { Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceived) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post {
            if (!PixelShadeRuntime.isEnabled(this)) return@post
            if (!PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
                StatusBarSuppression.restoreTemporarily(this) { restored, _ ->
                    if (restored) rebuildTriggers()
                    else updateServiceNotification("Recovery needed - open Pixel Shade or use ADB")
                }
            } else if (verifyTriggers()) {
                StatusBarSuppression.sync(this)
            } else {
                failActivation(null, "Pixel Shade could not rebuild its gesture triggers after rotation")
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val receiver = activationReceiver(intent)
        if (intent?.action == ACTION_RECOVER_STOCK_SHADE) {
            recoverStockShade(receiver, "Pixel Shade opened its recovery service")
            return START_STICKY
        }
        if (!PixelShadeRuntime.isEnabled(this)) {
            if (PixelShadeRuntime.statusBarWasDisabled(this)) {
                recoverStockShade(receiver, "Pixel Shade was off while stock-shade recovery was pending")
                return START_STICKY
            }
            sendActivationResult(receiver, false, "Pixel Shade was turned off before activation completed")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
            if (intent?.action == ACTION_ENABLE_AND_SUPPRESS && !PixelShadeRuntime.statusBarWasDisabled(this)) {
                PixelShadeRuntime.setEnabled(this, false)
                sendActivationResult(receiver, false, "Pixel Shade is configured to stay hidden in landscape")
                stopSelf()
                return START_NOT_STICKY
            }
            StatusBarSuppression.restoreTemporarily(this) { restored, detail ->
                if (restored) rebuildTriggers()
                else updateServiceNotification("Recovery needed - open Pixel Shade or use ADB")
                sendActivationResult(receiver, false, "$detail. Pixel Shade is paused in landscape")
            }
            return START_STICKY
        }

        if (!verifyTriggers()) {
            failActivation(receiver, "Pixel Shade could not create every required top, side, or bottom trigger")
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_ENABLE_AND_SUPPRESS) {
            StatusBarSuppression.confirmAndDisable(this) { success, detail ->
                if (!success && !PixelShadeRuntime.statusBarWasDisabled(this)) {
                    PixelShadeRuntime.setEnabled(this, false)
                    rebuildTriggers()
                    stopSelf()
                } else if (!success) {
                    updateServiceNotification("Recovery needed - open Pixel Shade or use ADB")
                }
                sendActivationResult(receiver, success, detail)
            }
        } else {
            StatusBarSuppression.sync(this)
        }
        return START_STICKY
    }

    private fun verifyTriggers(): Boolean {
        if (!PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) return false
        val requiresApplicationOverlay = PixelShadeConfig.bottomEnabled(this) ||
            PixelShadeConfig.leftEnabled(this) || PixelShadeConfig.rightEnabled(this)
        if (requiresApplicationOverlay && !Settings.canDrawOverlays(this)) return false
        val accessibilityReady = PixelShadeAccessibilityService.requestTriggerRefresh()
        val overlayReady = rebuildTriggers()
        if (requiresApplicationOverlay && !registeredTrigger) return false
        return accessibilityReady || overlayReady || PixelShadeAccessibilityService.hasWorkingTrigger()
    }

    private fun recoverStockShade(receiver: ResultReceiver?, reason: String) {
        if (PixelShadeRuntime.statusBarWasDisabled(this)) PixelShadeRuntime.setEnabled(this, true)
        rebuildTriggers(forceOverlayTop = true)
        updateServiceNotification("Restoring Android's notification shade")
        StatusBarSuppression.restore(this) { restored, detail ->
            if (restored) {
                PixelShadeRuntime.setEnabled(this, false)
                rebuildTriggers()
                sendActivationResult(receiver, false, "$reason. $detail")
                stopSelf()
            } else {
                updateServiceNotification("Recovery needed - open Pixel Shade or use ADB")
                sendActivationResult(
                    receiver,
                    false,
                    "$reason. $detail. Recovery: ${StatusBarSuppression.ADB_RECOVERY_COMMAND}"
                )
            }
        }
    }

    private fun failActivation(receiver: ResultReceiver?, reason: String) {
        if (!PixelShadeRuntime.statusBarWasDisabled(this)) {
            PixelShadeRuntime.setEnabled(this, false)
            rebuildTriggers()
            sendActivationResult(receiver, false, "$reason. Android's notification shade remained available")
            stopSelf()
            return
        }

        // Keep the foreground process and any surviving replacement trigger alive
        // until restoration is confirmed. Removing the last trigger first would
        // strand the user if Shizuku disconnected while the stock shade was blocked.
        StatusBarSuppression.restore(this) { restored, restoreDetail ->
            val detail = "$reason. $restoreDetail"
            if (restored) {
                PixelShadeRuntime.setEnabled(this, false)
                rebuildTriggers()
                sendActivationResult(receiver, false, detail)
                stopSelf()
            } else {
                updateServiceNotification("Recovery needed - open Pixel Shade or use ADB")
                sendActivationResult(
                    receiver,
                    false,
                    "$detail. Pixel Shade is staying active; recovery: ${StatusBarSuppression.ADB_RECOVERY_COMMAND}"
                )
            }
        }
    }

    private fun sendActivationResult(receiver: ResultReceiver?, success: Boolean, detail: String) {
        receiver?.send(
            if (success) RESULT_ACTIVATION_OK else RESULT_ACTIVATION_FAILED,
            Bundle().apply { putString(RESULT_DETAIL, detail) }
        )
    }

    @Suppress("DEPRECATION")
    private fun activationReceiver(intent: Intent?): ResultReceiver? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_ACTIVATION_RECEIVER, ResultReceiver::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_ACTIVATION_RECEIVER)
        }

    private fun rebuildTriggers(forceOverlayTop: Boolean = false): Boolean {
        rebuildingTriggers = true
        triggers.forEach { runCatching { wm.removeView(it) } }
        triggers.clear()
        registeredTrigger = false
        if (!PixelShadeRuntime.isEnabled(this) || !PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
            rebuildingTriggers = false
            return false
        }

        val overlayAllowed = Settings.canDrawOverlays(this)
        if (overlayAllowed) {
            // Accessibility owns the primary top trigger only after its overlay
            // was actually registered. A configured-but-disconnected service is
            // not enough to authorize stock-shade suppression.
            if (forceOverlayTop || !PixelShadeAccessibilityService.hasWorkingTrigger()) addTopTrigger()
            if (PixelShadeConfig.bottomEnabled(this)) addBottomTrigger()
            if (PixelShadeConfig.leftEnabled(this)) addSideTrigger(TriggerEdge.LEFT)
            if (PixelShadeConfig.rightEnabled(this)) addSideTrigger(TriggerEdge.RIGHT)
        }
        registeredTrigger = triggers.any { it.isAttachedToWindow }
        rebuildingTriggers = false
        return registeredTrigger || PixelShadeAccessibilityService.hasWorkingTrigger()
    }
    private fun visibleStripPx(maxSize: Int): Int {
        if (PixelShadeConfig.hideHandleIcon(this)) return 0
        return (PixelShadeConfig.triggerVisibleDp(this) * resources.displayMetrics.density)
            .roundToInt().coerceIn(0, maxSize)
    }

    private fun addTopTrigger() {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val height = (PixelShadeConfig.triggerHeightDp(this) * d).roundToInt().coerceAtLeast(1)
        val visibleHeight = visibleStripPx(height)
        val width = (screenW * PixelShadeConfig.topWidthPercent(this).coerceIn(10f, 100f) / 100f).roundToInt()
        val centerX = (screenW * PixelShadeConfig.topXPercent(this).coerceIn(0f, 100f) / 100f).roundToInt()
        val view = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
            setOnTouchListener(GestureListener(TriggerEdge.TOP, allowBrightness = true))
            if (visibleHeight > 0) {
                addView(View(this@PixelShadeTriggerService).apply {
                    setBackgroundColor(PixelShadeConfig.triggerColor(this@PixelShadeTriggerService))
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, visibleHeight, Gravity.TOP))
            }
        }
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (centerX - width / 2).coerceIn(0, (screenW - width).coerceAtLeast(0))
            y = (PixelShadeConfig.triggerOffsetDp(this@PixelShadeTriggerService) * d).roundToInt()
        }
        trackTrigger(view)
        runCatching {
            wm.addView(view, lp)
            triggers += view
        }
    }

    private fun addBottomTrigger() {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val height = (PixelShadeConfig.bottomHeightDp(this) * d).roundToInt().coerceAtLeast(1)
        val visibleHeight = visibleStripPx(height)
        val width = (screenW * PixelShadeConfig.bottomWidthPercent(this).coerceIn(10f, 100f) / 100f).roundToInt()
        val centerX = (screenW * PixelShadeConfig.bottomXPercent(this).coerceIn(0f, 100f) / 100f).roundToInt()
        val view = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
            setOnTouchListener(GestureListener(TriggerEdge.BOTTOM, allowBrightness = false))
            if (visibleHeight > 0) {
                addView(View(this@PixelShadeTriggerService).apply {
                    setBackgroundColor(PixelShadeConfig.triggerColor(this@PixelShadeTriggerService))
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, visibleHeight, Gravity.BOTTOM))
            }
        }
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = (centerX - width / 2).coerceIn(0, (screenW - width).coerceAtLeast(0))
            y = 0
        }
        trackTrigger(view)
        runCatching {
            wm.addView(view, lp)
            triggers += view
        }
    }

    private fun addSideTrigger(edge: TriggerEdge) {
        val left = edge == TriggerEdge.LEFT
        val d = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels
        val widthDp = if (left) PixelShadeConfig.leftWidthDp(this) else PixelShadeConfig.rightWidthDp(this)
        val heightDp = if (left) PixelShadeConfig.leftHeightDp(this) else PixelShadeConfig.rightHeightDp(this)
        val yPct = if (left) PixelShadeConfig.leftYPercent(this) else PixelShadeConfig.rightYPercent(this)
        val width = (widthDp * d).roundToInt().coerceAtLeast(1)
        val height = (heightDp * d).roundToInt().coerceAtLeast(1)
        val centerY = (screenH * yPct.coerceIn(0f, 100f) / 100f).roundToInt()
        val view = View(this).apply {
            setBackgroundColor(0x00000000)
            setOnTouchListener(GestureListener(edge, allowBrightness = false))
        }
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (left) Gravity.START else Gravity.END) or Gravity.TOP
            y = (centerY - height / 2).coerceIn(0, (screenH - height).coerceAtLeast(0))
        }
        trackTrigger(view)
        runCatching {
            wm.addView(view, lp)
            triggers += view
        }
    }

    private fun trackTrigger(view: View) {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) {
                registeredTrigger = true
            }

            override fun onViewDetachedFromWindow(detached: View) {
                mainHandler.post {
                    registeredTrigger = triggers.any { it.isAttachedToWindow }
                    if (!rebuildingTriggers && PixelShadeRuntime.isEnabled(this@PixelShadeTriggerService) &&
                        !PixelShadeAccessibilityService.hasWorkingTrigger() && !registeredTrigger
                    ) {
                        failActivation(null, "Pixel Shade lost its last attached gesture trigger")
                    }
                }
            }
        })
    }
    private inner class GestureListener(
        private val edge: TriggerEdge,
        private val allowBrightness: Boolean
    ) : View.OnTouchListener {
        private var x0 = 0f
        private var y0 = 0f
        private var b0 = 128
        private var mode = 0
        private var lastBottomTapAt = 0L
        private var lastBottomTapX = 0f
        private var lastBottomTapY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (!PixelShadeRuntime.isEnabled(this@PixelShadeTriggerService)) return false
            val d = resources.displayMetrics.density
            val deadZone = PixelShadeConfig.deadZoneDp(this@PixelShadeTriggerService) * d
            val pullDistance = PixelShadeConfig.pullDistanceDp(this@PixelShadeTriggerService) * d
            val brightnessEnabled = allowBrightness &&
                PixelShadeConfig.brightnessEnabled(this@PixelShadeTriggerService) &&
                Settings.System.canWrite(this@PixelShadeTriggerService)
            val sensitivity = PixelShadeConfig.brightnessSensitivity(this@PixelShadeTriggerService)
            val reverse = PixelShadeConfig.brightnessReverse(this@PixelShadeTriggerService)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX
                    y0 = e.rawY
                    mode = 0
                    b0 = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    if (PixelShadeConfig.shouldSuppressStockShade(this@PixelShadeTriggerService)) PixelShadeAccessibilityService.requestCollapse()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (mode == 0) {
                        if (brightnessEnabled && abs(dx) >= deadZone && abs(dx) > abs(dy) * 1.2f) {
                            mode = 2
                        } else if ((edge != TriggerEdge.BOTTOM || PixelShadeConfig.bottomActivation(this@PixelShadeTriggerService) == BottomTriggerActivation.SWIPE_UP) && edgeGestureDistance(dx, dy) >= deadZone) {
                            mode = 1
                        }
                    }
                    if (mode == 2 && Settings.System.canWrite(this@PixelShadeTriggerService)) {
                        val direction = if (reverse) -1f else 1f
                        val linearDelta = dx / resources.displayMetrics.widthPixels * 255f * sensitivity * direction
                        val target = (b0 + linearDelta).roundToInt().coerceIn(1, 255)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    val openedBySwipe = mode == 1 && edgeGestureDistance(dx, dy) >= pullDistance
                    val openedByDoubleTap = edge == TriggerEdge.BOTTOM &&
                        PixelShadeConfig.bottomActivation(this@PixelShadeTriggerService) == BottomTriggerActivation.DOUBLE_TAP &&
                        isBottomDoubleTap(v, e, deadZone)
                    if (openedBySwipe || openedByDoubleTap) {
                        if (PixelShadeConfig.vibrateOnTouch(this@PixelShadeTriggerService)) {
                            runCatching { v.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
                        }
                        openShade()
                    }
                    mode = 0
                }
                MotionEvent.ACTION_CANCEL -> mode = 0
            }
            return true
        }

        private fun edgeGestureDistance(dx: Float, dy: Float): Float = when (edge) {
            TriggerEdge.BOTTOM -> if (dy < 0f && abs(dy) > abs(dx) * 1.15f) -dy else 0f
            TriggerEdge.TOP,
            TriggerEdge.LEFT,
            TriggerEdge.RIGHT -> if (dy > 0f && abs(dy) > abs(dx) * 1.15f) dy else 0f
        }

        private fun isBottomDoubleTap(v: View, e: MotionEvent, deadZone: Float): Boolean {
            val isTap = abs(e.rawX - x0) <= deadZone && abs(e.rawY - y0) <= deadZone
            if (!isTap) {
                lastBottomTapAt = 0L
                return false
            }
            val now = e.eventTime
            val slop = ViewConfiguration.get(v.context).scaledDoubleTapSlop.toFloat()
            val doubleTap = lastBottomTapAt > 0L &&
                now - lastBottomTapAt <= ViewConfiguration.getDoubleTapTimeout() &&
                abs(e.rawX - lastBottomTapX) <= slop && abs(e.rawY - lastBottomTapY) <= slop
            if (doubleTap) {
                lastBottomTapAt = 0L
                return true
            }
            lastBottomTapAt = now
            lastBottomTapX = e.rawX
            lastBottomTapY = e.rawY
            return false
        }
    }

    private fun openShade() {
        if (!PixelShadeRuntime.isEnabled(this)) return
        val launchShade = {
            startActivity(Intent(this, PixelShadePanelV2Activity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        if (PixelShadeConfig.shouldSuppressStockShade(this)) {
            PixelShadeAccessibilityService.requestCollapse()
            StatusBarSuppression.collapsePanels(this, launchShade)
        } else {
            launchShade()
        }
    }

    private fun serviceNotification(text: String): Notification {
        val openSettings = PendingIntent.getActivity(
            this,
            1717,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "pixel_shade")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pixel Shade active")
            .setContentText(text)
            .setContentIntent(openSettings)
            .setOngoing(true)
            .build()
    }

    private fun updateServiceNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1717, serviceNotification(text))
    }

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel("pixel_shade", "Pixel Shade", NotificationManager.IMPORTANCE_MIN)
        )
    }

    override fun onDestroy() {
        // onDestroy is too late to begin an intentional restore. All normal stop paths
        // restore first; this only schedules a new foreground recovery owner if Android
        // tears the service down while the persistent disabled marker is still set.
        val accessibilityFallbackReady = if (PixelShadeRuntime.isEnabled(this)) {
            PixelShadeAccessibilityService.requestTriggerRefresh()
        } else {
            false
        }
        val restartRecovery = PixelShadeRuntime.statusBarWasDisabled(this) && !accessibilityFallbackReady

        rebuildingTriggers = true
        triggers.forEach { runCatching { wm.removeView(it) } }
        triggers.clear()
        registeredTrigger = false
        rebuildingTriggers = false
        if (instance === this) instance = null
        runCatching { Shizuku.removeBinderReceivedListener(shizukuBinderReceived) }
        super.onDestroy()

        if (restartRecovery) requestStockShadeRecovery(applicationContext)
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
