package com.crimson.pixelshade

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PixelShadePanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent {
            val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) {
                dynamicDarkColorScheme(this)
            } else darkColorScheme()
            MaterialTheme(colorScheme = scheme) {
                PixelShadePanel(onDismiss = { finish() })
            }
        }
    }
}

private data class SystemTile(val label: String, val icon: ImageVector, val intent: Intent)

@Composable
private fun PixelShadePanel(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val customTiles = remember { PixelShadeTileStore.load(context) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    val time = remember { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val date = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }

    val systemTiles = remember {
        listOf(
            SystemTile("Internet", Icons.Default.Wifi, Intent(Settings.ACTION_WIRELESS_SETTINGS)),
            SystemTile("Bluetooth", Icons.Default.Bluetooth, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
            SystemTile("Do Not Disturb", Icons.Default.DoNotDisturbOn, Intent(Settings.ACTION_ZEN_MODE_SETTINGS)),
            SystemTile("Rotation", Icons.Default.ScreenRotation, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragTotal += amount },
                    onDragEnd = { if (dragTotal < -100f) onDismiss(); dragTotal = 0f },
                    onDragCancel = { dragTotal = 0f }
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(time, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                        Text(date, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Brightness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                            if (Settings.System.canWrite(context)) {
                                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (it * 255).toInt().coerceIn(1, 255))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    systemTiles.chunked(2).forEach { rowTiles ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowTiles.forEachIndexed { index, tile ->
                                val active = index == 0 && rowTiles === systemTiles.chunked(2).firstOrNull()
                                PixelSystemTile(tile, active, Modifier.weight(1f)) {
                                    runCatching { context.startActivity(tile.intent) }
                                }
                            }
                            if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (customTiles.isNotEmpty()) {
                item { Text("Custom", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(customTiles.size) { index ->
                    val tile = customTiles[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(64.dp).clickable { PixelShadeTileStore.launch(context, tile) },
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(iconForTile(tile), null, Modifier.size(24.dp))
                            Column {
                                Text(tile.label, style = MaterialTheme.typography.labelLarge)
                                Text(tile.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pixel Shade", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        IconButton(onClick = { context.startActivity(Intent(context, MainActivity::class.java)) }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.KeyboardArrowUp, "Dismiss") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PixelSystemTile(tile: SystemTile, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(modifier.height(72.dp).clickable(onClick = onClick), shape = RoundedCornerShape(36.dp), color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(tile.icon, null, Modifier.size(24.dp))
            Text(tile.label, style = MaterialTheme.typography.labelLarge)
        }
    }
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
