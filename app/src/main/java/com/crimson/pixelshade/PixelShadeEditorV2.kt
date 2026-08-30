@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.crimson.pixelshade

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


enum class PixelShadeEditorTab(val label: String, val icon: ImageVector) {
    HANDLE("Handle", Icons.Default.SwipeDown),
    LAYOUT("Layout", Icons.Default.DashboardCustomize),
    COLORS("Colors", Icons.Default.Palette),
    SLIDERS("Sliders", Icons.Default.Tune),
    NOTIFICATIONS("Notifications", Icons.Default.Notifications),
    MOTION("Motion", Icons.Default.Animation),
    ADVANCED("Advanced", Icons.Default.Build)
}

@Composable
fun PixelShadeEditorV2(
    onClose: () -> Unit,
    onOpenTiles: () -> Unit,
    initialTab: PixelShadeEditorTab = PixelShadeEditorTab.HANDLE
) {
    val context = LocalContext.current
    val prefs = remember { PixelShadeConfig.prefs(context) }
    val scheme = MaterialTheme.colorScheme

    val initialTriggerHeight = remember { PixelShadeConfig.triggerHeightDp(context) }
    val initialVisibleHeight = remember { PixelShadeConfig.triggerVisibleDp(context) }
    val initialTopWidth = remember { PixelShadeConfig.topWidthPercent(context) }
    val initialTopX = remember { PixelShadeConfig.topXPercent(context) }
    val initialOffset = remember { PixelShadeConfig.triggerOffsetDp(context) }
    val initialPullDistance = remember { PixelShadeConfig.pullDistanceDp(context) }
    val initialBrightnessGesture = remember { PixelShadeConfig.brightnessEnabled(context) }
    val initialBrightnessSensitivity = remember { PixelShadeConfig.brightnessSensitivity(context) }
    val initialSuppressStock = remember { PixelShadeConfig.suppressStockShade(context) }
    val initialOpacity = remember { PixelShadeConfig.panelOpacity(context) }
    val initialBlur = remember { PixelShadeConfig.blurRadius(context) }
    val initialTileCorner = remember { PixelShadeConfig.tileCornerDp(context) }
    val initialOpenDuration = remember { PixelShadeConfig.openDurationMs(context).toFloat() }
    val initialCloseDuration = remember { PixelShadeConfig.closeDurationMs(context).toFloat() }
    val initialLeftEnabled = remember { PixelShadeConfig.leftEnabled(context) }
    val initialRightEnabled = remember { PixelShadeConfig.rightEnabled(context) }
    val initialThemeMode = remember { PixelShadeThemeEngine.mode(context) }

    var triggerHeight by remember { mutableFloatStateOf(initialTriggerHeight) }
    var visibleHeight by remember { mutableFloatStateOf(initialVisibleHeight) }
    var topWidth by remember { mutableFloatStateOf(initialTopWidth) }
    var topX by remember { mutableFloatStateOf(initialTopX) }
    var offset by remember { mutableFloatStateOf(initialOffset) }
    var pullDistance by remember { mutableFloatStateOf(initialPullDistance) }
    var brightnessGesture by remember { mutableStateOf(initialBrightnessGesture) }
    var brightnessSensitivity by remember { mutableFloatStateOf(initialBrightnessSensitivity) }
    var suppressStock by remember { mutableStateOf(initialSuppressStock) }
    var opacity by remember { mutableFloatStateOf(initialOpacity) }
    var blur by remember { mutableFloatStateOf(initialBlur) }
    var tileCorner by remember { mutableFloatStateOf(initialTileCorner) }
    var openDuration by remember { mutableFloatStateOf(initialOpenDuration) }
    var closeDuration by remember { mutableFloatStateOf(initialCloseDuration) }
    var leftEnabled by remember { mutableStateOf(initialLeftEnabled) }
    var rightEnabled by remember { mutableStateOf(initialRightEnabled) }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var themeMode by remember { mutableStateOf(initialThemeMode) }

    fun initialColor(key: String, fallback: Color) = PixelShadeThemeEngine.storedColor(context, key)
        ?: "#%08X".format(fallback.value.toLong() shr 32)

    var panelHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_PANEL) ?: colorToHex(scheme.surface)) }
    var activeTileHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_ACTIVE_TILE) ?: colorToHex(scheme.primary)) }
    var inactiveTileHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_INACTIVE_TILE) ?: colorToHex(scheme.surfaceContainerHigh)) }
    var activeIconHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_ACTIVE_ICON) ?: colorToHex(scheme.onPrimary)) }
    var inactiveIconHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_INACTIVE_ICON) ?: colorToHex(scheme.onSurface)) }
    var primaryTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_PRIMARY_TEXT) ?: colorToHex(scheme.onSurface)) }
    var secondaryTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_SECONDARY_TEXT) ?: colorToHex(scheme.onSurfaceVariant)) }
    var brightnessTrackHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_BRIGHTNESS_TRACK) ?: colorToHex(scheme.surfaceContainerHighest)) }
    var brightnessFillHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL) ?: colorToHex(scheme.primary)) }

    fun currentPalette(): PixelShadePalette {
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
        return when (themeMode) {
            PixelShadeThemeEngine.Mode.DYNAMIC -> dynamic
            PixelShadeThemeEngine.Mode.HYBRID -> dynamic.copy(
                panel = PixelShadeThemeEngine.parseOr(panelHex, dynamic.panel),
                activeTile = PixelShadeThemeEngine.parseOr(activeTileHex, dynamic.activeTile),
                inactiveTile = PixelShadeThemeEngine.parseOr(inactiveTileHex, dynamic.inactiveTile),
                brightnessFill = PixelShadeThemeEngine.parseOr(brightnessFillHex, dynamic.brightnessFill)
            )
            PixelShadeThemeEngine.Mode.MANUAL -> PixelShadePalette(
                panel = PixelShadeThemeEngine.parseOr(panelHex, dynamic.panel),
                activeTile = PixelShadeThemeEngine.parseOr(activeTileHex, dynamic.activeTile),
                inactiveTile = PixelShadeThemeEngine.parseOr(inactiveTileHex, dynamic.inactiveTile),
                activeIcon = PixelShadeThemeEngine.parseOr(activeIconHex, dynamic.activeIcon),
                inactiveIcon = PixelShadeThemeEngine.parseOr(inactiveIconHex, dynamic.inactiveIcon),
                primaryText = PixelShadeThemeEngine.parseOr(primaryTextHex, dynamic.primaryText),
                secondaryText = PixelShadeThemeEngine.parseOr(secondaryTextHex, dynamic.secondaryText),
                brightnessTrack = PixelShadeThemeEngine.parseOr(brightnessTrackHex, dynamic.brightnessTrack),
                brightnessFill = PixelShadeThemeEngine.parseOr(brightnessFillHex, dynamic.brightnessFill)
            )
        }
    }

    fun persist() {
        prefs.edit()
            .putFloat(PixelShadeConfig.KEY_TRIGGER_HEIGHT_DP, triggerHeight)
            .putFloat(PixelShadeConfig.KEY_TRIGGER_VISIBLE_DP, visibleHeight.coerceAtMost(triggerHeight))
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
        PixelShadeThemeEngine.setMode(context, themeMode)
        listOf(
            PixelShadeThemeEngine.KEY_PANEL to panelHex,
            PixelShadeThemeEngine.KEY_ACTIVE_TILE to activeTileHex,
            PixelShadeThemeEngine.KEY_INACTIVE_TILE to inactiveTileHex,
            PixelShadeThemeEngine.KEY_ACTIVE_ICON to activeIconHex,
            PixelShadeThemeEngine.KEY_INACTIVE_ICON to inactiveIconHex,
            PixelShadeThemeEngine.KEY_PRIMARY_TEXT to primaryTextHex,
            PixelShadeThemeEngine.KEY_SECONDARY_TEXT to secondaryTextHex,
            PixelShadeThemeEngine.KEY_BRIGHTNESS_TRACK to brightnessTrackHex,
            PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL to brightnessFillHex
        ).forEach { (key, value) -> PixelShadeThemeEngine.putColor(context, key, value) }
        context.startService(Intent(context, PixelShadeTriggerService::class.java).setAction("com.crimson.pixelshade.REFRESH_CONFIG"))
        PixelShadeAccessibilityService.requestTriggerRefresh()
        StatusBarSuppression.sync(context)
    }

    fun resetDefaults() {
        triggerHeight = 10f
        visibleHeight = 2f
        topWidth = 100f
        topX = 50f
        offset = 0f
        pullDistance = 28f
        brightnessGesture = true
        brightnessSensitivity = 1f
        suppressStock = true
        opacity = .92f
        blur = 24f
        tileCorner = 24f
        openDuration = 320f
        closeDuration = 220f
        leftEnabled = false
        rightEnabled = false
        themeMode = PixelShadeThemeEngine.Mode.DYNAMIC
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Pixel Shade") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") } },
                actions = {
                    TextButton(onClick = { resetDefaults() }) { Text("Reset") }
                    TextButton(onClick = { persist(); onClose() }) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Live shade preview · scroll the preview itself to inspect the whole panel",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            InteractivePixel17Preview(
                palette = currentPalette(),
                opacity = opacity,
                tileCorner = tileCorner,
                triggerHeight = triggerHeight,
                visibleHeight = visibleHeight,
                topWidth = topWidth,
                topX = topX,
                offset = offset,
                onMoveTrigger = { dxPercent, dyDp ->
                    topX = (topX + dxPercent).coerceIn(0f, 100f)
                    offset = (offset + dyDp).coerceIn(0f, 120f)
                },
                onResizeWidth = { deltaPercent -> topWidth = (topWidth + deltaPercent).coerceIn(10f, 100f) },
                onResizeHeight = { deltaDp ->
                    triggerHeight = (triggerHeight + deltaDp).coerceIn(1f, 120f)
                    visibleHeight = visibleHeight.coerceAtMost(triggerHeight)
                }
            )

            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 8.dp) {
                PixelShadeEditorTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        icon = { Icon(tab.icon, null, Modifier.size(20.dp)) }
                    )
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    PixelShadeEditorTab.HANDLE -> {
                        EditorSection("Trigger handle", Icons.Default.SwipeDown) {
                            Text("Drag the outlined trigger directly in the preview. Drag its right handle to resize width and its lower handle to resize the touch height.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            EditorSlider("Touch height", triggerHeight, 1f..120f, "${triggerHeight.roundToInt()} dp") { triggerHeight = it; visibleHeight = visibleHeight.coerceAtMost(it) }
                            EditorSlider("Visible strip", visibleHeight, 0f..24f, "${visibleHeight.roundToInt()} dp") { visibleHeight = it.coerceAtMost(triggerHeight) }
                            EditorSlider("Width", topWidth, 10f..100f, "${topWidth.roundToInt()}%") { topWidth = it }
                            EditorSlider("Horizontal position", topX, 0f..100f, "${topX.roundToInt()}%") { topX = it }
                            EditorSlider("Vertical offset", offset, 0f..120f, "${offset.roundToInt()} dp") { offset = it }
                            EditorSlider("Pull distance", pullDistance, 8f..180f, "${pullDistance.roundToInt()} dp") { pullDistance = it }
                            EditorSwitch("Left edge trigger", leftEnabled) { leftEnabled = it }
                            EditorSwitch("Right edge trigger", rightEnabled) { rightEnabled = it }
                        }
                    }
                    PixelShadeEditorTab.LAYOUT -> {
                        EditorSection("Pixel 17 layout", Icons.Default.DashboardCustomize) {
                            Text("The shade uses a four-unit Pixel-style grid: compact 1×1 tiles and 2×1 labeled tiles. Custom tiles keep their selected 1×1, 2×1 or 4×1 span.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = onOpenTiles, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.GridView, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Edit tiles and shortcuts")
                            }
                            EditorSlider("Tile corner radius", tileCorner, 12f..32f, "${tileCorner.roundToInt()} dp") { tileCorner = it }
                            EditorSlider("Panel opacity", opacity, .45f..1f, "${(opacity * 100).roundToInt()}%") { opacity = it }
                            EditorSlider("Background blur", blur, 0f..80f, "${blur.roundToInt()} dp") { blur = it }
                        }
                    }
                    PixelShadeEditorTab.COLORS -> {
                        EditorSection("Theme engine", Icons.Default.Palette) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                PixelShadeThemeEngine.Mode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = themeMode == mode,
                                        onClick = { themeMode = mode },
                                        shape = SegmentedButtonDefaults.itemShape(index, PixelShadeThemeEngine.Mode.entries.size)
                                    ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                }
                            }
                            Text(
                                when (themeMode) {
                                    PixelShadeThemeEngine.Mode.DYNAMIC -> "Dynamic follows the current Material You wallpaper scheme."
                                    PixelShadeThemeEngine.Mode.HYBRID -> "Hybrid keeps dynamic text/icons but lets you override the panel, tiles and accent."
                                    PixelShadeThemeEngine.Mode.MANUAL -> "Manual exposes every shade color independently."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (themeMode != PixelShadeThemeEngine.Mode.DYNAMIC) {
                                HexColorEditor("Panel background", panelHex) { panelHex = it }
                                HexColorEditor("Active tile", activeTileHex) { activeTileHex = it }
                                HexColorEditor("Inactive tile", inactiveTileHex) { inactiveTileHex = it }
                                HexColorEditor("Brightness fill", brightnessFillHex) { brightnessFillHex = it }
                            }
                            if (themeMode == PixelShadeThemeEngine.Mode.MANUAL) {
                                HexColorEditor("Active icon", activeIconHex) { activeIconHex = it }
                                HexColorEditor("Inactive icon", inactiveIconHex) { inactiveIconHex = it }
                                HexColorEditor("Primary text", primaryTextHex) { primaryTextHex = it }
                                HexColorEditor("Secondary text", secondaryTextHex) { secondaryTextHex = it }
                                HexColorEditor("Brightness track", brightnessTrackHex) { brightnessTrackHex = it }
                            }
                        }
                    }
                    PixelShadeEditorTab.SLIDERS -> {
                        EditorSection("Brightness and gestures", Icons.Default.Tune) {
                            EditorSwitch("Horizontal trigger swipe changes brightness", brightnessGesture) { brightnessGesture = it }
                            if (brightnessGesture) {
                                EditorSlider("Brightness sensitivity", brightnessSensitivity, .25f..3f, String.format("%.2fx", brightnessSensitivity)) { brightnessSensitivity = it }
                            }
                            Text("The visible brightness control in the shade uses the same theme-engine track and fill colors shown above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PixelShadeEditorTab.NOTIFICATIONS -> {
                        EditorSection("Notifications", Icons.Default.Notifications) {
                            Text("Notification cards, media controls, actions, dismiss and Clear all are enabled when notification access is granted. The preview includes demo notification content so lower-shade styling can be inspected without waiting for a real notification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Notification access")
                            }
                        }
                    }
                    PixelShadeEditorTab.MOTION -> {
                        EditorSection("Pixel motion", Icons.Default.Animation) {
                            Text("Opening should track the pull gesture directly; spring/easing is used only when settling after release. These values control the settle phase, not a canned screen entrance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            EditorSlider("Open settle", openDuration, 180f..600f, "${openDuration.roundToInt()} ms") { openDuration = it }
                            EditorSlider("Close settle", closeDuration, 120f..420f, "${closeDuration.roundToInt()} ms") { closeDuration = it }
                        }
                    }
                    PixelShadeEditorTab.ADVANCED -> {
                        EditorSection("System integration", Icons.Default.Build) {
                            EditorSwitch("Block OxygenOS stock shade", suppressStock) { suppressStock = it }
                            Text("The normal block uses Android's statusbar-expansion disable state. The separate Oplus QS plugin isolation control remains in the main settings screen as an optional diagnostic fallback.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InteractivePixel17Preview(
    palette: PixelShadePalette,
    opacity: Float,
    tileCorner: Float,
    triggerHeight: Float,
    visibleHeight: Float,
    topWidth: Float,
    topX: Float,
    offset: Float,
    onMoveTrigger: (Float, Float) -> Unit,
    onResizeWidth: (Float) -> Unit,
    onResizeHeight: (Float) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(390.dp).padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.panel.copy(alpha = opacity))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        val previewWidthDp = maxWidth
        val previewWidthPx = with(density) { previewWidthDp.toPx() }
        val shadeScroll = rememberScrollState()

        Column(
            Modifier.fillMaxSize().verticalScroll(shadeScroll).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("11:00", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
                    Text("Thu, Mar 26", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("No service", style = MaterialTheme.typography.labelMedium, color = palette.primaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, Modifier.size(15.dp), tint = palette.primaryText)
                        Text("53%", style = MaterialTheme.typography.labelSmall, color = palette.primaryText)
                    }
                }
            }

            PreviewBrightness(palette)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewCompactTile(Icons.Default.Wifi, true, tileCorner, palette, Modifier.weight(1f))
                PreviewCompactTile(Icons.Default.SwapVert, true, tileCorner, palette, Modifier.weight(1f))
                PreviewWideTile("Bluetooth", Icons.Default.Bluetooth, false, tileCorner, palette, Modifier.weight(2f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewWideTile("Flashlight", Icons.Default.FlashlightOn, false, tileCorner, palette, Modifier.weight(1f))
                PreviewWideTile("Screen record", Icons.Default.ScreenShare, false, tileCorner, palette, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewWideTile("Modes", Icons.Default.DoNotDisturbOn, false, tileCorner, palette, Modifier.weight(1f))
                PreviewWideTile("Rotation", Icons.Default.ScreenRotation, true, tileCorner, palette, Modifier.weight(1f))
            }

            Text("Notifications", style = MaterialTheme.typography.titleSmall, color = palette.secondaryText)
            Surface(shape = RoundedCornerShape(22.dp), color = palette.inactiveTile) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Messages", style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
                    Text("Live preview", style = MaterialTheme.typography.titleMedium, color = palette.primaryText)
                    Text("Scroll this preview independently to inspect notifications, media and the lower shade.", style = MaterialTheme.typography.bodySmall, color = palette.secondaryText)
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = palette.inactiveTile) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Media", style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
                    Text("Nothing playing", style = MaterialTheme.typography.titleMedium, color = palette.primaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = palette.inactiveIcon)
                        Icon(Icons.Default.PlayArrow, null, tint = palette.inactiveIcon)
                        Icon(Icons.Default.SkipNext, null, tint = palette.inactiveIcon)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.Edit, "Edit", tint = palette.primaryText)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.PowerSettingsNew, "Power", tint = palette.primaryText)
            }
            Spacer(Modifier.height(40.dp))
        }

        val zoneWidth = previewWidthDp * (topWidth / 100f)
        val zoneStart = (previewWidthDp * (topX / 100f) - zoneWidth / 2f).coerceIn(0.dp, previewWidthDp - zoneWidth)
        val previewTouchHeight = triggerHeight.dp.coerceAtMost(56.dp)

        Box(
            Modifier.offset(x = zoneStart, y = offset.dp)
                .width(zoneWidth)
                .height(previewTouchHeight)
                .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                .pointerInput(topX, offset, topWidth) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onMoveTrigger(drag.x / previewWidthPx * 100f, with(density) { drag.y.toDp().value })
                    }
                }
        ) {
            if (visibleHeight > 0f) {
                Box(
                    Modifier.fillMaxWidth().height(visibleHeight.dp.coerceAtMost(previewTouchHeight))
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = .65f))
                )
            }
            Text(
                "trigger",
                modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).padding(horizontal = 5.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                Modifier.align(Alignment.CenterEnd).size(width = 14.dp, height = 30.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(7.dp))
                    .pointerInput(topWidth) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onResizeWidth(drag.x / previewWidthPx * 100f)
                        }
                    }
            )
            Box(
                Modifier.align(Alignment.BottomCenter).size(width = 34.dp, height = 12.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                    .pointerInput(triggerHeight) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onResizeHeight(with(density) { drag.y.toDp().value })
                        }
                    }
            )
        }
    }
}

