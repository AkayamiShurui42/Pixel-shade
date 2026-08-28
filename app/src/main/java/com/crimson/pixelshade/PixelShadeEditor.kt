package com.crimson.pixelshade

import android.content.Context
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
fun PixelShadeEditor(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PixelShadeConfig.prefs(context) }

    var triggerHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerHeightDp(context)) }
    var visibleHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerVisibleDp(context)) }
    var offset by remember { mutableFloatStateOf(PixelShadeConfig.triggerOffsetDp(context)) }
    var brightness by remember { mutableStateOf(PixelShadeConfig.brightnessEnabled(context)) }
    var sensitivity by remember { mutableFloatStateOf(PixelShadeConfig.brightnessSensitivity(context)) }
    var reverse by remember { mutableStateOf(PixelShadeConfig.brightnessReverse(context)) }
    var deadZone by remember { mutableFloatStateOf(PixelShadeConfig.deadZoneDp(context)) }
    var tapAction by remember { mutableStateOf(PixelShadeConfig.tapAction(context)) }
    var opacity by remember { mutableFloatStateOf(PixelShadeConfig.panelOpacity(context)) }
    var blur by remember { mutableFloatStateOf(PixelShadeConfig.blurRadius(context)) }
    var panelCorner by remember { mutableFloatStateOf(PixelShadeConfig.panelCornerDp(context)) }
    var tileCorner by remember { mutableFloatStateOf(PixelShadeConfig.tileCornerDp(context)) }

    fun persist() {
        prefs.edit()
            .putFloat(PixelShadeConfig.KEY_TRIGGER_HEIGHT_DP, triggerHeight)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_VISIBLE_DP, visibleHeight)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_OFFSET_DP, offset)
            .putBoolean(PixelShadeConfig.KEY_BRIGHTNESS_ENABLED, brightness)
            .putFloat(PixelShadeConfig.KEY_BRIGHTNESS_SENSITIVITY, sensitivity)
            .putBoolean(PixelShadeConfig.KEY_BRIGHTNESS_REVERSE, reverse)
            .putFloat(PixelShadeConfig.KEY_GESTURE_DEAD_ZONE_DP, deadZone)
            .putString(PixelShadeConfig.KEY_TAP_ACTION, tapAction)
            .putFloat(PixelShadeConfig.KEY_PANEL_OPACITY, opacity)
            .putFloat(PixelShadeConfig.KEY_BLUR_RADIUS, blur)
            .putFloat(PixelShadeConfig.KEY_PANEL_CORNER_DP, panelCorner)
            .putFloat(PixelShadeConfig.KEY_TILE_CORNER_DP, tileCorner)
            .apply()
        context.startService(Intent(context, PixelShadeTriggerService::class.java).setAction("com.crimson.pixelshade.REFRESH_CONFIG"))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Pixel Shade") },
                navigationIcon = {
                    TextButton(onClick = onClose) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Live preview", style = MaterialTheme.typography.titleLarge)
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(panelCorner.dp)),
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("9:41", style = MaterialTheme.typography.titleMedium)
                                Text("100%")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(2) { index ->
                                    Surface(
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        shape = RoundedCornerShape(tileCorner.dp),
                                        color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Box(contentAlignment = Alignment.Center) { Text(if (index == 0) "Internet" else "Bluetooth") }
                                    }
                                }
                            }
                            Text("Notifications", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            EditorCard("Trigger") {
                LabeledSlider("Gesture zone", triggerHeight, 1f..80f, "${triggerHeight.toInt()} dp") { triggerHeight = it; persist() }
                LabeledSlider("Visible strip", visibleHeight, 1f..20f, "${visibleHeight.toInt()} dp") { visibleHeight = it; persist() }
                LabeledSlider("Vertical offset", offset, 0f..64f, "${offset.toInt()} dp") { offset = it; persist() }
            }

            EditorCard("Gestures") {
                SwitchRow("Swipe horizontally for brightness", brightness) { brightness = it; persist() }
                if (brightness) {
                    LabeledSlider("Brightness sensitivity", sensitivity, 0.25f..3f, String.format("%.2fx", sensitivity)) { sensitivity = it; persist() }
                    SwitchRow("Reverse brightness direction", reverse) { reverse = it; persist() }
                }
                LabeledSlider("Gesture dead zone", deadZone, 8f..48f, "${deadZone.toInt()} dp") { deadZone = it; persist() }
                Text("Tap action", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("none", "shade", "quick_settings", "settings").forEach { action ->
                        FilterChip(
                            selected = tapAction == action,
                            onClick = { tapAction = action; persist() },
                            label = { Text(action.replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            EditorCard("Panel style") {
                LabeledSlider("Opacity", opacity, 0.45f..1f, "${(opacity * 100).toInt()}%") { opacity = it; persist() }
                LabeledSlider("Blur", blur, 0f..60f, "${blur.toInt()} dp") { blur = it; persist() }
                LabeledSlider("Panel corners", panelCorner, 0f..48f, "${panelCorner.toInt()} dp") { panelCorner = it; persist() }
                LabeledSlider("Tile corners", tileCorner, 0f..32f, "${tileCorner.toInt()} dp") { tileCorner = it; persist() }
            }

            EditorCard("Presets") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        triggerHeight = 10f; visibleHeight = 2f; offset = 0f; brightness = true; sensitivity = 1f; reverse = false; deadZone = 24f; opacity = 0.92f; blur = 24f; panelCorner = 32f; tileCorner = 24f; persist()
                    }, modifier = Modifier.weight(1f)) { Text("Pixel") }
                    FilledTonalButton(onClick = {
                        triggerHeight = 18f; visibleHeight = 4f; offset = 0f; brightness = true; sensitivity = 1.35f; reverse = false; deadZone = 18f; opacity = 0.86f; blur = 36f; panelCorner = 24f; tileCorner = 16f; persist()
                    }, modifier = Modifier.weight(1f)) { Text("Compact") }
                }
            }
        }
    }
}

@Composable
private fun EditorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(display, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
