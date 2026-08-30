@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.crimson.pixelshade

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PixelShadeEditorV2(onClose: () -> Unit, onOpenTiles: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PixelShadeConfig.prefs(context) }

    var triggerHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerHeightDp(context)) }
    var visibleHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerVisibleDp(context)) }
    var topWidth by remember { mutableFloatStateOf(PixelShadeConfig.topWidthPercent(context)) }
    var topX by remember { mutableFloatStateOf(PixelShadeConfig.topXPercent(context)) }
    var offset by remember { mutableFloatStateOf(PixelShadeConfig.triggerOffsetDp(context)) }
    var pullDistance by remember { mutableFloatStateOf(PixelShadeConfig.pullDistanceDp(context)) }
    var brightnessGesture by remember { mutableStateOf(PixelShadeConfig.brightnessEnabled(context)) }
    var brightnessSensitivity by remember { mutableFloatStateOf(PixelShadeConfig.brightnessSensitivity(context)) }
    var suppressStock by remember { mutableStateOf(PixelShadeConfig.suppressStockShade(context)) }
    var opacity by remember { mutableFloatStateOf(PixelShadeConfig.panelOpacity(context)) }
    var blur by remember { mutableFloatStateOf(PixelShadeConfig.blurRadius(context)) }
    var tileCorner by remember { mutableFloatStateOf(PixelShadeConfig.tileCornerDp(context)) }
    var openDuration by remember { mutableFloatStateOf(PixelShadeConfig.openDurationMs(context).toFloat()) }
    var closeDuration by remember { mutableFloatStateOf(PixelShadeConfig.closeDurationMs(context).toFloat()) }
    var leftEnabled by remember { mutableStateOf(PixelShadeConfig.leftEnabled(context)) }
    var rightEnabled by remember { mutableStateOf(PixelShadeConfig.rightEnabled(context)) }

    fun persist() {
        prefs.edit()
            .putFloat(PixelShadeConfig.KEY_TRIGGER_HEIGHT_DP, triggerHeight)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_VISIBLE_DP, visibleHeight)
            .putFloat(PixelShadeConfig.KEY_TOP_WIDTH_PERCENT, topWidth)
            .putFloat(PixelShadeConfig.KEY_TOP_X_PERCENT, topX)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_OFFSET_DP, offset)
            .putFloat(PixelShadeConfig.KEY_PULL_DISTANCE_DP, pullDistance)
            .putBoolean(PixelShadeConfig.KEY_BRIGHTNESS_ENABLED, brightnessGesture)
            .putFloat(PixelShadeConfig.KEY_BRIGHTNESS_SENSITIVITY, brightnessSensitivity)
            .putBoolean(PixelShadeConfig.KEY_SUPPRESS_STOCK_SHADE, suppressStock)
            .putFloat(PixelShadeConfig.KEY_PANEL_OPACITY, opacity)
            .putFloat(PixelShadeConfig.KEY_BLUR_RADIUS, blur)
            .putFloat(PixelShadeConfig.KEY_TILE_CORNER_DP, tileCorner)
            .putInt(PixelShadeConfig.KEY_OPEN_DURATION_MS, openDuration.toInt())
            .putInt(PixelShadeConfig.KEY_CLOSE_DURATION_MS, closeDuration.toInt())
            .putBoolean(PixelShadeConfig.KEY_LEFT_ENABLED, leftEnabled)
            .putBoolean(PixelShadeConfig.KEY_RIGHT_ENABLED, rightEnabled)
            .putString(PixelShadeConfig.KEY_OPEN_ANIMATION, "pixel17")
            .apply()
        context.startService(Intent(context, PixelShadeTriggerService::class.java).setAction("com.crimson.pixelshade.REFRESH_CONFIG"))
        PixelShadeAccessibilityService.requestTriggerRefresh()
        StatusBarSuppression.sync(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pixel Shade editor") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { persist(); onClose() }) { Text("Done") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Live Android 17 shade", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("This preview stays pinned while the controls below scroll.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Pixel17Preview(
                opacity = opacity,
                blur = blur,
                tileCorner = tileCorner,
                triggerHeight = triggerHeight,
                visibleHeight = visibleHeight,
                topWidth = topWidth,
                topX = topX,
                offset = offset
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    EditorSection("Trigger", Icons.Default.SwipeDown) {
                        EditorSlider("Touch zone", triggerHeight, 1f..120f, "${triggerHeight.toInt()} dp") { triggerHeight = it; visibleHeight = visibleHeight.coerceAtMost(it); persist() }
                        EditorSlider("Visible strip", visibleHeight, 0f..24f, "${visibleHeight.toInt()} dp") { visibleHeight = it.coerceAtMost(triggerHeight); persist() }
                        EditorSlider("Width", topWidth, 10f..100f, "${topWidth.toInt()}%") { topWidth = it; persist() }
                        EditorSlider("Horizontal position", topX, 0f..100f, "${topX.toInt()}%") { topX = it; persist() }
                        EditorSlider("Vertical offset", offset, 0f..96f, "${offset.toInt()} dp") { offset = it; persist() }
                        EditorSlider("Pull distance", pullDistance, 8f..180f, "${pullDistance.toInt()} dp") { pullDistance = it; persist() }
                    }
                }
                item {
                    EditorSection("Gestures", Icons.Default.Gesture) {
                        EditorSwitch("Horizontal swipe brightness", brightnessGesture) { brightnessGesture = it; persist() }
                        if (brightnessGesture) EditorSlider("Brightness sensitivity", brightnessSensitivity, .25f..3f, String.format("%.2fx", brightnessSensitivity)) { brightnessSensitivity = it; persist() }
                        EditorSwitch("Block OxygenOS shade", suppressStock) { suppressStock = it; persist() }
                        EditorSwitch("Left edge trigger", leftEnabled) { leftEnabled = it; persist() }
                        EditorSwitch("Right edge trigger", rightEnabled) { rightEnabled = it; persist() }
                    }
                }
                item {
                    EditorSection("Material 3 Expressive", Icons.Default.Palette) {
                        EditorSlider("Panel opacity", opacity, .55f..1f, "${(opacity * 100).toInt()}%") { opacity = it; persist() }
                        EditorSlider("Background blur", blur, 0f..80f, "${blur.toInt()} dp") { blur = it; persist() }
                        EditorSlider("Tile corner radius", tileCorner, 12f..36f, "${tileCorner.toInt()} dp") { tileCorner = it; persist() }
                    }
                }
                item {
                    EditorSection("Pixel motion", Icons.Default.Animation) {
                        Text("The Pixel preset uses spring settling instead of the old generic screen entrance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        EditorSlider("Open settle", openDuration, 180f..600f, "${openDuration.toInt()} ms") { openDuration = it; persist() }
                        EditorSlider("Close settle", closeDuration, 120f..420f, "${closeDuration.toInt()} ms") { closeDuration = it; persist() }
                    }
                }
                item {
                    FilledTonalButton(onClick = onOpenTiles, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Default.GridView, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Edit tiles and shortcuts")
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Pixel17Preview(
    opacity: Float,
    blur: Float,
    tileCorner: Float,
    triggerHeight: Float,
    visibleHeight: Float,
    topWidth: Float,
    topX: Float,
    offset: Float
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(330.dp).padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(30.dp))
    ) {
        val w = maxWidth
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
            tonalElevation = (blur / 20f).dp
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("10:30", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        Text("Sun, Aug 30", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {}) { Icon(Icons.Default.Settings, null) }
                }
                Surface(Modifier.fillMaxWidth().height(34.dp), shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(.64f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.weight(.36f))
                        Icon(Icons.Default.Brightness6, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveCompactTile(Icons.Default.Wifi, true, tileCorner, Modifier.weight(1f))
                    ExpressiveCompactTile(Icons.Default.SwapVert, true, tileCorner, Modifier.weight(1f))
                    ExpressiveWideTile("Bluetooth", Icons.Default.Bluetooth, false, tileCorner, Modifier.weight(2f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveWideTile("Flashlight", Icons.Default.FlashlightOn, false, tileCorner, Modifier.weight(1f))
                    ExpressiveWideTile("Modes", Icons.Default.DoNotDisturbOn, false, tileCorner, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveWideTile("Rotation", Icons.Default.ScreenRotation, false, tileCorner, Modifier.weight(1f))
                    ExpressiveWideTile("Home", Icons.Default.Home, true, tileCorner, Modifier.weight(1f))
                }
            }
        }

        val zoneWidth = w * (topWidth / 100f)
        val zoneStart = (w * (topX / 100f) - zoneWidth / 2f).coerceIn(0.dp, w - zoneWidth)
        Box(
            Modifier.offset(x = zoneStart, y = offset.dp).width(zoneWidth).height(triggerHeight.dp.coerceAtMost(40.dp))
                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = .75f), RoundedCornerShape(4.dp))
        ) {
            if (visibleHeight > 0f) Box(Modifier.fillMaxWidth().height(visibleHeight.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .45f)))
        }
    }
}

@Composable
private fun ExpressiveCompactTile(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, corner: Float, modifier: Modifier) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(modifier.aspectRatio(1f), shape = RoundedCornerShape(corner.dp.coerceAtMost(28.dp)), color = bg, contentColor = fg) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
    }
}

@Composable
private fun ExpressiveWideTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, corner: Float, modifier: Modifier) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(modifier.height(60.dp), shape = RoundedCornerShape(corner.dp.coerceAtMost(30.dp)), color = bg, contentColor = fg) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun EditorSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun EditorSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
