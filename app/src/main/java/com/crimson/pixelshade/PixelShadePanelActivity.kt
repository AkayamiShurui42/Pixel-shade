package com.crimson.pixelshade

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

private data class SystemTile(val label: String, val icon: ImageVector, val intent: Intent, val compact: Boolean = false, val active: Boolean = false)

@Composable
private fun PixelShadePanel(onFinish: () -> Unit) {
    val context = LocalContext.current
    val customTiles = remember { PixelShadeTileStore.load(context) }
    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    val time = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val date = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }
    val opacity = PixelShadeConfig.panelOpacity(context)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun closeShade() {
        scope.launch {
            progress.animateTo(
                0f,
                spring(dampingRatio = .9f, stiffness = Spring.StiffnessMediumLow)
            )
            onFinish()
        }
    }

    LaunchedEffect(Unit) {
        progress.snapTo(.04f)
        progress.animateTo(
            1f,
            spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    val systemTiles = remember {
        listOf(
            SystemTile("Wi-Fi", Icons.Default.Wifi, Intent(Settings.ACTION_WIFI_SETTINGS), compact = true, active = true),
            SystemTile("Mobile data", Icons.Default.SwapVert, Intent(Settings.ACTION_DATA_USAGE_SETTINGS), compact = true, active = true),
            SystemTile("Bluetooth", Icons.Default.Bluetooth, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            SystemTile("Flashlight", Icons.Default.FlashlightOn, Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            SystemTile("Modes", Icons.Default.DoNotDisturbOn, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)),
            SystemTile("Rotation", Icons.Default.ScreenRotation, Intent(Settings.ACTION_DISPLAY_SETTINGS)),
            SystemTile("Home", Icons.Default.Home, Intent(Settings.ACTION_SETTINGS), active = true)
        )
    }

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
                    FilledTonalIconButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            }
                        }
                    ) { Icon(Icons.Default.Settings, "Settings") }
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
                        ExpressiveCompactSystemTile(systemTiles[0], Modifier.weight(1f)) { safeLaunch(context, systemTiles[0].intent) }
                        ExpressiveCompactSystemTile(systemTiles[1], Modifier.weight(1f)) { safeLaunch(context, systemTiles[1].intent) }
                        ExpressiveWideSystemTile(systemTiles[2], Modifier.weight(2f)) { safeLaunch(context, systemTiles[2].intent) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveWideSystemTile(systemTiles[3], Modifier.weight(1f)) { safeLaunch(context, systemTiles[3].intent) }
                        ExpressiveWideSystemTile(systemTiles[4], Modifier.weight(1f)) { safeLaunch(context, systemTiles[4].intent) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveWideSystemTile(systemTiles[5], Modifier.weight(1f)) { safeLaunch(context, systemTiles[5].intent) }
                        ExpressiveWideSystemTile(systemTiles[6], Modifier.weight(1f)) { safeLaunch(context, systemTiles[6].intent) }
                    }
                }
            }

            if (customTiles.isNotEmpty()) {
                item { Text("Custom", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(customTiles.size) { index ->
                    val tile = customTiles[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(62.dp).clickable { PixelShadeTileStore.launch(context, tile) },
                        shape = RoundedCornerShape(PixelShadeConfig.tileCornerDp(context).dp.coerceAtMost(31.dp)),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(iconForTile(tile), null, Modifier.size(23.dp))
                            Column {
                                Text(tile.label, style = MaterialTheme.typography.labelLarge)
                                Text(tile.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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

            if (customTiles.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("You’re all caught up", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Slider(
                    value = value.coerceIn(.01f, 1f),
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
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

private fun safeLaunch(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
