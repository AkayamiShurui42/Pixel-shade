package com.crimson.pixelshade

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PixelShadePanelActivity : ComponentActivity() {
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
            MaterialTheme(colorScheme = scheme) { PixelShadePanel(onFinish = { finish() }) }
        }
    }
}

private data class SystemTile(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val compact: Boolean = false,
    val active: Boolean = false
)

@Composable
private fun PixelShadePanel(onFinish: () -> Unit) {
    val context = LocalContext.current
    val customTiles = remember { PixelShadeTileStore.load(context) }
    val notifications = PixelShadeNotificationStore.items
    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    var wifiOn by remember { mutableStateOf(SystemActionController.wifiEnabled(context)) }
    var mobileOn by remember { mutableStateOf(SystemActionController.mobileDataEnabled(context)) }
    var bluetoothOn by remember { mutableStateOf(SystemActionController.bluetoothEnabled(context)) }
    var torchOn by remember { mutableStateOf(SystemActionController.torchEnabled()) }
    var dndOn by remember { mutableStateOf(SystemActionController.dndEnabled(context)) }
    var rotationOn by remember { mutableStateOf(SystemActionController.rotationEnabled(context)) }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) torchOn = SystemActionController.toggleFlashlight(context)
    }

    val time = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val date = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }
    val opacity = PixelShadeConfig.panelOpacity(context)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun closeShade() {
        scope.launch {
            progress.animateTo(0f, spring(dampingRatio = .9f, stiffness = Spring.StiffnessMediumLow))
            onFinish()
        }
    }

    LaunchedEffect(Unit) {
        wifiOn = SystemActionController.wifiEnabled(context)
        mobileOn = SystemActionController.mobileDataEnabled(context)
        bluetoothOn = SystemActionController.bluetoothEnabled(context)
        dndOn = SystemActionController.dndEnabled(context)
        rotationOn = SystemActionController.rotationEnabled(context)
        progress.snapTo(.04f)
        progress.animateTo(1f, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow))
    }

    val systemTiles = listOf(
        SystemTile("wifi", "Wi-Fi", Icons.Default.Wifi, compact = true, active = wifiOn),
        SystemTile("mobile", "Mobile data", Icons.Default.SwapVert, compact = true, active = mobileOn),
        SystemTile("bluetooth", "Bluetooth", Icons.Default.Bluetooth, active = bluetoothOn),
        SystemTile("flashlight", "Flashlight", Icons.Default.FlashlightOn, active = torchOn),
        SystemTile("dnd", "Modes", Icons.Default.DoNotDisturbOn, active = dndOn),
        SystemTile("rotation", "Rotation", Icons.Default.ScreenRotation, active = rotationOn),
        SystemTile("home", "Home", Icons.Default.Home, active = true)
    )

    fun activateSystemTile(tile: SystemTile) {
        when (tile.id) {
            "wifi" -> SystemActionController.toggleWifi(context) { wifiOn = it }
            "mobile" -> SystemActionController.toggleMobileData(context) { mobileOn = it }
            "bluetooth" -> SystemActionController.toggleBluetooth(context) { bluetoothOn = it }
            "flashlight" -> {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    torchOn = SystemActionController.toggleFlashlight(context)
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            }
            "dnd" -> dndOn = SystemActionController.toggleDnd(context)
            "rotation" -> rotationOn = SystemActionController.toggleRotation(context)
            "home" -> safeLaunch(context, Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    val mediaNotification = notifications.firstOrNull { it.isMedia }
    val regularNotifications = notifications.filterNot { it.key == mediaNotification?.key }

    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = -(1f - progress.value) * 92f
        },
        color = MaterialTheme.colorScheme.surface.copy(alpha = opacity)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 42.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(time, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(date, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { safeLaunch(context, Intent(Settings.ACTION_SETTINGS)) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { closeShade() }) { Icon(Icons.Default.Close, "Close") }
                }
            }

            item {
                ExpressiveBrightness(
                    value = brightness,
                    onValueChange = {
                        brightness = it.coerceIn(.01f, 1f)
                        if (Settings.System.canWrite(context)) {
                            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).roundToInt().coerceIn(1, 255))
                        }
                    }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveCompactSystemTile(systemTiles[0], Modifier.weight(1f)) { activateSystemTile(systemTiles[0]) }
                        ExpressiveCompactSystemTile(systemTiles[1], Modifier.weight(1f)) { activateSystemTile(systemTiles[1]) }
                        ExpressiveWideSystemTile(systemTiles[2], Modifier.weight(2f)) { activateSystemTile(systemTiles[2]) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveWideSystemTile(systemTiles[3], Modifier.weight(1f)) { activateSystemTile(systemTiles[3]) }
                        ExpressiveWideSystemTile(systemTiles[4], Modifier.weight(1f)) { activateSystemTile(systemTiles[4]) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveWideSystemTile(systemTiles[5], Modifier.weight(1f)) { activateSystemTile(systemTiles[5]) }
                        ExpressiveWideSystemTile(systemTiles[6], Modifier.weight(1f)) { activateSystemTile(systemTiles[6]) }
                    }
                }
            }

            if (customTiles.isNotEmpty()) {
                item { Text("Custom", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                customTileRows(customTiles).forEach { rowTiles ->
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            var used = 0
                            rowTiles.forEach { tile ->
                                used += tile.widthUnits
                                CustomShadeTile(tile, Modifier.weight(tile.widthUnits.toFloat())) {
                                    PixelShadeTileStore.launch(context, tile)
                                }
                            }
                            if (used < 4) Spacer(Modifier.weight((4 - used).toFloat()))
                        }
                    }
                }
            }

            if (mediaNotification != null) {
                item {
                    MediaNotificationCard(mediaNotification) {
                        if (PixelShadeNotificationStore.open(mediaNotification)) closeShade()
                    }
                }
            }

            if (regularNotifications.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (regularNotifications.any { it.clearable }) {
                            TextButton(onClick = { PixelShadeNotificationStore.clearAll() }) { Text("Clear all") }
                        }
                    }
                }
                items(regularNotifications.size, key = { regularNotifications[it].key }) { index ->
                    val notification = regularNotifications[index]
                    ShadeNotificationCard(notification, onOpen = {
                        if (PixelShadeNotificationStore.open(notification)) closeShade()
                    })
                }
            } else if (mediaNotification == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.NotificationsNone, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No notifications", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { safeLaunch(context, Intent(context, MainActivity::class.java)) }) {
                        Icon(Icons.Default.Edit, "Edit Pixel Shade")
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        Modifier.width(64.dp).height(28.dp).pointerInput(Unit) { detectTapGestures { closeShade() } },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveBrightness(value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Brightness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(value = value.coerceIn(.01f, 1f), onValueChange = onValueChange, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Brightness6, "Brightness")
                }
            }
        }
    }
}

