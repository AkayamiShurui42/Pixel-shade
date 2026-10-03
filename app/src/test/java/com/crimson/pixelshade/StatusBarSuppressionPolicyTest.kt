package com.crimson.pixelshade

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarSuppressionPolicyTest {
    @Test
    fun suppressionRequiresRuntimeRequestExplicitArmAndVerifiedTrigger() {
        assertTrue(StatusBarSuppressionPolicy.shouldDisable(true, true, true, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(false, true, true, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(true, false, true, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(true, true, false, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(true, true, true, false))
    }

    @Test
    fun legacyRequestedPreferenceCannotDisableWithoutNewArmBit() {
        assertFalse(
            StatusBarSuppressionPolicy.shouldDisable(
                runtimeEnabled = true,
                requested = true,
                armed = false,
                triggerReady = true
            )
        )
    }

    @Test
    fun privilegedCommandsStayExact() {
        assertArrayEquals(
            arrayOf("cmd", "statusbar", "send-disable-flag", "statusbar-expansion"),
            StatusBarSuppressionPolicy.disableCommand()
        )
        assertArrayEquals(
            arrayOf("cmd", "statusbar", "send-disable-flag", "none"),
            StatusBarSuppressionPolicy.restoreCommand()
        )
    }
}
