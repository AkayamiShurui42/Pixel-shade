package com.crimson.pixelshade

data class PixelShadeDiagnostic(
    val name: String,
    val status: Status,
    val detail: String
) {
    enum class Status { OK, WARNING, ERROR }
}

data class PixelShadeDiagnosticInput(
    val appVersion: String,
    val manufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val triggerEnabled: Boolean,
    val triggerServiceRunning: Boolean,
    val accessibilityConnected: Boolean,
    val overlayPermission: Boolean,
    val optionalEdgeTriggersEnabled: Boolean,
    val notificationPermissionGranted: Boolean,
    val notificationAccess: Boolean,
    val notificationsEnabled: Boolean,
    val brightnessEnabled: Boolean,
    val writeSettingsPermission: Boolean,
    val batteryOptimizationExempt: Boolean,
    val shadeSuppressionRequested: Boolean,
    val shizukuConnected: Boolean,
    val shizukuPermissionGranted: Boolean,
    val suppressionSafe: Boolean,
    val suppressionApplied: Boolean,
    val backendLastResult: String
)

private fun String.forSupportReport(): String = trim()
    .replace(Regex("\\s+"), " ")
    .filter { it.isLetterOrDigit() || it in " .,:;_+-/()[]" }
    .take(180)
    .ifBlank { "No backend result recorded" }

data class PixelShadeDiagnosticReport(
    val input: PixelShadeDiagnosticInput,
    val checks: List<PixelShadeDiagnostic>
) {
    val issueCount: Int
        get() = checks.count { it.status == PixelShadeDiagnostic.Status.ERROR || it.status == PixelShadeDiagnostic.Status.WARNING }

    fun toShareableText(): String = buildString {
        appendLine("Pixel Shade diagnostic report")
        appendLine("App: ${input.appVersion}")
        appendLine("Device: ${input.manufacturer} ${input.deviceModel}".trim())
        appendLine("Android: ${input.androidVersion} (API ${input.sdkInt})")
        appendLine("Issues found: $issueCount")
        appendLine("Shizuku/backend: ${input.backendLastResult.forSupportReport()}")
        appendLine()
        checks.forEach { check ->
            val status = when (check.status) {
                PixelShadeDiagnostic.Status.OK -> "OK"
                PixelShadeDiagnostic.Status.WARNING -> "WARNING"
                PixelShadeDiagnostic.Status.ERROR -> "ERROR"
            }
            appendLine("[$status] ${check.name}")
            appendLine(check.detail)
        }
        appendLine()
        append("This report contains setup and device-version checks only; it does not include notifications or logs.")
    }
}

object PixelShadeDiagnostics {
    fun inspect(input: PixelShadeDiagnosticInput): PixelShadeDiagnosticReport {
        val checks = buildList {
            if (!input.triggerEnabled) {
                add(PixelShadeDiagnostic("Replacement trigger", PixelShadeDiagnostic.Status.WARNING, "Pixel Shade is disabled. Enable it on the home screen to use gesture triggers."))
            } else if (!input.accessibilityConnected && !input.overlayPermission) {
                add(PixelShadeDiagnostic("Replacement trigger", PixelShadeDiagnostic.Status.ERROR, "No trigger route is available. Enable Pixel Shade Accessibility or grant Display over other apps permission."))
            } else if (!input.accessibilityConnected && !input.triggerServiceRunning) {
                add(PixelShadeDiagnostic("Replacement trigger", PixelShadeDiagnostic.Status.ERROR, "Pixel Shade is enabled but its trigger service is not running. Re-enable the service; if it stops again, check battery restrictions."))
            } else {
                add(PixelShadeDiagnostic("Replacement trigger", PixelShadeDiagnostic.Status.OK, if (input.accessibilityConnected) "The accessibility trigger is connected." else "The overlay trigger service is running."))
            }

            if (input.triggerEnabled && input.optionalEdgeTriggersEnabled && (!input.overlayPermission || !input.triggerServiceRunning)) {
                add(PixelShadeDiagnostic("Additional edge triggers", PixelShadeDiagnostic.Status.WARNING, "Bottom and side triggers need Display over other apps permission and the overlay trigger service running."))
            }

            if (input.sdkInt >= 33 && input.triggerEnabled && !input.notificationPermissionGranted) {
                add(PixelShadeDiagnostic("Foreground service notification", PixelShadeDiagnostic.Status.WARNING, "Android notification permission is denied. The foreground service can still run, but its notification may be hidden from the notification drawer."))
            }

            if (input.notificationsEnabled && !input.notificationAccess) {
                add(PixelShadeDiagnostic("Notification access", PixelShadeDiagnostic.Status.ERROR, "Notification display is enabled but access is not granted. Enable Pixel Shade in Android's Notification access settings."))
            } else {
                add(PixelShadeDiagnostic("Notification access", PixelShadeDiagnostic.Status.OK, if (input.notificationsEnabled) "Notification access is available." else "Notification display is disabled in Pixel Shade settings."))
            }

            if (input.brightnessEnabled && !input.writeSettingsPermission) {
                add(PixelShadeDiagnostic("Brightness control", PixelShadeDiagnostic.Status.WARNING, "Brightness gestures are enabled but Modify system settings permission is missing."))
            } else {
                add(PixelShadeDiagnostic("Brightness control", PixelShadeDiagnostic.Status.OK, if (input.brightnessEnabled) "Brightness control has the required system-settings permission." else "Brightness gesture control is disabled."))
            }

            if (input.triggerEnabled && !input.batteryOptimizationExempt) {
                add(PixelShadeDiagnostic("Background reliability", PixelShadeDiagnostic.Status.WARNING, "Battery optimization may stop the trigger service in the background. Exempt Pixel Shade if triggers stop after the screen is off."))
            } else {
                add(PixelShadeDiagnostic("Background reliability", PixelShadeDiagnostic.Status.OK, "No battery-optimization issue detected."))
            }

            if (!input.shadeSuppressionRequested) {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.OK, "Stock-shade suppression is not requested."))
            } else if (
                !input.triggerEnabled ||
                (!input.accessibilityConnected && (!input.overlayPermission || !input.triggerServiceRunning))
            ) {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.ERROR, "Suppression is requested but the replacement trigger is not ready. Enable Pixel Shade and ensure Accessibility or overlay triggers are available."))
            } else if (!input.shizukuConnected) {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.ERROR, "Suppression is requested but Shizuku is not connected. Start Shizuku, then return to Pixel Shade."))
            } else if (!input.shizukuPermissionGranted) {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.ERROR, "Suppression is requested but Pixel Shade has not been granted Shizuku permission."))
            } else if (!input.suppressionSafe) {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.ERROR, "Suppression is requested but its safety gate is closed. Enable Pixel Shade and ensure Accessibility or overlay triggers are available."))
            } else if (!input.suppressionApplied) {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.WARNING, "The safety gate is ready, but Pixel Shade has not recorded a successful suppression command. Last backend result: ${input.backendLastResult.forSupportReport()}"))
            } else {
                add(PixelShadeDiagnostic("Stock shade suppression", PixelShadeDiagnostic.Status.OK, "Shizuku access, the safety gate, and the last suppression command are ready. Last backend result: ${input.backendLastResult.forSupportReport()}"))
            }
        }
        return PixelShadeDiagnosticReport(input, checks)
    }
}
