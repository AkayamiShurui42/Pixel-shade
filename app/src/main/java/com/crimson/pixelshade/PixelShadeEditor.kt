package com.crimson.pixelshade

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Edit Pixel Shade") }, navigationIcon = { TextButton(onClick = onClose) { Text("Done") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Live preview", style = MaterialTheme.typography.titleLarge)
                    Surface(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(panelCorner.dp)), shape = RoundedCornerShape(panelCorner.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("9:41"); Text("100%") }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf("Internet", "Bluetooth").forEachIndexed { i, label -> Surface(Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(tileCorner.dp), color = if (i == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Text(label) } } }
                            }
                        }
                    }
                }
            }

            EditorCard("Top trigger") {
                SliderRow("Touch height", triggerHeight, 1f..100f, "${triggerHeight.toInt()} dp") { triggerHeight = it; persist() }
                SliderRow("Visible height", visibleHeight, 0f..32f, "${visibleHeight.toInt()} dp") { visibleHeight = it.coerceAtMost(triggerHeight); persist() }
                SliderRow("Width", topWidth, 10f..100f, "${topWidth.toInt()}%") { topWidth = it; persist() }
                SliderRow("Horizontal position", topX, 0f..100f, "${topX.toInt()}%") { topX = it; persist() }
                SliderRow("Vertical offset", offset, 0f..96f, "${offset.toInt()} dp") { offset = it; persist() }
                SliderRow("Pull distance", pullDistance, 8f..160f, "${pullDistance.toInt()} dp") { pullDistance = it; persist() }
                Text("Visible color")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#66FFFFFF" to "White", "#6690CAF9" to "Blue", "#66A5D6A7" to "Green", "#66CE93D8" to "Purple", "#00000000" to "Hidden").forEach { (hex, label) -> FilterChip(selected = triggerColor == hex, onClick = { triggerColor = hex; persist() }, label = { Text(label) }) }
                }
            }

            EdgeCard("Left edge", leftEnabled, { leftEnabled = it; persist() }, leftWidth, { leftWidth = it; persist() }, leftHeight, { leftHeight = it; persist() }, leftY, { leftY = it; persist() })
            EdgeCard("Right edge", rightEnabled, { rightEnabled = it; persist() }, rightWidth, { rightWidth = it; persist() }, rightHeight, { rightHeight = it; persist() }, rightY, { rightY = it; persist() })

            EditorCard("Gestures & system shade") {
                SwitchRow("Horizontal swipe controls brightness", brightness) { brightness = it; persist() }
                if (brightness) {
                    SliderRow("Brightness sensitivity", sensitivity, 0.25f..3f, String.format("%.2fx", sensitivity)) { sensitivity = it; persist() }
                    SwitchRow("Reverse brightness", reverse) { reverse = it; persist() }
                }
                SliderRow("Gesture dead zone", deadZone, 8f..48f, "${deadZone.toInt()} dp") { deadZone = it; persist() }
                SwitchRow("Suppress stock notification shade", suppressStock) { suppressStock = it; persist() }
                Text("Suppression requires Accessibility and collapses the OEM shade when Pixel Shade owns the gesture.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            EditorCard("Panel style") {
                SliderRow("Opacity", opacity, 0.45f..1f, "${(opacity * 100).toInt()}%") { opacity = it; persist() }
                SliderRow("Blur", blur, 0f..60f, "${blur.toInt()} dp") { blur = it; persist() }
                SliderRow("Panel corners", panelCorner, 0f..48f, "${panelCorner.toInt()} dp") { panelCorner = it; persist() }
                SliderRow("Tile corners", tileCorner, 0f..32f, "${tileCorner.toInt()} dp") { tileCorner = it; persist() }
            }

            EditorCard("Animations") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pixel" to "Pixel", "material_fade" to "Material fade", "material_slide" to "Material slide", "spring" to "Soft spring").forEach { (id, label) -> FilterChip(selected = openAnimation == id, onClick = { openAnimation = id; persist() }, label = { Text(label) }) }
                }
                SliderRow("Open speed", openDuration, 140f..700f, "${openDuration.toInt()} ms") { openDuration = it; persist() }
                SliderRow("Close speed", closeDuration, 100f..500f, "${closeDuration.toInt()} ms") { closeDuration = it; persist() }
                SliderRow("Overshoot", overshoot, 0f..0.35f, String.format("%.2f", overshoot)) { overshoot = it; persist() }
            }

            EditorCard("Quick tiles") {
                Text("Create app, activity and website tiles, choose custom labels/icons, and reorder them inside Pixel Shade.")
                Button(onClick = onOpenTiles, modifier = Modifier.fillMaxWidth()) { Text("Edit quick tiles") }
            }
        }
    }
}

@Composable private fun EditorCard(title: String, content: @Composable ColumnScope.() -> Unit) { ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); content() } } }
@Composable private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) { Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Slider(value, onChange, valueRange = range) } }
@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) } }
@Composable private fun EdgeCard(title: String, enabled: Boolean, onEnabled: (Boolean) -> Unit, width: Float, onWidth: (Float) -> Unit, height: Float, onHeight: (Float) -> Unit, y: Float, onY: (Float) -> Unit) { EditorCard(title) { SwitchRow("Enable", enabled, onEnabled); if (enabled) { SliderRow("Width", width, 6f..48f, "${width.toInt()} dp", onWidth); SliderRow("Height", height, 60f..500f, "${height.toInt()} dp", onHeight); SliderRow("Vertical position", y, 0f..100f, "${y.toInt()}%", onY) } } }
