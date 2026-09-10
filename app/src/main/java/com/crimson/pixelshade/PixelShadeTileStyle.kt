package com.crimson.pixelshade

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

object PixelShadeTileStyle {
    const val KEY_GRADIENTS_ENABLED = "tile_style_gradients_enabled"
    const val KEY_ICON_SHAPE = "tile_style_icon_shape"
    const val KEY_ACTIVE_GRADIENT_START = "tile_style_active_gradient_start"
    const val KEY_ACTIVE_GRADIENT_END = "tile_style_active_gradient_end"
    const val KEY_ACTIVE_GRADIENT_DIRECTION = "tile_style_active_gradient_direction"
    const val KEY_INACTIVE_GRADIENT_START = "tile_style_inactive_gradient_start"
    const val KEY_INACTIVE_GRADIENT_END = "tile_style_inactive_gradient_end"
    const val KEY_INACTIVE_GRADIENT_DIRECTION = "tile_style_inactive_gradient_direction"

    enum class IconShape {
        CIRCLE,
        ROUNDED_SQUARE,
        SQUIRCLE
    }

    fun gradientsEnabled(context: Context): Boolean =
        PixelShadeConfig.prefs(context).getBoolean(KEY_GRADIENTS_ENABLED, false)

    fun iconShape(context: Context): IconShape = runCatching {
        IconShape.valueOf(
            PixelShadeConfig.prefs(context).getString(KEY_ICON_SHAPE, IconShape.CIRCLE.name)
                ?: IconShape.CIRCLE.name
        )
    }.getOrDefault(IconShape.CIRCLE)

    fun activeGradientStart(context: Context, fallback: Color): Color =
        PixelShadeThemeEngine.parseOr(
            PixelShadeConfig.prefs(context).getString(KEY_ACTIVE_GRADIENT_START, null),
            fallback
        )

    fun activeGradientEnd(context: Context, fallback: Color): Color =
        PixelShadeThemeEngine.parseOr(
            PixelShadeConfig.prefs(context).getString(KEY_ACTIVE_GRADIENT_END, null),
            fallback
        )

    fun activeGradientDirection(context: Context): Float =
        PixelShadeConfig.prefs(context).getFloat(KEY_ACTIVE_GRADIENT_DIRECTION, 90f).normalizeDegrees()

    fun inactiveGradientStart(context: Context, fallback: Color): Color =
        PixelShadeThemeEngine.parseOr(
            PixelShadeConfig.prefs(context).getString(KEY_INACTIVE_GRADIENT_START, null),
            fallback
        )

    fun inactiveGradientEnd(context: Context, fallback: Color): Color =
        PixelShadeThemeEngine.parseOr(
            PixelShadeConfig.prefs(context).getString(KEY_INACTIVE_GRADIENT_END, null),
            fallback
        )

    fun inactiveGradientDirection(context: Context): Float =
        PixelShadeConfig.prefs(context).getFloat(KEY_INACTIVE_GRADIENT_DIRECTION, 90f).normalizeDegrees()

    fun putGradient(
        context: Context,
        active: Boolean,
        startHex: String,
        endHex: String,
        directionDegrees: Float
    ) {
        val prefs = PixelShadeConfig.prefs(context)
        prefs.edit()
            .putString(
                if (active) KEY_ACTIVE_GRADIENT_START else KEY_INACTIVE_GRADIENT_START,
                PixelShadeThemeEngine.normalizeHex(startHex)
            )
            .putString(
                if (active) KEY_ACTIVE_GRADIENT_END else KEY_INACTIVE_GRADIENT_END,
                PixelShadeThemeEngine.normalizeHex(endHex)
            )
            .putFloat(
                if (active) KEY_ACTIVE_GRADIENT_DIRECTION else KEY_INACTIVE_GRADIENT_DIRECTION,
                directionDegrees.normalizeDegrees()
            )
            .apply()
    }

    fun setGradientsEnabled(context: Context, enabled: Boolean) {
        PixelShadeConfig.prefs(context).edit().putBoolean(KEY_GRADIENTS_ENABLED, enabled).apply()
    }

    fun setIconShape(context: Context, shape: IconShape) {
        PixelShadeConfig.prefs(context).edit().putString(KEY_ICON_SHAPE, shape.name).apply()
    }
}

@Immutable
data class PixelShadeTileBrushes(
    val active: Brush,
    val inactive: Brush
)

fun pixelShadeTileBrushes(
    context: Context,
    activeFallback: Color,
    inactiveFallback: Color
): PixelShadeTileBrushes {
    if (!PixelShadeTileStyle.gradientsEnabled(context)) {
        return PixelShadeTileBrushes(
            active = Brush.linearGradient(listOf(activeFallback, activeFallback)),
            inactive = Brush.linearGradient(listOf(inactiveFallback, inactiveFallback))
        )
    }

    return PixelShadeTileBrushes(
        active = directionalGradient(
            PixelShadeTileStyle.activeGradientStart(context, activeFallback),
            PixelShadeTileStyle.activeGradientEnd(context, activeFallback),
            PixelShadeTileStyle.activeGradientDirection(context)
        ),
        inactive = directionalGradient(
            PixelShadeTileStyle.inactiveGradientStart(context, inactiveFallback),
            PixelShadeTileStyle.inactiveGradientEnd(context, inactiveFallback),
            PixelShadeTileStyle.inactiveGradientDirection(context)
        )
    )
}

private fun directionalGradient(start: Color, end: Color, degrees: Float): Brush {
    val radians = Math.toRadians(degrees.toDouble())
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val extent = 10_000f
    return Brush.linearGradient(
        colors = listOf(start, end),
        start = Offset(-dx * extent, -dy * extent),
        end = Offset(dx * extent, dy * extent)
    )
}

private fun Float.normalizeDegrees(): Float {
    val normalized = this % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
