package com.crimson.pixelshade

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class PixelShadePanelV2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = window.attributes
                lp.blurBehindRadius = (PixelShadeConfig.blurRadius(this) * resources.displayMetrics.density).roundToInt()
                window.attributes = lp
            }
        }
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val scheme = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Pixel17RuntimeShade(onFinish = { finish() })
            }
        }
    }
}

private data class RuntimeTile(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val compact: Boolean,
    val active: Boolean
)

@Composable
private fun Pixel17RuntimeShade(onFinish: () -> Unit) {
    val context = LocalContext.current
    val palette = rememberPixelShadePalette(context, MaterialTheme.colorScheme)
    val opacity = PixelShadeConfig.panelOpacity(context)
    val panelPadding = PixelShadeConfig.panelPaddingDp(context).coerceIn(8f, 40f).dp
    val customTiles = remember { PixelShadeTileStore.load(context) }
    val notifications = PixelShadeNotificationStore.items
    val systemStatus = rememberRuntimeSystemStatus(context)
    val openDurationMs = remember { PixelShadeConfig.openDurationMs(context).coerceIn(80, 1_000) }
    val closeDurationMs = remember { PixelShadeConfig.closeDurationMs(context).coerceIn(80, 1_000) }
    val showHeader = PixelShadeConfig.showPanelHeader(context)
    val showSystemIcons = PixelShadeConfig.showSystemIcons(context)
    val showFooter = PixelShadeConfig.showPanelFooter(context)
    val hideTileText = PixelShadeConfig.hideTileText(context)
    val tileHeight = PixelShadeConfig.tileHeightDp(context).coerceIn(44f, 96f).dp

    val headerText = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_HEADER_TEXT, palette.primaryText)
    val tileText = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_TILE_TEXT, palette.primaryText)
    val notificationBackground = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_NOTIFICATION_BACKGROUND, palette.inactiveTile)
    val footerBackground = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_FOOTER_BACKGROUND, Color.Transparent)
    val footerText = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_FOOTER_TEXT, palette.primaryText)
    val handleColor = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_HANDLE, palette.secondaryText)

    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    var wifiOn by remember { mutableStateOf(SystemActionController.wifiEnabled(context)) }
    var mobileOn by remember { mutableStateOf(SystemActionController.mobileDataEnabled(context)) }
    var bluetoothOn by remember { mutableStateOf(SystemActionController.bluetoothEnabled(context)) }
    var torchOn by remember { mutableStateOf(SystemActionController.torchEnabled()) }
    var dndOn by remember { mutableStateOf(SystemActionController.dndEnabled(context)) }
    var rotationOn by remember { mutableStateOf(SystemActionController.rotationEnabled(context)) }
    var closing by remember { mutableStateOf(false) }

    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun closeShade() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(durationMillis = closeDurationMs, easing = FastOutSlowInEasing))
            onFinish()
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            torchOn = SystemActionController.toggleFlashlight(context)
            if (PixelShadeConfig.autoCloseTile(context)) closeShade()
        }
    }

    BackHandler(enabled = !closing) { closeShade() }

    fun launchAndClose(intent: Intent) {
        val launched = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (launched) onFinish()
    }

    LaunchedEffect(Unit) {
        wifiOn = SystemActionController.wifiEnabled(context)
        mobileOn = SystemActionController.mobileDataEnabled(context)
        bluetoothOn = SystemActionController.bluetoothEnabled(context)
        dndOn = SystemActionController.dndEnabled(context)
        rotationOn = SystemActionController.rotationEnabled(context)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = openDurationMs, easing = FastOutSlowInEasing))
    }

    val tiles = listOf(
        RuntimeTile("wifi", "Wi-Fi", Icons.Default.Wifi, compact = true, active = wifiOn),
        RuntimeTile("mobile", "Mobile data", Icons.Default.SwapVert, compact = true, active = mobileOn),
        RuntimeTile("bluetooth", "Bluetooth", Icons.Default.Bluetooth, compact = false, active = bluetoothOn),
        RuntimeTile("flashlight", "Flashlight", Icons.Default.FlashlightOn, compact = false, active = torchOn),
        RuntimeTile("dnd", "Modes", Icons.Default.DoNotDisturbOn, compact = false, active = dndOn),
        RuntimeTile("rotation", "Rotation", Icons.Default.ScreenRotation, compact = false, active = rotationOn)
    )

    fun activate(tile: RuntimeTile) {
        var canAutoClose = true
        when (tile.id) {
            "wifi" -> SystemActionController.toggleWifi(context) { wifiOn = it }
            "mobile" -> SystemActionController.toggleMobileData(context) { mobileOn = it }
            "bluetooth" -> SystemActionController.toggleBluetooth(context) { bluetoothOn = it }
            "flashlight" -> {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    torchOn = SystemActionController.toggleFlashlight(context)
                } else {
                    canAutoClose = false
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            "dnd" -> dndOn = SystemActionController.toggleDnd(context)
            "rotation" -> rotationOn = SystemActionController.toggleRotation(context)
        }
        if (canAutoClose && PixelShadeConfig.autoCloseTile(context)) closeShade()
    }

    val showNotifications = PixelShadeConfig.showNotifications(context)
    val onlyMedia = PixelShadeConfig.onlyMediaNotifications(context)
    val hidePersistent = PixelShadeConfig.hidePersistentNotifications(context)
    val visibleNotifications = if (!showNotifications) {
        emptyList()
    } else if (onlyMedia) {
        notifications.filter { it.isMedia }
    } else {
        notifications.filterNot { hidePersistent && it.ongoing }
    }
    val mediaNotification = visibleNotifications.firstOrNull { it.isMedia }
    val regularNotifications = visibleNotifications.filterNot { it.key == mediaNotification?.key }
    val removeNotificationSpacing = PixelShadeConfig.removeNotificationSpacing(context)
    val autoExpandNotifications = PixelShadeConfig.autoExpandNotifications(context)

    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = -(1f - progress.value) * 54f
        },
        color = palette.panel.copy(alpha = opacity)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = panelPadding, end = panelPadding, top = 34.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showHeader) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(systemStatus.time, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = headerText)
                            Text(systemStatus.date, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
                        }
                        if (showSystemIcons) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(systemStatus.connectionLabel, style = MaterialTheme.typography.labelMedium, color = headerText)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    when (systemStatus.transport) {
                                        RuntimeTransport.WIFI -> Icon(Icons.Default.Wifi, "Wi-Fi", Modifier.size(15.dp), tint = headerText)
                                        RuntimeTransport.CELLULAR -> Icon(Icons.Default.SignalCellularAlt, "Cellular", Modifier.size(15.dp), tint = headerText)
                                        else -> Unit
                                    }
                                    if (systemStatus.charging) Icon(Icons.Default.Bolt, "Charging", Modifier.size(14.dp), tint = headerText)
                                    Icon(Icons.Default.BatteryFull, "Battery", Modifier.size(15.dp), tint = headerText)
                                    Text(if (systemStatus.batteryPercent >= 0) "${systemStatus.batteryPercent}%" else "—", style = MaterialTheme.typography.labelSmall, color = headerText)
                                }
                            }
                        }
                    }
                }
            }

            item {
                RuntimeBrightness(
                    value = brightness,
                    palette = palette,
                    onValueChange = {
                        brightness = it.coerceIn(.01f, 1f)
                        if (Settings.System.canWrite(context)) {
                            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).roundToInt().coerceIn(1, 255))
                        }
                    },
                    onSettings = { launchAndClose(Intent(Settings.ACTION_SETTINGS)) }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeCompactTile(tiles[0], palette, Modifier.weight(1f)) { activate(tiles[0]) }
                        RuntimeCompactTile(tiles[1], palette, Modifier.weight(1f)) { activate(tiles[1]) }
                        RuntimeWideTile(tiles[2], palette, Modifier.weight(2f), tileHeight, hideTileText, tileText) { activate(tiles[2]) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeWideTile(tiles[3], palette, Modifier.weight(1f), tileHeight, hideTileText, tileText) { activate(tiles[3]) }
                        RuntimeWideTile(tiles[4], palette, Modifier.weight(1f), tileHeight, hideTileText, tileText) { activate(tiles[4]) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeWideTile(tiles[5], palette, Modifier.weight(1f), tileHeight, hideTileText, tileText) { activate(tiles[5]) }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (customTiles.isNotEmpty()) {
                item { Text("Custom", style = MaterialTheme.typography.labelLarge, color = palette.secondaryText) }
                runtimeCustomRows(customTiles).forEach { rowTiles ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var used = 0
                            rowTiles.forEach { tile ->
                                val span = tile.widthUnits.coerceIn(1, 4)
                                used += span
                                RuntimeCustomTile(tile, palette, Modifier.weight(span.toFloat()), tileHeight, hideTileText, tileText) {
                                    if (PixelShadeTileStore.launch(context, tile)) onFinish()
                                }
                            }
                            if (used < 4) Spacer(Modifier.weight((4 - used).toFloat()))
                        }
                    }
                }
            }

            if (mediaNotification != null) {
                item { RuntimeMediaCard(mediaNotification, palette, notificationBackground, onFinish) }
            }

            if (showNotifications && regularNotifications.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall, color = palette.secondaryText, modifier = Modifier.weight(1f))
                        if (regularNotifications.any { it.clearable }) {
                            TextButton(onClick = {
                                PixelShadeNotificationStore.clearAll()
                                if (PixelShadeConfig.autoCloseAfterClear(context)) closeShade()
                            }) { Text("Clear all") }
                        }
                    }
                }
                if (removeNotificationSpacing) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            regularNotifications.forEach { item ->
                                RuntimeNotificationCard(item, palette, notificationBackground, autoExpandNotifications, onFinish)
                            }
                        }
                    }
                } else {
                    items(regularNotifications.size, key = { regularNotifications[it].key }) { index ->
                        RuntimeNotificationCard(regularNotifications[index], palette, notificationBackground, autoExpandNotifications, onFinish)
                    }
                }
            } else if (showNotifications && mediaNotification == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(28.dp), tint = palette.secondaryText)
                        Text("You're all caught up", color = palette.secondaryText)
                    }
                }
            }

            if (showFooter) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = footerBackground) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { launchAndClose(Intent(context, MainActivity::class.java)) }) {
                                Icon(Icons.Default.Edit, "Edit", tint = footerText)
                            }
                            IconButton(enabled = PixelShadeAccessibilityService.isConnected(), onClick = {
                                if (PixelShadeAccessibilityService.requestPowerDialog()) onFinish()
                            }) {
                                Icon(Icons.Default.PowerSettingsNew, "Power", tint = footerText)
                            }
                        }
                    }
                }
            }

            item { RuntimeDismissHandle(handleColor = handleColor, onClose = { closeShade() }) }
        }
    }
}

