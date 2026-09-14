package com.crimson.pixelshade

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import af.shizuku.Shizuku
import af.shizuku.ShizukuPlusAPI
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

    private val main = Handler(Looper.getMainLooper())
    private const val PREF_LAST_RESULT = "statusbar_last_result"

    /** User-visible diagnostics: never silently assume the privileged command worked. */
    fun lastResult(context: Context): String =
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LAST_RESULT, "Not checked yet") ?: "Not checked yet"

    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isEnhancedBackend(): Boolean = isReady() && runCatching {
        ShizukuPlusAPI.isEnhancedApiSupported()
    }.getOrDefault(false)

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

    fun setExpansionDisabled(context: Context, disabled: Boolean, onComplete: ((Boolean, String) -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            if (!isReady()) {
                publish(app, "Shizuku+ is not connected or permission was not granted", onComplete, false)
                return@execute
            }

            val args = if (disabled) {
                arrayOf("cmd", "statusbar", "send-disable-flag", "statusbar-expansion")
            } else {
                arrayOf("cmd", "statusbar", "send-disable-flag", "none")
            }

            val result = runCatching { ShizukuPlusAPI.Shell.executeCommand(args) }.getOrNull()
            val success = result?.isSuccess() == true
            val detail = when {
                success && isEnhancedBackend() -> "Shizuku+ changed stock status-bar expansion"
                success -> "Shizuku changed stock status-bar expansion"
                result != null -> result.error.ifBlank { result.output }.ifBlank { "Status-bar command failed" }
                else -> "Could not run the Shizuku+ status-bar command"
            }
            if (success) PixelShadeRuntime.setStatusBarDisabledMarker(app, disabled)
            publish(app, detail, onComplete, success)
        }
    }

    /** Collapse a panel already opened by SystemUI before presenting the replacement shade. */
    fun collapsePanels(context: Context, onComplete: () -> Unit) {
        executor.execute {
            if (isReady()) {
                runCatching { ShizukuPlusAPI.Shell.executeCommand(arrayOf("cmd", "statusbar", "collapse")) }
            }
            main.post(onComplete)
        }
    }

    private fun publish(
        context: Context,
        detail: String,
        onComplete: ((Boolean, String) -> Unit)?,
        success: Boolean
    ) {
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_RESULT, detail)
            .apply()
        main.post { onComplete?.invoke(success, detail) }
    }
}
