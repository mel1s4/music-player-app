package dev.melisa.musicplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val folder: String,
    val year: String,
    /** File modification time, used to seed first-seen stamps on the first scan. */
    val lastModifiedMs: Long = 0L,
    /** File size in bytes, for the library summary. 0 when the provider hides it. */
    val sizeBytes: Long = 0L,
)

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma")

/** Containers that can actually carry an ID3v2 tag; others would just waste an open. */
private val ID3_EXTENSIONS = setOf("mp3", "wav", "aif", "aiff", "ape", "wv")

private const val MAX_PARALLEL_PARSES = 4

/**
 * A dot at the start of a name is the convention for a hidden entry on Android.
 *
 * Syncthing keeps its version history in `.stversions`, which mirrors the library and
 * would otherwise import every song a second time; `.nomedia`, macOS `._*` sidecars and
 * `.trashed-*` files are equally unwanted. The granted library root itself is never
 * checked: picking a dot folder as the library is an explicit request to scan it.
 */
private fun isHiddenName(name: String): Boolean = name.startsWith(".")

private const val TAG = "MusicLibrary"

/**
 * Walks the folders the user granted through the Storage Access Framework.
 *
 * The walk asks the provider for one cursor per directory with the five columns a track
 * needs, instead of the four separate queries `DocumentFile` performs per child. The
 * expensive part — opening every file with a metadata retriever and an ID3 reader — is
 * cached by URI + size + mtime in [TrackMetaCache], so only new or edited files pay it.
 * Cache misses are parsed with a small amount of parallelism.
 *
 * Every read goes through the tree permission taken in AppSettings.addLibrary, so this
 * needs no storage permission in the manifest. Runs on Dispatchers.IO.
 */
object MusicLibrary {

    suspend fun scan(
        context: Context,
        libraries: List<String>,
        cache: TrackMetaCache? = null,
    ): List<Track> = withContext(Dispatchers.IO) {
        if (libraries.isEmpty()) return@withContext emptyList()
        val startedAt = android.os.SystemClock.elapsedRealtime()

        val cached = runCatching { cache?.loadTrackMeta().orEmpty() }.getOrDefault(emptyMap())
        val files = mutableListOf<FileEntry>()
        libraries.forEach { tree ->
            coroutineContext.ensureActive()
            collect(context, Uri.parse(tree), files)
        }

        val parsed = coroutineScope {
            val limit = Semaphore(MAX_PARALLEL_PARSES)
            files.map { file ->
                async {
                    limit.withPermit {
                        coroutineContext.ensureActive()
                        parseEntry(context, file, cached[file.uri.toString()])
                    }
                }
            }.awaitAll()
        }

        val tracks = parsed.mapNotNull { it.track }.sortedBy { it.title.lowercase() }
        val fresh = parsed.mapNotNull { it.cached }
        if (cache != null) {
            if (fresh.isNotEmpty()) {
                runCatching { cache.saveTrackMeta(fresh) }
            }
            // A file removed from the device should not come back as a ghost on the
            // next launch's instant snapshot, so keep the cache equal to this scan.
            runCatching { cache.pruneTrackMeta(tracks.map { it.uri.toString() }) }
        }

        if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
            android.util.Log.d(
                TAG,
                "scan: ${files.size} files, ${fresh.size} parsed, " +
                    "${android.os.SystemClock.elapsedRealtime() - startedAt} ms",
            )
        }

