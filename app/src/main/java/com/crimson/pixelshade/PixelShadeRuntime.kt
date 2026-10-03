package com.crimson.pixelshade

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import af.shizuku.Shizuku
import af.shizuku.ShizukuPlusAPI
import af.shizuku.ShizukuRemoteProcess
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
            .commit()
    }

    internal fun statusBarWasDisabled(context: Context): Boolean =
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_STATUSBAR_DISABLED, false)

    internal fun setStatusBarDisabledMarker(context: Context, disabled: Boolean): Boolean =
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_STATUSBAR_DISABLED, disabled)
            .commit()
}

/**
 * Uses Android's StatusBarShellCommand disable flag through Shizuku's shell identity.
 * This is the same command available from an ADB shell; it disables panel expansion,
 * not SystemUI itself. Suppression is allowed only after the user explicitly arms it.
 */
object StatusBarSuppression {
    const val ADB_RECOVERY_COMMAND = "adb shell cmd statusbar send-disable-flag none"

    private const val COMMAND_TIMEOUT_MS = 4_000L
    private const val VERIFY_TIMEOUT_MS = 2_000L
    private const val COLLAPSE_TIMEOUT_MS = 200L
    private const val PANEL_LAUNCH_BUDGET_MS = 250L
    private const val DISABLE_EXPAND_MASK = 0x00010000L
    private const val PREF_LAST_RESULT = "statusbar_last_result"

