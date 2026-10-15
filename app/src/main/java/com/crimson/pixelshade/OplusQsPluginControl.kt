package com.crimson.pixelshade

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import af.shizuku.Shizuku
import af.shizuku.ShizukuRemoteProcess
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object OplusQsPluginControl {
    const val ACTION_SEPARATE_QS_PLUGIN = "com.android.systemui.action.SEPARATE_QS_PLUGIN"
    private const val PREF_DISABLED_PACKAGE = "oplus_separate_qs_package_disabled_by_pixel_shade"
    private const val COMMAND_TIMEOUT_MS = 5_000L
    private const val PROCESS_TERMINATION_TIMEOUT_MS = 1_000L
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PixelShade-OplusPlugin").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    data class Candidate(
        val packageName: String,
        val serviceName: String,
        val label: String,
        val safeToIsolate: Boolean
    )

    private data class ShellOutcome(
        val exitCode: Int,
        val output: String = "",
        val error: String = "",
        val timedOut: Boolean = false,
        val terminationConfirmed: Boolean = true
    ) {
        val success: Boolean get() = !timedOut && exitCode == 0
    }

    @Suppress("DEPRECATION")
    fun discover(context: Context): List<Candidate> {
        val pm = context.packageManager
        val results = runCatching {
            pm.queryIntentServices(Intent(ACTION_SEPARATE_QS_PLUGIN), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        return results.mapNotNull { info ->
            val service = info.serviceInfo ?: return@mapNotNull null
            val pkg = service.packageName.orEmpty()
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            Candidate(
                packageName = pkg,
                serviceName = service.name.orEmpty(),
                label = label,
                safeToIsolate = pkg.isNotBlank() &&
                    pkg != "com.android.systemui" &&
                    (pkg.startsWith("com.oplus.") || pkg.startsWith("com.coloros.") || pkg.startsWith("com.oneplus."))
            )
        }.distinctBy { it.packageName to it.serviceName }
    }

    fun packageDisabledByUs(context: Context): String? =
        PixelShadeConfig.prefs(context).getString(PREF_DISABLED_PACKAGE, null)

    fun isolate(context: Context, candidate: Candidate, callback: (Boolean) -> Unit) {
        val app = context.applicationContext
        if (!candidate.safeToIsolate || !SystemActionController.shizukuReady()) {
            callback(false)
            return
        }
        // Persist the recovery target before changing package state. Enabling an
        // already-enabled package is harmless if the process dies before disable runs.
        val recoveryRecorded = PixelShadeConfig.prefs(app).edit()
            .putString(PREF_DISABLED_PACKAGE, candidate.packageName)
            .commit()
        if (!recoveryRecorded) {
            callback(false)
            return
        }

        runShell(arrayOf("pm", "disable-user", "--user", "0", candidate.packageName)) { outcome ->
            if (!outcome.terminationConfirmed) {
                callback(false)
                return@runShell
            }
            verifyPackageDisabled(candidate.packageName) { disabled ->
                when (disabled) {
                    true -> callback(true)
                    false -> {
                        clearRecoveryMarker(app, candidate.packageName)
                        callback(false)
                    }
                    null -> callback(false)
                }
            }
        }
    }

    fun restore(context: Context, callback: (Boolean) -> Unit = {}) {
        val app = context.applicationContext
        val pkg = packageDisabledByUs(app)
        if (pkg.isNullOrBlank()) {
            callback(true)
            return
        }
        if (!SystemActionController.shizukuReady()) {
            callback(false)
            return
        }

        runShell(arrayOf("pm", "enable", "--user", "0", pkg)) { outcome ->
            if (!outcome.terminationConfirmed) {
                callback(false)
                return@runShell
            }
            verifyPackageDisabled(pkg) { disabled ->
                val restored = disabled == false && clearRecoveryMarker(app, pkg)
                callback(restored)
            }
        }
    }

    private fun verifyPackageDisabled(pkg: String, callback: (Boolean?) -> Unit) {
        runShell(arrayOf("pm", "list", "packages", "-d", "--user", "0", pkg)) { outcome ->
            val disabled = if (outcome.success) {
                OplusPackageStateParser.isDisabled(outcome.output, pkg)
            } else {
                null
            }
            callback(disabled)
        }
    }

    private fun clearRecoveryMarker(context: Context, pkg: String): Boolean {
        if (packageDisabledByUs(context) != pkg) return false
        return PixelShadeConfig.prefs(context).edit().remove(PREF_DISABLED_PACKAGE).commit()
    }

    private fun runShell(args: Array<String>, callback: (ShellOutcome) -> Unit) {
        runCatching {
            executor.execute {
                val outcome = executeBounded(args)
                main.post { callback(outcome) }
            }
        }.onFailure {
            main.post { callback(ShellOutcome(-1, error = it.message ?: "Command could not start")) }
        }
    }

    @Suppress("DEPRECATION")
    private fun executeBounded(args: Array<String>): ShellOutcome {
        var process: ShizukuRemoteProcess? = null
        return try {
            val remote = Shizuku.newProcess(args, null, null)
            process = remote
            val output = StringBuilder()
            val error = StringBuilder()
            val stdoutThread = streamDrainer(remote.inputStream, output, "PixelShade-oplus-stdout")
            val stderrThread = streamDrainer(remote.errorStream, error, "PixelShade-oplus-stderr")
            val finished = remote.waitForTimeout(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val terminationConfirmed = finished || terminateAndConfirm(remote)
            stdoutThread.join(500L)
            stderrThread.join(500L)
            if (finished) {
                ShellOutcome(remote.exitValue(), output.toString().trim(), error.toString().trim())
            } else {
                ShellOutcome(
                    exitCode = -1,
                    output = output.toString().trim(),
                    error = "Command timed out",
                    timedOut = true,
                    terminationConfirmed = terminationConfirmed
                )
            }
        } catch (failure: Throwable) {
            ShellOutcome(
                exitCode = -1,
                error = failure.message ?: "Command failed",
                terminationConfirmed = process?.let(::terminateAndConfirm) ?: true
            )
        } finally {
            process?.let { runCatching { it.destroy() } }
        }
    }

    private fun terminateAndConfirm(process: ShizukuRemoteProcess): Boolean {
        runCatching { process.destroy() }
        return runCatching {
            process.waitForTimeout(PROCESS_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS) || !process.alive()
        }.getOrDefault(false)
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
}
