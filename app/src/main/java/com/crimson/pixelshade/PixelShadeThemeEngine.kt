package com.crimson.pixelshade

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

object PixelShadeThemeEngine {
    const val KEY_THEME_MODE = "theme_engine_mode"
    const val KEY_PANEL = "theme_panel"
    const val KEY_ACTIVE_TILE = "theme_active_tile"
    const val KEY_INACTIVE_TILE = "theme_inactive_tile"
    const val KEY_ACTIVE_ICON = "theme_active_icon"
    const val KEY_INACTIVE_ICON = "theme_inactive_icon"
    const val KEY_PRIMARY_TEXT = "theme_primary_text"
    const val KEY_SECONDARY_TEXT = "theme_secondary_text"
    const val KEY_BRIGHTNESS_TRACK = "theme_brightness_track"
    const val KEY_BRIGHTNESS_FILL = "theme_brightness_fill"

    enum class Mode { DYNAMIC, HYBRID, MANUAL }

    fun mode(context: Context): Mode = runCatching {
        Mode.valueOf(PixelShadeConfig.prefs(context).getString(KEY_THEME_MODE, Mode.DYNAMIC.name) ?: Mode.DYNAMIC.name)
    }.getOrDefault(Mode.DYNAMIC)

    fun setMode(context: Context, mode: Mode) {
        PixelShadeConfig.prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun putColor(context: Context, key: String, value: String) {
        PixelShadeConfig.prefs(context).edit().putString(key, normalizeHex(value)).apply()
    }

    fun storedColor(context: Context, key: String): String? = PixelShadeConfig.prefs(context).getString(key, null)

    fun normalizeHex(input: String): String {
        val raw = input.trim().removePrefix("#").uppercase()
        val normalized = when (raw.length) {
            6 -> "FF$raw"
            8 -> raw
            else -> ""
        }
        return if (normalized.length == 8 && normalized.all { it in '0'..'9' || it in 'A'..'F' }) "#$normalized" else input.trim()
    }

    fun parseOr(value: String?, fallback: Color): Color {
        if (value.isNullOrBlank()) return fallback
        return runCatching { Color(AndroidColor.parseColor(normalizeHex(value))) }.getOrDefault(fallback)
    }
}

@Immutable
data class PixelShadePalette(
    val panel: Color,
    val activeTile: Color,
    val inactiveTile: Color,
    val activeIcon: Color,
    val inactiveIcon: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val brightnessTrack: Color,
    val brightnessFill: Color
)

@Composable
fun rememberPixelShadePalette(context: Context, scheme: ColorScheme): PixelShadePalette {
    val prefs = PixelShadeConfig.prefs(context)
    val mode = PixelShadeThemeEngine.mode(context)
    return remember(
        mode,
        prefs.getString(PixelShadeThemeEngine.KEY_PANEL, null),
        prefs.getString(PixelShadeThemeEngine.KEY_ACTIVE_TILE, null),
        prefs.getString(PixelShadeThemeEngine.KEY_INACTIVE_TILE, null),
        prefs.getString(PixelShadeThemeEngine.KEY_ACTIVE_ICON, null),
        prefs.getString(PixelShadeThemeEngine.KEY_INACTIVE_ICON, null),
        prefs.getString(PixelShadeThemeEngine.KEY_PRIMARY_TEXT, null),
        prefs.getString(PixelShadeThemeEngine.KEY_SECONDARY_TEXT, null),
        prefs.getString(PixelShadeThemeEngine.KEY_BRIGHTNESS_TRACK, null),
        prefs.getString(PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL, null),
        scheme
    ) {
        val dynamic = PixelShadePalette(
            panel = scheme.surface,
            activeTile = scheme.primary,
            inactiveTile = scheme.surfaceContainerHigh,
            activeIcon = scheme.onPrimary,
            inactiveIcon = scheme.onSurface,
            primaryText = scheme.onSurface,
            secondaryText = scheme.onSurfaceVariant,
            brightnessTrack = scheme.surfaceContainerHighest,
            brightnessFill = scheme.primary
        )
        when (mode) {
            PixelShadeThemeEngine.Mode.DYNAMIC -> dynamic
            PixelShadeThemeEngine.Mode.HYBRID -> dynamic.copy(
                panel = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_PANEL, null), dynamic.panel),
                activeTile = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_ACTIVE_TILE, null), dynamic.activeTile),
                inactiveTile = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_INACTIVE_TILE, null), dynamic.inactiveTile),
                brightnessFill = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL, null), dynamic.brightnessFill)
            )
            PixelShadeThemeEngine.Mode.MANUAL -> PixelShadePalette(
                panel = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_PANEL, null), dynamic.panel),
                activeTile = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_ACTIVE_TILE, null), dynamic.activeTile),
                inactiveTile = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_INACTIVE_TILE, null), dynamic.inactiveTile),
                activeIcon = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_ACTIVE_ICON, null), dynamic.activeIcon),
                inactiveIcon = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_INACTIVE_ICON, null), dynamic.inactiveIcon),
                primaryText = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_PRIMARY_TEXT, null), dynamic.primaryText),
                secondaryText = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_SECONDARY_TEXT, null), dynamic.secondaryText),
                brightnessTrack = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_BRIGHTNESS_TRACK, null), dynamic.brightnessTrack),
                brightnessFill = PixelShadeThemeEngine.parseOr(prefs.getString(PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL, null), dynamic.brightnessFill)
            )
        }
    }
}
