package com.crimson.pixelshade

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.*
import kotlin.math.abs
import kotlin.math.roundToInt

class PixelShadeTriggerService : Service() {
    private lateinit var wm: WindowManager
    private var trigger: View? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(
            1717,
            Notification.Builder(this, "pixel_shade")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Pixel Shade active")
                .setContentText("Top-edge gesture trigger is running")
                .build()
        )
        showTrigger()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showTrigger()
        return START_STICKY
    }

    private fun showTrigger() {
        trigger?.let { runCatching { wm.removeView(it) } }
        trigger = null
        if (!Settings.canDrawOverlays(this)) return

        val density = resources.displayMetrics.density
        val triggerHeight = PixelShadeConfig.triggerHeightDp(this).coerceAtLeast(1f)
        val visibleHeight = PixelShadeConfig.triggerVisibleDp(this).coerceIn(0f, triggerHeight)
        val offset = PixelShadeConfig.triggerOffsetDp(this).coerceAtLeast(0f)
        val h = (triggerHeight * density).roundToInt().coerceAtLeast(1)

        val v = View(this).apply {
            setBackgroundColor(if (visibleHeight > 0f) Color.argb(28, 255, 255, 255) else Color.TRANSPARENT)
            setOnTouchListener(GestureListener())
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = (offset * density).roundToInt()
        }

        wm.addView(v, lp)
        trigger = v
    }

    private inner class GestureListener : View.OnTouchListener {
        private var x0 = 0f
        private var y0 = 0f
        private var b0 = 128
        private var mode = 0

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val d = resources.displayMetrics.density
            val deadZone = PixelShadeConfig.deadZoneDp(this@PixelShadeTriggerService) * d
            val brightnessEnabled = PixelShadeConfig.brightnessEnabled(this@PixelShadeTriggerService)
            val sensitivity = PixelShadeConfig.brightnessSensitivity(this@PixelShadeTriggerService)
            val reverse = PixelShadeConfig.brightnessReverse(this@PixelShadeTriggerService)

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX
                    y0 = e.rawY
                    mode = 0
                    b0 = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
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
                        val value = (b0 + delta).roundToInt().coerceIn(1, 255)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (mode == 1 || dy >= deadZone) {
                        openMain()
                    } else if (abs(dx) < deadZone && abs(dy) < deadZone) {
                        when (PixelShadeConfig.tapAction(this@PixelShadeTriggerService)) {
                            "shade", "quick_settings", "settings" -> openMain()
                        }
                    }
                }
            }
            return true
        }
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("pixel_shade", "Pixel Shade", NotificationManager.IMPORTANCE_MIN)
        )
    }

    override fun onDestroy() {
        trigger?.let { runCatching { wm.removeView(it) } }
        trigger = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
