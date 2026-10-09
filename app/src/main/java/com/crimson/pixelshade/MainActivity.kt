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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.ResultReceiver
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import af.shizuku.Shizuku

private const val SHIZUKU_REQUEST = 1718
private const val PREF_THEME_MODE = "theme_mode"
private enum class ThemeMode { SYSTEM, LIGHT, DARK }
private enum class Screen { HOME, EDITOR, TILES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PixelShadeRuntime.isEnabled(this)) {
            val restarted = runCatching {
                ContextCompat.startForegroundService(this, Intent(this, PixelShadeTriggerService::class.java))
            }.isSuccess
            if (!restarted) {
                StatusBarSuppression.restore(this) { restored, _ ->
                    if (restored) PixelShadeRuntime.setEnabled(this, false)
                }
            }
        } else {
            StatusBarSuppression.restoreIfNeeded(this)
        }
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
                onOpenTiles = { screen = Screen.TILES },
                onPreview = {
                    context.startActivity(
                        Intent(context, PixelShadePanelV2Activity::class.java)
                            .putExtra(PixelShadePanelV2Activity.EXTRA_START_EXPANDED, true)
                    )
                }
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
    onOpenTiles: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var triggerEnabled by remember { mutableStateOf(PixelShadeRuntime.isEnabled(context)) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var setupExpanded by remember { mutableStateOf(false) }
    var oxygenExpanded by remember { mutableStateOf(false) }
    var showEnableWarning by remember { mutableStateOf(false) }
    var activationPending by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                triggerEnabled = PixelShadeRuntime.isEnabled(context)
                if (triggerEnabled) {
                    val triggerReady = PixelShadeTriggerService.hasWorkingTrigger() ||
                        PixelShadeAccessibilityService.hasWorkingTrigger()
                    if (triggerReady) {
                        StatusBarSuppression.sync(context)
                    } else {
                        val restarted = runCatching {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, PixelShadeTriggerService::class.java)
                            )
                        }.isSuccess
                        if (!restarted) {
                            StatusBarSuppression.restore(context) { restored, detail ->
                                if (restored) {
                                    PixelShadeRuntime.setEnabled(context, false)
                                    triggerEnabled = false
                                } else {
                                    operationMessage = "$detail. Recovery: ${StatusBarSuppression.ADB_RECOVERY_COMMAND}"
                                }
                            }
                        }
                    }
                } else {
                    StatusBarSuppression.restoreIfNeeded(context)
                }
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        val binderReceived = Shizuku.OnBinderReceivedListener {
            if (PixelShadeRuntime.isEnabled(context)) {
                val triggerReady = PixelShadeTriggerService.hasWorkingTrigger() ||
                    PixelShadeAccessibilityService.hasWorkingTrigger()
                if (triggerReady) {
                    StatusBarSuppression.sync(context)
                } else {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, PixelShadeTriggerService::class.java)
                        )
                    }
                }
            } else {
                StatusBarSuppression.restoreIfNeeded(context)
            }
            refresh++
        }
        val binderDead = Shizuku.OnBinderDeadListener { refresh++ }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST) {
                if (grantResult == PackageManager.PERMISSION_GRANTED &&
                    PixelShadeRuntime.isEnabled(context) &&
                    (PixelShadeTriggerService.hasWorkingTrigger() || PixelShadeAccessibilityService.hasWorkingTrigger())
                ) {
                    StatusBarSuppression.sync(context)
                }
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
    val shizukuPlus = shizukuGranted && StatusBarSuppression.isEnhancedBackend()
    val adbOverride = prefs.getBoolean(AdbOverrideReceiver.PREF_ADB_OVERRIDE, false)
    // An ADB broadcast can exercise the setup UI but cannot deliver a Shizuku Binder.
    // Never present it as privileged SystemUI control.
    val privilegedReady = shizukuGranted
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
    val triggerVerified = PixelShadeTriggerService.hasWorkingTrigger() ||
        PixelShadeAccessibilityService.hasWorkingTrigger()
    val configuredOverlayTrigger = PixelShadeConfig.bottomEnabled(context) ||
        PixelShadeConfig.leftEnabled(context) || PixelShadeConfig.rightEnabled(context)

    fun finishStoppingReplacement(afterStopped: (() -> Unit)? = null) {
        activationPending = false
        triggerEnabled = false
        PixelShadeRuntime.setEnabled(context, false)
        context.stopService(Intent(context, PixelShadeTriggerService::class.java))
        PixelShadeAccessibilityService.requestTriggerRefresh()
        refresh++
        afterStopped?.invoke()
    }

    fun finishAfterOemRestore(afterStopped: (() -> Unit)? = null) {
        val isolatedPackage = OplusQsPluginControl.packageDisabledByUs(context)
        if (isolatedPackage == null) {
            finishStoppingReplacement(afterStopped)
            return
        }
        OplusQsPluginControl.restore(context) { restored ->
            if (restored) {
                finishStoppingReplacement(afterStopped)
            } else {
                operationMessage = "Android's stock shade was restored, but $isolatedPackage is still disabled. Run: adb shell pm enable --user 0 $isolatedPackage"
                refresh++
            }
        }
    }

    fun stopReplacement(afterStopped: (() -> Unit)? = null) {
        activationPending = false
        operationMessage = "Restoring the Android notification shade..."
        StatusBarSuppression.restore(context) { success, detail ->
            operationMessage = if (success) {
                detail
            } else {
                "$detail. Pixel Shade is staying active so recovery controls remain available."
            }
            if (success) finishAfterOemRestore(afterStopped)
            refresh++
        }
    }

    fun startRuntimeOnly() {
        if (activationPending || PixelShadeRuntime.isEnabled(context)) return

        when {
            !notificationGranted -> {
                operationMessage = "Allow notifications first so Pixel Shade can keep a visible recovery control while it is active."
                setupExpanded = true
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            configuredOverlayTrigger && !overlayGranted -> {
                operationMessage = "Your configured side or bottom handles require Display over apps permission."
                setupExpanded = true
                return
            }
            !accessibility && !overlayGranted -> {
                operationMessage = "Enable Accessibility or Display over apps first so Pixel Shade can attach a working replacement trigger."
                setupExpanded = true
                return
            }
        }

        PixelShadeRuntime.setEnabled(context, true)
        triggerEnabled = true
        operationMessage = if (shizukuGranted) {
            "Pixel Shade runtime enabled. Stock shade suppression remains off until you explicitly enable it."
        } else {
            "Pixel Shade runtime enabled. Shizuku is not connected or authorized, so Android's stock shade remains available."
        }

        val serviceStarted = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PixelShadeTriggerService::class.java)
            )
        }.isSuccess

        if (!serviceStarted) {
            PixelShadeRuntime.setEnabled(context, false)
            triggerEnabled = false
            operationMessage = "Pixel Shade could not start its trigger service."
        }
        refresh++
    }

    fun startReplacement() {
        if (activationPending) return
        when {
            !shizukuGranted -> {
                operationMessage = "Grant Shizuku access before disabling the Android notification shade."
                setupExpanded = true
                return
            }
            !notificationGranted -> {
                operationMessage = "Allow notifications first so the ongoing recovery control stays visible while Android's shade is disabled."
                setupExpanded = true
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            configuredOverlayTrigger && !overlayGranted -> {
                operationMessage = "Your configured side or bottom handles require Display over apps permission."
                setupExpanded = true
                return
            }
            !accessibility && !overlayGranted -> {
                operationMessage = "Enable Accessibility or Display over apps first so Pixel Shade can verify a working pull-down trigger."
                setupExpanded = true
                return
            }
        }
        PixelShadeRuntime.setEnabled(context, true)
        triggerEnabled = true
        activationPending = true
        operationMessage = "Verifying a working Pixel Shade trigger before disabling Android's shade..."

        val activationReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                activationPending = false
                val success = resultCode == PixelShadeTriggerService.RESULT_ACTIVATION_OK
                val detail = resultData?.getString(PixelShadeTriggerService.RESULT_DETAIL)
                    ?: "Pixel Shade activation did not return a status message"
                triggerEnabled = PixelShadeRuntime.isEnabled(context)
                operationMessage = when {
                    success -> "$detail. Restore it before uninstalling Pixel Shade."
                    PixelShadeRuntime.statusBarWasDisabled(context) ->
                        "$detail. Pixel Shade is staying active because Android's shade may still be disabled."
                    else -> "$detail. Pixel Shade did not disable the stock pull-down."
                }
                if (!success && !PixelShadeRuntime.isEnabled(context)) {
                    context.stopService(Intent(context, PixelShadeTriggerService::class.java))
                }
                refresh++
            }
        }

        val serviceStarted = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PixelShadeTriggerService::class.java)
                    .setAction(PixelShadeTriggerService.ACTION_ENABLE_AND_SUPPRESS)
                    .putExtra(PixelShadeTriggerService.EXTRA_ACTIVATION_RECEIVER, activationReceiver)
            )
        }.isSuccess
        if (!serviceStarted) {
            activationPending = false
            if (
                PixelShadeRuntime.statusBarWasDisabled(context) ||
                OplusQsPluginControl.packageDisabledByUs(context) != null
            ) {
                operationMessage = "Pixel Shade could not start its trigger service; restoring Android's shade now..."
                StatusBarSuppression.restore(context) { restored, detail ->
                    operationMessage = if (restored) {
                        detail
                    } else {
                        "$detail. Keep Pixel Shade open or use ADB recovery."
                    }
                    if (restored) finishAfterOemRestore()
                    refresh++
                }
            } else {
                PixelShadeRuntime.setEnabled(context, false)
                PixelShadeConfig.disarmStockShadeSuppression(context)
                triggerEnabled = false
                operationMessage = "Pixel Shade could not start, so Android's notification shade was not disabled."
            }
        }
    }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Pixel Shade") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 3.dp,
                color = when {
                    triggerEnabled && triggerVerified -> MaterialTheme.colorScheme.primaryContainer
                    triggerEnabled -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when {
                                activationPending -> "Starting replacement"
                                triggerEnabled && triggerVerified -> "Replacement active"
                                triggerEnabled -> "Recovery needed"
                                else -> "Replacement off"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                activationPending -> "Verifying a reachable trigger before changing Android's shade"
                                triggerEnabled && triggerVerified -> "A live Pixel Shade trigger is attached"
                                triggerEnabled -> "No live trigger is verified; open recovery controls below"
                                else -> "Enable Pixel Shade to replace the stock pull-down gesture"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = triggerEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) startRuntimeOnly() else stopReplacement()
                        }
                    )
                }
            }

            SettingsNavigationSection("Customize") {
                SettingsNavigationRow(
                    title = "Quick settings tiles",
                    subtitle = "Choose and arrange the controls shown in your shade",
                    icon = Icons.Default.GridView,
                    onClick = onOpenTiles
                )
                SettingsNavigationDivider()
                SettingsNavigationRow(
                    title = "Layout and spacing",
                    subtitle = "Tune columns, tile sizes, padding, and panel geometry",
                    icon = Icons.Default.DashboardCustomize,
                    onClick = { onOpenEditor(PixelShadeEditorTab.LAYOUT) }
                )
                SettingsNavigationDivider()
                SettingsNavigationRow(
                    title = "Colors and theme",
                    subtitle = "Set the panel, accent, text, slider, and notification colors",
                    icon = Icons.Default.Palette,
                    onClick = { onOpenEditor(PixelShadeEditorTab.COLORS) }
                )
                SettingsNavigationDivider()
                SettingsNavigationRow(
                    title = "Tile appearance",
                    subtitle = "Adjust active and inactive fills, gradients, corners, and labels",
                    icon = Icons.Default.Gradient,
                    onClick = { onOpenEditor(PixelShadeEditorTab.TILE_STYLES) }
                )
            }

            SettingsNavigationSection("Gestures") {
                SettingsNavigationRow(
                    title = "Pull-down and brightness gestures",
                    subtitle = "Place top, side, or bottom handles and tune status-bar brightness swipes",
                    icon = Icons.Default.SwipeDown,
                    onClick = { onOpenEditor(PixelShadeEditorTab.HANDLE) }
                )
                SettingsNavigationDivider()
                SettingsNavigationRow(
                    title = "Motion and feedback",
                    subtitle = "Adjust expansion response, animation timing, and haptics",
                    icon = Icons.Default.Animation,
                    onClick = { onOpenEditor(PixelShadeEditorTab.MOTION) }
                )
            }

            SettingsNavigationSection("Content") {
                SettingsNavigationRow(
                    title = "Notifications",
                    subtitle = "Configure notification cards, media, filtering, spacing, and actions",
                    icon = Icons.Default.Notifications,
                    onClick = { onOpenEditor(PixelShadeEditorTab.NOTIFICATIONS) }
                )
            }

            SettingsNavigationSection("Preview and tools") {
                SettingsNavigationRow(
                    title = "Live shade preview",
                    subtitle = "Open the current shade at full size before enabling it",
                    icon = Icons.Default.Visibility,
                    onClick = onPreview
                )
                SettingsNavigationDivider()
                SettingsNavigationRow(
                    title = "Advanced controls",
                    subtitle = "Fine-tune behavior and review less common options",
                    icon = Icons.Default.Build,
                    onClick = { onOpenEditor(PixelShadeEditorTab.ADVANCED) }
                )
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
                PermissionRow(
                    if (shizukuPlus) "Shizuku+ privileged access" else "Shizuku privileged access",
                    privilegedReady
                ) {
                    runCatching {
                        when {
                            !Shizuku.pingBinder() -> operationMessage = "Start Shizuku+ first, then return here to grant Pixel Shade access."
                            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Shizuku.requestPermission(SHIZUKU_REQUEST)
                            else -> operationMessage = if (StatusBarSuppression.isEnhancedBackend()) "Shizuku+ backend connected" else "Standard Shizuku backend connected"
                        }
                    }.onFailure { operationMessage = "Could not contact the Shizuku+ backend" }
                    refresh++
                }
                if (adbOverride && !shizukuGranted) {
                    Text(
                        "ADB diagnostic override is enabled, but it cannot replace a Shizuku+ Binder for SystemUI control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            ShizukuDiagnosticsSection()

            SettingsExpansionCard(
                title = "Stock shade control",
                subtitle = if (PixelShadeRuntime.statusBarWasDisabled(context)) "Stock-shade recovery is armed" else "Android notification shade available",
                expanded = oxygenExpanded,
                onToggle = { oxygenExpanded = !oxygenExpanded }
            ) {
                Text(
                    "Pixel Shade uses Shizuku to run Android's status-bar shell command and block the normal notification-shade pull-down. SystemUI itself stays running.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Restore before uninstalling. Emergency recovery: ${StatusBarSuppression.ADB_RECOVERY_COMMAND}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text("Backend: ${StatusBarSuppression.lastResult(context)}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = shizukuGranted,
                        onClick = { showEnableWarning = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Disable stock shade") }
                    OutlinedButton(
                        onClick = {
                            StatusBarSuppression.restore(context) { success, detail ->
                                if (!success) {
                                    operationMessage = detail
                                    refresh++
                                } else {
                                    OplusQsPluginControl.restore(context) { pluginRestored ->
                                        operationMessage = if (pluginRestored) {
                                            "$detail. Any OEM QS plug-in isolated by Pixel Shade was restored."
                                        } else {
                                            val pkg = OplusQsPluginControl.packageDisabledByUs(context)
                                            "$detail. The OEM QS plug-in still needs recovery: adb shell pm enable --user 0 $pkg"
                                        }
                                        refresh++
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Restore stock system shade") }
                }
                HorizontalDivider()
                Text("OEM separate-QS plug-in compatibility", style = MaterialTheme.typography.titleSmall)
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
                                    enabled = shizukuGranted && triggerEnabled && triggerVerified && PixelShadeConfig.shouldSuppressStockShade(context),
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
                onClick = { stopReplacement() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore stock shade and turn off Pixel Shade") }

            OutlinedButton(
                onClick = {
                    stopReplacement {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}"))
                            )
                        }.onFailure {
                            operationMessage = "The stock shade was restored. Open Android app settings to uninstall Pixel Shade."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore stock shade, then uninstall") }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showEnableWarning) {
        AlertDialog(
            onDismissRequest = { showEnableWarning = false },
            icon = { Icon(Icons.Default.WarningAmber, null) },
            title = { Text("Disable Android's stock shade?") },
            text = {
                Text(
                    "Pixel Shade will use Shizuku to run the same privileged shell command available through ADB and block the normal notification-shade pull-down system-wide. " +
                        "Pixel Shade first verifies its own top, side, or bottom trigger, then uses that trigger to replace the pull-down. Android exposes these ADB status-bar flags through shared shell state, so restoring with none can also clear flags set by another ADB/Shizuku tool. " +
                        "Keep notifications allowed so the foreground recovery control remains visible. " +
                        (if (batteryExempt) "Battery optimization exemption is active. " else "Battery optimization is not exempt; OxygenOS may stop background recovery, so granting the exemption is strongly recommended. ") +
                        "Uninstalling directly from Android settings may leave the stock shade disabled; always use Pixel Shade's restore-first uninstall action. Emergency recovery: ${StatusBarSuppression.ADB_RECOVERY_COMMAND}"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showEnableWarning = false
                    startReplacement()
                }) { Text("Disable stock shade and enable") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsNavigationSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsNavigationDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 74.dp))
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
