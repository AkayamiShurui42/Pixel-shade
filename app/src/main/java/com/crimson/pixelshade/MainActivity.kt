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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku

private const val SHIZUKU_REQUEST = 1718
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
    val prefs = remember {
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
    }
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
    val prefs = remember {
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
    }
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
    val adbOverride = prefs.getBoolean(AdbOverrideReceiver.PREF_ADB_OVERRIDE, false)
    val privilegedPathReady = shizukuGranted || adbOverride

    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val notificationAccess = enabledListeners.contains(context.packageName)
    val enabledAccessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val accessibility = enabledAccessibility.contains(
        ComponentName(context, PixelShadeAccessibilityService::class.java).flattenToString(),
        ignoreCase = true
    )
    val pm = context.getSystemService(PowerManager::class.java)
    val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Pixel Shade") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your shade, your rules", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "A Pixel-style replacement shade for OxygenOS with dynamic color, custom gestures and privileged controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when {
                                    shizukuGranted -> "Shizuku connected"
                                    adbOverride -> "ADB fallback active"
                                    else -> "Privileged access needed"
                                }
                            )
                        }
                    )
                }
            }

            SettingsCard("Appearance") {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            SettingsCard("Permissions") {
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
                    label = if (adbOverride) "Privileged access (ADB fallback)" else "Shizuku / Shizuku+",
                    granted = privilegedPathReady
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
            }

            if (!shizukuGranted) {
                SettingsCard("ADB fallback") {
                    Text(
                        if (adbOverride)
                            "Fallback is enabled. Pixel Shade will let you continue even though Shizuku itself is not granted. Android permissions are still enforced normally."
                        else
                            "If Shizuku refuses to report its grant, enable the fallback from ADB and reopen Pixel Shade.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (adbOverride) {
                        FilledTonalButton(
                            onClick = {
                                prefs.edit().putBoolean(AdbOverrideReceiver.PREF_ADB_OVERRIDE, false).apply()
                                refresh++
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Disable ADB fallback") }
                    }
                }
            }

            Button(
                enabled = overlayGranted && privilegedPathReady,
                onClick = {
                    triggerEnabled = !triggerEnabled
                    val i = Intent(context, PixelShadeTriggerService::class.java)
                    if (triggerEnabled) context.startForegroundService(i) else context.stopService(i)
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(if (triggerEnabled) "Disable Pixel Shade" else "Start Pixel Shade")
            }

            if (!privilegedPathReady) {
                Text(
                    "Start is locked until Shizuku works or the ADB fallback is enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
            }
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) "Ready" else "Required",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(onClick = onGrant, enabled = !granted) {
            Text(if (granted) "Done" else "Grant")
        }
    }
}
