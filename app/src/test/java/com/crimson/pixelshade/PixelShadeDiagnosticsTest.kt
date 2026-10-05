package com.crimson.pixelshade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelShadeDiagnosticsTest {
    @Test
    fun reportsMissingTriggerAndRequiredNotificationAccess() {
        val report = PixelShadeDiagnostics.inspect(input().copy(
            triggerEnabled = true,
            accessibilityConnected = false,
            overlayPermission = false,
            notificationAccess = false
        ))

        assertEquals(2, report.checks.count { it.status == PixelShadeDiagnostic.Status.ERROR })
        assertTrue(report.checks.any { it.name == "Replacement trigger" && it.status == PixelShadeDiagnostic.Status.ERROR })
        assertTrue(report.checks.any { it.name == "Notification access" && it.status == PixelShadeDiagnostic.Status.ERROR })
    }

    @Test
    fun reportIncludesOnlyDiagnosticSummaryAndExplicitlyExcludesPrivateContent() {
        val report = PixelShadeDiagnostics.inspect(input())
        val text = report.toShareableText()

        assertTrue(text.contains("Pixel Shade diagnostic report"))
        assertTrue(text.contains("Issues found: 0"))
        assertTrue(text.contains("does not include notifications or logs"))
        assertFalse(text.contains("notification title"))
    }

    @Test
    fun identifiesMissingShizukuWhenStockShadeSuppressionIsRequested() {
        val report = PixelShadeDiagnostics.inspect(input().copy(
            shadeSuppressionRequested = true,
            suppressionApplied = false
        ))

        assertTrue(report.checks.any {
            it.name == "Stock shade suppression" &&
                it.status == PixelShadeDiagnostic.Status.ERROR &&
                it.detail.contains("Shizuku is not connected")
        })
    }

    @Test
    fun detectsTriggerServiceThatStoppedAfterBeingEnabled() {
        val report = PixelShadeDiagnostics.inspect(input().copy(
            triggerEnabled = true,
            triggerServiceRunning = false,
            accessibilityConnected = false,
            overlayPermission = true
        ))

        assertTrue(report.checks.any {
            it.name == "Replacement trigger" &&
                it.status == PixelShadeDiagnostic.Status.ERROR &&
                it.detail.contains("service is not running")
        })
    }

    @Test
    fun doesNotCallShadeSuppressionSafeWhenTheOverlayTriggerServiceIsDown() {
        val report = PixelShadeDiagnostics.inspect(input().copy(
            triggerServiceRunning = false,
            accessibilityConnected = false,
            overlayPermission = true,
            shadeSuppressionRequested = true,
            shizukuConnected = true,
            shizukuPermissionGranted = true,
            suppressionSafe = true,
            suppressionApplied = true
        ))

        assertTrue(report.checks.any {
            it.name == "Stock shade suppression" &&
                it.status == PixelShadeDiagnostic.Status.ERROR &&
                it.detail.contains("replacement trigger is not ready")
        })
    }

    @Test
    fun warnsWhenForegroundServiceNotificationPermissionIsDenied() {
        val report = PixelShadeDiagnostics.inspect(input().copy(
            sdkInt = 33,
            notificationPermissionGranted = false
        ))

        assertTrue(report.checks.any {
            it.name == "Foreground service notification" &&
                it.status == PixelShadeDiagnostic.Status.WARNING &&
                it.detail.contains("can still run")
        })
    }

    @Test
    fun includesSanitizedLatestBackendResultInShareReport() {
        val report = PixelShadeDiagnostics.inspect(input().copy(backendLastResult = "backend failed\nwith\u0000 details"))
        val text = report.toShareableText()

        assertTrue(text.contains("Shizuku/backend: backend failed with details"))
        assertFalse(text.contains('\u0000'))
    }

    private fun input() = PixelShadeDiagnosticInput(
        appVersion = "0.1.0",
        manufacturer = "Test",
        deviceModel = "Device",
        androidVersion = "Android 17",
        sdkInt = 36,
        triggerEnabled = true,
        triggerServiceRunning = true,
        accessibilityConnected = true,
        overlayPermission = true,
        optionalEdgeTriggersEnabled = false,
        notificationPermissionGranted = true,
        notificationAccess = true,
        notificationsEnabled = true,
        brightnessEnabled = true,
        writeSettingsPermission = true,
        batteryOptimizationExempt = true,
        shadeSuppressionRequested = false,
        shizukuConnected = false,
        shizukuPermissionGranted = false,
        suppressionSafe = false,
        suppressionApplied = false,
        backendLastResult = "Not checked yet"
    )
}
