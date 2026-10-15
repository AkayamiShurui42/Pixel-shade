package com.crimson.pixelshade

/** Parses the effective StatusBarManagerService state exposed by `dumpsys statusbar`. */
internal object StatusBarStateVerifier {
    private const val DISABLE_EXPAND_MASK = 0x00010000L
    private val disabled1Pattern = Regex("mDisabled1\\s*=\\s*0x([0-9a-fA-F]+)")

    /** Returns null when an OEM dump does not expose a recognizable disabled1 field. */
    fun expansionDisabled(dump: String): Boolean? {
        val flags = disabled1Pattern.findAll(dump)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toLongOrNull(16) }
            .toList()
        if (flags.isEmpty()) return null
        return flags.any { it and DISABLE_EXPAND_MASK != 0L }
    }
}
