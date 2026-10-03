package com.crimson.pixelshade

/** Pure policy kept separate from Android/Shizuku so the safety contract is unit-testable. */
internal object StatusBarSuppressionPolicy {
    fun shouldDisable(
        runtimeEnabled: Boolean,
        requested: Boolean,
        armed: Boolean,
        triggerReady: Boolean
    ): Boolean = runtimeEnabled && requested && armed && triggerReady

    fun disableCommand(): Array<String> =
        arrayOf("cmd", "statusbar", "send-disable-flag", "statusbar-expansion")

    fun restoreCommand(): Array<String> =
        arrayOf("cmd", "statusbar", "send-disable-flag", "none")
}
