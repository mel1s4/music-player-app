package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream

/**
 * A tag larger than this is almost always corrupt (the synchsafe size can claim 256 MB)
 * or a file with absurd embedded art. Allocating that on a phone is an OOM waiting for a
 * malformed download, so refuse it and let the platform reader supply the basics.
 */
private const val MAX_TAG_BYTES = 8 * 1024 * 1024

/** Text frames we care about, read straight out of the ID3v2 tag. */
data class Id3Fields(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val genre: String? = null,
    val comment: String? = null,
)

/**
 * Minimal ID3v2.2/2.3/2.4 text reader.
 *
 * Why not MediaMetadataRetriever: it follows the frame's declared encoding literally, and
 * a lot of real-world files declare ISO-8859-1 while containing UTF-8. This reads the
 * encoding byte itself and, for the Latin-1 case, also checks whether the bytes are
 * actually valid UTF-8 — which is how "∞" survives instead of becoming "â€ž".
 *
 * Returns null when the file has no ID3v2 header (FLAC, M4A, OGG), so callers keep using
 * the platform reader for those.
 */
fun readId3(context: Context, uri: Uri): Id3Fields? {
    val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: return null
    return stream.use { raw ->
        val input = BufferedInputStream(raw)
        val header = ByteArray(10)
        if (input.read(header) < 10) return null
        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return null
        }

        val major = header[3].toInt() and 0xFF
        if (major !in 2..4) return null

        val flags = header[5].toInt() and 0xFF
        val tagSize = synchsafeSize(header, 6, 4)
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null

        var body = ByteArray(tagSize)
        var total = 0
        while (total < tagSize) {
            val read = input.read(body, total, tagSize - total)
            if (read <= 0) break
            total += read
        }
        if (total <= 0) return null
        body = body.copyOf(total)

        // Tag-level unsynchronisation (v2.2/v2.3 only): FF 00 -> FF.
        if (flags and 0x80 != 0) body = removeUnsynchronisation(body)

        val fields = parseFrames(body, major)
        return if (fields.title == null && fields.artist == null && fields.album == null) {
            null
        } else {
            fields
        }
    }
}

private fun parseFrames(body: ByteArray, major: Int): Id3Fields {
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var albumArtist: String? = null
    var year: String? = null
    var track: String? = null
    var disc: String? = null
    var genre: String? = null
    var comment: String? = null

    val idLength = if (major == 2) 3 else 4
    var offset = 0

    while (offset + idLength + 2 <= body.size) {
        if (body[offset] == 0.toByte()) break // padding

        val id = buildString(idLength) {
            for (i in 0 until idLength) append((body[offset + i].toInt() and 0xFF).toChar())
        }

        val (frameSize, headerSize) = when (major) {
            2 -> {
                val size = ((body[offset + 3].toInt() and 0xFF) shl 16) or
                    ((body[offset + 4].toInt() and 0xFF) shl 8) or
                    (body[offset + 5].toInt() and 0xFF)
                size to 6
            }

            3 -> {
                val size = beInt(body, offset + 4)
                size to 10
            }

            else -> synchsafeSize(body, offset + 4, 4) to 10
        }

        if (frameSize <= 0 || offset + headerSize + frameSize > body.size) break

        val contentOffset = offset + headerSize
        val content = body.copyOfRange(contentOffset, contentOffset + frameSize)

        when (id) {
            "TIT2", "TT2" -> title = title ?: decodeTextFrame(content)
            "TPE1", "TP1" -> artist = artist ?: decodeTextFrame(content)
            "TALB", "TAL" -> album = album ?: decodeTextFrame(content)
            "TPE2", "TP2" -> albumArtist = albumArtist ?: decodeTextFrame(content)
            "TYER", "TDRC", "TYE" -> year = year ?: decodeTextFrame(content)
            "TRCK", "TRK" -> track = track ?: decodeTextFrame(content)
            "TPOS", "TPA" -> disc = disc ?: decodeTextFrame(content)
            "TCON", "TCO" -> genre = genre ?: decodeTextFrame(content)
            "COMM", "COM" -> comment = comment ?: decodeCommentFrame(content)
        }

        offset = contentOffset + frameSize
    }

    return Id3Fields(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        year = year,
        track = track,
        disc = disc,
        genre = genre,
        comment = comment,
    )
}

