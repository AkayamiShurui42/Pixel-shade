package com.crimson.pixelshade

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
            active = SolidColor(activeFallback),
            inactive = SolidColor(inactiveFallback)
        )
    }

    return PixelShadeTileBrushes(
        active = DirectionalGradientBrush(
            startColor = PixelShadeTileStyle.activeGradientStart(context, activeFallback),
            endColor = PixelShadeTileStyle.activeGradientEnd(context, activeFallback),
            degrees = PixelShadeTileStyle.activeGradientDirection(context)
        ),
        inactive = DirectionalGradientBrush(
            startColor = PixelShadeTileStyle.inactiveGradientStart(context, inactiveFallback),
            endColor = PixelShadeTileStyle.inactiveGradientEnd(context, inactiveFallback),
            degrees = PixelShadeTileStyle.inactiveGradientDirection(context)
        )
    )
}

private class DirectionalGradientBrush(
    private val startColor: Color,
    private val endColor: Color,
    private val degrees: Float
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        if (size.width <= 0f || size.height <= 0f) {
            return LinearGradientShader(
                from = Offset.Zero,
                to = Offset(1f, 1f),
                colors = listOf(startColor, endColor)
            )
        }

        val radians = Math.toRadians(degrees.toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        val radius = sqrt(size.width * size.width + size.height * size.height) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        return LinearGradientShader(
            from = Offset(centerX - dx * radius, centerY - dy * radius),
            to = Offset(centerX + dx * radius, centerY + dy * radius),
            colors = listOf(startColor, endColor)
        )
    }
}

private fun Float.normalizeDegrees(): Float {
    val normalized = this % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
