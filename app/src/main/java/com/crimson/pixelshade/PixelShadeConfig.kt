package com.crimson.pixelshade

import android.content.Context
import android.graphics.Color

object PixelShadeConfig {
    private const val PREFS = "pixel_shade_settings"

    // Trigger geometry
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
    const val KEY_BOTTOM_ENABLED = "bottom_enabled"
    const val KEY_BOTTOM_WIDTH_PERCENT = "bottom_width_percent"
    const val KEY_BOTTOM_HEIGHT_DP = "bottom_height_dp"
    const val KEY_BOTTOM_X_PERCENT = "bottom_x_percent"

    // Trigger visibility / environment rules
    const val KEY_HIDE_HANDLE_ICON = "hide_handle_icon"
    const val KEY_HIDE_IN_FULLSCREEN = "hide_in_fullscreen"
    const val KEY_HIDE_IN_LANDSCAPE = "hide_in_landscape"
    const val KEY_HIDE_WHEN_KEYBOARD_OPEN = "hide_when_keyboard_open"
    const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"

    // Gesture behavior
    const val KEY_BRIGHTNESS_ENABLED = "brightness_enabled"
    const val KEY_BRIGHTNESS_SENSITIVITY = "brightness_sensitivity"
    const val KEY_BRIGHTNESS_REVERSE = "brightness_reverse"
    const val KEY_SMOOTH_BRIGHTNESS = "smooth_brightness"
    const val KEY_GESTURE_DEAD_ZONE_DP = "gesture_dead_zone_dp"
    const val KEY_TAP_ACTION = "tap_action"
    const val KEY_SUPPRESS_STOCK_SHADE = "suppress_stock_shade"
    const val KEY_VIBRATE_ON_TOUCH = "vibrate_on_touch"
    const val KEY_USE_DEVICE_HAPTICS = "use_device_haptics"
    const val KEY_AUTO_CLOSE_TILE = "auto_close_tile"

    // Panel / tile layout
    const val KEY_PANEL_OPACITY = "panel_opacity"
    const val KEY_BLUR_RADIUS = "blur_radius"
    const val KEY_PANEL_CORNER_DP = "panel_corner_dp"
    const val KEY_PANEL_PADDING_DP = "panel_padding_dp"
    const val KEY_TILE_CORNER_DP = "tile_corner_dp"
    const val KEY_TILE_HEIGHT_DP = "tile_height_dp"
    const val KEY_LAYOUT_ROWS = "layout_rows"
    const val KEY_LAYOUT_COLUMNS = "layout_columns"
    const val KEY_LAYOUT_SMALL_COLUMNS = "layout_small_columns"
    const val KEY_SHOW_SYSTEM_ICONS = "show_system_icons"
    const val KEY_SHOW_PANEL_HEADER = "show_panel_header"
    const val KEY_SHOW_PANEL_FOOTER = "show_panel_footer"
    const val KEY_HIDE_TILE_TEXT = "hide_tile_text"
    const val KEY_CROP_APP_ICONS = "crop_app_icons"
    const val KEY_USE_24_HOUR_CLOCK = "use_24_hour_clock"

    // Notification presentation / filtering
    const val KEY_SHOW_NOTIFICATIONS = "show_notifications"
    const val KEY_HIDE_PERSISTENT_NOTIFICATIONS = "hide_persistent_notifications"
    const val KEY_ONLY_MEDIA_NOTIFICATIONS = "only_media_notifications"
    const val KEY_REMOVE_NOTIFICATION_SPACING = "remove_notification_spacing"
    const val KEY_AUTO_EXPAND_NOTIFICATIONS = "auto_expand_notifications"
    const val KEY_QUICK_CLEAR_ALL = "quick_clear_all"
    const val KEY_AUTO_CLOSE_AFTER_CLEAR = "auto_close_after_clear"
    const val KEY_DYNAMIC_NOTIFICATION_COLORS = "dynamic_notification_colors"

    // Header / status behavior
    const val KEY_SHOW_WIFI_SSID = "show_wifi_ssid"
    const val KEY_SHOW_NETWORK_TYPE = "show_network_type"

    // Motion
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
    fun bottomEnabled(context: Context) = prefs(context).getBoolean(KEY_BOTTOM_ENABLED, false)
    fun bottomWidthPercent(context: Context) = prefs(context).getFloat(KEY_BOTTOM_WIDTH_PERCENT, 100f)
    fun bottomHeightDp(context: Context) = prefs(context).getFloat(KEY_BOTTOM_HEIGHT_DP, 12f)
    fun bottomXPercent(context: Context) = prefs(context).getFloat(KEY_BOTTOM_X_PERCENT, 50f)

    fun hideHandleIcon(context: Context) = prefs(context).getBoolean(KEY_HIDE_HANDLE_ICON, false)
    fun hideInFullscreen(context: Context) = prefs(context).getBoolean(KEY_HIDE_IN_FULLSCREEN, false)
    fun hideInLandscape(context: Context) = prefs(context).getBoolean(KEY_HIDE_IN_LANDSCAPE, false)
    fun hideWhenKeyboardOpen(context: Context) = prefs(context).getBoolean(KEY_HIDE_WHEN_KEYBOARD_OPEN, false)
    fun showOnLockScreen(context: Context) = prefs(context).getBoolean(KEY_SHOW_ON_LOCK_SCREEN, false)

