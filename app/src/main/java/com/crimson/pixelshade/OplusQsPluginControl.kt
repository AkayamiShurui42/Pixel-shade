package com.crimson.pixelshade

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

object OplusQsPluginControl {
    const val ACTION_SEPARATE_QS_PLUGIN = "com.android.systemui.action.SEPARATE_QS_PLUGIN"
    private const val PREF_DISABLED_PACKAGE = "oplus_separate_qs_package_disabled_by_pixel_shade"
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
        if (!candidate.safeToIsolate || !SystemActionController.shizukuReady()) {
            callback(false)
            return
        }
        runShell(arrayOf("pm", "disable-user", "--user", "0", candidate.packageName)) { ok ->
            if (ok) PixelShadeConfig.prefs(context).edit().putString(PREF_DISABLED_PACKAGE, candidate.packageName).apply()
            callback(ok)
        }
    }

    fun restore(context: Context, callback: (Boolean) -> Unit = {}) {
        val pkg = packageDisabledByUs(context)
        if (pkg.isNullOrBlank()) {
            callback(true)
            return
        }
        if (!SystemActionController.shizukuReady()) {
            callback(false)
            return
        }
        runShell(arrayOf("pm", "enable", pkg)) { ok ->
            if (ok) PixelShadeConfig.prefs(context).edit().remove(PREF_DISABLED_PACKAGE).apply()
            callback(ok)
        }
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
}
