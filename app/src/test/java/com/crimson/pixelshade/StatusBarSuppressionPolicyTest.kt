package com.crimson.pixelshade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarSuppressionPolicyTest {
    @Test
    fun suppressionRequiresEverySafetyPrecondition() {
        assertTrue(
            StatusBarSuppressionPolicy.shouldDisable(
                pixelShadeEnabled = true,
                suppressionRequested = true,
                privilegedBackendReady = true,
                replacementTriggerReady = true
            )
        )

        assertFalse(StatusBarSuppressionPolicy.shouldDisable(false, true, true, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(true, false, true, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(true, true, false, true))
        assertFalse(StatusBarSuppressionPolicy.shouldDisable(true, true, true, false))
    }

    @Test
    fun staleDisableMarkerMustBeRestoredWhenPolicyNoLongerAllowsBlocking() {
        assertTrue(StatusBarSuppressionPolicy.shouldRestore(markerSet = true, shouldDisableNow = false))
        assertFalse(StatusBarSuppressionPolicy.shouldRestore(markerSet = true, shouldDisableNow = true))
        assertFalse(StatusBarSuppressionPolicy.shouldRestore(markerSet = false, shouldDisableNow = false))
    }
}
