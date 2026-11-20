package dev.melisa.musicplayer.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * What the user needs to judge a file before editing it: how big it is and how it is
 * compressed.
 *
 * Bitrate comes from the container's audio track when the extractor exposes it, with a
 * metadata-retriever fallback for containers that do not. Codec falls back to the file
 * extension if the extractor cannot open the document.
 */
data class AudioFileDetails(
    val sizeBytes: Long,
    val codec: String?,
    val bitrateKbps: Int?,
    val lossless: Boolean,
)

object AudioFileInfo {

    suspend fun read(
        context: Context,
        uri: Uri,
        fallbackSizeBytes: Long,
    ): AudioFileDetails = withContext(Dispatchers.IO) {
        val size = querySize(context, uri).takeIf { it > 0L } ?: fallbackSizeBytes

        var codec: String? = null
        var bitrate: Int? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("audio/")) continue
                codec = codecForMime(mime)
                bitrate = runCatching { format.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull()
                break
            }
        } catch (_: Throwable) {
            // Damaged or unsupported container: the extension still tells us the format.
        } finally {
            runCatching { extractor.release() }
        }

        if (bitrate == null) {
            bitrate = retrieverBitrate(context, uri)
        }
        if (codec == null) {
            codec = codecForExtension(uri.lastPathSegment.orEmpty())
        }

        val lossless = codec in LOSSLESS_CODECS
        AudioFileDetails(
            sizeBytes = size,
            codec = codec,
            // Bits per second -> rounded kbps.
            bitrateKbps = bitrate?.let { (it / 1000.0).roundToInt() },
            lossless = lossless,
        )
    }

    private fun querySize(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
                }
        }.getOrNull() ?: 0L

    private fun retrieverBitrate(context: Context, uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun codecForMime(mime: String): String? = when {
        mime.contains("mpeg", ignoreCase = true) -> "MP3"
        mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("alac", ignoreCase = true) -> "ALAC"
        mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("wma", ignoreCase = true) -> "WMA"
        mime.contains("raw", ignoreCase = true) || mime.contains("pcm", ignoreCase = true) -> "WAV"
        else -> null
    }

    private fun codecForExtension(path: String): String? {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "MP3"
            "m4a", "aac" -> "AAC"
            "flac" -> "FLAC"
            "alac" -> "ALAC"
            "ogg" -> "Vorbis"
            "opus" -> "Opus"
            "wav" -> "WAV"
            "aiff", "aif" -> "AIFF"
            "wma" -> "WMA"
            else -> null
        }
    }

    private val LOSSLESS_CODECS = setOf("FLAC", "ALAC", "WAV", "AIFF")
}
