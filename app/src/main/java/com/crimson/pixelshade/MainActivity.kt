@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
private enum class Screen { HOME, EDITOR, TILES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarSuppression.restoreIfNeeded(this)
        setContent { PixelShadeRoot() }
    }
}

@Composable
private fun PixelShadeRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var themeMode by remember { mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString(PREF_THEME_MODE, ThemeMode.SYSTEM.name)!!) }.getOrDefault(ThemeMode.SYSTEM)) }
    val dark = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) } else { if (dark) darkColorScheme() else lightColorScheme() }

    MaterialTheme(colorScheme = colors) {
        when (screen) {
            Screen.HOME -> PixelShadeSetup(themeMode, { themeMode = it; prefs.edit().putString(PREF_THEME_MODE, it.name).apply() }, { screen = Screen.EDITOR })
            Screen.EDITOR -> PixelShadeEditorV2(onClose = { screen = Screen.HOME }, onOpenTiles = { screen = Screen.TILES })
            Screen.TILES -> PixelShadeTileEditor(onClose = { screen = Screen.EDITOR })
        }
    }
}

@Composable
private fun PixelShadeSetup(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, onOpenEditor: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var triggerEnabled by remember { mutableStateOf(PixelShadeRuntime.isEnabled(context)) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                triggerEnabled = PixelShadeRuntime.isEnabled(context)
                StatusBarSuppression.restoreIfNeeded(context)
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        val binderReceived = Shizuku.OnBinderReceivedListener {
            StatusBarSuppression.restoreIfNeeded(context)
            StatusBarSuppression.sync(context)
            refresh++
        }
        val binderDead = Shizuku.OnBinderDeadListener { refresh++ }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) StatusBarSuppression.sync(context)
                refresh++
            }
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

    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)
    val writeSettings = Settings.System.canWrite(context)
    val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val shizukuGranted = shizukuRunning && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    val adbOverride = prefs.getBoolean(AdbOverrideReceiver.PREF_ADB_OVERRIDE, false)
    val privilegedReady = shizukuGranted || adbOverride
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val notificationAccess = enabledListeners.contains(context.packageName)
    val enabledAccessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val accessibility = enabledAccessibility.contains(ComponentName(context, PixelShadeAccessibilityService::class.java).flattenToString(), ignoreCase = true)
    val batteryExempt = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Pixel Shade") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your shade, your rules", style = MaterialTheme.typography.headlineSmall)
                    Text("Pixel-style shade replacement with editable triggers, tiles, motion and dynamic Material color.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AssistChip(onClick = {}, label = { Text(when { shizukuGranted -> "Shizuku connected"; adbOverride -> "ADB fallback active"; else -> "Privileged access optional" }) })
                }
            }

            SettingsCard("Customize") {
                Text("Edit trigger zones, one-handed side gestures, panel style, animation behavior and integrated custom quick tiles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenEditor, modifier = Modifier.fillMaxWidth()) { Text("Edit Pixel Shade") }
            }

            SettingsCard("Appearance") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode -> FilterChip(selected = themeMode == mode, onClick = { onThemeModeChange(mode) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
            }

            SettingsCard("Permissions") {
                PermissionRow("Notifications", notificationGranted) { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                PermissionRow("Notification access", notificationAccess) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                PermissionRow("Accessibility", accessibility) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                PermissionRow("Display over apps", overlayGranted) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                PermissionRow("Modify system settings", writeSettings) { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                PermissionRow(if (adbOverride) "Privileged access (ADB fallback)" else "Shizuku / Shizuku+", privilegedReady) {
                    runCatching { if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(SHIZUKU_REQUEST) }.onFailure { refresh++ }
                }
                PermissionRow("Ignore battery optimization", batteryExempt) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }.recoverCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
            }

            if (PixelShadeConfig.suppressStockShade(context) && !shizukuGranted) {
                Text("Stock-shade blocking needs Shizuku. Accessibility collapse remains available as a fallback, but it cannot set Android's statusbar-expansion disable flag.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(enabled = accessibility || overlayGranted, onClick = {
                val next = !triggerEnabled
                triggerEnabled = next
                PixelShadeRuntime.setEnabled(context, next)
                val i = Intent(context, PixelShadeTriggerService::class.java)
                if (next) {
                    context.startForegroundService(i)
                    StatusBarSuppression.sync(context)
                } else {
                    StatusBarSuppression.setExpansionDisabled(context, false)
                    context.stopService(i)
                }
                PixelShadeAccessibilityService.requestTriggerRefresh()
            }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) { Text(if (triggerEnabled) "Disable Pixel Shade" else "Start Pixel Shade") }

            OutlinedButton(enabled = shizukuGranted, onClick = {
                triggerEnabled = false
                PixelShadeRuntime.setEnabled(context, false)
                StatusBarSuppression.setExpansionDisabled(context, false)
                context.stopService(Intent(context, PixelShadeTriggerService::class.java))
                PixelShadeAccessibilityService.requestTriggerRefresh()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Restore OxygenOS shade")
            }
            Text("Emergency restore clears Pixel Shade's privileged status-bar expansion block and stops its trigger service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) { ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); content() } } }
@Composable private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label); Text(if (granted) "Ready" else "Required", style = MaterialTheme.typography.bodySmall, color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }; FilledTonalButton(onClick = onGrant, enabled = !granted) { Text(if (granted) "Done" else "Grant") } } }
