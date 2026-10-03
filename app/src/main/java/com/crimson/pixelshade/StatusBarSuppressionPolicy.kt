package com.crimson.pixelshade

/**
 * Pure decision logic for the persistent SystemUI expansion disable flag.
 *
 * The stock shade may only be disabled when Pixel Shade is enabled, the user
 * explicitly requested suppression, Shizuku is usable, and at least one
 * replacement trigger path is actually available. This prevents a persistent
 * "no shade at all" state when one of the required runtime pieces is missing.
 */
internal object StatusBarSuppressionPolicy {
    fun shouldDisable(
        pixelShadeEnabled: Boolean,
        suppressionRequested: Boolean,
        privilegedBackendReady: Boolean,
        replacementTriggerReady: Boolean
    ): Boolean =
        pixelShadeEnabled &&
            suppressionRequested &&
            privilegedBackendReady &&
            replacementTriggerReady

    fun shouldRestore(
        markerSet: Boolean,
        shouldDisableNow: Boolean
    ): Boolean = markerSet && !shouldDisableNow
}
