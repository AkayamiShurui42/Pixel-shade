package com.crimson.pixelshade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restores the persistent gesture trigger after a reboot or app replacement when
 * Pixel Shade was enabled before the process went away.
 *
 * Accessibility-overlay users are normally restored by Android when the
 * accessibility service reconnects. This receiver covers the standalone
 * foreground-service/TYPE_APPLICATION_OVERLAY trigger path as well.
 */
class PixelShadeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supportedAction = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        val runtimeEnabled = PixelShadeRuntime.isEnabled(context)
        val recoveryPending = PixelShadeRuntime.statusBarWasDisabled(context)
        val pluginRecoveryPending = OplusQsPluginControl.packageDisabledByUs(context) != null
        if (!supportedAction || (!runtimeEnabled && !recoveryPending && !pluginRecoveryPending)) return

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PixelShadeTriggerService::class.java).apply {
                    if (!runtimeEnabled && (recoveryPending || pluginRecoveryPending)) {
                        action = PixelShadeTriggerService.ACTION_RECOVER_STOCK_SHADE
                    }
                }
            )
        }
    }
}
