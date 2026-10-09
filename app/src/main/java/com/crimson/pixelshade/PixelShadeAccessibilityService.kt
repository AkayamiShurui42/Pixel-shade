package com.crimson.pixelshade

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

class PixelShadeAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: PixelShadeAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun hasWorkingTrigger(): Boolean = instance?.topTriggerAttached == true

        fun requestCollapse() {
            instance?.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }

        fun requestPowerDialog(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true

        fun requestTriggerRefresh(): Boolean = instance?.rebuildTopTrigger() == true
    }

    private lateinit var wm: WindowManager
    @Volatile private var topTrigger: View? = null
    @Volatile private var topTriggerAttached = false
    @Volatile private var rebuildingTopTrigger = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val accessibilityReady = rebuildTopTrigger()
        val overlayFallbackReady = PixelShadeTriggerService.requestTriggerRefresh()
        if (accessibilityReady || overlayFallbackReady) {
            StatusBarSuppression.sync(this)
        } else if (PixelShadeRuntime.isEnabled(this) && !PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
            StatusBarSuppression.restoreTemporarily(this) { restored, _ ->
                if (!restored) PixelShadeTriggerService.requestStockShadeRecovery(this)
            }
        } else if (PixelShadeRuntime.isEnabled(this)) {
            PixelShadeTriggerService.requestStockShadeRecovery(this)
        } else {
            StatusBarSuppression.restoreIfNeeded(this)
        }
    }

    fun rebuildTopTrigger(): Boolean {
        rebuildingTopTrigger = true
        if (!::wm.isInitialized) {
            rebuildingTopTrigger = false
            return false
        }
        topTrigger?.let { runCatching { wm.removeView(it) } }
        topTrigger = null
        topTriggerAttached = false
        if (!PixelShadeRuntime.isEnabled(this) || !PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
            rebuildingTopTrigger = false
            return false
        }

        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val touchHeight = (PixelShadeConfig.triggerHeightDp(this) * density).roundToInt().coerceAtLeast(1)
        val visibleHeight = (PixelShadeConfig.triggerVisibleDp(this) * density).roundToInt().coerceIn(0, touchHeight)
        val width = (screenW * PixelShadeConfig.topWidthPercent(this).coerceIn(10f, 100f) / 100f).roundToInt()
        val centerX = (screenW * PixelShadeConfig.topXPercent(this).coerceIn(0f, 100f) / 100f).roundToInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
            isClickable = true
            setOnTouchListener(TopGestureListener())
            if (visibleHeight > 0) {
                addView(View(this@PixelShadeAccessibilityService).apply {
                    setBackgroundColor(PixelShadeConfig.triggerColor(this@PixelShadeAccessibilityService))
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, visibleHeight, Gravity.TOP))
            }
        }

        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) {
                if (topTrigger === attached) topTriggerAttached = true
            }

            override fun onViewDetachedFromWindow(detached: View) {
                if (topTrigger === detached) {
                    topTriggerAttached = false
                    if (!rebuildingTopTrigger) handleLostTopTrigger()
                }
            }
        })

        val lp = WindowManager.LayoutParams(
            width,
            touchHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (centerX - width / 2).coerceIn(0, (screenW - width).coerceAtLeast(0))
            y = (PixelShadeConfig.triggerOffsetDp(this@PixelShadeAccessibilityService) * density).roundToInt()
        }

        runCatching {
            wm.addView(root, lp)
            topTrigger = root
            topTriggerAttached = root.isAttachedToWindow
        }
        rebuildingTopTrigger = false
        return topTriggerAttached
    }

    private inner class TopGestureListener : View.OnTouchListener {
        private var x0 = 0f
        private var y0 = 0f
        private var brightness0 = 128
        private var mode = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (!PixelShadeRuntime.isEnabled(this@PixelShadeAccessibilityService)) return false
            val density = resources.displayMetrics.density
            val deadZone = PixelShadeConfig.deadZoneDp(this@PixelShadeAccessibilityService) * density
            val pullDistance = PixelShadeConfig.pullDistanceDp(this@PixelShadeAccessibilityService) * density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = event.rawX
                    y0 = event.rawY
                    mode = 0
                    brightness0 = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    if (PixelShadeConfig.shouldSuppressStockShade(this@PixelShadeAccessibilityService)) requestCollapse()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - x0
                    val dy = event.rawY - y0
                    if (mode == 0) {
                        if (PixelShadeConfig.brightnessEnabled(this@PixelShadeAccessibilityService) &&
                            Settings.System.canWrite(this@PixelShadeAccessibilityService) && abs(dx) >= deadZone && abs(dx) > abs(dy) * 1.2f) mode = 2
                        else if (dy >= deadZone && abs(dy) > abs(dx) * 1.2f) mode = 1
                    }
                    if (mode == 2 && Settings.System.canWrite(this@PixelShadeAccessibilityService)) {
                        val direction = if (PixelShadeConfig.brightnessReverse(this@PixelShadeAccessibilityService)) -1f else 1f
                        val delta = dx / resources.displayMetrics.widthPixels * 255f * PixelShadeConfig.brightnessSensitivity(this@PixelShadeAccessibilityService) * direction
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (brightness0 + delta).roundToInt().coerceIn(1, 255))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - y0
                    if (mode == 1 && dy >= pullDistance) openShade()
                }
                MotionEvent.ACTION_CANCEL -> mode = 0
            }
            return true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!PixelShadeRuntime.isEnabled(this)) return
        if (!PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
            StatusBarSuppression.restoreTemporarily(this) { restored, _ ->
                if (restored) {
                    rebuildTopTrigger()
                    PixelShadeTriggerService.requestTriggerRefresh()
                } else {
                    PixelShadeTriggerService.requestStockShadeRecovery(this)
                }
            }
            return
        }
        val accessibilityReady = rebuildTopTrigger()
        val overlayReady = PixelShadeTriggerService.requestTriggerRefresh()
        if (accessibilityReady || overlayReady) {
            StatusBarSuppression.sync(this)
        } else {
            PixelShadeTriggerService.requestStockShadeRecovery(this)
        }
    }

    private fun handleLostTopTrigger() {
        if (!PixelShadeRuntime.isEnabled(this)) return
        if (!PixelShadeConfig.triggersAllowedInCurrentConfiguration(this)) {
            StatusBarSuppression.restoreTemporarily(this) { restored, _ ->
                if (!restored) PixelShadeTriggerService.requestStockShadeRecovery(this)
            }
        } else if (!PixelShadeTriggerService.requestTriggerRefresh()) {
            PixelShadeTriggerService.requestStockShadeRecovery(this)
        }
    }
    private fun openShade() {
        if (!PixelShadeRuntime.isEnabled(this)) return
        val launchShade = {
            startActivity(Intent(this, PixelShadePanelV2Activity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        if (PixelShadeConfig.shouldSuppressStockShade(this)) {
            requestCollapse()
            StatusBarSuppression.collapsePanels(this, launchShade)
        } else {
            launchShade()
        }
    }

    override fun onDestroy() {
        // Force an application-overlay top trigger before removing the Accessibility
        // overlay. If that handoff is unavailable, a foreground recovery service owns
        // restoration and remains visible until Android's stock shade is verified.
        val runtimeEnabled = PixelShadeRuntime.isEnabled(this)
        val fallbackReady = runtimeEnabled && PixelShadeTriggerService.requestRecoveryHandoff()

        rebuildingTopTrigger = true
        topTrigger?.let { runCatching { wm.removeView(it) } }
        topTrigger = null
        topTriggerAttached = false
        rebuildingTopTrigger = false
        if (instance === this) instance = null

        if (runtimeEnabled && !fallbackReady) {
            if (PixelShadeRuntime.statusBarWasDisabled(this)) {
                val recoveryStarted = PixelShadeTriggerService.requestStockShadeRecovery(this)
                if (!recoveryStarted) {
                    StatusBarSuppression.restore(this) { restored, _ ->
                        if (restored) PixelShadeRuntime.setEnabled(this, false)
                    }
                }
            } else {
                PixelShadeRuntime.setEnabled(this, false)
            }
        }
        super.onDestroy()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (PixelShadeRuntime.isEnabled(this) && event?.packageName == "com.android.systemui" && PixelShadeConfig.shouldSuppressStockShade(this)) {
            val type = event.eventType
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                requestCollapse()
            }
        }
    }

    override fun onInterrupt() = Unit
}
