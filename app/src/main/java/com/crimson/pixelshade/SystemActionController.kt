package com.crimson.pixelshade

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object SystemActionController {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PixelShade-SystemActions").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var torchEnabled = false

    fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun wifiEnabled(context: Context): Boolean = runCatching {
        context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
    }.getOrDefault(false)

    fun mobileDataEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "mobile_data", 0) != 0
    }.getOrDefault(false)

    fun bluetoothEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "bluetooth_on", 0) != 0
    }.getOrDefault(false)

    fun rotationEnabled(context: Context): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) != 0
    }.getOrDefault(true)

    fun dndEnabled(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)

    fun torchEnabled(): Boolean = torchEnabled

    fun toggleWifi(context: Context, onResult: (Boolean) -> Unit) {
        val next = !wifiEnabled(context)
        if (!shizukuReady()) {
            launch(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            return
        }
        runShell(arrayOf("cmd", "wifi", "set-wifi-enabled", if (next) "enabled" else "disabled")) { ok ->
            if (ok) onResult(next) else launch(context, Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    fun toggleMobileData(context: Context, onResult: (Boolean) -> Unit) {
        val next = !mobileDataEnabled(context)
        if (!shizukuReady()) {
            launch(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            return
        }
        runShell(arrayOf("cmd", "phone", "data", if (next) "enable" else "disable")) { ok ->
            if (ok) onResult(next) else launch(context, Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
        }
    }

    fun toggleBluetooth(context: Context, onResult: (Boolean) -> Unit) {
        val next = !bluetoothEnabled(context)
        if (!shizukuReady()) {
            launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        runShell(arrayOf("cmd", "bluetooth_manager", if (next) "enable" else "disable")) { ok ->
            if (ok) onResult(next) else launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    fun toggleRotation(context: Context): Boolean {
        if (!Settings.System.canWrite(context)) {
            launch(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return rotationEnabled(context)
        }
        val next = !rotationEnabled(context)
        val ok = Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (next) 1 else 0)
        return if (ok) next else !next
    }

    fun toggleDnd(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return dndEnabled(context)
        }
        val next = !dndEnabled(context)
        runCatching {
            nm.setInterruptionFilter(
                if (next) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }.onFailure { return !next }
        return next
    }

    fun toggleFlashlight(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return torchEnabled
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return torchEnabled
        val next = !torchEnabled
        val ok = runCatching { manager.setTorchMode(cameraId, next) }.isSuccess
        if (ok) torchEnabled = next
        return torchEnabled
    }

    private fun runShell(args: Array<String>, callback: (Boolean) -> Unit) {
        executor.execute {
            val ok = runCatching {
                @Suppress("DEPRECATION")
                val process = Shizuku.newProcess(args, null, null)
                val code = process.waitFor()
                runCatching { process.destroy() }
                code == 0
            }.getOrDefault(false)
            main.post { callback(ok) }
        }
    }

    private fun launch(context: Context, intent: Intent) {
        main.post {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
