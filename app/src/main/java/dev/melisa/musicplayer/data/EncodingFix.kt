package dev.melisa.musicplayer.data

/**
 * Repairs text that was decoded with the wrong charset.
 *
 * Some ID3v2.3 files declare a single-byte encoding but actually store UTF-8. The
 * platform then shows "„" (bytes E2 80 9E) as "â€ž". Reinterpreting those characters as
 * Windows-1252 bytes and decoding them as UTF-8 recovers the original.
 *
 * Windows-1252 rather than ISO-8859-1 on purpose: the 0x80–0x9F range is where the
 * giveaway characters live (€, ž, ", …), and Latin-1 has no mapping for them.
 */
fun repairMojibake(value: String?): String {
    if (value.isNullOrEmpty()) return value.orEmpty()
    if (value.all { it.code < 0x80 }) return value

    val bytes = ByteArray(value.length)
    value.forEachIndexed { index, char ->
        val code = char.code
        // Latin-1 and the C1 range keep their own code as the byte; the CP1252
        // "smart" characters live above 0xFF and need the table.
        val mapped = if (code <= 0xFF) code else CP1252_TO_BYTE[char] ?: return value
        bytes[index] = mapped.toByte()
    }

    val decoded = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return value
    // Invalid UTF-8 means the original was single-byte after all: keep it untouched.
    if (decoded.contains('\uFFFD')) return value
    return decoded
}

/** Windows-1252 characters in 0x80–0x9F, back to the byte they came from. */
private val CP1252_TO_BYTE: Map<Char, Int> = mapOf(
    '\u20AC' to 0x80, // €
    '\u201A' to 0x82, // ‚
    '\u0192' to 0x83, // ƒ
    '\u201E' to 0x84, // „
    '\u2026' to 0x85, // …
    '\u2020' to 0x86, // †
    '\u2021' to 0x87, // ‡
    '\u02C6' to 0x88, // ˆ
    '\u2030' to 0x89, // ‰
    '\u0160' to 0x8A, // Š
    '\u2039' to 0x8B, // ‹
    '\u0152' to 0x8C, // Œ
    '\u017D' to 0x8E, // Ž
    '\u2018' to 0x91, // '
    '\u2019' to 0x92, // '
    '\u201C' to 0x93, // "
    '\u201D' to 0x94, // "
    '\u2022' to 0x95, // •
    '\u2013' to 0x96, // –
    '\u2014' to 0x97, // —
    '\u02DC' to 0x98, // ˜
    '\u2122' to 0x99, // ™
    '\u0161' to 0x9A, // š
    '\u203A' to 0x9B, // ›
    '\u0153' to 0x9C, // œ
    '\u017E' to 0x9E, // ž
    '\u0178' to 0x9F, // Ÿ
)
