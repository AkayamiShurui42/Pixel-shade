package com.crimson.pixelshade

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PixelShadeAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: PixelShadeAccessibilityService? = null
        fun requestCollapse() {
            instance?.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
