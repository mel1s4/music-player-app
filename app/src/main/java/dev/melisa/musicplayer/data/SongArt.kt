package dev.melisa.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The picture a song shows, resolved in the order the library already understands:
 *
 * 1. album cover — `cover.jpg` beside the tracks, then embedded artwork;
 * 2. artist portrait — the image saved from the artist editor;
 * 3. nothing, and the UI falls back to a music glyph.
 *
 * Album covers are shared by folder, so a 20-song album decodes its cover once. The
 * two small LRU caches keep list-sized (192px) and player-sized (640px) copies apart;
 * a miss is remembered too, otherwise art-less tracks would retry on every scroll.
 */
object SongArt {

    private const val ROW_PX = 192
    private const val HERO_PX = 640

    private val rowCache = LruCache<String, ImageBitmap>(96)
    private val heroCache = LruCache<String, ImageBitmap>(10)

    /** Keys already checked with no artwork found. Bounded so it cannot grow forever. */
    private val missing = LruCache<String, Boolean>(256)

    /** One decode at a time: cover I/O is heavy and duplicates are common while scrolling. */
    private val mutex = Mutex()

    /** Bumped when the user edits artwork, so open lists reload instead of showing stale covers. */
    var revision by mutableStateOf(0)
        private set

    fun invalidate() {
        rowCache.evictAll()
        heroCache.evictAll()
        missing.evictAll()
        revision++
    }

    suspend fun load(
        context: Context,
        libraries: List<String>,
        track: Track,
        large: Boolean,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        // Before the user grants a folder there is nothing to look up. Do not cache a
        // miss here: the same call must be retried once libraries arrive.
        if (libraries.isEmpty()) return@withContext null

        val px = if (large) HERO_PX else ROW_PX
        val cache = if (large) heroCache else rowCache
        val albumKey = "album|" + track.folder.lowercase()
        val artistName = track.artist?.trim().orEmpty()
        val artistKey = artistName.takeIf { it.isNotEmpty() }?.let { "artist|" + it.lowercase() }

        cache.get(albumKey)?.let { return@withContext it }
        artistKey?.let { key -> cache.get(key)?.let { return@withContext it } }

        mutex.withLock {
            cache.get(albumKey)?.let { return@withLock it }
            artistKey?.let { key -> cache.get(key)?.let { return@withLock it } }

            if (missing.get(albumKey) == null) {
                val cover = runCatching { AlbumArt.load(context, libraries, track.uri, px) }
                    .getOrNull()
                    ?: embedded(context, track.uri, px)
                if (cover != null) {
                    val image = cover.asImageBitmap()
                    cache.put(albumKey, image)
                    return@withLock image
                }
                missing.put(albumKey, true)
            }

            if (artistKey != null && missing.get(artistKey) == null) {
                val portrait = runCatching { ArtistArt.load(context, artistName, px) }.getOrNull()
                if (portrait != null) {
                    val image = portrait.asImageBitmap()
                    cache.put(artistKey, image)
                    return@withLock image
                }
                missing.put(artistKey, true)
            }

            null
        }
    }

    private fun embedded(context: Context, uri: Uri, maxPx: Int): android.graphics.Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            decodeBytesSampled(bytes, maxPx)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
