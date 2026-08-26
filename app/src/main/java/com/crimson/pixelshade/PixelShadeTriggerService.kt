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
        startForeground(1717, Notification.Builder(this, "pixel_shade")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pixel Shade active")
            .setContentText("Top-edge gesture trigger is running")
            .build())
        showTrigger()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showTrigger()
        return START_STICKY
    }

    private fun showTrigger() {
        trigger?.let { runCatching { wm.removeView(it) } }
        if (!Settings.canDrawOverlays(this)) return
        val density = resources.displayMetrics.density
        val h = (18f * density).roundToInt().coerceAtLeast(1)
        val v = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener(GestureListener())
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
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
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = e.rawX; y0 = e.rawY; mode = 0
                    b0 = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - x0
                    val dy = e.rawY - y0
                    if (mode == 0) {
                        if (abs(dx) >= 24f*d && abs(dx) > abs(dy)*1.2f) mode = 2
                        else if (dy >= 28f*d && abs(dy) > abs(dx)*1.2f) mode = 1
                    }
                    if (mode == 2 && Settings.System.canWrite(this@PixelShadeTriggerService)) {
                        val value = (b0 + dx/resources.displayMetrics.widthPixels*255f).roundToInt().coerceIn(1,255)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                    }
                }
                MotionEvent.ACTION_UP -> if (mode == 1 || e.rawY-y0 >= 28f*d) {
                    startActivity(Intent(this@PixelShadeTriggerService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                }
            }
            return true
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("pixel_shade", "Pixel Shade", NotificationManager.IMPORTANCE_MIN))
    }

    override fun onDestroy() {
        trigger?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
