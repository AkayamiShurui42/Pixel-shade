package com.crimson.pixelshade

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku

private const val SHIZUKU_REQUEST = 1718
private const val PREFS_NAME = "pixel_shade_settings"
private const val PREF_THEME_MODE = "theme_mode"

private enum class ThemeMode { SYSTEM, LIGHT, DARK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PixelShadeRoot() }
    }
}

@Composable
private fun PixelShadeRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var themeMode by remember {
        mutableStateOf(
            runCatching { ThemeMode.valueOf(prefs.getString(PREF_THEME_MODE, ThemeMode.SYSTEM.name)!!) }
                .getOrDefault(ThemeMode.SYSTEM)
        )
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colors) {
        PixelShadeSetup(
            themeMode = themeMode,
            onThemeModeChange = {
                themeMode = it
                prefs.edit().putString(PREF_THEME_MODE, it.name).apply()
            }
        )
    }
}

@Composable
private fun PixelShadeSetup(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var triggerEnabled by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        val binderReceived = Shizuku.OnBinderReceivedListener { refresh++ }
        val binderDead = Shizuku.OnBinderDeadListener { refresh++ }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_REQUEST) refresh++
        }

        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceived) }
        runCatching { Shizuku.addBinderDeadListener(binderDead) }
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResult) }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        }
    }

    @Suppress("UNUSED_EXPRESSION") refresh

    val notificationGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)
    val writeSettings = Settings.System.canWrite(context)
    val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val shizukuGranted = shizukuRunning && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val notificationAccess = enabledListeners.contains(context.packageName)
    val enabledAccessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val accessibility = enabledAccessibility.contains(
        ComponentName(context, PixelShadeAccessibilityService::class.java).flattenToString(),
        ignoreCase = true
    )
    val pm = context.getSystemService(PowerManager::class.java)
    val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pixel Shade", style = MaterialTheme.typography.headlineMedium)
            Text("Standalone replacement shade setup", style = MaterialTheme.typography.bodyMedium)

            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            PermissionRow("Notifications", notificationGranted) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("Notification access", notificationAccess) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermissionRow("Accessibility", accessibility) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            PermissionRow("Display over apps", overlayGranted) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            PermissionRow("Modify system settings", writeSettings) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
            PermissionRow(
                label = if (shizukuRunning) "Shizuku / Shizuku+" else "Shizuku / Shizuku+ (service not running)",
                granted = shizukuGranted
            ) {
                runCatching {
                    when {
                        !Shizuku.pingBinder() -> refresh++
                        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                            Shizuku.requestPermission(SHIZUKU_REQUEST)
                        else -> refresh++
                    }
                }.onFailure { refresh++ }
            }
            PermissionRow("Ignore battery optimization", batteryExempt) {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }.recoverCatching {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }

            Button(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh permission status")
            }
            Button(
                enabled = overlayGranted,
                onClick = {
                    triggerEnabled = !triggerEnabled
                    val i = Intent(context, PixelShadeTriggerService::class.java)
                    if (triggerEnabled) context.startForegroundService(i) else context.stopService(i)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (triggerEnabled) "Disable trigger" else "Enable trigger")
            }

            Text(
                "First test: verify permission state, the top-edge trigger, and brightness gesture before suppressing the stock shade.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(if (granted) "Granted" else "Required", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onGrant, enabled = !granted) {
            Text(if (granted) "Ready" else "Grant")
        }
    }
}