@Composable
private fun PreviewBrightness(palette: PixelShadePalette) {
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = palette.brightnessTrack) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(.66f)
                        .background(palette.brightnessFill, RoundedCornerShape(14.dp))
                )
            }
        }
        Surface(Modifier.width(52.dp).fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = palette.inactiveTile) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, "Settings", tint = palette.inactiveIcon) }
        }
    }
}

@Composable
private fun PreviewCompactTile(icon: ImageVector, active: Boolean, corner: Float, palette: PixelShadePalette, modifier: Modifier) {
    val bg = if (active) palette.activeTile else palette.inactiveTile
    val fg = if (active) palette.activeIcon else palette.inactiveIcon
    Surface(modifier.aspectRatio(1f), shape = RoundedCornerShape(corner.dp.coerceAtMost(26.dp)), color = bg) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(25.dp), tint = fg) }
    }
}

@Composable
private fun PreviewWideTile(label: String, icon: ImageVector, active: Boolean, corner: Float, palette: PixelShadePalette, modifier: Modifier) {
    val bg = if (active) palette.activeTile else palette.inactiveTile
    val fg = if (active) palette.activeIcon else palette.inactiveIcon
    Surface(modifier.height(62.dp), shape = RoundedCornerShape(corner.dp.coerceAtMost(28.dp)), color = bg) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = fg)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = fg)
        }
    }
}

@Composable
private fun HexColorEditor(label: String, value: String, onValue: (String) -> Unit) {
    val fallback = MaterialTheme.colorScheme.surface
    val swatch = PixelShadeThemeEngine.parseOr(value, fallback)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(swatch).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun colorToHex(color: Color): String {
    val a = (color.alpha * 255).roundToInt().coerceIn(0, 255)
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X%02X".format(a, r, g, b)
}

@Composable
private fun EditorSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
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
