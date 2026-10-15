package com.crimson.pixelshade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OplusPackageStateParserTest {
    @Test
    fun exactDisabledPackageIsRecognized() {
        assertTrue(
            OplusPackageStateParser.isDisabled(
                "package:com.oplus.systemui.qsplugin",
                "com.oplus.systemui.qsplugin"
            )
        )
    }

    @Test
    fun pathPrefixedPackageIsRecognized() {
        assertTrue(
            OplusPackageStateParser.isDisabled(
                "package:/system_ext/priv-app/OplusQs.apk=com.oplus.systemui.qsplugin",
                "com.oplus.systemui.qsplugin"
            )
        )
    }

    @Test
    fun similarlyNamedPackageIsRejected() {
        assertFalse(
            OplusPackageStateParser.isDisabled(
                "package:com.oplus.systemui.qsplugin.extra",
                "com.oplus.systemui.qsplugin"
            )
        )
    }
}
