package com.crimson.pixelshade

import android.content.Context
import android.graphics.Color

object PixelShadeConfig {
    private const val PREFS = "pixel_shade_settings"

    const val KEY_TRIGGER_HEIGHT_DP = "trigger_height_dp"
    const val KEY_TRIGGER_VISIBLE_DP = "trigger_visible_dp"
    const val KEY_TRIGGER_OFFSET_DP = "trigger_offset_dp"
    const val KEY_TOP_WIDTH_PERCENT = "top_width_percent"
    const val KEY_TOP_X_PERCENT = "top_x_percent"
    const val KEY_TRIGGER_COLOR = "trigger_color"
    const val KEY_PULL_DISTANCE_DP = "pull_distance_dp"

    const val KEY_LEFT_ENABLED = "left_enabled"
    const val KEY_LEFT_WIDTH_DP = "left_width_dp"
    const val KEY_LEFT_HEIGHT_DP = "left_height_dp"
    const val KEY_LEFT_Y_PERCENT = "left_y_percent"
    const val KEY_RIGHT_ENABLED = "right_enabled"
    const val KEY_RIGHT_WIDTH_DP = "right_width_dp"
    const val KEY_RIGHT_HEIGHT_DP = "right_height_dp"
    const val KEY_RIGHT_Y_PERCENT = "right_y_percent"

    const val KEY_BRIGHTNESS_ENABLED = "brightness_enabled"
    const val KEY_BRIGHTNESS_SENSITIVITY = "brightness_sensitivity"
    const val KEY_BRIGHTNESS_REVERSE = "brightness_reverse"
    const val KEY_GESTURE_DEAD_ZONE_DP = "gesture_dead_zone_dp"
    const val KEY_TAP_ACTION = "tap_action"
    const val KEY_SUPPRESS_STOCK_SHADE = "suppress_stock_shade"

    const val KEY_PANEL_OPACITY = "panel_opacity"
    const val KEY_BLUR_RADIUS = "blur_radius"
    const val KEY_PANEL_CORNER_DP = "panel_corner_dp"
    const val KEY_TILE_CORNER_DP = "tile_corner_dp"

    const val KEY_OPEN_ANIMATION = "open_animation"
    const val KEY_OPEN_DURATION_MS = "open_duration_ms"
    const val KEY_CLOSE_DURATION_MS = "close_duration_ms"
    const val KEY_ANIMATION_OVERSHOOT = "animation_overshoot"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun triggerHeightDp(context: Context) = prefs(context).getFloat(KEY_TRIGGER_HEIGHT_DP, 10f)
    fun triggerVisibleDp(context: Context) = prefs(context).getFloat(KEY_TRIGGER_VISIBLE_DP, 2f)
    fun triggerOffsetDp(context: Context) = prefs(context).getFloat(KEY_TRIGGER_OFFSET_DP, 0f)
    fun topWidthPercent(context: Context) = prefs(context).getFloat(KEY_TOP_WIDTH_PERCENT, 100f)
    fun topXPercent(context: Context) = prefs(context).getFloat(KEY_TOP_X_PERCENT, 50f)
    fun triggerColorString(context: Context) = prefs(context).getString(KEY_TRIGGER_COLOR, "#66FFFFFF") ?: "#66FFFFFF"
    fun triggerColor(context: Context): Int = runCatching { Color.parseColor(triggerColorString(context)) }.getOrDefault(Color.argb(102, 255, 255, 255))
    fun pullDistanceDp(context: Context) = prefs(context).getFloat(KEY_PULL_DISTANCE_DP, 28f)

    fun leftEnabled(context: Context) = prefs(context).getBoolean(KEY_LEFT_ENABLED, false)
    fun leftWidthDp(context: Context) = prefs(context).getFloat(KEY_LEFT_WIDTH_DP, 18f)
    fun leftHeightDp(context: Context) = prefs(context).getFloat(KEY_LEFT_HEIGHT_DP, 180f)
    fun leftYPercent(context: Context) = prefs(context).getFloat(KEY_LEFT_Y_PERCENT, 40f)
    fun rightEnabled(context: Context) = prefs(context).getBoolean(KEY_RIGHT_ENABLED, false)
    fun rightWidthDp(context: Context) = prefs(context).getFloat(KEY_RIGHT_WIDTH_DP, 18f)
    fun rightHeightDp(context: Context) = prefs(context).getFloat(KEY_RIGHT_HEIGHT_DP, 180f)
    fun rightYPercent(context: Context) = prefs(context).getFloat(KEY_RIGHT_Y_PERCENT, 40f)

    fun brightnessEnabled(context: Context) = prefs(context).getBoolean(KEY_BRIGHTNESS_ENABLED, true)
    fun brightnessSensitivity(context: Context) = prefs(context).getFloat(KEY_BRIGHTNESS_SENSITIVITY, 1f)
    fun brightnessReverse(context: Context) = prefs(context).getBoolean(KEY_BRIGHTNESS_REVERSE, false)
    fun deadZoneDp(context: Context) = prefs(context).getFloat(KEY_GESTURE_DEAD_ZONE_DP, 24f)
    fun tapAction(context: Context) = prefs(context).getString(KEY_TAP_ACTION, "none") ?: "none"
    fun suppressStockShade(context: Context) = prefs(context).getBoolean(KEY_SUPPRESS_STOCK_SHADE, true)

    fun panelOpacity(context: Context) = prefs(context).getFloat(KEY_PANEL_OPACITY, 0.92f)
    fun blurRadius(context: Context) = prefs(context).getFloat(KEY_BLUR_RADIUS, 24f)
    fun panelCornerDp(context: Context) = prefs(context).getFloat(KEY_PANEL_CORNER_DP, 32f)
    fun tileCornerDp(context: Context) = prefs(context).getFloat(KEY_TILE_CORNER_DP, 24f)

    fun openAnimation(context: Context) = prefs(context).getString(KEY_OPEN_ANIMATION, "pixel") ?: "pixel"
    fun openDurationMs(context: Context) = prefs(context).getInt(KEY_OPEN_DURATION_MS, 320)
    fun closeDurationMs(context: Context) = prefs(context).getInt(KEY_CLOSE_DURATION_MS, 220)
    fun animationOvershoot(context: Context) = prefs(context).getFloat(KEY_ANIMATION_OVERSHOOT, 0.08f)
}