    private val mutationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PixelShade-StatusBar").apply { isDaemon = true }
    }
    private val collapseExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "PixelShade-Collapse").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    private val disabled1Pattern = Regex("mDisabled1=0x([0-9a-fA-F]+)")

    private data class ShellOutcome(
        val exitCode: Int,
        val output: String = "",
        val error: String = "",
        val timedOut: Boolean = false
    ) {
        val success: Boolean get() = !timedOut && exitCode == 0
        val failureDetail: String
            get() = when {
                timedOut -> "Command timed out"
                error.isNotBlank() -> error
                output.isNotBlank() -> output
                else -> "Command failed with exit code $exitCode"
            }
    }

    private enum class Verification { MATCH, MISMATCH, UNAVAILABLE }

    fun lastResult(context: Context): String =
        context.getSharedPreferences(AdbOverrideReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LAST_RESULT, "Not checked yet") ?: "Not checked yet"

    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isEnhancedBackend(): Boolean = isReady() && runCatching {
        ShizukuPlusAPI.isEnhancedApiSupported()
    }.getOrDefault(false)

    private fun hasVerifiedTrigger(): Boolean =
        PixelShadeTriggerService.hasWorkingTrigger() || PixelShadeAccessibilityService.hasWorkingTrigger()

    private fun shouldDisableNow(context: Context): Boolean =
        StatusBarSuppressionPolicy.shouldDisable(
            runtimeEnabled = PixelShadeRuntime.isEnabled(context),
            requested = PixelShadeConfig.suppressStockShade(context),
            armed = PixelShadeConfig.suppressionArmed(context),
            triggerReady = hasVerifiedTrigger()
        )

    fun sync(context: Context) {
        val app = context.applicationContext
        when {
            shouldDisableNow(app) -> setExpansionDisabled(app, true)
            PixelShadeRuntime.statusBarWasDisabled(app) -> restore(app)
        }
    }

    fun restoreIfNeeded(context: Context) {
        val app = context.applicationContext
        if (!shouldDisableNow(app) && PixelShadeRuntime.statusBarWasDisabled(app)) restore(app)
    }

    fun confirmAndDisable(
        context: Context,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val app = context.applicationContext
        if (!hasVerifiedTrigger()) {
            publish(app, "Pixel Shade did not verify a working replacement trigger", onComplete, false)
            return
        }
        if (!PixelShadeConfig.armStockShadeSuppression(app)) {
            publish(app, "Could not save stock-shade authorization", onComplete, false)
            return
        }
        setExpansionDisabled(app, true) { success, detail ->
            if (!success) PixelShadeConfig.disarmStockShadeSuppression(app)
            onComplete?.invoke(success, detail)
        }
    }

    fun restore(
        context: Context,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val app = context.applicationContext
        PixelShadeConfig.disarmStockShadeSuppression(app)
        setExpansionDisabled(app, false, onComplete)
    }

    /** Temporarily restores stock expansion while retaining the user's authorization. */
    fun restoreTemporarily(
        context: Context,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) = setExpansionDisabled(context.applicationContext, false, onComplete)

    private fun setExpansionDisabled(
        context: Context,
        disabled: Boolean,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val app = context.applicationContext
        mutationExecutor.execute {
            if (disabled && !shouldDisableNow(app)) {
                publish(app, "Stock-shade disable canceled because Pixel Shade is no longer armed", onComplete, false)
                return@execute
            }

            if (!isReady()) {
                if (!disabled && !PixelShadeRuntime.statusBarWasDisabled(app)) {
                    publish(app, "Android notification shade was already available", onComplete, true)
                    return@execute
                }
                val detail = if (disabled) {
                    "Shizuku is not connected or Pixel Shade permission was not granted"
                } else {
                    "Could not restore through Shizuku. Run: $ADB_RECOVERY_COMMAND"
                }
                publish(app, detail, onComplete, false)
                return@execute
            }

            if (disabled) {
                disableWithRecovery(app, onComplete)
            } else {
                restoreAndVerify(app, onComplete)
            }
        }
    }

    private fun disableWithRecovery(
        app: Context,
        onComplete: ((Boolean, String) -> Unit)?
    ) {
        if (!shouldDisableNow(app)) {
            publish(app, "Stock-shade disable canceled because the replacement trigger changed", onComplete, false)
            return
        }

        val markerAlreadySet = PixelShadeRuntime.statusBarWasDisabled(app)
        if (!PixelShadeRuntime.setStatusBarDisabledMarker(app, true)) {
            publish(app, "Could not record recovery state, so the stock shade was not disabled", onComplete, false)
            return
        }

        // Close the check/command gap as far as the shell interface allows. Trigger-loss
        // callbacks are the final backstop if the view disappears while the command runs.
        if (!shouldDisableNow(app)) {
            if (markerAlreadySet) {
                rollbackDisable(app, "Stock-shade disable canceled because the replacement trigger changed", onComplete)
            } else {
                PixelShadeRuntime.setStatusBarDisabledMarker(app, false)
                publish(app, "Stock-shade disable canceled because the replacement trigger changed", onComplete, false)
            }
            return
        }

        val result = executeBounded(StatusBarSuppressionPolicy.disableCommand(), COMMAND_TIMEOUT_MS)
        val verified = result.success && verifyExpansionState(expectedDisabled = true) == Verification.MATCH
        val triggerStillReady = shouldDisableNow(app)
        if (verified && triggerStillReady) {
            val backend = if (isEnhancedBackend()) "Shizuku+" else "Shizuku"
            publish(app, "$backend disabled and verified Android notification-shade expansion", onComplete, true)
            return
        }

        val reason = when {
            !result.success -> result.failureDetail
            !triggerStillReady -> "Pixel Shade lost its verified trigger while disabling the stock shade"
            else -> "Android did not report the expected status-bar expansion state"
        }
        rollbackDisable(app, reason, onComplete)
    }

    private fun rollbackDisable(
        app: Context,
        reason: String,
        onComplete: ((Boolean, String) -> Unit)?
    ) {
        val recovery = executeBounded(StatusBarSuppressionPolicy.restoreCommand(), COMMAND_TIMEOUT_MS)
        val restored = recovery.success && verifyExpansionState(expectedDisabled = false) == Verification.MATCH
        val detail = if (restored) {
            PixelShadeRuntime.setStatusBarDisabledMarker(app, false)
            "$reason. Android's notification shade was restored automatically"
        } else {
            "$reason. Automatic restore could not be verified; run: $ADB_RECOVERY_COMMAND"
        }
        publish(app, detail, onComplete, false)
    }

    private fun restoreAndVerify(
        app: Context,
        onComplete: ((Boolean, String) -> Unit)?
    ) {
        if (!PixelShadeRuntime.statusBarWasDisabled(app)) {
            publish(app, "Android notification shade was already available", onComplete, true)
            return
        }

        val result = executeBounded(StatusBarSuppressionPolicy.restoreCommand(), COMMAND_TIMEOUT_MS)
        val verification = if (result.success) {
            verifyExpansionState(expectedDisabled = false)
        } else {
            Verification.UNAVAILABLE
        }
        val success = result.success && verification == Verification.MATCH
        val backend = if (isEnhancedBackend()) "Shizuku+" else "Shizuku"
        val detail = when {
            success -> "$backend restored and verified Android notification-shade expansion"
            !result.success -> "${result.failureDetail}. Run: $ADB_RECOVERY_COMMAND"
            verification == Verification.MISMATCH ->
                "Android still reports notification-shade expansion disabled. Run: $ADB_RECOVERY_COMMAND"
            else -> "Could not verify that Android restored notification-shade expansion. Run: $ADB_RECOVERY_COMMAND"
        }
        if (success) PixelShadeRuntime.setStatusBarDisabledMarker(app, false)
        publish(app, detail, onComplete, success)
    }

    /**
     * Confirms the effective DISABLE_EXPAND bit reported by StatusBarManagerService.
     * Unknown OEM dump formats are deliberately treated as unverified so teardown keeps
     * the replacement trigger and recovery notification alive.
     */
    private fun verifyExpansionState(expectedDisabled: Boolean): Verification {
        val dump = executeBounded(arrayOf("dumpsys", "statusbar"), VERIFY_TIMEOUT_MS)
        if (!dump.success) return Verification.UNAVAILABLE
        val flags = disabled1Pattern.findAll(dump.output)
            .mapNotNull { it.groupValues.getOrNull(1)?.toLongOrNull(16) }
            .toList()
        if (flags.isEmpty()) return Verification.UNAVAILABLE
        val actualDisabled = flags.any { it and DISABLE_EXPAND_MASK != 0L }
        return if (actualDisabled == expectedDisabled) Verification.MATCH else Verification.MISMATCH
    }

    fun collapsePanels(context: Context, onComplete: () -> Unit) {
        val delivered = AtomicBoolean(false)
        val deliver = Runnable {
            if (delivered.compareAndSet(false, true)) onComplete()
        }
        main.postDelayed(deliver, PANEL_LAUNCH_BUDGET_MS)
        runCatching {
            collapseExecutor.execute {
                try {
                    if (isReady()) {
                        executeBounded(arrayOf("cmd", "statusbar", "collapse"), COLLAPSE_TIMEOUT_MS)
                    }
                } finally {
                    main.post(deliver)
                }
            }
        }.onFailure { main.post(deliver) }
    }

    @Suppress("DEPRECATION")
    private fun executeBounded(args: Array<String>, timeoutMs: Long): ShellOutcome {
        var process: ShizukuRemoteProcess? = null
        return try {
            val remoteProcess = Shizuku.newProcess(args, null, null)
            process = remoteProcess
            val output = StringBuilder()
            val error = StringBuilder()
            val stdoutThread = streamDrainer(remoteProcess.inputStream, output, "PixelShade-shell-stdout")
            val stderrThread = streamDrainer(remoteProcess.errorStream, error, "PixelShade-shell-stderr")
            val finished = remoteProcess.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) runCatching { remoteProcess.destroy() }
            stdoutThread.join(500L)
            stderrThread.join(500L)
            if (finished) {
                ShellOutcome(remoteProcess.exitValue(), output.toString().trim(), error.toString().trim())
            } else {
                ShellOutcome(-1, output.toString().trim(), "Command timed out", timedOut = true)
            }
        } catch (failure: Throwable) {
            ShellOutcome(-1, error = failure.message ?: "Command failed")
        } finally {
            process?.let { runCatching { it.destroy() } }
        }
    }

    private fun streamDrainer(stream: InputStream, target: StringBuilder, name: String): Thread =
        Thread({
            runCatching {
                stream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> target.append(line).append('\n') }
                }
            }
        }, name).apply {
            isDaemon = true
            start()
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