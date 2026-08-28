@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.crimson.pixelshade

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
fun PixelShadeEditor(onClose: () -> Unit, onOpenTiles: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PixelShadeConfig.prefs(context) }

    var triggerHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerHeightDp(context)) }
    var visibleHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerVisibleDp(context)) }
    var offset by remember { mutableFloatStateOf(PixelShadeConfig.triggerOffsetDp(context)) }
    var topWidth by remember { mutableFloatStateOf(PixelShadeConfig.topWidthPercent(context)) }
    var topX by remember { mutableFloatStateOf(PixelShadeConfig.topXPercent(context)) }
    var triggerColor by remember { mutableStateOf(PixelShadeConfig.triggerColorString(context)) }
    var pullDistance by remember { mutableFloatStateOf(PixelShadeConfig.pullDistanceDp(context)) }

    var leftEnabled by remember { mutableStateOf(PixelShadeConfig.leftEnabled(context)) }
    var leftWidth by remember { mutableFloatStateOf(PixelShadeConfig.leftWidthDp(context)) }
    var leftHeight by remember { mutableFloatStateOf(PixelShadeConfig.leftHeightDp(context)) }
    var leftY by remember { mutableFloatStateOf(PixelShadeConfig.leftYPercent(context)) }
    var rightEnabled by remember { mutableStateOf(PixelShadeConfig.rightEnabled(context)) }
    var rightWidth by remember { mutableFloatStateOf(PixelShadeConfig.rightWidthDp(context)) }
    var rightHeight by remember { mutableFloatStateOf(PixelShadeConfig.rightHeightDp(context)) }
    var rightY by remember { mutableFloatStateOf(PixelShadeConfig.rightYPercent(context)) }

    var brightness by remember { mutableStateOf(PixelShadeConfig.brightnessEnabled(context)) }
    var sensitivity by remember { mutableFloatStateOf(PixelShadeConfig.brightnessSensitivity(context)) }
    var reverse by remember { mutableStateOf(PixelShadeConfig.brightnessReverse(context)) }
    var deadZone by remember { mutableFloatStateOf(PixelShadeConfig.deadZoneDp(context)) }
    var suppressStock by remember { mutableStateOf(PixelShadeConfig.suppressStockShade(context)) }

    var opacity by remember { mutableFloatStateOf(PixelShadeConfig.panelOpacity(context)) }
    var blur by remember { mutableFloatStateOf(PixelShadeConfig.blurRadius(context)) }
    var panelCorner by remember { mutableFloatStateOf(PixelShadeConfig.panelCornerDp(context)) }
    var tileCorner by remember { mutableFloatStateOf(PixelShadeConfig.tileCornerDp(context)) }

    var openAnimation by remember { mutableStateOf(PixelShadeConfig.openAnimation(context)) }
    var openDuration by remember { mutableFloatStateOf(PixelShadeConfig.openDurationMs(context).toFloat()) }
    var closeDuration by remember { mutableFloatStateOf(PixelShadeConfig.closeDurationMs(context).toFloat()) }
    var overshoot by remember { mutableFloatStateOf(PixelShadeConfig.animationOvershoot(context)) }

    fun persist() {
        prefs.edit()
            .putFloat(PixelShadeConfig.KEY_TRIGGER_HEIGHT_DP, triggerHeight)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_VISIBLE_DP, visibleHeight)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_OFFSET_DP, offset)
            .putFloat(PixelShadeConfig.KEY_TOP_WIDTH_PERCENT, topWidth)
            .putFloat(PixelShadeConfig.KEY_TOP_X_PERCENT, topX)
            .putString(PixelShadeConfig.KEY_TRIGGER_COLOR, triggerColor)
            .putFloat(PixelShadeConfig.KEY_PULL_DISTANCE_DP, pullDistance)
            .putBoolean(PixelShadeConfig.KEY_LEFT_ENABLED, leftEnabled)
            .putFloat(PixelShadeConfig.KEY_LEFT_WIDTH_DP, leftWidth)
            .putFloat(PixelShadeConfig.KEY_LEFT_HEIGHT_DP, leftHeight)
            .putFloat(PixelShadeConfig.KEY_LEFT_Y_PERCENT, leftY)
            .putBoolean(PixelShadeConfig.KEY_RIGHT_ENABLED, rightEnabled)
            .putFloat(PixelShadeConfig.KEY_RIGHT_WIDTH_DP, rightWidth)
            .putFloat(PixelShadeConfig.KEY_RIGHT_HEIGHT_DP, rightHeight)
            .putFloat(PixelShadeConfig.KEY_RIGHT_Y_PERCENT, rightY)
            .putBoolean(PixelShadeConfig.KEY_BRIGHTNESS_ENABLED, brightness)
            .putFloat(PixelShadeConfig.KEY_BRIGHTNESS_SENSITIVITY, sensitivity)
            .putBoolean(PixelShadeConfig.KEY_BRIGHTNESS_REVERSE, reverse)
            .putFloat(PixelShadeConfig.KEY_GESTURE_DEAD_ZONE_DP, deadZone)
            .putBoolean(PixelShadeConfig.KEY_SUPPRESS_STOCK_SHADE, suppressStock)
            .putFloat(PixelShadeConfig.KEY_PANEL_OPACITY, opacity)
            .putFloat(PixelShadeConfig.KEY_BLUR_RADIUS, blur)
            .putFloat(PixelShadeConfig.KEY_PANEL_CORNER_DP, panelCorner)
            .putFloat(PixelShadeConfig.KEY_TILE_CORNER_DP, tileCorner)
            .putString(PixelShadeConfig.KEY_OPEN_ANIMATION, openAnimation)
            .putInt(PixelShadeConfig.KEY_OPEN_DURATION_MS, openDuration.toInt())
            .putInt(PixelShadeConfig.KEY_CLOSE_DURATION_MS, closeDuration.toInt())
            .putFloat(PixelShadeConfig.KEY_ANIMATION_OVERSHOOT, overshoot)
            .apply()
        context.startService(Intent(context, PixelShadeTriggerService::class.java).setAction("com.crimson.pixelshade.REFRESH_CONFIG"))
        PixelShadeAccessibilityService.requestTriggerRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pixel Shade editor") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { persist(); onClose() }) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Live shade", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("The preview is the editor. Trigger bounds, tile shape and panel styling update immediately.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LiveShadeCanvas(
                topWidth = topWidth,
                topX = topX,
                visibleHeight = visibleHeight,
                triggerHeight = triggerHeight,
                offset = offset,
                triggerColor = triggerColor,
                leftEnabled = leftEnabled,
                leftWidth = leftWidth,
                leftHeight = leftHeight,
                leftY = leftY,
                rightEnabled = rightEnabled,
                rightWidth = rightWidth,
                rightHeight = rightHeight,
                rightY = rightY,
                opacity = opacity,
                panelCorner = panelCorner,
                tileCorner = tileCorner,
                onOpenTiles = onOpenTiles
            )

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VisualSection("Top trigger", Icons.Default.SwipeDown) {
                    SliderSetting("Touch zone", triggerHeight, 1f..120f, "${triggerHeight.toInt()} dp") { triggerHeight = it; visibleHeight = visibleHeight.coerceAtMost(it); persist() }
                    SliderSetting("Visible strip", visibleHeight, 0f..24f, "${visibleHeight.toInt()} dp") { visibleHeight = it.coerceAtMost(triggerHeight); persist() }
                    SliderSetting("Width", topWidth, 10f..100f, "${topWidth.toInt()}%") { topWidth = it; persist() }
                    SliderSetting("Position", topX, 0f..100f, "${topX.toInt()}%") { topX = it; persist() }
                    SliderSetting("Vertical offset", offset, 0f..96f, "${offset.toInt()} dp") { offset = it; persist() }
                    SliderSetting("Activation distance", pullDistance, 8f..180f, "${pullDistance.toInt()} dp") { pullDistance = it; persist() }
                    Text("Indicator", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#66FFFFFF" to "Neutral", "#6690CAF9" to "Blue", "#66A5D6A7" to "Green", "#66CE93D8" to "Purple", "#00000000" to "Hidden").forEach { (hex, text) ->
                            FilterChip(selected = triggerColor == hex, onClick = { triggerColor = hex; persist() }, label = { Text(text) })
                        }
                    }
                }

                VisualSection("Side triggers", Icons.Default.TouchApp) {
                    TriggerEdgeControls("Left", leftEnabled, { leftEnabled = it; persist() }, leftWidth, { leftWidth = it; persist() }, leftHeight, { leftHeight = it; persist() }, leftY, { leftY = it; persist() })
                    HorizontalDivider()
                    TriggerEdgeControls("Right", rightEnabled, { rightEnabled = it; persist() }, rightWidth, { rightWidth = it; persist() }, rightHeight, { rightHeight = it; persist() }, rightY, { rightY = it; persist() })
                }

                VisualSection("Gestures", Icons.Default.Gesture) {
                    SettingSwitch("Swipe sideways for brightness", "Horizontal movement wins only after the gesture clearly resolves sideways.", brightness) { brightness = it; persist() }
                    if (brightness) {
                        SliderSetting("Sensitivity", sensitivity, 0.25f..3f, String.format("%.2fx", sensitivity)) { sensitivity = it; persist() }
                        SettingSwitch("Reverse direction", null, reverse) { reverse = it; persist() }
                    }
                    SliderSetting("Gesture dead zone", deadZone, 4f..48f, "${deadZone.toInt()} dp") { deadZone = it; persist() }
                    SettingSwitch("Block OxygenOS shade", "Uses the accessibility overlay trigger and immediately dismisses SystemUI if OxygenOS still starts opening.", suppressStock) { suppressStock = it; persist() }
                }

                VisualSection("Appearance", Icons.Default.Palette) {
                    SliderSetting("Panel opacity", opacity, 0.45f..1f, "${(opacity * 100).toInt()}%") { opacity = it; persist() }
                    SliderSetting("Blur", blur, 0f..60f, "${blur.toInt()} dp") { blur = it; persist() }
                    SliderSetting("Panel corners", panelCorner, 0f..48f, "${panelCorner.toInt()} dp") { panelCorner = it; persist() }
                    SliderSetting("Tile corners", tileCorner, 0f..36f, "${tileCorner.toInt()} dp") { tileCorner = it; persist() }
                }

                VisualSection("Motion", Icons.Default.Animation) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pixel" to "Pixel", "material_fade" to "Fade", "material_slide" to "Slide", "spring" to "Spring").forEach { (id, text) ->
                            FilterChip(selected = openAnimation == id, onClick = { openAnimation = id; persist() }, label = { Text(text) })
                        }
                    }
                    SliderSetting("Open", openDuration, 140f..700f, "${openDuration.toInt()} ms") { openDuration = it; persist() }
                    SliderSetting("Close", closeDuration, 100f..500f, "${closeDuration.toInt()} ms") { closeDuration = it; persist() }
                    SliderSetting("Overshoot", overshoot, 0f..0.35f, String.format("%.2f", overshoot)) { overshoot = it; persist() }
                }

                FilledTonalButton(onClick = onOpenTiles, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.GridView, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Edit tiles and shortcuts")
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun LiveShadeCanvas(
    topWidth: Float,
    topX: Float,
    visibleHeight: Float,
    triggerHeight: Float,
    offset: Float,
    triggerColor: String,
    leftEnabled: Boolean,
    leftWidth: Float,
    leftHeight: Float,
    leftY: Float,
    rightEnabled: Boolean,
    rightWidth: Float,
    rightHeight: Float,
    rightY: Float,
    opacity: Float,
    panelCorner: Float,
    tileCorner: Float,
    onOpenTiles: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(triggerColor)) }.getOrDefault(Color.Transparent)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(460.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(34.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(34.dp))
    ) {
        val canvasW = maxWidth
        val canvasH = maxHeight
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(30.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
                Text("9:41", Modifier.align(Alignment.CenterStart).padding(start = 18.dp), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.SignalCellularAlt, null, Modifier.size(15.dp))
                    Icon(Icons.Default.Wifi, null, Modifier.size(15.dp))
                    Icon(Icons.Default.BatteryFull, null, Modifier.size(15.dp))
                }
            }

            Surface(
                Modifier.fillMaxWidth().padding(top = 30.dp).height(430.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
                shape = RoundedCornerShape(bottomStart = panelCorner.dp, bottomEnd = panelCorner.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("9:41", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                            Text("Fri, Aug 28", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Settings, null)
                    }
                    Slider(value = .62f, onValueChange = {})
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PreviewTile("Internet", Icons.Default.Wifi, true, tileCorner, Modifier.weight(1f))
                        PreviewTile("Bluetooth", Icons.Default.Bluetooth, false, tileCorner, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PreviewTile("Do Not Disturb", Icons.Default.DoNotDisturbOn, false, tileCorner, Modifier.weight(1f))
                        PreviewTile("Rotation", Icons.Default.ScreenRotation, false, tileCorner, Modifier.weight(1f))
                    }
                    Surface(
                        Modifier.fillMaxWidth().height(76.dp).clickable(onClick = onOpenTiles),
                        shape = RoundedCornerShape(tileCorner.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GridView, null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Custom tiles", fontWeight = FontWeight.Medium)
                                Text("Tap to edit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            val width = canvasW * (topWidth / 100f)
            val start = (canvasW * (topX / 100f) - width / 2).coerceIn(0.dp, canvasW - width)
            Box(
                Modifier.offset(x = start, y = (offset / 96f * 52f).dp).width(width).height((triggerHeight.coerceAtLeast(2f) / 120f * 48f).dp.coerceAtLeast(5.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            ) {
                if (visibleHeight > 0) Box(Modifier.fillMaxWidth().height((visibleHeight.coerceAtMost(24f) / 24f * 8f).dp.coerceAtLeast(1.dp)).background(color))
            }

            if (leftEnabled) {
                Box(Modifier.align(Alignment.CenterStart).offset(y = ((leftY - 50f) / 100f * canvasH.value).dp).width((leftWidth / 48f * 12f).dp.coerceAtLeast(3.dp)).height((leftHeight / 500f * 180f).dp.coerceAtLeast(24.dp)).border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp)))
            }
            if (rightEnabled) {
                Box(Modifier.align(Alignment.CenterEnd).offset(y = ((rightY - 50f) / 100f * canvasH.value).dp).width((rightWidth / 48f * 12f).dp.coerceAtLeast(3.dp)).height((rightHeight / 500f * 180f).dp.coerceAtLeast(24.dp)).border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp)))
            }
        }
    }
}

@Composable
private fun PreviewTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, corner: Float, modifier: Modifier = Modifier) {
    Surface(modifier.height(66.dp), shape = RoundedCornerShape(corner.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}

@Composable
private fun VisualSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SettingSwitch(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TriggerEdgeControls(title: String, enabled: Boolean, onEnabled: (Boolean) -> Unit, width: Float, onWidth: (Float) -> Unit, height: Float, onHeight: (Float) -> Unit, y: Float, onY: (Float) -> Unit) {
    SettingSwitch(title, null, enabled, onEnabled)
    if (enabled) {
        SliderSetting("Width", width, 6f..48f, "${width.toInt()} dp", onWidth)
        SliderSetting("Height", height, 60f..500f, "${height.toInt()} dp", onHeight)
        SliderSetting("Vertical position", y, 0f..100f, "${y.toInt()}%", onY)
    }
}
