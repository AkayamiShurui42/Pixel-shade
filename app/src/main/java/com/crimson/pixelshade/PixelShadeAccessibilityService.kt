package com.crimson.pixelshade

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PixelShadeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
