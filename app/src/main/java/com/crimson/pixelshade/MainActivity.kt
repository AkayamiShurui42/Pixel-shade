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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var editorTab by remember { mutableStateOf(PixelShadeEditorTab.HANDLE) }
    var themeMode by remember {
        mutableStateOf(
            runCatching { ThemeMode.valueOf(prefs.getString(PREF_THEME_MODE, ThemeMode.SYSTEM.name)!!) }
                .getOrDefault(ThemeMode.SYSTEM)
        )
    }
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colors) {
        when (screen) {
            Screen.HOME -> PixelShadeSetup(
                themeMode = themeMode,
                onThemeModeChange = {
                    themeMode = it
                    prefs.edit().putString(PREF_THEME_MODE, it.name).apply()
                },
                onOpenEditor = {
                    editorTab = it
                    screen = Screen.EDITOR
                },
                onOpenTiles = { screen = Screen.TILES }
            )
            Screen.EDITOR -> PixelShadeEditorV2(
                onClose = { screen = Screen.HOME },
                onOpenTiles = { screen = Screen.TILES },
                initialTab = editorTab
            )
            Screen.TILES -> PixelShadeTileEditorNext(onClose = { screen = Screen.HOME })
        }
    }
}

@Composable
private fun PixelShadeSetup(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenEditor: (PixelShadeEditorTab) -> Unit,
    onOpenTiles: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var triggerEnabled by remember { mutableStateOf(PixelShadeRuntime.isEnabled(context)) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var setupExpanded by remember { mutableStateOf(false) }
    var oxygenExpanded by remember { mutableStateOf(false) }
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

    val notificationGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)
    val writeSettings = Settings.System.canWrite(context)
    val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val shizukuGranted = shizukuRunning && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    val adbOverride = prefs.getBoolean(AdbOverrideReceiver.PREF_ADB_OVERRIDE, false)
    val privilegedReady = shizukuGranted || adbOverride
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val notificationAccess = enabledListeners.contains(context.packageName)
    val enabledAccessibility = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val accessibility = enabledAccessibility.contains(
        ComponentName(context, PixelShadeAccessibilityService::class.java).flattenToString(),
        ignoreCase = true
    )
    val batteryExempt = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
    val oplusPlugins = remember(refresh) { OplusQsPluginControl.discover(context) }
    val disabledPluginPackage = OplusQsPluginControl.packageDisabledByUs(context)

    fun setServiceEnabled(enabled: Boolean) {
        triggerEnabled = enabled
        PixelShadeRuntime.setEnabled(context, enabled)
        val serviceIntent = Intent(context, PixelShadeTriggerService::class.java)
        if (enabled) {
            context.startForegroundService(serviceIntent)
            StatusBarSuppression.sync(context)
        } else {
            StatusBarSuppression.setExpansionDisabled(context, false)
            context.stopService(serviceIntent)
        }
        PixelShadeAccessibilityService.requestTriggerRefresh()
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Pixel Shade") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(34.dp),
                tonalElevation = 3.dp,
                color = if (triggerEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (triggerEnabled) "Service running" else "Service stopped",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (triggerEnabled) "Pixel Shade owns the configured trigger regions" else "Enable the service to replace the stock pull-down gesture",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = triggerEnabled, onCheckedChange = { setServiceEnabled(it) })
                }
            }

            Text("Customize", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HubCategory("Tiles", Icons.Default.GridView, onOpenTiles)
                    HubCategory("Sliders", Icons.Default.Tune) { onOpenEditor(PixelShadeEditorTab.SLIDERS) }
                    HubCategory("Colors", Icons.Default.Palette) { onOpenEditor(PixelShadeEditorTab.COLORS) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HubCategory("Handle", Icons.Default.SwipeDown) { onOpenEditor(PixelShadeEditorTab.HANDLE) }
                    HubCategory("Layout", Icons.Default.DashboardCustomize) { onOpenEditor(PixelShadeEditorTab.LAYOUT) }
                    HubCategory("Notifications", Icons.Default.Notifications) { onOpenEditor(PixelShadeEditorTab.NOTIFICATIONS) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HubCategory("Motion", Icons.Default.Animation) { onOpenEditor(PixelShadeEditorTab.MOTION) }
                    HubCategory("Advanced", Icons.Default.Build) { onOpenEditor(PixelShadeEditorTab.ADVANCED) }
                    HubCategory("Preview", Icons.Default.Visibility) { onOpenEditor(PixelShadeEditorTab.LAYOUT) }
                }
            }

            HorizontalDivider()

            SettingsExpansionCard(
                title = "Setup & permissions",
                subtitle = setupSummary(notificationAccess, accessibility, overlayGranted, writeSettings, privilegedReady),
                expanded = setupExpanded,
                onToggle = { setupExpanded = !setupExpanded }
            ) {
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
                PermissionRow(if (adbOverride) "Privileged access (ADB fallback)" else "Shizuku / Shizuku+", privilegedReady) {
                    runCatching {
                        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                            Shizuku.requestPermission(SHIZUKU_REQUEST)
                        }
                    }.onFailure { refresh++ }
                }
                PermissionRow("Ignore battery optimization", batteryExempt) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                        )
                    }.recoverCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                Text("App appearance", style = MaterialTheme.typography.titleSmall)
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

            SettingsExpansionCard(
                title = "OxygenOS integration",
                subtitle = if (PixelShadeConfig.suppressStockShade(context)) "Stock shade blocking enabled" else "Stock shade blocking disabled",
                expanded = oxygenExpanded,
                onToggle = { oxygenExpanded = !oxygenExpanded }
            ) {
                Text(
                    "Primary block uses Android's statusbar-expansion disable flag through Shizuku.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("ADB: cmd statusbar send-disable-flag statusbar-expansion", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = shizukuGranted,
                        onClick = {
                            StatusBarSuppression.setExpansionDisabled(context, true)
                            operationMessage = "Requested stock shade block"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Block now") }
                    OutlinedButton(
                        enabled = shizukuGranted,
                        onClick = {
                            StatusBarSuppression.setExpansionDisabled(context, false)
                            operationMessage = "Requested stock shade restore"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Restore") }
                }
                HorizontalDivider()
                Text("Separate OxygenOS QS plugin", style = MaterialTheme.typography.titleSmall)
                when {
                    disabledPluginPackage != null -> {
                        Text("Pixel Shade isolated: $disabledPluginPackage", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            enabled = shizukuGranted,
                            onClick = {
                                OplusQsPluginControl.restore(context) { ok ->
                                    operationMessage = if (ok) "OxygenOS QS plugin restored" else "Could not restore QS plugin"
                                    refresh++
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restore separate QS plugin") }
                    }
                    oplusPlugins.isEmpty() -> Text(
                        "No external separate-QS plugin is visible to Pixel Shade on this build. The framework expansion block remains independent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> oplusPlugins.forEach { candidate ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(candidate.label, style = MaterialTheme.typography.titleSmall)
                            Text("${candidate.packageName}\n${candidate.serviceName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (candidate.safeToIsolate) {
                                OutlinedButton(
                                    enabled = shizukuGranted,
                                    onClick = {
                                        OplusQsPluginControl.isolate(context, candidate) { ok ->
                                            operationMessage = if (ok) "Isolated ${candidate.packageName}" else "Could not isolate ${candidate.packageName}"
                                            refresh++
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Isolate plugin (experimental)") }
                            } else {
                                Text("Protected: Pixel Shade will not disable SystemUI itself.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                operationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }

            OutlinedButton(
                enabled = shizukuGranted,
                onClick = {
                    setServiceEnabled(false)
                    OplusQsPluginControl.restore(context) { refresh++ }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Emergency restore OxygenOS shade") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HubCategory(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp), shape = CircleShape) {
            Icon(icon, label, Modifier.size(29.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SettingsExpansionCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggle) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (expanded) content()
        }
    }
}

private fun setupSummary(notificationAccess: Boolean, accessibility: Boolean, overlay: Boolean, write: Boolean, privileged: Boolean): String {
    val ready = listOf(notificationAccess, accessibility, overlay, write, privileged).count { it }
    return "$ready / 5 core capabilities ready"
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
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
