package com.crimson.pixelshade

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object PixelShadeRuntime {
    const val PREF_ENABLED = "pixel_shade_enabled"
    private const val PREF_STATUSBAR_DISABLED = "statusbar_expansion_disabled_by_pixel_shade"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()
    }

    internal fun statusBarWasDisabled(context: Context): Boolean =
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_STATUSBAR_DISABLED, false)

    internal fun setStatusBarDisabledMarker(context: Context, disabled: Boolean) {
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_STATUSBAR_DISABLED, disabled)
            .apply()
    }
}

/**
 * Uses Android's own StatusBarShellCommand disable flag through Shizuku's shell identity.
 * OxygenOS ultimately gates separate-QS expansion through CommandQueue.panelsEnabled(),
 * which reads the same DISABLE_EXPAND state produced by statusbar-expansion.
 *
 * The feature remains opt-in through PixelShadeConfig.suppressStockShade(). The marker is
 * deliberately persisted so the app can undo a stale shell-level disable after a process restart.
 */
object StatusBarSuppression {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PixelShade-StatusBar").apply { isDaemon = true }
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        val shouldDisable = PixelShadeRuntime.isEnabled(app) && PixelShadeConfig.suppressStockShade(app)
        setExpansionDisabled(app, shouldDisable)
    }

    fun restoreIfNeeded(context: Context) {
        val app = context.applicationContext
        if (!PixelShadeRuntime.isEnabled(app) && PixelShadeRuntime.statusBarWasDisabled(app)) {
            setExpansionDisabled(app, false)
        }
    }

    fun setExpansionDisabled(context: Context, disabled: Boolean) {
        val app = context.applicationContext
        executor.execute {
            val ready = runCatching {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!ready) return@execute

            val args = if (disabled) {
                arrayOf("cmd", "statusbar", "send-disable-flag", "statusbar-expansion")
            } else {
                arrayOf("cmd", "statusbar", "send-disable-flag", "none")
            }

            val success = runCatching {
                @Suppress("DEPRECATION")
                val process = Shizuku.newProcess(args, null, null)
                val code = process.waitFor()
                runCatching { process.destroy() }
                code == 0
            }.getOrDefault(false)

            if (success) {
                PixelShadeRuntime.setStatusBarDisabledMarker(app, disabled)
            }
        }
    }
}
