package dev.melisa.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** An `artist.jpg` sidecar found in a library folder during a scan. */
data class ArtistImage(
    val uri: Uri,
    val modifiedMs: Long,
    val sizeBytes: Long,
)

/**
 * Artist portraits: the `artist.jpg` beside an artist's folders, imported by the
 * scanner, plus any image the user picked in the editor.
 *
 * Both copies live inside the app. An imported sidecar is copied here because an artist
 * has no single folder of its own on the device — a guest artist can appear in far-flung
 * albums, and the library grant can later be removed. A picked image is kept in a
 * separate file and wins over the imported one.
 */
object ArtistArt {

    /** Largest side of an imported portrait; the player shows artist art at up to 640px. */
    private const val PORTRAIT_PX = 640

    private const val SOURCE_PREFS = "artist-art-sources"

    /** One import at a time: two scans can overlap while one is being cancelled. */
    private val syncMutex = Mutex()

    private fun root(context: Context): File = File(context.filesDir, "artist-art")

    private fun manualFile(context: Context, artist: String): File =
        File(root(context), "${key(artist)}.jpg")

    private fun autoFile(context: Context, artistKey: String): File =
        File(File(root(context), "auto"), "$artistKey.jpg")

    private fun key(artist: String): String =
        artist.trim().lowercase().hashCode().toUInt().toString(16)

    suspend fun save(context: Context, artist: String, bitmap: Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            writeJpeg(manualFile(context, artist), bitmap)
        }

    suspend fun load(context: Context, artist: String, maxPx: Int = 0): Bitmap? =
        withContext(Dispatchers.IO) {
            val manual = manualFile(context, artist)
            if (manual.isFile) {
                decodeFileSampled(manual.absolutePath, maxPx)?.let { return@withContext it }
            }
            val imported = autoFile(context, key(artist))
            if (imported.isFile) {
                decodeFileSampled(imported.absolutePath, maxPx)?.let { return@withContext it }
            }
            null
        }

    /**
     * Brings the imported portraits in line with the sidecars a scan found, keyed by the
     * artist tag each one belongs to.
     *
     * A source is decoded again only when its document, size or modification time
     * changed; a sidecar that disappeared takes its imported copy with it. Returns true
     * when a portrait was added, replaced or removed so the UI can drop cached monograms.
     */
    suspend fun sync(
        context: Context,
        sources: Map<String, ArtistImage>,
    ): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val prefs = context.getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
            val present = mutableSetOf<String>()
            var changed = false

            sources.forEach { (artist, source) ->
                coroutineContext.ensureActive()
                val artistKey = key(artist)
                present += artistKey
                // The stored size is part of the signature so changing PORTRAIT_PX
                // re-imports instead of leaving older, larger copies behind.
                val signature = "${source.uri}|${source.modifiedMs}|${source.sizeBytes}|$PORTRAIT_PX"
                val target = autoFile(context, artistKey)
                if (target.isFile && prefs.getString(artistKey, null) == signature) {
                    return@forEach
                }
                val bitmap = runCatching {
                    context.contentResolver.openInputStream(source.uri)?.use { input ->
                        decodeBytesSampled(input.readBytes(), PORTRAIT_PX)
                            ?.scaledTo(PORTRAIT_PX)
                    }
                }.getOrNull() ?: return@forEach
                if (writeJpeg(target, bitmap)) {
                    prefs.edit().putString(artistKey, signature).apply()
                    changed = true
                }
            }

            prefs.all.keys.filterNot { it in present }.forEach { stale ->
                coroutineContext.ensureActive()
                prefs.edit().remove(stale).apply()
                if (autoFile(context, stale).delete()) changed = true
            }
            changed
        }
    }

    /** Decode sampling only halves; this brings a larger portrait down to the stored size. */
    private fun Bitmap.scaledTo(maxPx: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxPx) return this
        val ratio = maxPx.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== this) recycle()
        return scaled
    }

    /** Writes through a temporary file so a reader never sees a half-written portrait. */
    private fun writeJpeg(target: File, bitmap: Bitmap): Boolean {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        val written = runCatching {
            temp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (temp.renameTo(target)) {
                true
            } else {
                target.delete()
                temp.renameTo(target)
            }
        }.getOrDefault(false)
        if (!written) temp.delete()
        return written
    }
}

/**
 * Album covers go into the album's own folder as `cover.jpg`.
 *
 * That is deliberately *not* ID3 artwork rewriting: a sidecar file is portable (most
 * players read it), needs no audio-file surgery, and cannot corrupt a track.
 */
object AlbumArt {

    private const val COVER_NAME = "cover.jpg"

    suspend fun save(
        context: Context,
        libraries: List<String>,
        trackUri: Uri,
        bitmap: Bitmap,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val documentId = DocumentsContract.getDocumentId(trackUri)
            val parentId = documentId.substringBeforeLast('/')
            val tree = treeFor(libraries, documentId) ?: return@runCatching false
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)

            val existing = findCover(context, parentUri)
            val target = existing ?: DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "image/jpeg",
                COVER_NAME,
            ) ?: return@runCatching false

            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } != null
        }.getOrDefault(false)
    }

    suspend fun load(
        context: Context,
        libraries: List<String>,
        trackUri: Uri,
        maxPx: Int = 0,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val documentId = DocumentsContract.getDocumentId(trackUri)
            val parentId = documentId.substringBeforeLast('/')
            val tree = treeFor(libraries, documentId) ?: return@runCatching null
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            val coverUri = findCover(context, parentUri) ?: return@runCatching null
            val bytes = context.contentResolver.openInputStream(coverUri)?.use { it.readBytes() }
                ?: return@runCatching null
            decodeBytesSampled(bytes, maxPx)
        }.getOrNull()
    }

    /** The tree URI whose document id is a prefix of this file's id. */
    private fun treeFor(libraries: List<String>, documentId: String): Uri? {
        for (library in libraries) {
            val tree = Uri.parse(library)
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            if (treeId != null && documentId.startsWith(treeId)) return tree
        }
        return null
    }

    private fun findCover(context: Context, parentUri: Uri): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(parentUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (name.equals(COVER_NAME, ignoreCase = true)) {
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(0))
                }
            }
        }
        return null
    }
}

/**
 * BitmapFactory helpers shared by the cover loaders.
 *
 * Covers are displayed at 48dp in lists and at a few hundred dp in the player; decoding
 * a 2000px JPEG for every row would blow both memory and scroll performance. Sampling
 * happens at decode time so the full-size bitmap never exists.
 */
internal fun decodeBytesSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
    if (maxPx <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

internal fun decodeFileSampled(path: String, maxPx: Int): Bitmap? {
    if (maxPx <= 0) return BitmapFactory.decodeFile(path)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)
}

private fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxPx || height / (sample * 2) >= maxPx) {
        sample *= 2
    }
    return sample
}