private fun decodeTextFrame(content: ByteArray): String? {
    if (content.isEmpty()) return null
    val encoding = content[0].toInt() and 0xFF
    val text = decodeText(content, 1, content.size - 1, encoding)
    return text.trim().trimEnd('\u0000').ifBlank { null }
}

private fun decodeCommentFrame(content: ByteArray): String? {
    if (content.size < 5) return null
    val encoding = content[0].toInt() and 0xFF
    // 3 bytes of language, then a null-terminated description, then the text.
    val descriptionEnd = findTerminator(content, 4, encoding)
    val textStart = descriptionEnd + if (encoding == 1 || encoding == 2) 2 else 1
    if (textStart >= content.size) return null
    val text = decodeText(content, textStart, content.size - textStart, encoding)
    return text.trim().trimEnd('\u0000').ifBlank { null }
}

private fun findTerminator(bytes: ByteArray, from: Int, encoding: Int): Int {
    var i = from
    if (encoding == 1 || encoding == 2) {
        while (i + 1 < bytes.size) {
            if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) return i
            i += 2
        }
        return bytes.size
    }
    while (i < bytes.size) {
        if (bytes[i] == 0.toByte()) return i
        i++
    }
    return bytes.size
}

private fun decodeText(bytes: ByteArray, offset: Int, length: Int, encoding: Int): String {
    if (length <= 0) return ""
    val slice = bytes.copyOfRange(offset, offset + length)
    return when (encoding) {
        // Declared Latin-1, but frequently actually UTF-8 in the wild.
        0 -> if (looksLikeUtf8(slice)) String(slice, Charsets.UTF_8)
        else String(slice, Charsets.ISO_8859_1)

        1 -> decodeUtf16(slice)
        2 -> String(slice, Charsets.UTF_16BE)
        3 -> String(slice, Charsets.UTF_8)
        else -> String(slice, Charsets.ISO_8859_1)
    }
}

private fun decodeUtf16(bytes: ByteArray): String {
    if (bytes.size >= 2) {
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xFE) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (b0 == 0xFE && b1 == 0xFF) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
    }
    return String(bytes, Charsets.UTF_16LE)
}

/** Valid UTF-8 with at least one multi-byte sequence. */
private fun looksLikeUtf8(bytes: ByteArray): Boolean {
    var i = 0
    var multiByte = false
    while (i < bytes.size) {
        val b = bytes[i].toInt() and 0xFF
        if (b == 0) break
        if (b < 0x80) {
            i++
            continue
        }
        val length = when {
            b and 0xE0 == 0xC0 -> 2
            b and 0xF0 == 0xE0 -> 3
            b and 0xF8 == 0xF0 -> 4
            else -> return false
        }
        if (i + length > bytes.size) return false
        for (j in 1 until length) {
            if (bytes[i + j].toInt() and 0xC0 != 0x80) return false
        }
        multiByte = true
        i += length
    }
    return multiByte
}

private fun synchsafeSize(bytes: ByteArray, offset: Int, length: Int): Int {
    var value = 0
    for (i in 0 until length) {
        value = (value shl 7) or (bytes[offset + i].toInt() and 0x7F)
    }
    return value
}

private fun beInt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

private fun removeUnsynchronisation(bytes: ByteArray): ByteArray {
    val out = ByteArray(bytes.size)
    var written = 0
    var i = 0
    while (i < bytes.size) {
        out[written++] = bytes[i]
        if ((bytes[i].toInt() and 0xFF) == 0xFF &&
            i + 1 < bytes.size &&
            bytes[i + 1] == 0.toByte()
        ) {
            i++ // skip the inserted zero
        }
        i++
    }
    return out.copyOf(written)
}
