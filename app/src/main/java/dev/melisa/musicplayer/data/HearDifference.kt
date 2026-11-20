package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import java.io.File

/** How much of the track the comparison helper converts. */
enum class PreviewScope { Clip, Full }

/**
 * The slice used for a quick A/B. It sits in the middle of the song, where a short
 * excerpt is more representative than the intro or the fade-out.
 */
data class PreviewClip(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * Previews a few bitrates of the same track without touching the library file.
 *
 * The helper deliberately encodes into private cache files: the user is auditioning
 * options, not publishing them. Only the bitrate chosen on the results page is written
 * back to the library through [CompressAudio].
 */
object HearDifference {

    /** Long enough to hear artefacts, short enough to try several bitrates quickly. */
    const val CLIP_LENGTH_MS = 20_000L

    fun clipFor(durationMs: Long): PreviewClip {
        if (durationMs <= 0L) return PreviewClip(0L, 0L)
        val length = durationMs.coerceAtMost(CLIP_LENGTH_MS)
        val start = ((durationMs - length) / 2L).coerceAtLeast(0L)
        return PreviewClip(start, start + length)
    }

    /**
     * Only bitrates that leave a worthwhile saving are offered. Comparing a version
     * that would make the file bigger is not a useful choice.
     */
    fun eligibleBitrates(track: Track): List<Int> =
        CompressAudio.TARGET_BITRATES.filter { bitrate ->
            CompressAudio.willSave(track.sizeBytes, track.durationMs, bitrate)
        }

    suspend fun convertPreview(
        context: Context,
        source: Uri,
        targetBitrateKbps: Int,
        scope: PreviewScope,
        fullDurationMs: Long,
        output: File,
        onProgress: (Float) -> Unit = {},
    ): TranscodeOutput {
        val clip = if (scope == PreviewScope.Clip && fullDurationMs > 0L) {
            clipFor(fullDurationMs)
        } else {
            null
        }
        return AudioTranscoder.transcode(
            context = context,
            source = source,
            targetBitrateKbps = targetBitrateKbps,
            output = output,
            expectedDurationMs = clip?.durationMs ?: fullDurationMs,
            startMs = clip?.startMs ?: 0L,
            endMs = clip?.endMs ?: 0L,
            onProgress = onProgress,
        )
    }
}
