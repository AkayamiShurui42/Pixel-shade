package com.crimson.pixelshade

/**
 * The replacement shade has two visible expansion levels. CLOSED is retained
 * here as a first-class state so gestures, back handling, and panel teardown
 * share the same model instead of treating activity dismissal as a separate UI.
 */
internal enum class ShadeState {
    CLOSED,
    NOTIFICATIONS,
    QUICK_SETTINGS
}

internal fun ShadeState.quickSettingsExpansion(): Float = when (this) {
    ShadeState.CLOSED, ShadeState.NOTIFICATIONS -> 0f
    ShadeState.QUICK_SETTINGS -> 1f
}
