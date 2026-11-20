package dev.melisa.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

enum class CompressFailure {
    UnsupportedFormat,
    Encoder,
    ReadOnly,
    NoSpace,
    Verification,
    NoSavings,
    NotFound,
    Io,
}

sealed interface CompressOutcome {
    data class Success(val result: CompressResult) : CompressOutcome
    data class Failure(val reason: CompressFailure, val detail: String? = null) : CompressOutcome
}

data class CompressResult(
    val originalUri: Uri,
    val newUri: Uri,
    val sourceBytes: Long,
    val newBytes: Long,
    val durationMs: Long,
    /** True only when the original file was removed, so the space is actually freed. */
    val replaced: Boolean,
)

data class CompressEstimate(
    val sourceBytes: Long,
    val targetBytes: Long,
    val savingsBytes: Long,
    val savingsPercent: Int,
)

/**
 * Turns a track into a smaller AAC/M4A file, publishes it next to the original and
 * optionally removes the original.
 *
 * The order matters: transcode to a private cache file, verify it opens and has the
 * right duration, copy the tags and artwork, publish through SAF, check the published
 * size, and only then delete the original. Any failure leaves the library untouched.
 *
 * Output is AAC in MP4 because that is what the platform encoder and muxer can write;
 * converting to MP3 or Opus would need a native codec.
 */
object CompressAudio {

    /** Presets shown in the UI, in kbps. */
    val TARGET_BITRATES = listOf(96, 128, 160, 192)

    /** A conversion is refused when the result would not be meaningfully smaller. */
    private const val MIN_SAVING_FRACTION = 0.05f

    fun targetBytes(durationMs: Long, bitrateKbps: Int): Long {
        if (durationMs <= 0L || bitrateKbps <= 0) return 0L
        val payload = bitrateKbps * 1000L / 8L * durationMs / 1000L
        // MP4 framing and the AAC encoder's padding are small but real.
        return (payload * 1.03).toLong() + 1_024L
    }

    /** Average bitrate implied by the file size, for files whose container hides it. */
    fun estimatedSourceKbps(sizeBytes: Long, durationMs: Long): Int {
        if (sizeBytes <= 0L || durationMs <= 0L) return 0
        return (sizeBytes * 8.0 / durationMs).roundToInt()
    }

    /** True when [bitrateKbps] leaves a worthwhile saving for this exact track. */
    fun willSave(sourceBytes: Long, durationMs: Long, bitrateKbps: Int): Boolean {
        if (durationMs <= 0L || bitrateKbps <= 0) return false
        val target = targetBytes(durationMs, bitrateKbps)
        if (target <= 0L) return false
        return sourceBytes <= 0L || target < sourceBytes * (1f - MIN_SAVING_FRACTION)
    }

    fun estimate(sourceBytes: Long, durationMs: Long, bitrateKbps: Int): CompressEstimate {
        val rawTarget = targetBytes(durationMs, bitrateKbps)
        val target = if (sourceBytes > 0L) rawTarget.coerceAtMost(sourceBytes) else rawTarget
        val savings = (sourceBytes - target).coerceAtLeast(0L)
        val percent = if (sourceBytes > 0L) (savings * 100 / sourceBytes).toInt() else 0
        return CompressEstimate(
            sourceBytes = sourceBytes,
            targetBytes = target,
            savingsBytes = savings,
            savingsPercent = percent,
        )
    }

