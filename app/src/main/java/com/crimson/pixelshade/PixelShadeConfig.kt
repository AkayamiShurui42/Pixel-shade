package com.crimson.pixelshade

import android.content.Context

object PixelShadeConfig {
    private const val PREFS = "pixel_shade_settings"

    const val KEY_TRIGGER_HEIGHT_DP = "trigger_height_dp"
    const val KEY_TRIGGER_VISIBLE_DP = "trigger_visible_dp"
    const val KEY_TRIGGER_OFFSET_DP = "trigger_offset_dp"
    const val KEY_BRIGHTNESS_ENABLED = "brightness_enabled"
    const val KEY_BRIGHTNESS_SENSITIVITY = "brightness_sensitivity"
    const val KEY_BRIGHTNESS_REVERSE = "brightness_reverse"
    const val KEY_GESTURE_DEAD_ZONE_DP = "gesture_dead_zone_dp"
    const val KEY_TAP_ACTION = "tap_action"
    const val KEY_PANEL_OPACITY = "panel_opacity"
    const val KEY_BLUR_RADIUS = "blur_radius"
    const val KEY_PANEL_CORNER_DP = "panel_corner_dp"
    const val KEY_TILE_CORNER_DP = "tile_corner_dp"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun triggerHeightDp(context: Context) = prefs(context).getFloat(KEY_TRIGGER_HEIGHT_DP, 10f)
    fun triggerVisibleDp(context: Context) = prefs(context).getFloat(KEY_TRIGGER_VISIBLE_DP, 2f)
    fun triggerOffsetDp(context: Context) = prefs(context).getFloat(KEY_TRIGGER_OFFSET_DP, 0f)
    fun brightnessEnabled(context: Context) = prefs(context).getBoolean(KEY_BRIGHTNESS_ENABLED, true)
    fun brightnessSensitivity(context: Context) = prefs(context).getFloat(KEY_BRIGHTNESS_SENSITIVITY, 1f)
    fun brightnessReverse(context: Context) = prefs(context).getBoolean(KEY_BRIGHTNESS_REVERSE, false)
    fun deadZoneDp(context: Context) = prefs(context).getFloat(KEY_GESTURE_DEAD_ZONE_DP, 24f)
    fun tapAction(context: Context) = prefs(context).getString(KEY_TAP_ACTION, "none") ?: "none"
    fun panelOpacity(context: Context) = prefs(context).getFloat(KEY_PANEL_OPACITY, 0.92f)
    fun blurRadius(context: Context) = prefs(context).getFloat(KEY_BLUR_RADIUS, 24f)
    fun panelCornerDp(context: Context) = prefs(context).getFloat(KEY_PANEL_CORNER_DP, 32f)
    fun tileCornerDp(context: Context) = prefs(context).getFloat(KEY_TILE_CORNER_DP, 24f)
}
