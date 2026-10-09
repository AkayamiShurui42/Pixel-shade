package com.crimson.pixelshade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusBarStateVerifierTest {
    @Test
    fun reportsExpansionAvailableWhenBitIsClear() {
        assertEquals(false, StatusBarStateVerifier.expansionDisabled("mDisabled1=0x0"))
    }

    @Test
    fun reportsExpansionDisabledWhenMaskIsPresent() {
        assertEquals(true, StatusBarStateVerifier.expansionDisabled("mDisabled1=0x10000"))
    }

    @Test
    fun checksEveryDisplayEntry() {
        val dump = """
            displayId=0
             mDisabled1=0x0
            displayId=1
             mDisabled1 = 0x10020
        """.trimIndent()

        assertEquals(true, StatusBarStateVerifier.expansionDisabled(dump))
    }

    @Test
    fun refusesToGuessForUnknownOemDumpFormat() {
        assertNull(StatusBarStateVerifier.expansionDisabled("status bar dump unavailable"))
    }
}