    suspend fun compress(
        context: Context,
        source: Uri,
        targetBitrateKbps: Int,
        keepOriginal: Boolean,
        sourceBytes: Long,
        sourceDurationMs: Long,
        onProgress: (Float) -> Unit = {},
    ): CompressOutcome = withContext(Dispatchers.IO) {
        val cacheFile = File.createTempFile("compress", ".m4a", context.cacheDir)
        try {
            val expected = when {
                sourceDurationMs > 0L -> sourceDurationMs
                else -> durationOf(context, source)
            }

            val estimatedTarget = targetBytes(expected, targetBitrateKbps)
            val needed = (sourceBytes.takeIf { it > 0L } ?: estimatedTarget) +
                estimatedTarget + 8L * 1024 * 1024
            if (context.cacheDir.usableSpace < needed) {
                Log.w(TAG, "no space: need $needed, have ${context.cacheDir.usableSpace}")
                return@withContext CompressOutcome.Failure(CompressFailure.NoSpace)
            }

            onProgress(0.01f)
            val transcoded = try {
                AudioTranscoder.transcode(
                    context = context,
                    source = source,
                    targetBitrateKbps = targetBitrateKbps,
                    output = cacheFile,
                    expectedDurationMs = expected,
                    onProgress = { onProgress(0.02f + it * 0.84f) },
                )
            } catch (t: TranscodeException) {
                Log.w(TAG, "transcode failed: ${t.failure} ${t.message}", t)
                return@withContext CompressOutcome.Failure(t.failure.toCompressFailure(), t.message)
            }

            onProgress(0.87f)
            val durationMs = verify(cacheFile, expected) ?: run {
                Log.w(TAG, "verification failed: expected ${expected}ms, file ${cacheFile.length()}B")
                return@withContext CompressOutcome.Failure(CompressFailure.Verification)
            }

            val newBytes = cacheFile.length()
            if (newBytes <= 0L) {
                Log.w(TAG, "empty output")
                return@withContext CompressOutcome.Failure(CompressFailure.Verification)
            }
            if (sourceBytes > 0L && newBytes >= sourceBytes * (1f - MIN_SAVING_FRACTION)) {
                // Turning a 128 kbps MP3 into a 120 kbps one is all downside.
                Log.w(TAG, "no saving: ${sourceBytes}B -> ${newBytes}B")
                return@withContext CompressOutcome.Failure(CompressFailure.NoSavings)
            }

            onProgress(0.89f)
            copyTags(context, source, cacheFile)
            // A tag commit rewrites the container; if it damaged the file, publishing it
            // would delete the only good copy of the song.
            if (verify(cacheFile, expected) == null) {
                Log.w(TAG, "verification failed after tags")
                return@withContext CompressOutcome.Failure(CompressFailure.Verification)
            }
            onProgress(0.92f)

            val published = try {
                publish(context, source, cacheFile)
            } catch (t: Throwable) {
                val reason = when (t) {
                    is SecurityException -> CompressFailure.ReadOnly
                    is CompressException -> t.failure
                    else -> CompressFailure.ReadOnly
                }
                Log.w(TAG, "publish failed: $reason", t)
                return@withContext CompressOutcome.Failure(reason, t.message)
            }

            onProgress(0.97f)
            var replaced = false
            if (!keepOriginal) {
                replaced = runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, source)
                }.getOrDefault(false)
            }

