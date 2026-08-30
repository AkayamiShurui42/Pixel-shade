package com.crimson.pixelshade

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

class PixelShadeTriggerService : Service() {
    private lateinit var wm: WindowManager
    private val triggers = mutableListOf<View>()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1717, Notification.Builder(this, "pixel_shade")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pixel Shade active")
            .setContentText("Gesture triggers are running")
            .build())
        StatusBarSuppression.restoreIfNeeded(this)
        StatusBarSuppression.sync(this)
        rebuildTriggers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!PixelShadeRuntime.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        StatusBarSuppression.sync(this)
        rebuildTriggers()
        PixelShadeAccessibilityService.requestTriggerRefresh()
        return START_STICKY
    }

    private fun rebuildTriggers() {
        triggers.forEach { runCatching { wm.removeView(it) } }
        triggers.clear()
        if (!PixelShadeRuntime.isEnabled(this)) return
        if (!Settings.canDrawOverlays(this)) return
        if (!PixelShadeAccessibilityService.isConnected()) addTopTrigger()
        if (PixelShadeConfig.leftEnabled(this)) addSideTrigger(true)
        if (PixelShadeConfig.rightEnabled(this)) addSideTrigger(false)
    }

    private fun addTopTrigger() {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val height = (PixelShadeConfig.triggerHeightDp(this) * d).roundToInt().coerceAtLeast(1)
        val visibleHeight = (PixelShadeConfig.triggerVisibleDp(this) * d).roundToInt().coerceIn(0, height)
        val width = (screenW * PixelShadeConfig.topWidthPercent(this).coerceIn(10f, 100f) / 100f).roundToInt()
        val centerX = (screenW * PixelShadeConfig.topXPercent(this).coerceIn(0f, 100f) / 100f).roundToInt()
        val view = FrameLayout(this).apply {
            setBackgroundColor(0x00000000)
            setOnTouchListener(GestureListener())
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
        wm.addView(view, lp)
        triggers += view
    }

    private fun addSideTrigger(left: Boolean) {
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
            setOnTouchListener(GestureListener())
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
        wm.addView(view, lp)
        triggers += view
    }

    private inner class GestureListener : View.OnTouchListener {
        private var x0 = 0f
        private var y0 = 0f
        private var b0 = 128
        private var mode = 0

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (!PixelShadeRuntime.isEnabled(this@PixelShadeTriggerService)) return false
            val d = resources.displayMetrics.density
            val deadZone = PixelShadeConfig.deadZoneDp(this@PixelShadeTriggerService) * d
            val pullDistance = PixelShadeConfig.pullDistanceDp(this@PixelShadeTriggerService) * d
            val brightnessEnabled = PixelShadeConfig.brightnessEnabled(this@PixelShadeTriggerService)
            val sensitivity = PixelShadeConfig.brightnessSensitivity(this@PixelShadeTriggerService)
            val reverse = PixelShadeConfig.brightnessReverse(this@PixelShadeTriggerService)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX
                    y0 = e.rawY
                    mode = 0
                    b0 = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    if (PixelShadeConfig.suppressStockShade(this@PixelShadeTriggerService)) PixelShadeAccessibilityService.requestCollapse()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (mode == 0) {
                        if (brightnessEnabled && abs(dx) >= deadZone && abs(dx) > abs(dy) * 1.2f) mode = 2
                        else if (dy >= deadZone && abs(dy) > abs(dx) * 1.2f) mode = 1
                    }
                    if (mode == 2 && Settings.System.canWrite(this@PixelShadeTriggerService)) {
                        val direction = if (reverse) -1f else 1f
                        val delta = dx / resources.displayMetrics.widthPixels * 255f * sensitivity * direction
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (b0 + delta).roundToInt().coerceIn(1, 255))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dy = e.rawY - y0
                    if (mode == 1 && dy >= pullDistance) openShade()
                }
                MotionEvent.ACTION_CANCEL -> mode = 0
            }
            return true
        }
    }

    private fun openShade() {
        if (!PixelShadeRuntime.isEnabled(this)) return
        if (PixelShadeConfig.suppressStockShade(this)) PixelShadeAccessibilityService.requestCollapse()
        startActivity(Intent(this, PixelShadePanelV2Activity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel("pixel_shade", "Pixel Shade", NotificationManager.IMPORTANCE_MIN)
        )
    }

    override fun onDestroy() {
        triggers.forEach { runCatching { wm.removeView(it) } }
        triggers.clear()
        if (!PixelShadeRuntime.isEnabled(this)) {
            StatusBarSuppression.setExpansionDisabled(this, false)
        }
        PixelShadeAccessibilityService.requestTriggerRefresh()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