        tracks
    }

    // ------------------------------------------------------------------ walking

    private data class FileEntry(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val modifiedMs: Long,
    )

    private val PROJECTION = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
    )

    private suspend fun collect(context: Context, tree: Uri, out: MutableList<FileEntry>) {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        if (rootId != null) {
            val fast = mutableListOf<FileEntry>()
            try {
                walkTree(context, tree, rootId, fast)
                out += fast
                return
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // A provider that rejects the cursor walk still gets the DocumentFile path.
            }
        }

        val root = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull() ?: return
        collectDocumentFile(context, root, out)
    }

    /** One query per directory: id, name, type, size and mtime for every child at once. */
    private suspend fun walkTree(
        context: Context,
        tree: Uri,
        parentId: String,
        out: MutableList<FileEntry>,
    ) {
        coroutineContext.ensureActive()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = context.contentResolver.query(children, PROJECTION, null, null, null)
            ?: throw IllegalStateException("provider returned no cursor")

        cursor.use { c ->
            val idIndex = c.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            if (idIndex < 0) throw IllegalStateException("no document id column")
            val nameIndex = c.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = c.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val sizeIndex = c.getColumnIndex(Document.COLUMN_SIZE)
            val modifiedIndex = c.getColumnIndex(Document.COLUMN_LAST_MODIFIED)

            while (c.moveToNext()) {
                coroutineContext.ensureActive()
                val documentId = c.getString(idIndex) ?: continue
                val name = (if (nameIndex >= 0) c.getString(nameIndex) else null)
                    ?: documentId.substringAfterLast('/')
                // Hidden directories are skipped before recursing, so a mirror tree
                // such as `.stversions` costs no further queries at all.
                if (isHiddenName(name)) continue
                val mime = if (mimeIndex >= 0) c.getString(mimeIndex) else null
                if (mime == Document.MIME_TYPE_DIR) {
                    walkTree(context, tree, documentId, out)
                    continue
                }

                val extension = name.substringAfterLast('.', "").lowercase()
                if (extension !in AUDIO_EXTENSIONS) continue
                if (mime != null && mime.startsWith("image/")) continue

                val size = if (sizeIndex >= 0 && !c.isNull(sizeIndex)) c.getLong(sizeIndex) else 0L
                val modified =
                    if (modifiedIndex >= 0 && !c.isNull(modifiedIndex)) c.getLong(modifiedIndex) else 0L
                out += FileEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
                    name = name,
                    sizeBytes = size,
                    modifiedMs = modified,
                )
            }
        }
    }

    /** Fallback for providers that do not support the bulk child query. */
    private suspend fun collectDocumentFile(
        context: Context,
        dir: DocumentFile,
        out: MutableList<FileEntry>,
    ) {
        coroutineContext.ensureActive()
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        children.forEach { child ->
            coroutineContext.ensureActive()
            val name = child.name ?: return@forEach
            if (isHiddenName(name)) return@forEach
            if (child.isDirectory) {
                collectDocumentFile(context, child, out)
            } else {
                val extension = name.substringAfterLast('.', "").lowercase()
                if (extension !in AUDIO_EXTENSIONS) return@forEach
                out += FileEntry(
                    uri = child.uri,
                    name = name,
                    sizeBytes = runCatching { child.length() }.getOrDefault(0L),
                    modifiedMs = runCatching { child.lastModified() }.getOrDefault(0L),
                )
            }
        }
    }

    // ------------------------------------------------------------------ parsing

    private data class ParsedEntry(val track: Track?, val cached: CachedTrackMeta?)

    private suspend fun parseEntry(
        context: Context,
        file: FileEntry,
        cached: CachedTrackMeta?,
    ): ParsedEntry {
        val cacheHit = cached != null &&
            file.sizeBytes > 0L &&
            file.modifiedMs > 0L &&
            cached.sizeBytes == file.sizeBytes &&
            cached.modifiedMs == file.modifiedMs

        if (cacheHit) {
            return ParsedEntry(track = file.toTrack(cached), cached = null)
        }

        val track = readTrack(context, file)
        return ParsedEntry(
            track = track,
            cached = CachedTrackMeta(
                uri = file.uri.toString(),
                sizeBytes = file.sizeBytes,
                modifiedMs = file.modifiedMs,
                title = track.title,
                artist = track.artist,
                album = track.album,
                year = track.year,
                durationMs = track.durationMs,
            ),
        )
    }

    private fun FileEntry.toTrack(meta: CachedTrackMeta): Track = Track(
        uri = uri,
        title = meta.title,
        artist = meta.artist,
        album = meta.album,
        durationMs = meta.durationMs,
        folder = songFileInfo(uri).folder,
        year = meta.year,
        lastModifiedMs = modifiedMs,
        sizeBytes = sizeBytes,
    )

    private fun readTrack(context: Context, file: FileEntry): Track {
        var title = file.name.substringBeforeLast('.')
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        var year = ""

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, file.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.let(::repairMojibake)
                ?.takeIf { it.isNotBlank() }
                ?.let { title = it }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.let(::repairMojibake)
                ?.takeIf { it.isNotBlank() }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.let(::repairMojibake)
                ?.takeIf { it.isNotBlank() }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.trim()
                .orEmpty()
        } catch (_: Exception) {
            // Unreadable or unsupported codec: still list it under its file name.
        } finally {
            runCatching { retriever.release() }
        }

        // The ID3 reader knows the frame encoding byte; the platform reader does not.
        // Only containers that can carry ID3v2 are worth the extra open.
        if (file.name.substringAfterLast('.', "").lowercase() in ID3_EXTENSIONS) {
            val id3 = runCatching { readId3(context, file.uri) }.getOrNull()
            id3?.title?.let { title = it }
            id3?.artist?.let { artist = it }
            id3?.album?.let { album = it }
            id3?.year?.takeIf { it.isNotBlank() }?.let { year = it }
        }

        return Track(
            uri = file.uri,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            folder = songFileInfo(file.uri).folder,
            year = year,
            lastModifiedMs = file.modifiedMs,
            sizeBytes = file.sizeBytes,
        )
    }
}