@Composable
private fun ExpressiveCompactSystemTile(tile: SystemTile, modifier: Modifier, onClick: () -> Unit) {
    val container = if (tile.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (tile.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(modifier.aspectRatio(1f).clickable(onClick = onClick), shape = RoundedCornerShape(26.dp), color = container, contentColor = content) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(tile.icon, tile.label, Modifier.size(27.dp)) }
    }
}

@Composable
private fun ExpressiveWideSystemTile(tile: SystemTile, modifier: Modifier, onClick: () -> Unit) {
    val container = if (tile.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (tile.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(modifier.height(68.dp).clickable(onClick = onClick), shape = RoundedCornerShape(30.dp), color = container, contentColor = content) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Icon(tile.icon, null, Modifier.size(24.dp))
            Text(tile.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun CustomShadeTile(tile: PixelShadeTile, modifier: Modifier, onClick: () -> Unit) {
    val height = if (tile.heightUnits >= 2) 134.dp else 64.dp
    Surface(
        modifier = modifier.height(height).clickable(onClick = onClick),
        shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(LocalContext.current).dp.coerceAtMost(32.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        if (tile.widthUnits == 1) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(iconForTile(tile), tile.label, Modifier.size(25.dp)) }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(iconForTile(tile), null, Modifier.size(24.dp))
                Column {
                    Text(tile.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text(tile.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ShadeNotificationCard(item: ShadeNotification, onOpen: () -> Unit) {
    val modifier = if (item.contentIntent != null) Modifier.fillMaxWidth().clickable(onClick = onOpen) else Modifier.fillMaxWidth()
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Text(item.appLabel.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(item.appLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (item.clearable) {
                    IconButton(onClick = { PixelShadeNotificationStore.dismiss(item.key) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp))
                    }
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MediaNotificationCard(item: ShadeNotification, onOpen: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.appLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f))
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f))
            if (item.actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    item.actions.take(5).forEach { action ->
                        FilledTonalButton(onClick = { PixelShadeNotificationStore.runAction(action) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(action.title, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun customTileRows(tiles: List<PixelShadeTile>): List<List<PixelShadeTile>> {
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

private fun safeLaunch(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun iconForTile(tile: PixelShadeTile): ImageVector = when (tile.iconValue) {
    "bolt" -> Icons.Default.Bolt
    "flashlight" -> Icons.Default.FlashlightOn
    "bluetooth" -> Icons.Default.Bluetooth
    "wifi" -> Icons.Default.Wifi
    "link" -> Icons.Default.Link
    "language" -> Icons.Default.Language
    "gamepad" -> Icons.Default.SportsEsports
    "terminal" -> Icons.Default.Terminal
    "settings" -> Icons.Default.Settings
    "star" -> Icons.Default.Star
    "camera" -> Icons.Default.PhotoCamera
    else -> when (tile.type) {
        "website" -> Icons.Default.Language
        "activity" -> Icons.Default.OpenInNew
        else -> Icons.Default.Apps
    }
}
