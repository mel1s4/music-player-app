package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Mirrors the app's own metadata into the audio file, best effort.
 *
 * The database is the source of truth; this is a courtesy to other players and to the
 * filesystem: a rating becomes an ID3 POPM (or the format's RATING field), app tags go
 * into CUSTOM1 and the favourite flag into CUSTOM2.
 *
 * Writes are debounced per track, because every write is a copy → tag → copy-back of
 * the whole file. Tapping like three times in a row rewrites the file once.
 */
object SongFileSync {

    private const val DEBOUNCE_MS = 1500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = mutableMapOf<String, Job>()

    fun schedule(
        context: Context,
        uri: Uri,
        rating: Int,
        tags: List<String>,
        favorite: Boolean,
        onWritten: (() -> Unit)? = null,
    ) {
        val key = uri.toString()
        val appContext = context.applicationContext
        synchronized(pending) {
            pending[key]?.cancel()
            pending[key] = scope.launch {
                try {
                    delay(DEBOUNCE_MS)
                    if (write(appContext, uri, rating, tags, favorite)) {
                        onWritten?.invoke()
                    }
                } finally {
                    if (pending[key] === coroutineContext[Job]) {
                        synchronized(pending) { pending.remove(key) }
                    }
                }
            }
        }
    }

    /** Drops queued writes, for when the user turns file syncing off. */
    fun cancelPending() {
        synchronized(pending) {
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }

    /** Drops a queued write for one file whose metadata is about to be written. */
    fun cancelPending(uri: Uri) {
        synchronized(pending) {
            pending.remove(uri.toString())?.cancel()
        }
    }

    private fun write(
        context: Context,
        uri: Uri,
        rating: Int,
        tags: List<String>,
        favorite: Boolean,
    ): Boolean {
        val temp = copyToCache(context, uri) ?: return false
        return try {
            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tagOrCreateAndSetDefault

            // POPM is 0..255; 0 means "unrated" in most players.
            val ratingValue = (rating * 255 / 100).coerceIn(0, 255)
            runCatching { tag.setField(FieldKey.RATING, ratingValue.toString()) }

            val joinedTags = tags.joinToString("; ")
            runCatching {
                if (joinedTags.isBlank()) {
                    tag.deleteField(FieldKey.CUSTOM1)
                } else {
                    tag.setField(FieldKey.CUSTOM1, joinedTags)
                }
            }

            runCatching {
                if (favorite) {
                    tag.setField(FieldKey.CUSTOM2, "1")
                } else {
                    tag.deleteField(FieldKey.CUSTOM2)
                }
            }

            audioFile.commit()

            val output = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            output.use { sink ->
                temp.inputStream().use { input -> input.copyTo(sink) }
            }
            true
        } catch (_: Throwable) {
            // Read-only folder, unsupported container or a transient SAF failure. The
            // database keeps the data and the next edit retries.
            false
        } finally {
            runCatching { temp.delete() }
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..5 }
            ?: "audio"
        val temp = File.createTempFile("sync", ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { input.copyTo(it) }
        } ?: return null
        temp
    }.getOrNull()
}