@Composable
private fun RuntimeDismissHandle(handleColor: Color, onClose: () -> Unit) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 36.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .pointerInput(thresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { _, dragAmount -> dragDistance += dragAmount },
                    onDragEnd = {
                        if (dragDistance <= -thresholdPx) onClose()
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Surface(Modifier.width(44.dp).height(4.dp), shape = RoundedCornerShape(2.dp), color = handleColor.copy(alpha = .45f)) {}
    }
}

@Composable
private fun RuntimeBrightness(value: Float, palette: PixelShadePalette, onValueChange: (Float) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val track = palette.brightnessTrack
    val progressColor = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_SLIDER_PROGRESS, palette.brightnessFill)
    val thumbColor = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_SLIDER_THUMB, palette.brightnessFill)
    val iconColor = PixelShadeThemeEngine.resolvedColor(context, PixelShadeThemeEngine.KEY_SLIDER_ICON, palette.activeIcon)
    val fraction = value.coerceIn(.01f, 1f)
    val shape = RoundedCornerShape(24.dp)

    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(track)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (size.width > 0) onValueChange((offset.x / size.width.toFloat()).coerceIn(.01f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) onValueChange((offset.x / size.width.toFloat()).coerceIn(.01f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            if (size.width > 0) onValueChange((change.position.x / size.width.toFloat()).coerceIn(.01f, 1f))
                            change.consume()
                        }
                    )
                }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(progressColor))
            val thumbSize = 30.dp
            val thumbX = (maxWidth - thumbSize).coerceAtLeast(0.dp) * fraction
            Box(Modifier.align(Alignment.CenterStart).offset(x = thumbX).size(thumbSize).background(thumbColor, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Brightness6, "Brightness", Modifier.size(18.dp), tint = iconColor)
            }
        }
        Surface(Modifier.width(56.dp).fillMaxHeight().clickable(onClick = onSettings), shape = RoundedCornerShape(18.dp), color = palette.inactiveTile) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, "Settings", tint = palette.inactiveIcon) }
        }
    }
}

