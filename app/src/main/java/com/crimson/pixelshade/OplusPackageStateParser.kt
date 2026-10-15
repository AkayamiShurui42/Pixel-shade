package com.crimson.pixelshade

/**
 * Parses the exact package records emitted by pm list packages. Keeping this
 * Android-free makes the OEM recovery postcondition independently testable.
 */
internal object OplusPackageStateParser {
    fun isDisabled(output: String, packageName: String): Boolean =
        output.lineSequence()
            .map(String::trim)
            .any { it == "package:$packageName" || it.endsWith("=$packageName") }
}
