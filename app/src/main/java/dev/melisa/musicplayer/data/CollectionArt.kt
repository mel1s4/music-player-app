package dev.melisa.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Artist portraits are stored inside the app: an artist has no folder of its own on the
 * device, so there is nothing portable to write them next to.
 */
object ArtistArt {

    private fun file(context: Context, artist: String): File {
        val dir = File(context.filesDir, "artist-art").apply { mkdirs() }
        val key = artist.trim().lowercase().hashCode().toUInt().toString(16)
        return File(dir, "$key.jpg")
    }

    suspend fun save(context: Context, artist: String, bitmap: Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                file(context, artist).outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }.isSuccess
        }

    suspend fun load(context: Context, artist: String, maxPx: Int = 0): Bitmap? =
        withContext(Dispatchers.IO) {
            val target = file(context, artist)
            if (!target.exists()) return@withContext null
            decodeFileSampled(target.absolutePath, maxPx)
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