            onProgress(1f)
            CompressOutcome.Success(
                CompressResult(
                    originalUri = source,
                    newUri = published,
                    sourceBytes = sourceBytes.takeIf { it > 0L } ?: newBytes,
                    newBytes = newBytes,
                    durationMs = durationMs,
                    replaced = replaced,
                ),
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "compress failed", t)
            CompressOutcome.Failure(CompressFailure.Io, t.message)
        } finally {
            runCatching { cacheFile.delete() }
        }
    }

    private fun TranscodeFailure.toCompressFailure(): CompressFailure = when (this) {
        TranscodeFailure.UnsupportedFormat -> CompressFailure.UnsupportedFormat
        TranscodeFailure.NoAudioTrack -> CompressFailure.UnsupportedFormat
        TranscodeFailure.Encoder -> CompressFailure.Encoder
        TranscodeFailure.Muxer -> CompressFailure.Encoder
        TranscodeFailure.Io -> CompressFailure.Io
    }

    private suspend fun durationOf(context: Context, source: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, source)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * The encoded file is only trusted if the platform can read a sensible duration from
     * it. This catches an encoder that produced a header and nothing else.
     */
    private fun verify(file: File, expectedMs: Long): Long? {
        if (!file.exists() || file.length() == 0L) return null
        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
        if (duration <= 0L) return null
        if (expectedMs > 0L) {
            val tolerance = max(1_500L, expectedMs / 50)
            if (durationDeltaMs(duration, expectedMs) > tolerance) return null
        }
        return duration
    }

    /**
     * Copies textual tags and the first embedded picture. Tags are a courtesy here: if
     * jaudiotagger cannot express a field in MP4, the conversion still succeeds and the
     * database remains the source of truth for ratings, tags and favourites.
     */
    private fun copyTags(context: Context, source: Uri, target: File) {
        // jaudiotagger picks its reader from the extension, so the cache copy has to
        // keep the original one (".audio" would be unreadable).
        val extension = songFileInfo(source).name
            .substringAfterLast('.', "")
            .takeIf { it.length in 1..5 }
            ?: "audio"
        val sourceCopy = File.createTempFile("compress-src", ".$extension", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                sourceCopy.outputStream().use { output -> input.copyTo(output) }
            } ?: return

            val sourceTag = runCatching { AudioFileIO.read(sourceCopy).tag }.getOrNull() ?: return
            val targetFile = runCatching { AudioFileIO.read(target) }.getOrNull() ?: return
            val targetTag = runCatching { targetFile.tagOrCreateAndSetDefault }.getOrNull() ?: return

            TAG_KEYS.forEach { key ->
                val value = runCatching { sourceTag.getFirst(key) }.getOrNull()
                if (!value.isNullOrBlank()) {
                    runCatching { targetTag.setField(key, value) }
                }
            }

            val artwork = runCatching { sourceTag.firstArtwork }.getOrNull()
            if (artwork?.binaryData != null) {
                runCatching {
                    val copy = ArtworkFactory.getNew()
                    copy.binaryData = artwork.binaryData
                    copy.mimeType = artwork.mimeType ?: "image/jpeg"
                    copy.pictureType = artwork.pictureType
                    targetTag.setField(copy)
                }
            }

            runCatching { targetFile.commit() }
        } catch (_: Throwable) {
            // Tag copying is best effort; the audio conversion already succeeded.
        } finally {
            runCatching { sourceCopy.delete() }
        }
    }

    /**
     * Writes the cache file into the original's folder through SAF.
     *
     * A temp document is created first and renamed into place, so an interrupted write
     * never leaves a half-written file under a library-visible name. When a provider
     * cannot rename, the bytes are copied into a final document and the temp is removed.
     */
    private fun publish(context: Context, source: Uri, cacheFile: File): Uri {
        val resolver = context.contentResolver
        val parent = parentDocumentUri(source)
            ?: throw CompressException(CompressFailure.ReadOnly, "no writable parent")
        val sourceName = songFileInfo(source).name
        val base = sourceName.substringBeforeLast('.').ifBlank { "audio" }
        val sourceExtension = sourceName.substringAfterLast('.', "").lowercase()
        val proposed = if (sourceExtension == "m4a" || sourceExtension == "aac") {
            "$base (compressed).m4a"
        } else {
            "$base.m4a"
        }
        val finalName = uniqueName(context, parent, proposed)
        val tempDoc = runCatching {
            DocumentsContract.createDocument(
                resolver,
                parent,
                "application/octet-stream",
                ".$finalName.part",
            )
        }.getOrNull() ?: throw CompressException(CompressFailure.ReadOnly, "create failed")

        try {
            writeDocument(context, tempDoc, cacheFile)

            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, tempDoc, finalName)
            }.getOrNull()
            if (renamed != null) {
                verifyPublished(context, renamed, cacheFile, resolver)
                return renamed
            }

            // Some providers (cloud, USB) refuse renameDocument; copy under the final name.
            val finalDoc = runCatching {
                DocumentsContract.createDocument(resolver, parent, "audio/mp4", finalName)
            }.getOrNull() ?: throw CompressException(CompressFailure.ReadOnly, "create final failed")
            try {
                writeDocument(context, finalDoc, cacheFile)
                verifyPublished(context, finalDoc, cacheFile, resolver)
            } catch (t: Throwable) {
                runCatching { DocumentsContract.deleteDocument(resolver, finalDoc) }
                throw t
            }
            runCatching { DocumentsContract.deleteDocument(resolver, tempDoc) }
            return finalDoc
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, tempDoc) }
            throw t
        }
    }

    /** The published bytes must be the bytes that passed verification, not a prefix. */
    private fun verifyPublished(
        context: Context,
        uri: Uri,
        cacheFile: File,
        resolver: android.content.ContentResolver,
    ) {
        val size = sizeOf(context, uri)
        if (size <= 0L || size != cacheFile.length()) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw CompressException(
                CompressFailure.Verification,
                "published size $size != ${cacheFile.length()}",
            )
        }
    }

    private fun writeDocument(context: Context, uri: Uri, file: File) {
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw CompressException(CompressFailure.ReadOnly, "open output failed")
        output.use { sink ->
            file.inputStream().use { input -> input.copyTo(sink) }
            sink.flush()
        }
    }

    private fun parentDocumentUri(source: Uri): Uri? {
        val documentId = runCatching { DocumentsContract.getDocumentId(source) }.getOrNull()
            ?: return null
        val parentId = documentId.substringBeforeLast('/', "")
        return if (parentId.isEmpty() || parentId == documentId) {
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(source) }.getOrNull()
                ?: return null
            runCatching { DocumentsContract.buildDocumentUriUsingTree(source, treeId) }.getOrNull()
        } else {
            runCatching { DocumentsContract.buildDocumentUriUsingTree(source, parentId) }.getOrNull()
                ?: runCatching {
                    DocumentsContract.buildDocumentUri(
                        source.authority ?: return null,
                        parentId,
                    )
                }.getOrNull()
        }
    }

    /** "Song.m4a", then "Song (2).m4a" — never silently overwrite a sibling. */
    private fun uniqueName(context: Context, parent: Uri, proposed: String): String {
        val existing = existingNames(context, parent)
        if (proposed.lowercase() !in existing) return proposed

        val base = proposed.substringBeforeLast('.')
        val extension = proposed.substringAfterLast('.', "m4a")
        var index = 2
        while ("$base ($index).$extension".lowercase() in existing) index++
        return "$base ($index).$extension"
    }

    private fun existingNames(context: Context, parent: Uri): Set<String> {
        val parentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrNull()
            ?: return emptySet()
        val children = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        }.getOrNull() ?: return emptySet()
        val names = mutableSetOf<String>()
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.lowercase()?.let(names::add)
                }
            }
        }
        return names
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private class CompressException(
        val failure: CompressFailure,
        message: String? = null,
    ) : Exception(message ?: failure.name)

    private const val TAG = "CompressAudio"

    private val TAG_KEYS = listOf(
        FieldKey.TITLE,
        FieldKey.ARTIST,
        FieldKey.ALBUM,
        FieldKey.ALBUM_ARTIST,
        FieldKey.YEAR,
        FieldKey.TRACK,
        FieldKey.TRACK_TOTAL,
        FieldKey.DISC_NO,
        FieldKey.DISC_TOTAL,
        FieldKey.GENRE,
        FieldKey.COMMENT,
        FieldKey.COMPOSER,
        FieldKey.LYRICIST,
        FieldKey.CONDUCTOR,
        FieldKey.REMIXER,
        FieldKey.GROUPING,
        FieldKey.COPYRIGHT,
        FieldKey.ISRC,
        FieldKey.BPM,
        FieldKey.RATING,
        FieldKey.CUSTOM1,
        FieldKey.CUSTOM2,
        FieldKey.CUSTOM3,
        FieldKey.CUSTOM4,
        FieldKey.LANGUAGE,
    )
}
