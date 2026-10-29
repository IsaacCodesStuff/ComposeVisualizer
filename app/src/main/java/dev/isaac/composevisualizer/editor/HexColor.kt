package dev.isaac.composevisualizer.editor

/**
 * Parses "#RRGGBB", "#AARRGGBB" (the # is optional) into 0xAARRGGBB.
 * Returns null for anything else, which the UI shows as an error.
 */
fun parseHexColor(input: String): Long? {
    val digits = input.trim().removePrefix("#")
    val allHex = digits.isNotEmpty() &&
            digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (!allHex) return null

    return when (digits.length) {
        6 -> 0xFF000000L or digits.toLong(16) // no alpha given: fully opaque
        8 -> digits.toLong(16)
        else -> null
    }
}

/** The inverse: shows 6 digits when opaque, 8 otherwise. */
fun formatHexColor(argb: Long): String {
    val alpha = (argb shr 24) and 0xFFL
    return if (alpha == 0xFFL) {
        "#%06X".format(argb and 0xFFFFFFL)
    } else {
        "#%08X".format(argb and 0xFFFFFFFFL)
    }
}