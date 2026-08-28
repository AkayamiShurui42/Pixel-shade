package com.crimson.pixelshade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AdbOverrideReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_ADB_OVERRIDE) return

        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ADB_OVERRIDE, enabled)
            .apply()
    }

    companion object {
        const val ACTION_SET_ADB_OVERRIDE = "com.crimson.pixelshade.SET_ADB_OVERRIDE"
        const val EXTRA_ENABLED = "enabled"
        const val PREFS_NAME = "pixel_shade_settings"
        const val PREF_ADB_OVERRIDE = "adb_override"
    }
}
