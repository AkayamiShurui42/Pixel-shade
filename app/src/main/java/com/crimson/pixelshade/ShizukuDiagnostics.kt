package com.crimson.pixelshade

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import af.shizuku.Shizuku
import af.shizuku.ShizukuPlusAPI
import af.shizuku.ShizukuRemoteProcess
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val SHIZUKU_DIAGNOSTIC_REQUEST = 1720
private const val DIAGNOSTIC_TIMEOUT_MS = 4_000L

private data class ShizukuSnapshot(
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val uid: Int?,
    val version: Int?,
    val patch: Int?,
    val selinuxContext: String?,
    val enhancedApi: Boolean,
    val customApi: Boolean
)

private data class CommandSmokeResult(
    val backend: String,
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    fun render(): String = buildString {
        appendLine("$backend")
        appendLine("$command")
        appendLine("exit=$exitCode")
        appendLine("stdout=${stdout.ifBlank { "<empty>" }}")
        append("stderr=${stderr.ifBlank { "<empty>" }}")
    }
}

@Composable
internal fun ShizukuDiagnosticsSection() {
    var snapshot by remember { mutableStateOf(readShizukuSnapshot()) }
    var commandOutput by remember { mutableStateOf("No smoke test has been run yet.") }
    var busy by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        val binderReceived = Shizuku.OnBinderReceivedListener {
            snapshot = readShizukuSnapshot()
        }
        val binderDead = Shizuku.OnBinderDeadListener {
            snapshot = readShizukuSnapshot()
        }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_DIAGNOSTIC_REQUEST) {
                snapshot = readShizukuSnapshot()
            }
        }

        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceived) }
        runCatching { Shizuku.addBinderDeadListener(binderDead) }
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResult) }

        onDispose {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
            runCatching { Shizuku.removeBinderDeadListener(binderDead) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Shizuku diagnostics", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tests the Binder and command backend independently from stock-shade suppression.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DiagnosticLine("Binder / pingBinder", if (snapshot.binderAlive) "CONNECTED" else "NOT CONNECTED")
            DiagnosticLine("Permission", if (snapshot.permissionGranted) "GRANTED" else "DENIED")
            DiagnosticLine("Server UID", snapshot.uid?.toString() ?: "unavailable")
            DiagnosticLine("Server version", snapshot.version?.toString() ?: "unavailable")
            DiagnosticLine("Server patch", snapshot.patch?.toString() ?: "unavailable")
            DiagnosticLine("SELinux context", snapshot.selinuxContext ?: "unavailable")
            DiagnosticLine("Enhanced API", snapshot.enhancedApi.toString())
            DiagnosticLine("Custom API", snapshot.customApi.toString())

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = snapshot.binderAlive && !snapshot.permissionGranted,
                    onClick = {
                        runCatching { Shizuku.requestPermission(SHIZUKU_DIAGNOSTIC_REQUEST) }
                            .onFailure {
                                commandOutput = "Permission request failed: ${it.message ?: it.javaClass.simpleName}"
                            }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Request permission")
                }

                OutlinedButton(
                    onClick = { snapshot = readShizukuSnapshot() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Refresh")
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = snapshot.permissionGranted && !busy,
                    onClick = {
                        busy = true
                        Thread {
                            val text = runSmokeTests("id")
                            mainHandler.post {
                                commandOutput = text
                                snapshot = readShizukuSnapshot()
                                busy = false
                            }
                        }.start()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Run id")
                }

                OutlinedButton(
                    enabled = snapshot.permissionGranted && !busy,
                    onClick = {
                        busy = true
                        Thread {
                            val text = runSmokeTests("cmd statusbar collapse")
                            mainHandler.post {
                                commandOutput = text
                                snapshot = readShizukuSnapshot()
                                busy = false
                            }
                        }.start()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Run collapse")
                }
            }

            Text(
                if (busy) "Running..." else commandOutput,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodySmall
    )
}

private fun readShizukuSnapshot(): ShizukuSnapshot {
    val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val permissionGranted = binderAlive && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    return ShizukuSnapshot(
        binderAlive = binderAlive,
        permissionGranted = permissionGranted,
        uid = if (binderAlive) runCatching { Shizuku.getUid() }.getOrNull() else null,
        version = if (binderAlive) runCatching { Shizuku.getVersion() }.getOrNull() else null,
        patch = if (binderAlive) runCatching { Shizuku.getServerPatchVersion() }.getOrNull() else null,
        selinuxContext = if (binderAlive) runCatching { Shizuku.getSELinuxContext() }.getOrNull() else null,
        enhancedApi = binderAlive && runCatching { ShizukuPlusAPI.isEnhancedApiSupported() }.getOrDefault(false),
        customApi = binderAlive && runCatching { Shizuku.isCustomApiEnabled() }.getOrDefault(false)
    )
}

private fun runSmokeTests(command: String): String {
    if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
        return "Binder is not connected."
    }
    if (runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return "Pixel Shade does not have Shizuku permission."
    }

    val standard = executeStandardShizuku(command)
    val enhancedAvailable = runCatching { ShizukuPlusAPI.isEnhancedApiSupported() }.getOrDefault(false)
    val enhanced = if (enhancedAvailable) {
        runCatching {
            val result = ShizukuPlusAPI.Shell.executeCommand(command)
            CommandSmokeResult(
                backend = "Shizuku+ Shell",
                command = command,
                exitCode = result.exitCode,
                stdout = result.output,
                stderr = result.error
            )
        }.getOrElse {
            CommandSmokeResult("Shizuku+ Shell", command, -1, "", it.message ?: it.javaClass.simpleName)
        }
    } else {
        null
    }

    return buildString {
        append(standard.render())
        appendLine()
        appendLine()
        if (enhanced != null) {
            append(enhanced.render())
        } else {
            append("Shizuku+ Shell\n$command\nenhanced API unavailable")
        }
    }
}

@Suppress("DEPRECATION")
private fun executeStandardShizuku(command: String): CommandSmokeResult {
    var process: ShizukuRemoteProcess? = null
    return try {
        val remote = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        process = remote
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = drain(remote.inputStream, stdout, "PixelShade-diag-stdout")
        val errThread = drain(remote.errorStream, stderr, "PixelShade-diag-stderr")
        val finished = remote.waitForTimeout(DIAGNOSTIC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) runCatching { remote.destroy() }
        outThread.join(500L)
        errThread.join(500L)

        CommandSmokeResult(
            backend = "Shizuku.newProcess",
            command = command,
            exitCode = if (finished) remote.exitValue() else -1,
            stdout = stdout.toString().trim(),
            stderr = if (finished) stderr.toString().trim() else "Command timed out"
        )
    } catch (failure: Throwable) {
        CommandSmokeResult(
            backend = "Shizuku.newProcess",
            command = command,
            exitCode = -1,
            stdout = "",
            stderr = failure.message ?: failure.javaClass.simpleName
        )
    } finally {
        process?.let { runCatching { it.destroy() } }
    }
}

private fun drain(stream: InputStream, target: StringBuilder, name: String): Thread =
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
