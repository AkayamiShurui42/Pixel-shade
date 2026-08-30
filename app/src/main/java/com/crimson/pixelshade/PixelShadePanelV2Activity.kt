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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun closeShade() {
        scope.launch {
            progress.animateTo(0f, spring(dampingRatio = .92f, stiffness = Spring.StiffnessMediumLow))
            onFinish()
        }
    }

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
        progress.snapTo(.08f)
        progress.animateTo(1f, spring(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow))
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
        }
    }

    val mediaNotification = notifications.firstOrNull { it.isMedia }
    val regularNotifications = notifications.filterNot { it.key == mediaNotification?.key }

    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = -(1f - progress.value) * 54f
        },
        color = palette.panel.copy(alpha = opacity)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(time, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
                        Text(date, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("No service", style = MaterialTheme.typography.labelMedium, color = palette.primaryText)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, null, Modifier.size(15.dp), tint = palette.primaryText)
                            Text("53%", style = MaterialTheme.typography.labelSmall, color = palette.primaryText)
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
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                (brightness * 255).roundToInt().coerceIn(1, 255)
                            )
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
                        RuntimeWideTile(tiles[2], palette, Modifier.weight(2f)) { activate(tiles[2]) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeWideTile(tiles[3], palette, Modifier.weight(1f)) { activate(tiles[3]) }
                        RuntimeWideTile(tiles[4], palette, Modifier.weight(1f)) { activate(tiles[4]) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeWideTile(tiles[5], palette, Modifier.weight(1f)) { activate(tiles[5]) }
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
                                RuntimeCustomTile(tile, palette, Modifier.weight(span.toFloat())) {
                                    if (PixelShadeTileStore.launch(context, tile)) onFinish()
                                }
                            }
                            if (used < 4) Spacer(Modifier.weight((4 - used).toFloat()))
                        }
                    }
                }
            }

            if (mediaNotification != null) {
                item { RuntimeMediaCard(mediaNotification, palette, onFinish) }
            }

            if (regularNotifications.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall, color = palette.secondaryText, modifier = Modifier.weight(1f))
                        if (regularNotifications.any { it.clearable }) {
                            TextButton(onClick = { PixelShadeNotificationStore.clearAll() }) { Text("Clear all") }
                        }
                    }
                }
                items(regularNotifications.size, key = { regularNotifications[it].key }) { index ->
                    RuntimeNotificationCard(regularNotifications[index], palette, onFinish)
                }
            } else if (mediaNotification == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(28.dp), tint = palette.secondaryText)
                        Text("You're all caught up", color = palette.secondaryText)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        launchAndClose(Intent(context, MainActivity::class.java))
                    }) { Icon(Icons.Default.Edit, "Edit", tint = palette.primaryText) }
                    IconButton(
                        enabled = PixelShadeAccessibilityService.isConnected(),
                        onClick = {
                            if (PixelShadeAccessibilityService.requestPowerDialog()) onFinish()
                        }
                    ) { Icon(Icons.Default.PowerSettingsNew, "Power", tint = palette.primaryText) }
                }
            }
        }
    }
}

@Composable
private fun RuntimeBrightness(
    value: Float,
    palette: PixelShadePalette,
    onValueChange: (Float) -> Unit,
    onSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(14.dp), color = palette.brightnessTrack) {
            Slider(
                value = value.coerceIn(.01f, 1f),
                onValueChange = onValueChange,
                colors = SliderDefaults.colors(
                    thumbColor = palette.brightnessFill,
                    activeTrackColor = palette.brightnessFill,
                    inactiveTrackColor = palette.brightnessTrack
                ),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        Surface(
            Modifier.width(54.dp).fillMaxHeight().clickable(onClick = onSettings),
            shape = RoundedCornerShape(14.dp),
            color = palette.inactiveTile
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Settings, "Settings", tint = palette.inactiveIcon)
            }
        }
    }
}

@Composable
private fun RuntimeCompactTile(tile: RuntimeTile, palette: PixelShadePalette, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (tile.active) palette.activeTile else palette.inactiveTile
    val fg = if (tile.active) palette.activeIcon else palette.inactiveIcon
    Surface(
        modifier.aspectRatio(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(LocalContext.current).dp.coerceAtMost(26.dp)),
        color = bg
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(tile.icon, tile.label, Modifier.size(26.dp), tint = fg)
        }
    }
}

@Composable
private fun RuntimeWideTile(tile: RuntimeTile, palette: PixelShadePalette, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (tile.active) palette.activeTile else palette.inactiveTile
    val fg = if (tile.active) palette.activeIcon else palette.inactiveIcon
    Surface(
        modifier.height(62.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(LocalContext.current).dp.coerceAtMost(28.dp)),
        color = bg
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(tile.icon, null, Modifier.size(22.dp), tint = fg)
            Text(tile.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun RuntimeCustomTile(tile: PixelShadeTile, palette: PixelShadePalette, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val span = tile.widthUnits.coerceIn(1, 4)
    Surface(
        modifier.height(if (tile.heightUnits >= 2) 126.dp else 62.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(context).dp.coerceAtMost(28.dp)),
        color = palette.inactiveTile
    ) {
        if (span == 1) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RuntimeCustomArtwork(tile, palette, Modifier.size(27.dp))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeCustomArtwork(tile, palette, Modifier.size(27.dp))
                Text(tile.label, style = MaterialTheme.typography.labelLarge, color = palette.primaryText, maxLines = 1)
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
private fun RuntimeNotificationCard(item: ShadeNotification, palette: PixelShadePalette, onFinish: () -> Unit) {
    val clickable = if (item.contentIntent != null) {
        Modifier.fillMaxWidth().clickable {
            if (PixelShadeNotificationStore.open(item)) onFinish()
        }
    } else Modifier.fillMaxWidth()
    Surface(clickable, shape = RoundedCornerShape(22.dp), color = palette.inactiveTile) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.appLabel, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText, modifier = Modifier.weight(1f))
                if (item.clearable) {
                    IconButton(onClick = { PixelShadeNotificationStore.dismiss(item.key) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp), tint = palette.secondaryText)
                    }
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
            if (item.actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    item.actions.take(3).forEach { action ->
                        TextButton(onClick = { PixelShadeNotificationStore.runAction(action) }) {
                            Text(action.title, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeMediaCard(item: ShadeNotification, palette: PixelShadePalette, onFinish: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable { if (PixelShadeNotificationStore.open(item)) onFinish() },
        shape = RoundedCornerShape(24.dp),
        color = palette.inactiveTile
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.appLabel, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
            if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
            if (item.actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    item.actions.take(5).forEach { action ->
                        FilledTonalButton(onClick = { PixelShadeNotificationStore.runAction(action) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Text(action.title, maxLines = 1)
                        }
                    }
                }
            }
        }
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
