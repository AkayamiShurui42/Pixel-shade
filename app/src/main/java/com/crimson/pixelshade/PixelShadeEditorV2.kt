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
    TILE_STYLES("Tile Styles", Icons.Default.Gradient),
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

    var triggerHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerHeightDp(context)) }
    var visibleHeight by remember { mutableFloatStateOf(PixelShadeConfig.triggerVisibleDp(context)) }
    var topWidth by remember { mutableFloatStateOf(PixelShadeConfig.topWidthPercent(context)) }
    var topX by remember { mutableFloatStateOf(PixelShadeConfig.topXPercent(context)) }
    var offset by remember { mutableFloatStateOf(PixelShadeConfig.triggerOffsetDp(context)) }
    var pullDistance by remember { mutableFloatStateOf(PixelShadeConfig.pullDistanceDp(context)) }

    var bottomEnabled by remember { mutableStateOf(PixelShadeConfig.bottomEnabled(context)) }
    var bottomWidth by remember { mutableFloatStateOf(PixelShadeConfig.bottomWidthPercent(context)) }
    var bottomHeight by remember { mutableFloatStateOf(PixelShadeConfig.bottomHeightDp(context)) }
    var bottomX by remember { mutableFloatStateOf(PixelShadeConfig.bottomXPercent(context)) }

    var leftEnabled by remember { mutableStateOf(PixelShadeConfig.leftEnabled(context)) }
    var leftWidth by remember { mutableFloatStateOf(PixelShadeConfig.leftWidthDp(context)) }
    var leftHeight by remember { mutableFloatStateOf(PixelShadeConfig.leftHeightDp(context)) }
    var leftY by remember { mutableFloatStateOf(PixelShadeConfig.leftYPercent(context)) }

    var rightEnabled by remember { mutableStateOf(PixelShadeConfig.rightEnabled(context)) }
    var rightWidth by remember { mutableFloatStateOf(PixelShadeConfig.rightWidthDp(context)) }
    var rightHeight by remember { mutableFloatStateOf(PixelShadeConfig.rightHeightDp(context)) }
    var rightY by remember { mutableFloatStateOf(PixelShadeConfig.rightYPercent(context)) }

    var hideHandleIcon by remember { mutableStateOf(PixelShadeConfig.hideHandleIcon(context)) }
    var hideLandscape by remember { mutableStateOf(PixelShadeConfig.hideInLandscape(context)) }

    var brightnessGesture by remember { mutableStateOf(PixelShadeConfig.brightnessEnabled(context)) }
    var brightnessSensitivity by remember { mutableFloatStateOf(PixelShadeConfig.brightnessSensitivity(context)) }

    var suppressStock by remember { mutableStateOf(PixelShadeConfig.suppressStockShade(context)) }
    var vibrateOnTouch by remember { mutableStateOf(PixelShadeConfig.vibrateOnTouch(context)) }
    var autoCloseTile by remember { mutableStateOf(PixelShadeConfig.autoCloseTile(context)) }

    var opacity by remember { mutableFloatStateOf(PixelShadeConfig.panelOpacity(context)) }
    var blur by remember { mutableFloatStateOf(PixelShadeConfig.blurRadius(context)) }
    var tileCorner by remember { mutableFloatStateOf(PixelShadeConfig.tileCornerDp(context)) }
    var panelPadding by remember { mutableFloatStateOf(PixelShadeConfig.panelPaddingDp(context)) }
    var tileHeight by remember { mutableFloatStateOf(PixelShadeConfig.tileHeightDp(context)) }
    var showSystemIcons by remember { mutableStateOf(PixelShadeConfig.showSystemIcons(context)) }
    var showPanelHeader by remember { mutableStateOf(PixelShadeConfig.showPanelHeader(context)) }
    var showPanelFooter by remember { mutableStateOf(PixelShadeConfig.showPanelFooter(context)) }
    var hideTileText by remember { mutableStateOf(PixelShadeConfig.hideTileText(context)) }
    var use24HourClock by remember { mutableStateOf(PixelShadeConfig.use24HourClock(context)) }

    var showNotifications by remember { mutableStateOf(PixelShadeConfig.showNotifications(context)) }
    var hidePersistent by remember { mutableStateOf(PixelShadeConfig.hidePersistentNotifications(context)) }
    var onlyMedia by remember { mutableStateOf(PixelShadeConfig.onlyMediaNotifications(context)) }
    var removeNotificationSpacing by remember { mutableStateOf(PixelShadeConfig.removeNotificationSpacing(context)) }
    var autoExpandNotifications by remember { mutableStateOf(PixelShadeConfig.autoExpandNotifications(context)) }
    var autoCloseAfterClear by remember { mutableStateOf(PixelShadeConfig.autoCloseAfterClear(context)) }

    var openDuration by remember { mutableFloatStateOf(PixelShadeConfig.openDurationMs(context).toFloat()) }
    var closeDuration by remember { mutableFloatStateOf(PixelShadeConfig.closeDurationMs(context).toFloat()) }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var themeMode by remember { mutableStateOf(PixelShadeThemeEngine.mode(context)) }

    var panelHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_PANEL) ?: colorToHex(scheme.surface)) }
    var activeTileHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_ACTIVE_TILE) ?: colorToHex(scheme.primary)) }
    var inactiveTileHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_INACTIVE_TILE) ?: colorToHex(scheme.surfaceContainerHigh)) }
    var activeIconHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_ACTIVE_ICON) ?: colorToHex(scheme.onPrimary)) }
    var inactiveIconHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_INACTIVE_ICON) ?: colorToHex(scheme.onSurface)) }
    var primaryTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_PRIMARY_TEXT) ?: colorToHex(scheme.onSurface)) }
    var secondaryTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_SECONDARY_TEXT) ?: colorToHex(scheme.onSurfaceVariant)) }
    var brightnessTrackHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_BRIGHTNESS_TRACK) ?: colorToHex(scheme.surfaceContainerHighest)) }
    var brightnessFillHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL) ?: colorToHex(scheme.primary)) }

    var notificationBackgroundHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_NOTIFICATION_BACKGROUND) ?: colorToHex(scheme.surfaceContainerHigh)) }
    var tileTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_TILE_TEXT) ?: colorToHex(scheme.onSurface)) }
    var headerTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_HEADER_TEXT) ?: colorToHex(scheme.onSurface)) }
    var footerBackgroundHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_FOOTER_BACKGROUND) ?: "#00000000") }
    var footerTextHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_FOOTER_TEXT) ?: colorToHex(scheme.onSurface)) }
    var handleHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_HANDLE) ?: colorToHex(scheme.onSurfaceVariant)) }
    var sliderIconHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_SLIDER_ICON) ?: colorToHex(scheme.onPrimary)) }
    var sliderThumbHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_SLIDER_THUMB) ?: colorToHex(scheme.primary)) }
    var sliderProgressHex by remember { mutableStateOf(PixelShadeThemeEngine.storedColor(context, PixelShadeThemeEngine.KEY_SLIDER_PROGRESS) ?: colorToHex(scheme.primary)) }

    var gradientsEnabled by remember { mutableStateOf(PixelShadeTileStyle.gradientsEnabled(context)) }
    var activeGradientStartHex by remember {
        mutableStateOf(prefs.getString(PixelShadeTileStyle.KEY_ACTIVE_GRADIENT_START, null) ?: activeTileHex)
    }
    var activeGradientEndHex by remember {
        mutableStateOf(prefs.getString(PixelShadeTileStyle.KEY_ACTIVE_GRADIENT_END, null) ?: brightnessFillHex)
    }
    var activeGradientDirection by remember { mutableFloatStateOf(PixelShadeTileStyle.activeGradientDirection(context)) }
    var inactiveGradientStartHex by remember {
        mutableStateOf(prefs.getString(PixelShadeTileStyle.KEY_INACTIVE_GRADIENT_START, null) ?: inactiveTileHex)
    }
    var inactiveGradientEndHex by remember {
        mutableStateOf(prefs.getString(PixelShadeTileStyle.KEY_INACTIVE_GRADIENT_END, null) ?: inactiveTileHex)
    }
    var inactiveGradientDirection by remember { mutableFloatStateOf(PixelShadeTileStyle.inactiveGradientDirection(context)) }

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
            .putBoolean(PixelShadeConfig.KEY_BOTTOM_ENABLED, bottomEnabled)
            .putFloat(PixelShadeConfig.KEY_BOTTOM_WIDTH_PERCENT, bottomWidth)
            .putFloat(PixelShadeConfig.KEY_BOTTOM_HEIGHT_DP, bottomHeight)
            .putFloat(PixelShadeConfig.KEY_BOTTOM_X_PERCENT, bottomX)
            .putBoolean(PixelShadeConfig.KEY_LEFT_ENABLED, leftEnabled)
            .putFloat(PixelShadeConfig.KEY_LEFT_WIDTH_DP, leftWidth)
            .putFloat(PixelShadeConfig.KEY_LEFT_HEIGHT_DP, leftHeight)
            .putFloat(PixelShadeConfig.KEY_LEFT_Y_PERCENT, leftY)
            .putBoolean(PixelShadeConfig.KEY_RIGHT_ENABLED, rightEnabled)
            .putFloat(PixelShadeConfig.KEY_RIGHT_WIDTH_DP, rightWidth)
            .putFloat(PixelShadeConfig.KEY_RIGHT_HEIGHT_DP, rightHeight)
            .putFloat(PixelShadeConfig.KEY_RIGHT_Y_PERCENT, rightY)
            .putBoolean(PixelShadeConfig.KEY_HIDE_HANDLE_ICON, hideHandleIcon)
            .putBoolean(PixelShadeConfig.KEY_HIDE_IN_LANDSCAPE, hideLandscape)
            .putBoolean(PixelShadeConfig.KEY_BRIGHTNESS_ENABLED, brightnessGesture)
            .putFloat(PixelShadeConfig.KEY_BRIGHTNESS_SENSITIVITY, brightnessSensitivity)
            .putBoolean(PixelShadeConfig.KEY_SUPPRESS_STOCK_SHADE, suppressStock)
            .putBoolean(PixelShadeConfig.KEY_VIBRATE_ON_TOUCH, vibrateOnTouch)
            .putBoolean(PixelShadeConfig.KEY_AUTO_CLOSE_TILE, autoCloseTile)
            .putFloat(PixelShadeConfig.KEY_PANEL_OPACITY, opacity)
            .putFloat(PixelShadeConfig.KEY_BLUR_RADIUS, blur)
            .putFloat(PixelShadeConfig.KEY_TILE_CORNER_DP, tileCorner)
            .putFloat(PixelShadeConfig.KEY_PANEL_PADDING_DP, panelPadding)
            .putFloat(PixelShadeConfig.KEY_TILE_HEIGHT_DP, tileHeight)
            .putBoolean(PixelShadeConfig.KEY_SHOW_SYSTEM_ICONS, showSystemIcons)
            .putBoolean(PixelShadeConfig.KEY_SHOW_PANEL_HEADER, showPanelHeader)
            .putBoolean(PixelShadeConfig.KEY_SHOW_PANEL_FOOTER, showPanelFooter)
            .putBoolean(PixelShadeConfig.KEY_HIDE_TILE_TEXT, hideTileText)
            .putBoolean(PixelShadeConfig.KEY_USE_24_HOUR_CLOCK, use24HourClock)
            .putBoolean(PixelShadeConfig.KEY_SHOW_NOTIFICATIONS, showNotifications)
            .putBoolean(PixelShadeConfig.KEY_HIDE_PERSISTENT_NOTIFICATIONS, hidePersistent)
            .putBoolean(PixelShadeConfig.KEY_ONLY_MEDIA_NOTIFICATIONS, onlyMedia)
            .putBoolean(PixelShadeConfig.KEY_REMOVE_NOTIFICATION_SPACING, removeNotificationSpacing)
            .putBoolean(PixelShadeConfig.KEY_AUTO_EXPAND_NOTIFICATIONS, autoExpandNotifications)
            .putBoolean(PixelShadeConfig.KEY_AUTO_CLOSE_AFTER_CLEAR, autoCloseAfterClear)
            .putInt(PixelShadeConfig.KEY_OPEN_DURATION_MS, openDuration.toInt())
            .putInt(PixelShadeConfig.KEY_CLOSE_DURATION_MS, closeDuration.toInt())
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
            PixelShadeThemeEngine.KEY_BRIGHTNESS_FILL to brightnessFillHex,
            PixelShadeThemeEngine.KEY_NOTIFICATION_BACKGROUND to notificationBackgroundHex,
            PixelShadeThemeEngine.KEY_TILE_TEXT to tileTextHex,
            PixelShadeThemeEngine.KEY_HEADER_TEXT to headerTextHex,
            PixelShadeThemeEngine.KEY_FOOTER_BACKGROUND to footerBackgroundHex,
            PixelShadeThemeEngine.KEY_FOOTER_TEXT to footerTextHex,
            PixelShadeThemeEngine.KEY_HANDLE to handleHex,
            PixelShadeThemeEngine.KEY_SLIDER_ICON to sliderIconHex,
            PixelShadeThemeEngine.KEY_SLIDER_THUMB to sliderThumbHex,
            PixelShadeThemeEngine.KEY_SLIDER_PROGRESS to sliderProgressHex
        ).forEach { (key, value) -> PixelShadeThemeEngine.putColor(context, key, value) }

        PixelShadeTileStyle.setGradientsEnabled(context, gradientsEnabled)
        PixelShadeTileStyle.putGradient(context, true, activeGradientStartHex, activeGradientEndHex, activeGradientDirection)
        PixelShadeTileStyle.putGradient(context, false, inactiveGradientStartHex, inactiveGradientEndHex, inactiveGradientDirection)

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
        bottomEnabled = false
        bottomWidth = 100f
        bottomHeight = 12f
        bottomX = 50f
        leftEnabled = false
        leftWidth = 18f
        leftHeight = 180f
        leftY = 40f
        rightEnabled = false
        rightWidth = 18f
        rightHeight = 180f
        rightY = 40f
        hideHandleIcon = false
        hideLandscape = false
        brightnessGesture = true
        brightnessSensitivity = 1f
        suppressStock = true
        vibrateOnTouch = true
        autoCloseTile = false
        opacity = .92f
        blur = 24f
        tileCorner = 24f
        panelPadding = 18f
        tileHeight = 62f
        showSystemIcons = true
        showPanelHeader = true
        showPanelFooter = true
        hideTileText = false
        use24HourClock = false
        showNotifications = true
        hidePersistent = false
        onlyMedia = false
        removeNotificationSpacing = false
        autoExpandNotifications = true
        autoCloseAfterClear = false
        openDuration = 320f
        closeDuration = 220f
        gradientsEnabled = false
        activeGradientStartHex = activeTileHex
        activeGradientEndHex = brightnessFillHex
        activeGradientDirection = 90f
        inactiveGradientStartHex = inactiveTileHex
        inactiveGradientEndHex = inactiveTileHex
        inactiveGradientDirection = 90f
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
                        EditorSection("Trigger handles", Icons.Default.SwipeDown) {
                            Text(
                                "Top opens downward. Bottom, left and right handles open with an upward swipe. Hiding the handle icon keeps the touch target active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            EditorSwitch("Hide handle icon", hideHandleIcon) { hideHandleIcon = it }
                            EditorSwitch("Hide handles in landscape", hideLandscape) { hideLandscape = it }
                            HorizontalDivider()
                            Text("Top handle", style = MaterialTheme.typography.titleSmall)
                            EditorSlider("Touch height", triggerHeight, 1f..120f, "${triggerHeight.roundToInt()} dp") { triggerHeight = it; visibleHeight = visibleHeight.coerceAtMost(it) }
                            EditorSlider("Visible strip", visibleHeight, 0f..24f, "${visibleHeight.roundToInt()} dp") { visibleHeight = it.coerceAtMost(triggerHeight) }
                            EditorSlider("Length", topWidth, 10f..100f, "${topWidth.roundToInt()}%") { topWidth = it }
                            EditorSlider("Position", topX, 0f..100f, "${topX.roundToInt()}%") { topX = it }
                            EditorSlider("Vertical offset", offset, 0f..120f, "${offset.roundToInt()} dp") { offset = it }
                            EditorSlider("Pull distance", pullDistance, 8f..180f, "${pullDistance.roundToInt()} dp") { pullDistance = it }
                            HorizontalDivider()
                            Text("Bottom handle", style = MaterialTheme.typography.titleSmall)
                            EditorSwitch("Enable bottom handle", bottomEnabled) { bottomEnabled = it }
                            if (bottomEnabled) {
                                EditorSlider("Length", bottomWidth, 10f..100f, "${bottomWidth.roundToInt()}%") { bottomWidth = it }
                                EditorSlider("Size", bottomHeight, 2f..64f, "${bottomHeight.roundToInt()} dp") { bottomHeight = it }
                                EditorSlider("Position", bottomX, 0f..100f, "${bottomX.roundToInt()}%") { bottomX = it }
                            }
                            HorizontalDivider()
                            Text("Left handle", style = MaterialTheme.typography.titleSmall)
                            EditorSwitch("Enable left handle", leftEnabled) { leftEnabled = it }
                            if (leftEnabled) {
                                EditorSlider("Length", leftHeight, 40f..900f, "${leftHeight.roundToInt()} dp") { leftHeight = it }
                                EditorSlider("Size", leftWidth, 2f..64f, "${leftWidth.roundToInt()} dp") { leftWidth = it }
                                EditorSlider("Position", leftY, 0f..100f, "${leftY.roundToInt()}%") { leftY = it }
                            }
                            HorizontalDivider()
                            Text("Right handle", style = MaterialTheme.typography.titleSmall)
                            EditorSwitch("Enable right handle", rightEnabled) { rightEnabled = it }
                            if (rightEnabled) {
                                EditorSlider("Length", rightHeight, 40f..900f, "${rightHeight.roundToInt()} dp") { rightHeight = it }
                                EditorSlider("Size", rightWidth, 2f..64f, "${rightWidth.roundToInt()} dp") { rightWidth = it }
                                EditorSlider("Position", rightY, 0f..100f, "${rightY.roundToInt()}%") { rightY = it }
                            }
                        }
                    }

                    PixelShadeEditorTab.LAYOUT -> {
                        EditorSection("Pixel 17 layout", Icons.Default.DashboardCustomize) {
                            FilledTonalButton(onClick = onOpenTiles, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.GridView, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Edit tiles and shortcuts")
                            }
                            EditorSlider("Tile corner radius", tileCorner, 12f..32f, "${tileCorner.roundToInt()} dp") { tileCorner = it }
                            EditorSlider("Tile height", tileHeight, 44f..96f, "${tileHeight.roundToInt()} dp") { tileHeight = it }
                            EditorSlider("Panel padding", panelPadding, 8f..40f, "${panelPadding.roundToInt()} dp") { panelPadding = it }
                            EditorSlider("Panel opacity", opacity, .45f..1f, "${(opacity * 100).roundToInt()}%") { opacity = it }
                            EditorSlider("Background blur", blur, 0f..80f, "${blur.roundToInt()} dp") { blur = it }
                            EditorSwitch("Show system icons", showSystemIcons) { showSystemIcons = it }
                            EditorSwitch("Show panel header", showPanelHeader) { showPanelHeader = it }
                            EditorSwitch("Show panel footer", showPanelFooter) { showPanelFooter = it }
                            EditorSwitch("Hide tile text", hideTileText) { hideTileText = it }
                            EditorSwitch("Use 24-hour clock", use24HourClock) { use24HourClock = it }
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
                                    PixelShadeThemeEngine.Mode.HYBRID -> "Hybrid keeps dynamic text/icons while allowing the major surfaces and accent to be overridden."
                                    PixelShadeThemeEngine.Mode.MANUAL -> "Manual exposes every color that the current runtime renderer actually consumes."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (themeMode != PixelShadeThemeEngine.Mode.DYNAMIC) {
                                HexColorEditor("Panel background", panelHex) { panelHex = it }
                                HexColorEditor("Tile background (enabled)", activeTileHex) { activeTileHex = it }
                                HexColorEditor("Tile background (disabled)", inactiveTileHex) { inactiveTileHex = it }
                                HexColorEditor("Brightness fill", brightnessFillHex) { brightnessFillHex = it }
                            }
                            if (themeMode == PixelShadeThemeEngine.Mode.MANUAL) {
                                HexColorEditor("Tile icon (enabled)", activeIconHex) { activeIconHex = it }
                                HexColorEditor("Tile icon (disabled)", inactiveIconHex) { inactiveIconHex = it }
                                HexColorEditor("Primary text", primaryTextHex) { primaryTextHex = it }
                                HexColorEditor("Secondary text", secondaryTextHex) { secondaryTextHex = it }
                                HexColorEditor("Brightness track", brightnessTrackHex) { brightnessTrackHex = it }
                                HexColorEditor("Notification background", notificationBackgroundHex) { notificationBackgroundHex = it }
                                HexColorEditor("Tile text", tileTextHex) { tileTextHex = it }
                                HexColorEditor("Header text", headerTextHex) { headerTextHex = it }
                                HexColorEditor("Footer background", footerBackgroundHex) { footerBackgroundHex = it }
                                HexColorEditor("Footer text", footerTextHex) { footerTextHex = it }
                                HexColorEditor("Dismiss handle", handleHex) { handleHex = it }
                                HexColorEditor("Slider icon", sliderIconHex) { sliderIconHex = it }
                                HexColorEditor("Slider thumb", sliderThumbHex) { sliderThumbHex = it }
                                HexColorEditor("Slider progress", sliderProgressHex) { sliderProgressHex = it }
                            }
                        }
                    }

                    PixelShadeEditorTab.TILE_STYLES -> {
                        EditorSection("Tile Styles", Icons.Default.Gradient) {
                            EditorSwitch("Enable tile gradients", gradientsEnabled) { gradientsEnabled = it }
                            Text(
                                if (gradientsEnabled) "Gradients replace only the tile background layer. Icon and text colors remain controlled by Colors."
                                else "Gradients are off; enabled and disabled tiles use the solid Colors values.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (gradientsEnabled) {
                                Text("Enabled tile gradient", style = MaterialTheme.typography.titleSmall)
                                HexColorEditor("Gradient color start", activeGradientStartHex) { activeGradientStartHex = it }
                                HexColorEditor("Gradient color end", activeGradientEndHex) { activeGradientEndHex = it }
                                EditorSlider("Direction", activeGradientDirection, 0f..360f, "${activeGradientDirection.roundToInt()}°") { activeGradientDirection = it }
                                HorizontalDivider()
                                Text("Disabled tile gradient", style = MaterialTheme.typography.titleSmall)
                                HexColorEditor("Gradient color start", inactiveGradientStartHex) { inactiveGradientStartHex = it }
                                HexColorEditor("Gradient color end", inactiveGradientEndHex) { inactiveGradientEndHex = it }
                                EditorSlider("Direction", inactiveGradientDirection, 0f..360f, "${inactiveGradientDirection.roundToInt()}°") { inactiveGradientDirection = it }
                            }
                            Text(
                                "The reference app also offers an icon-shape selector. Pixel Shade keeps that control hidden for now because the Pixel compact/wide/custom tile geometry does not yet have one consistent icon-container shape to modify.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    PixelShadeEditorTab.SLIDERS -> {
                        EditorSection("Brightness and gestures", Icons.Default.Tune) {
                            EditorSwitch("Horizontal trigger swipe changes brightness", brightnessGesture) { brightnessGesture = it }
                            if (brightnessGesture) {
                                EditorSlider("Brightness sensitivity", brightnessSensitivity, .25f..3f, String.format("%.2fx", brightnessSensitivity)) { brightnessSensitivity = it }
                            }
                            Text(
                                "The runtime brightness control is a custom Pixel-style filled pill; it no longer uses the stock Material slider that produced the malformed thumb in build 80.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    PixelShadeEditorTab.NOTIFICATIONS -> {
                        EditorSection("Notifications", Icons.Default.Notifications) {
                            EditorSwitch("Show notifications", showNotifications) { showNotifications = it }
                            if (showNotifications) {
                                EditorSwitch("Hide persistent notifications", hidePersistent) { hidePersistent = it }
                                EditorSwitch("Only show media notifications", onlyMedia) { onlyMedia = it }
                                EditorSwitch("Remove spacing between notifications", removeNotificationSpacing) { removeNotificationSpacing = it }
                                EditorSwitch("Auto expand notifications", autoExpandNotifications) { autoExpandNotifications = it }
                                EditorSwitch("Auto close after clearing notifications", autoCloseAfterClear) { autoCloseAfterClear = it }
                            }
                            FilledTonalButton(
                                onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Notification access") }
                            Text(
                                "Persistent filtering, compact stacking, media-only mode and expansion are applied by the runtime shade rather than being preview-only options.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    PixelShadeEditorTab.MOTION -> {
                        EditorSection("Pixel motion", Icons.Default.Animation) {
                            Text("These values control the settle phase of the replacement shade.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            EditorSlider("Open settle", openDuration, 180f..600f, "${openDuration.roundToInt()} ms") { openDuration = it }
                            EditorSlider("Close settle", closeDuration, 120f..420f, "${closeDuration.roundToInt()} ms") { closeDuration = it }
                        }
                    }

                    PixelShadeEditorTab.ADVANCED -> {
                        EditorSection("System integration", Icons.Default.Build) {
                            EditorSwitch("Block OxygenOS stock shade", suppressStock) { suppressStock = it }
                            EditorSwitch("Vibrate on successful handle gesture", vibrateOnTouch) { vibrateOnTouch = it }
                            EditorSwitch("Auto close after tapping a Quick Settings tile", autoCloseTile) { autoCloseTile = it }
                            Text(
                                "Fullscreen/keyboard hiding, foreground-app blacklists and app-icon-derived notification colors remain hidden until their runtime paths are implemented. No dead switches are exposed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    Text("Wi-Fi", style = MaterialTheme.typography.labelMedium, color = palette.primaryText)
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
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Modifier.offset(x = zoneStart, y = offset.dp).width(zoneWidth).height(previewTouchHeight)
                .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                .pointerInput(topX, offset, topWidth) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onMoveTrigger(drag.x / previewWidthPx * 100f, with(density) { drag.y.toDp().value })
                    }
                }
        ) {
            if (visibleHeight > 0f) {
                Box(Modifier.fillMaxWidth().height(visibleHeight.dp.coerceAtMost(previewTouchHeight)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .65f)))
            }
            Text(
                "trigger",
                modifier = Modifier.align(Alignment.Center).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).padding(horizontal = 5.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                Modifier.align(Alignment.CenterEnd).size(width = 14.dp, height = 30.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(7.dp))
                    .pointerInput(topWidth) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onResizeWidth(drag.x / previewWidthPx * 100f)
                        }
                    }
            )
            Box(
                Modifier.align(Alignment.BottomCenter).size(width = 34.dp, height = 12.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
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
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(23.dp)).background(palette.brightnessTrack)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(.66f).background(palette.brightnessFill))
            Surface(Modifier.align(Alignment.CenterStart).offset(x = 112.dp).size(28.dp), shape = RoundedCornerShape(14.dp), color = palette.brightnessFill) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Brightness6, null, Modifier.size(16.dp), tint = palette.activeIcon) }
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
        OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
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