@Composable
private fun RuntimeCompactTile(tile: RuntimeTile, palette: PixelShadePalette, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val fg = if (tile.active) palette.activeIcon else palette.inactiveIcon
    val shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(context).dp.coerceAtMost(26.dp))
    val brushes = pixelShadeTileBrushes(context, palette.activeTile, palette.inactiveTile)
    val brush = if (tile.active) brushes.active else brushes.inactive
    Box(modifier.aspectRatio(1f).clip(shape).background(brush).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(tile.icon, tile.label, Modifier.size(26.dp), tint = fg)
    }
}

@Composable
private fun RuntimeWideTile(
    tile: RuntimeTile,
    palette: PixelShadePalette,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    hideText: Boolean,
    textColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val fg = if (tile.active) palette.activeIcon else palette.inactiveIcon
    val shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(context).dp.coerceAtMost(28.dp))
    val brushes = pixelShadeTileBrushes(context, palette.activeTile, palette.inactiveTile)
    val brush = if (tile.active) brushes.active else brushes.inactive
    Box(modifier.height(height).clip(shape).background(brush).clickable(onClick = onClick)) {
        if (hideText) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(tile.icon, tile.label, Modifier.size(23.dp), tint = fg)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(tile.icon, null, Modifier.size(22.dp), tint = fg)
                Text(tile.label, style = MaterialTheme.typography.labelLarge, color = if (tile.active) fg else textColor, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RuntimeCustomTile(
    tile: PixelShadeTile,
    palette: PixelShadePalette,
    modifier: Modifier,
    baseHeight: androidx.compose.ui.unit.Dp,
    hideText: Boolean,
    textColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val span = tile.widthUnits.coerceIn(1, 4)
    val shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(context).dp.coerceAtMost(28.dp))
    val brush = pixelShadeTileBrushes(context, palette.activeTile, palette.inactiveTile).inactive
    Box(modifier.height(if (tile.heightUnits >= 2) baseHeight * 2 + 2.dp else baseHeight).clip(shape).background(brush).clickable(onClick = onClick)) {
        if (span == 1 || hideText) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RuntimeCustomArtwork(tile, palette, Modifier.size(27.dp))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeCustomArtwork(tile, palette, Modifier.size(27.dp))
                Text(tile.label, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RuntimeCustomArtwork(tile: PixelShadeTile, palette: PixelShadePalette, modifier: Modifier) {
    val context = LocalContext.current
    val component = remember(tile.type, tile.target) {
        if (tile.type == "activity") ComponentName.unflattenFromString(tile.target) else null
    }
    val targetPackage = when (tile.type) {
        "app" -> tile.target
        "activity" -> component?.packageName
        else -> null
    }
    when (tile.iconSource) {
        "material" -> Icon(materialTileIcon(tile.iconValue), tile.label, modifier, tint = palette.inactiveIcon)
        "pack" -> {
            val drawable = remember(tile.iconValue, tile.target) {
                IconPackResolver.resolveSelection(context, tile.iconValue, targetPackage, component)
            }
            if (drawable != null) {
                AppOrDrawableIcon(targetPackage, drawable, if (tile.monochrome) palette.inactiveIcon else null, modifier)
            } else Icon(Icons.Default.Apps, tile.label, modifier, tint = palette.inactiveIcon)
        }
        "app" -> {
            if (!targetPackage.isNullOrBlank()) {
                AppOrDrawableIcon(targetPackage, null, if (tile.monochrome) palette.inactiveIcon else null, modifier)
            } else Icon(Icons.Default.Apps, tile.label, modifier, tint = palette.inactiveIcon)
        }
        else -> Icon(Icons.Default.Apps, tile.label, modifier, tint = palette.inactiveIcon)
    }
}

@Composable
private fun RuntimeNotificationCard(item: ShadeNotification, palette: PixelShadePalette, background: Color, expanded: Boolean, onFinish: () -> Unit) {
    val clickable = if (item.contentIntent != null) {
        Modifier.fillMaxWidth().clickable { if (PixelShadeNotificationStore.open(item)) onFinish() }
    } else Modifier.fillMaxWidth()
    val duplicateTitle = item.title.trim().equals(item.appLabel.trim(), ignoreCase = true)
    val verticalPadding = if (item.ongoing) 10.dp else 12.dp
    val cardShape = if (item.ongoing) 18.dp else 22.dp

    Surface(clickable, shape = RoundedCornerShape(cardShape), color = background) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = verticalPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.appLabel, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText, modifier = Modifier.weight(1f), maxLines = 1)
                if (item.clearable) {
                    IconButton(onClick = { PixelShadeNotificationStore.dismiss(item.key) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", Modifier.size(17.dp), tint = palette.secondaryText)
                    }
                }
            }
            if (!duplicateTitle && item.title.isNotBlank()) {
                Text(item.title, style = if (item.ongoing) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium, fontWeight = if (item.ongoing) FontWeight.Medium else FontWeight.SemiBold, color = palette.primaryText, maxLines = if (expanded) 2 else 1)
            }
            if (item.text.isNotBlank()) {
                Text(item.text, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText, maxLines = if (expanded) 6 else 1)
            }
            if (item.actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    item.actions.take(3).forEach { action ->
                        TextButton(onClick = { PixelShadeNotificationStore.runAction(action) }) { Text(action.title, maxLines = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeMediaCard(item: ShadeNotification, palette: PixelShadePalette, background: Color, onFinish: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { if (PixelShadeNotificationStore.open(item)) onFinish() }, shape = RoundedCornerShape(24.dp), color = background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.appLabel, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText, maxLines = 2)
            if (item.actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    item.actions.take(5).forEach { action ->
                        val icon = mediaActionIcon(action.title)
                        if (icon != null) {
                            FilledIconButton(onClick = { PixelShadeNotificationStore.runAction(action) }) { Icon(icon, action.title) }
                        } else {
                            TextButton(onClick = { PixelShadeNotificationStore.runAction(action) }) { Text(action.title, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

private fun mediaActionIcon(title: String): ImageVector? {
    val value = title.trim().lowercase()
    return when {
        "previous" in value || value == "prev" -> Icons.Default.SkipPrevious
        "rewind" in value -> Icons.Default.FastRewind
        "pause" in value -> Icons.Default.Pause
        "play" in value || "resume" in value -> Icons.Default.PlayArrow
        "next" in value || "skip" in value -> Icons.Default.SkipNext
        "forward" in value -> Icons.Default.FastForward
        else -> null
    }
}

private fun runtimeCustomRows(tiles: List<PixelShadeTile>): List<List<PixelShadeTile>> {
    val rows = mutableListOf<MutableList<PixelShadeTile>>()
    var row = mutableListOf<PixelShadeTile>()
    var used = 0
    tiles.forEach { tile ->
        val width = tile.widthUnits.coerceIn(1, 4)
        if (used + width > 4 && row.isNotEmpty()) {
            rows += row
            row = mutableListOf()
            used = 0
        }
        row += tile
        used += width
        if (used >= 4) {
            rows += row
            row = mutableListOf()
            used = 0
        }
    }
    if (row.isNotEmpty()) rows += row
    return rows
}
