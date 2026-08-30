package com.crimson.pixelshade

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

        fun requestCollapse() {
            instance?.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }

        fun requestPowerDialog(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true

        fun requestTriggerRefresh() {
            instance?.let {
                it.rebuildTopTrigger()
                StatusBarSuppression.sync(it)
            }
        }
    }

    private lateinit var wm: WindowManager
    private var topTrigger: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        StatusBarSuppression.restoreIfNeeded(this)
        rebuildTopTrigger()
        StatusBarSuppression.sync(this)
    }

    fun rebuildTopTrigger() {
        if (!::wm.isInitialized) return
        topTrigger?.let { runCatching { wm.removeView(it) } }
        topTrigger = null
        if (!PixelShadeRuntime.isEnabled(this)) return

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
        }
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
                    if (PixelShadeConfig.suppressStockShade(this@PixelShadeAccessibilityService)) requestCollapse()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - x0
                    val dy = event.rawY - y0
                    if (mode == 0) {
                        if (PixelShadeConfig.brightnessEnabled(this@PixelShadeAccessibilityService) && abs(dx) >= deadZone && abs(dx) > abs(dy) * 1.2f) mode = 2
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

    private fun openShade() {
        if (!PixelShadeRuntime.isEnabled(this)) return
        if (PixelShadeConfig.suppressStockShade(this)) requestCollapse()
        startActivity(Intent(this, PixelShadePanelV2Activity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onDestroy() {
        topTrigger?.let { runCatching { wm.removeView(it) } }
        topTrigger = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (PixelShadeRuntime.isEnabled(this) && event?.packageName == "com.android.systemui" && PixelShadeConfig.suppressStockShade(this)) {
            val type = event.eventType
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                requestCollapse()
            }
        }
    }

    override fun onInterrupt() = Unit
}