    fun brightnessEnabled(context: Context) = prefs(context).getBoolean(KEY_BRIGHTNESS_ENABLED, true)
    fun brightnessSensitivity(context: Context) = prefs(context).getFloat(KEY_BRIGHTNESS_SENSITIVITY, 1f)
    fun brightnessReverse(context: Context) = prefs(context).getBoolean(KEY_BRIGHTNESS_REVERSE, false)
    fun smoothBrightness(context: Context) = prefs(context).getBoolean(KEY_SMOOTH_BRIGHTNESS, true)
    fun deadZoneDp(context: Context) = prefs(context).getFloat(KEY_GESTURE_DEAD_ZONE_DP, 24f)
    fun tapAction(context: Context) = prefs(context).getString(KEY_TAP_ACTION, "none") ?: "none"
    fun suppressStockShade(context: Context) = prefs(context).getBoolean(KEY_SUPPRESS_STOCK_SHADE, true)
    fun vibrateOnTouch(context: Context) = prefs(context).getBoolean(KEY_VIBRATE_ON_TOUCH, true)
    fun useDeviceHaptics(context: Context) = prefs(context).getBoolean(KEY_USE_DEVICE_HAPTICS, true)
    fun autoCloseTile(context: Context) = prefs(context).getBoolean(KEY_AUTO_CLOSE_TILE, false)

    fun panelOpacity(context: Context) = prefs(context).getFloat(KEY_PANEL_OPACITY, 0.92f)
    fun blurRadius(context: Context) = prefs(context).getFloat(KEY_BLUR_RADIUS, 24f)
    fun panelCornerDp(context: Context) = prefs(context).getFloat(KEY_PANEL_CORNER_DP, 32f)
    fun panelPaddingDp(context: Context) = prefs(context).getFloat(KEY_PANEL_PADDING_DP, 18f)
    fun tileCornerDp(context: Context) = prefs(context).getFloat(KEY_TILE_CORNER_DP, 24f)
    fun tileHeightDp(context: Context) = prefs(context).getFloat(KEY_TILE_HEIGHT_DP, 62f)
    fun layoutRows(context: Context) = prefs(context).getInt(KEY_LAYOUT_ROWS, 2).coerceIn(1, 6)
    fun layoutColumns(context: Context) = prefs(context).getInt(KEY_LAYOUT_COLUMNS, 4).coerceIn(2, 8)
    fun layoutSmallColumns(context: Context) = prefs(context).getInt(KEY_LAYOUT_SMALL_COLUMNS, 6).coerceIn(2, 8)
    fun showSystemIcons(context: Context) = prefs(context).getBoolean(KEY_SHOW_SYSTEM_ICONS, true)
    fun showPanelHeader(context: Context) = prefs(context).getBoolean(KEY_SHOW_PANEL_HEADER, true)
    fun showPanelFooter(context: Context) = prefs(context).getBoolean(KEY_SHOW_PANEL_FOOTER, true)
    fun hideTileText(context: Context) = prefs(context).getBoolean(KEY_HIDE_TILE_TEXT, false)
    fun cropAppIcons(context: Context) = prefs(context).getBoolean(KEY_CROP_APP_ICONS, true)
    fun use24HourClock(context: Context) = prefs(context).getBoolean(KEY_USE_24_HOUR_CLOCK, false)

    fun showNotifications(context: Context) = prefs(context).getBoolean(KEY_SHOW_NOTIFICATIONS, true)
    fun hidePersistentNotifications(context: Context) = prefs(context).getBoolean(KEY_HIDE_PERSISTENT_NOTIFICATIONS, false)
    fun onlyMediaNotifications(context: Context) = prefs(context).getBoolean(KEY_ONLY_MEDIA_NOTIFICATIONS, false)
    fun removeNotificationSpacing(context: Context) = prefs(context).getBoolean(KEY_REMOVE_NOTIFICATION_SPACING, false)
    fun autoExpandNotifications(context: Context) = prefs(context).getBoolean(KEY_AUTO_EXPAND_NOTIFICATIONS, true)
    fun quickClearAll(context: Context) = prefs(context).getBoolean(KEY_QUICK_CLEAR_ALL, false)
    fun autoCloseAfterClear(context: Context) = prefs(context).getBoolean(KEY_AUTO_CLOSE_AFTER_CLEAR, false)
    fun dynamicNotificationColors(context: Context) = prefs(context).getBoolean(KEY_DYNAMIC_NOTIFICATION_COLORS, false)

    fun showWifiSsid(context: Context) = prefs(context).getBoolean(KEY_SHOW_WIFI_SSID, false)
    fun showNetworkType(context: Context) = prefs(context).getBoolean(KEY_SHOW_NETWORK_TYPE, false)

    fun openAnimation(context: Context) = prefs(context).getString(KEY_OPEN_ANIMATION, "pixel") ?: "pixel"
    fun openDurationMs(context: Context) = prefs(context).getInt(KEY_OPEN_DURATION_MS, 320)
    fun closeDurationMs(context: Context) = prefs(context).getInt(KEY_CLOSE_DURATION_MS, 220)
    fun animationOvershoot(context: Context) = prefs(context).getFloat(KEY_ANIMATION_OVERSHOOT, 0.08f)
}
