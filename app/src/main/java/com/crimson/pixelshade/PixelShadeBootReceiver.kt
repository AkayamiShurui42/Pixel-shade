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
        if (!supportedAction || !PixelShadeRuntime.isEnabled(context)) return

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PixelShadeTriggerService::class.java)
            )
        }
    }
}
