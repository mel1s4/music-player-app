package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.Normalizer

/** A file that was moved, with the URI the rest of the app should use from now on. */
data class MovedTrack(val oldUri: Uri, val newUri: Uri)

enum class ReorderError {
    NoTracks,
    FolderNotWritable,
}

data class ReorderOutcome(
    val moved: List<MovedTrack> = emptyList(),
    val alreadySorted: Int = 0,
    val missingArtist: Int = 0,
    val failed: Int = 0,
    val error: ReorderError? = null,
)

enum class FolderOpError {
    NotFound,
    CrossLibrary,
    InvalidDestination,
    MoveFailed,
    DeleteFailed,
}

data class FolderMoveOutcome(
    val movedTracks: List<MovedTrack> = emptyList(),
    val movedFolder: Boolean = false,
    val error: FolderOpError? = null,
)

data class FolderExplodeOutcome(
    val movedTracks: List<MovedTrack> = emptyList(),
    val movedChildren: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val deleted: Boolean = false,
    val error: FolderOpError? = null,
)

data class FolderDeleteOutcome(
    val deleted: Boolean = false,
    val error: FolderOpError? = null,
)

/** One granted library tree: its root document, visible path and directory cache. */
private class LibraryContext(
    val root: Uri,
    val rootPath: String,
    val directories: MutableMap<String, Uri?> = mutableMapOf(),
)

/**
 * Files songs at the **root of their Music Library** as `artist/album/song` (or
 * `artist/song` when the track has no album).
 *
 * The selected folder is only the source scope. Reordering a nested `Downloads` folder
 * moves its songs into the library's existing artist/album tree instead of creating a
 * second tree inside `Downloads`. If the library root itself ends with the artist or
 * `artist/album`, that prefix is not repeated.
 *
 * The metadata is the scan's own [Track] data, so this never reopens the audio files.
 * Files without an artist are left alone, and files already inside the expected folder
 * are skipped. Names are kept as they are: this is a folder reorganisation, not a
 * rename pass, so the user's chosen file names survive.
 *
 * Every successful move returns both URIs. The caller must re-key the app database and
 * the playback queue with them; a SAF move changes the document URI, which would
 * otherwise orphan ratings, tags, favourites, history and playlist entries.
 */
object FolderReorder {

    suspend fun reorder(
        context: Context,
        tracks: List<Track>,
        onMoved: (MovedTrack) -> Unit = {},
        onProgress: (completed: Int, total: Int, title: String) -> Unit = { _, _, _ -> },
    ): ReorderOutcome = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) {
            return@withContext ReorderOutcome(error = ReorderError.NoTracks)
        }

        val ordered = tracks.sortedBy { it.title.lowercase() }
        val libraries = mutableMapOf<String, LibraryContext?>()
        val moved = mutableListOf<MovedTrack>()
        var alreadySorted = 0
        var missingArtist = 0
        var failed = 0

        ordered.forEachIndexed { index, track ->
            onProgress(index, ordered.size, track.title)

            val treeId = runCatching { DocumentsContract.getTreeDocumentId(track.uri) }
                .getOrNull()
            if (treeId == null) {
                failed++
                return@forEachIndexed
            }
            val library = libraries.getOrPut(treeId) {
                resolveLibrary(context, track, treeId)
            }
            if (library == null) {
                failed++
                return@forEachIndexed
            }

            val artist = sanitizeName(track.artist.orEmpty())
            if (artist.isEmpty()) {
                missingArtist++
                return@forEachIndexed
            }
            val album = sanitizeName(track.album.orEmpty())
            val targetSegments = relativeTargetSegments(
                library.rootPath,
                listOfNotNull(artist, album.takeIf { it.isNotEmpty() }),
            )

            val targetFolder = (listOf(library.rootPath.trim('/')) + targetSegments)
                .filter { it.isNotEmpty() }
                .joinToString("/")
            if (samePath(track.folder, targetFolder)) {
                alreadySorted++
                return@forEachIndexed
            }

            val targetDirectory = ensureDirectory(
                context,
                library.root,
                targetSegments,
                library.directories,
            )
            if (targetDirectory == null) {
                failed++
                return@forEachIndexed
            }

            val newUri = moveTrack(context, track.uri, targetDirectory)
            when {
                newUri == null -> failed++
                // A provider may report a successful move to the same place; that is
                // already the desired state, not a duplicate entry in the media queue.
                newUri == track.uri -> alreadySorted++
                else -> {
                    val result = MovedTrack(track.uri, newUri)
                    moved += result
                    // Report immediately: a move that already happened must be re-keyed
                    // even if the batch is interrupted before its last track.
                    onMoved(result)
                }
            }
        }

        onProgress(ordered.size, ordered.size, "")
        if (failed == ordered.size) {
            return@withContext ReorderOutcome(
                failed = failed,
                error = ReorderError.FolderNotWritable,
            )
        }
        ReorderOutcome(
            moved = moved,
            alreadySorted = alreadySorted,
            missingArtist = missingArtist,
            failed = failed,
        )
    }

    /**
     * Moves a whole folder below another folder in the same library tree.
     *
     * SAF exposes this as `moveDocument`; the provider keeps the folder and its contents
     * together. The returned mappings let the caller re-key every song inside it.
     */
    suspend fun moveFolder(
        context: Context,
        folderPath: String,
        destinationPath: String,
        folderTracks: List<Track>,
        destinationTracks: List<Track>,
    ): FolderMoveOutcome = withContext(Dispatchers.IO) {
        val source = folderPath.trim('/')
        val destination = destinationPath.trim('/')
        if (source.isEmpty() || destination.isEmpty()) {
            return@withContext FolderMoveOutcome(error = FolderOpError.NotFound)
        }
        if (samePath(source, destination) || isInside(destination, source)) {
            return@withContext FolderMoveOutcome(error = FolderOpError.InvalidDestination)
        }
        if (samePath(destination, source.substringBeforeLast('/', ""))) {
            return@withContext FolderMoveOutcome(error = FolderOpError.InvalidDestination)
        }

        val sourceTrack = folderTracks.firstOrNull()
            ?: return@withContext FolderMoveOutcome(error = FolderOpError.NotFound)
        val destinationTrack = destinationTracks.firstOrNull()
            ?: return@withContext FolderMoveOutcome(error = FolderOpError.NotFound)
        val sourceTree = treeId(sourceTrack.uri)
        val destinationTree = treeId(destinationTrack.uri)
        if (sourceTree == null || destinationTree == null || sourceTree != destinationTree) {
            return@withContext FolderMoveOutcome(error = FolderOpError.CrossLibrary)
        }

        val sourceUri = folderUriFor(sourceTrack, source)
            ?: return@withContext FolderMoveOutcome(error = FolderOpError.NotFound)
        val destinationUri = folderUriFor(destinationTrack, destination)
            ?: return@withContext FolderMoveOutcome(error = FolderOpError.NotFound)
        if (!isDirectory(context, sourceUri) || !isDirectory(context, destinationUri)) {
            return@withContext FolderMoveOutcome(error = FolderOpError.NotFound)
        }
        val sourceParent = parentDocumentUri(sourceUri)
            ?: return@withContext FolderMoveOutcome(error = FolderOpError.InvalidDestination)
        if (destinationUri == sourceParent) {
            return@withContext FolderMoveOutcome(error = FolderOpError.InvalidDestination)
        }

        val moved = runCatching {
            DocumentsContract.moveDocument(
                context.contentResolver,
                sourceUri,
                sourceParent,
                destinationUri,
            )
        }.getOrNull()
        if (moved == null || moved == sourceUri) {
            return@withContext FolderMoveOutcome(error = FolderOpError.MoveFailed)
        }

        val movedFolderId = runCatching { DocumentsContract.getDocumentId(moved) }
            .getOrNull() ?: return@withContext FolderMoveOutcome(error = FolderOpError.MoveFailed)
        val movedTracks = folderTracks.mapNotNull { track ->
            val relative = relativePathBelow(track, source) ?: return@mapNotNull null
            val newUri = runCatching {
                DocumentsContract.buildDocumentUriUsingTree(moved, "$movedFolderId/$relative")
            }.getOrNull() ?: return@mapNotNull null
            MovedTrack(track.uri, newUri)
        }

        FolderMoveOutcome(movedTracks = movedTracks, movedFolder = true)
    }

    /**
     * Moves every child of a folder into its parent, skipping names that already exist
     * there. The folder itself is deleted only when nothing was left behind.
     */
    suspend fun explodeFolder(
        context: Context,
        folderPath: String,
        folderTracks: List<Track>,
    ): FolderExplodeOutcome = withContext(Dispatchers.IO) {
        val source = folderPath.trim('/')
        if (source.isEmpty()) {
            return@withContext FolderExplodeOutcome(error = FolderOpError.NotFound)
        }
        val sourceTrack = folderTracks.firstOrNull()
            ?: return@withContext FolderExplodeOutcome(error = FolderOpError.NotFound)
        val sourceUri = folderUriFor(sourceTrack, source)
            ?: return@withContext FolderExplodeOutcome(error = FolderOpError.NotFound)
        val parentUri = parentDocumentUri(sourceUri)
            ?: return@withContext FolderExplodeOutcome(error = FolderOpError.InvalidDestination)
        val sourceChildren = children(context, sourceUri)
            ?: return@withContext FolderExplodeOutcome(error = FolderOpError.MoveFailed)
        val parentChildren = children(context, parentUri)
            ?: return@withContext FolderExplodeOutcome(error = FolderOpError.MoveFailed)

        val movedChildren = linkedMapOf<String, Uri>()
        var skipped = 0
        var failed = 0
        val parentNames = parentChildren.mapTo(mutableSetOf()) { normalizedPath(it.name).lowercase() }

        sourceChildren.forEach { child ->
            if (normalizedPath(child.name).lowercase() in parentNames) {
                skipped++
                return@forEach
            }
            val moved = if (child.isDirectory) {
                runCatching {
                    DocumentsContract.moveDocument(
                        context.contentResolver,
                        child.uri,
                        sourceUri,
                        parentUri,
                    )
                }.getOrNull()
            } else {
                moveTrack(context, child.uri, parentUri)
            }
            if (moved == null || moved == child.uri) {
                failed++
            } else {
                movedChildren[child.name] = moved
                parentNames += normalizedPath(child.name).lowercase()
            }
        }

        val movedTracks = folderTracks.mapNotNull { track ->
            val relative = relativePathBelow(track, source) ?: return@mapNotNull null
            val parts = relative.split('/')
            val childName = parts.firstOrNull() ?: return@mapNotNull null
            val newChild = movedChildren[childName] ?: return@mapNotNull null
            if (parts.size == 1) {
                MovedTrack(track.uri, newChild)
            } else {
                val childId = runCatching { DocumentsContract.getDocumentId(newChild) }
                    .getOrNull() ?: return@mapNotNull null
                val rest = parts.drop(1).joinToString("/")
                val newUri = runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(newChild, "$childId/$rest")
                }.getOrNull() ?: return@mapNotNull null
                MovedTrack(track.uri, newUri)
            }
        }

        val remaining = children(context, sourceUri)
        val deleted = remaining != null && remaining.isEmpty() && runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
        }.getOrDefault(false)

        FolderExplodeOutcome(
            movedTracks = movedTracks,
            movedChildren = movedChildren.size,
            skipped = skipped,
            failed = failed,
            deleted = deleted,
        )
    }

    /**
     * Deletes a folder and everything in it from the device.
     *
     * SAF defines `deleteDocument` on a directory as a recursive delete, so this one
     * call removes the folder, its subfolders and every song inside them. The folder's
     * URI is derived from a song it contains, just like move and explode: the Files
     * screen only builds folders from scanned songs, so a folder with no songs is not
     * addressable from there either.
     */
    suspend fun deleteFolder(
        context: Context,
        folderPath: String,
        folderTracks: List<Track>,
    ): FolderDeleteOutcome = withContext(Dispatchers.IO) {
        val source = folderPath.trim('/')
        if (source.isEmpty()) {
            return@withContext FolderDeleteOutcome(error = FolderOpError.NotFound)
        }
        val sourceTrack = folderTracks.firstOrNull()
            ?: return@withContext FolderDeleteOutcome(error = FolderOpError.NotFound)
        val sourceUri = folderUriFor(sourceTrack, source)
            ?: return@withContext FolderDeleteOutcome(error = FolderOpError.NotFound)
        if (!isDirectory(context, sourceUri)) {
            return@withContext FolderDeleteOutcome(error = FolderOpError.NotFound)
        }

        // Deleting is recursive by contract, so there is nothing else to remove here.
        // A provider may still refuse (read-only tree, missing document), and the
        // caller turns that refusal into a message instead of a silent no-op.
        val deleted = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
        }.getOrDefault(false)
        if (!deleted) {
            return@withContext FolderDeleteOutcome(error = FolderOpError.DeleteFailed)
        }
        FolderDeleteOutcome(deleted = true)
    }

    /**
     * The granted tree root for a track's file. A folder can be nested arbitrarily deep
     * inside the library; the tree document id always points at the library root, which
     * is exactly where reordered songs belong.
     */
    private fun resolveLibrary(context: Context, track: Track, treeId: String): LibraryContext? {
        val root = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(track.uri, treeId)
        }.getOrNull() ?: return null
        if (!isDirectory(context, root)) return null
        return LibraryContext(
            root = root,
            rootPath = folderPathForDocumentId(treeId),
        )
    }

    /**
     * "primary:Music/SyncMusic" -> "/Music/SyncMusic". The Files screen and [Track]
     * store folder paths the same way, so the two can be compared directly.
     */
    private fun folderPathForDocumentId(documentId: String): String {
        val volume = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':', documentId)
        return buildString {
            if (volume.isNotEmpty() && volume != "primary") append('/').append(volume)
            if (path.isNotEmpty()) append('/').append(path)
            if (isEmpty()) append('/')
        }
    }

    /**
     * Drops the part of `artist/album` that the library root already ends with.
     *
     * A library rooted at `Music` gets `artist/album` folders. A library that itself
     * points at an artist folder must not create `Artist/Artist` inside it. The
     * comparison is case-insensitive, like the "already in place" check.
     */
    private fun relativeTargetSegments(selectedPath: String, target: List<String>): List<String> {
        val selected = normalizedPath(selectedPath).split('/').filter { it.isNotBlank() }
        for (overlap in minOf(selected.size, target.size) downTo 1) {
            val matches = selected.takeLast(overlap)
                .zip(target.take(overlap))
                .all { (existing, wanted) -> sameName(existing, wanted) }
            if (matches) return target.drop(overlap)
        }
        return target
    }

    private fun ensureDirectory(
        context: Context,
        root: Uri,
        segments: List<String>,
        cache: MutableMap<String, Uri?>,
    ): Uri? {
        var current = root
        var key = ""
        segments.forEach { segment ->
            key = if (key.isEmpty()) segment else "$key/$segment"
            if (cache.containsKey(key)) {
                val cached = cache[key] ?: return null
                current = cached
                return@forEach
            }
            val next = findOrCreateDirectory(context, current, segment)
            cache[key] = next
            if (next == null) return null
            current = next
        }
        return current
    }

    private fun findOrCreateDirectory(context: Context, parent: Uri, name: String): Uri? {
        // Do not create a directory when the provider cannot list the current ones:
        // that would silently create a duplicate every time the folder is reordered.
        val siblings = children(context, parent) ?: return null
        val existing = siblings.firstOrNull { sameName(it.name, name) }
        if (existing != null) return existing.uri.takeIf { existing.isDirectory }

        val created = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                Document.MIME_TYPE_DIR,
                name,
            )
        }.getOrNull() ?: return null

        if (!isDirectory(context, created)) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
            return null
        }
        return created
    }

    private fun moveTrack(context: Context, source: Uri, targetParent: Uri): Uri? {
        val sourceParent = parentDocumentUri(source) ?: return null
        if (sourceParent == targetParent) return source

        // A duplicate name in the target must not make a provider overwrite a sibling.
        // Copying under a unique name is predictable; moving is left to the clean case.
        val nameTaken = children(context, targetParent)
            ?.any { sameName(it.name, songFileInfo(source).name) } == true
        if (nameTaken) return copyThenDelete(context, source, targetParent)

        // Prefer a provider-side move: it is atomic and does not rewrite the bytes.
        // Only some providers implement it, so a copy-and-delete is the fallback.
        val moved = runCatching {
            DocumentsContract.moveDocument(
                context.contentResolver,
                source,
                sourceParent,
                targetParent,
            )
        }.getOrNull()
        if (moved != null && moved != source) return moved

        return copyThenDelete(context, source, targetParent)
    }

    /**
     * Fallback for providers that cannot move a document. The source is deleted only
     * after the copy has the same size; if that fails, the new copy is removed and the
     * original stays exactly where it was.
     */
    private fun copyThenDelete(context: Context, source: Uri, targetParent: Uri): Uri? {
        val resolver = context.contentResolver
        val proposed = songFileInfo(source).name.ifBlank { "audio" }
        val target = runCatching {
            DocumentsContract.createDocument(
                resolver,
                targetParent,
                mimeType(context, source),
                uniqueName(context, targetParent, proposed),
            )
        }.getOrNull() ?: return null

        val sourceSize = sizeOf(context, source)
        try {
            val input = resolver.openInputStream(source) ?: throw IOException("open source")
            input.use { src ->
                val output = resolver.openOutputStream(target, "w") ?: throw IOException("open target")
                output.use { dst ->
                    src.copyTo(dst)
                    dst.flush()
                }
            }
            val targetSize = sizeOf(context, target)
            if (sourceSize > 0L && targetSize != sourceSize) {
                throw IOException("size mismatch")
            }
            val deleted = runCatching { DocumentsContract.deleteDocument(resolver, source) }
                .getOrDefault(false)
            if (!deleted) throw IOException("delete source")
            return target
        } catch (_: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            return null
        }
    }

    /**
     * A folder's own URI, derived from a track inside it.
     *
     * The same construction as the old selected-folder resolution: remove the path
     * between the track and the selected folder from the track's document id.
     */
    private fun folderUriFor(track: Track, folderPath: String): Uri? {
        val folder = track.folder.trim('/')
        val selected = folderPath.trim('/')
        if (selected.isEmpty()) return null

        val below = when {
            folder.equals(selected, ignoreCase = true) -> ""
            folder.length > selected.length &&
                folder.regionMatches(0, selected, 0, selected.length, ignoreCase = true) &&
                folder[selected.length] == '/' -> folder.substring(selected.length + 1)
            else -> return null
        }
        val depth = below.split('/').count { it.isNotBlank() }

        val documentId = runCatching { DocumentsContract.getDocumentId(track.uri) }.getOrNull()
            ?: return null
        val volume = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':', documentId)
        val parts = path.split('/').filter { it.isNotEmpty() }
        if (parts.size <= depth + 1) return null
        val folderId = parts.dropLast(depth + 1).joinToString("/")
        if (folderId.isEmpty()) return null

        val id = if (volume.isNotEmpty()) "$volume:$folderId" else folderId
        return runCatching {
            DocumentsContract.buildDocumentUriUsingTree(track.uri, id)
        }.getOrNull()
    }

    /** The track's path below [folderPath], including its file name. */
    private fun relativePathBelow(track: Track, folderPath: String): String? {
        val folder = track.folder.trim('/')
        val selected = folderPath.trim('/')
        val relativeFolder = when {
            folder.equals(selected, ignoreCase = true) -> ""
            folder.length > selected.length &&
                folder.regionMatches(0, selected, 0, selected.length, ignoreCase = true) &&
                folder[selected.length] == '/' -> folder.substring(selected.length + 1)
            else -> return null
        }
        val name = songFileInfo(track.uri).name
        return if (relativeFolder.isEmpty()) name else "$relativeFolder/$name"
    }

    private fun treeId(uri: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

    private fun isInside(path: String, folder: String): Boolean {
        val candidate = normalizedPath(path)
        val base = normalizedPath(folder)
        return candidate.equals(base, ignoreCase = true) ||
            candidate.startsWith("$base/", ignoreCase = true)
    }

    private fun parentDocumentUri(uri: Uri): Uri? {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        val volume = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':', documentId)
        val parentPath = path.substringBeforeLast('/', "")
        if (parentPath.isEmpty()) return null
        val parentId = if (volume.isNotEmpty()) "$volume:$parentPath" else parentPath
        return runCatching {
            DocumentsContract.buildDocumentUriUsingTree(uri, parentId)
        }.getOrNull()
    }

    private data class Child(val uri: Uri, val name: String, val isDirectory: Boolean)

    /** Null when the provider cannot list children, so callers do not create duplicates. */
    private fun children(context: Context, parent: Uri): List<Child>? {
        val parentId = runCatching { DocumentsContract.getDocumentId(parent) }.getOrNull()
            ?: return null
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        }.getOrNull() ?: return null

        return runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        val name = cursor.getString(1).orEmpty()
                        val isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR
                        val uri = runCatching {
                            DocumentsContract.buildDocumentUriUsingTree(parent, id)
                        }.getOrNull() ?: continue
                        add(Child(uri, name, isDirectory))
                    }
                }
            }
        }.getOrNull()
    }

    private fun isDirectory(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(Document.COLUMN_MIME_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == Document.MIME_TYPE_DIR
        } ?: false
    }.getOrDefault(false)

    private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(Document.COLUMN_SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun mimeType(context: Context, uri: Uri): String =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: "application/octet-stream"

    private fun uniqueName(context: Context, parent: Uri, proposed: String): String {
        val siblings = children(context, parent) ?: return proposed
        val existing = siblings.mapTo(mutableSetOf()) { it.name.lowercase() }
        if (proposed.lowercase() !in existing) return proposed

        val base = proposed.substringBeforeLast('.', proposed)
        val extension = proposed.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val candidate = if (extension.isNotEmpty()) "$base ($index).$extension" else "$base ($index)"
            if (candidate.lowercase() !in existing) return candidate
            index++
        }
    }

    /**
     * SAF display names may not contain separators or control characters. "." and ".."
     * would address a directory instead of a child, so they are rejected too.
     */
    private fun sanitizeName(raw: String): String = normalizedPath(
        raw.trim()
            .map { ch -> if (ch == '/' || ch == '\\' || ch.isISOControl()) '_' else ch }
            .joinToString("")
            .trim(),
    ).takeUnless { it.isEmpty() || it == "." || it == ".." }.orEmpty()

    /** Folders seen as the same path when they differ only in case or Unicode form. */
    private fun samePath(a: String, b: String): Boolean =
        normalizedPath(a).equals(normalizedPath(b), ignoreCase = true)

    private fun sameName(a: String, b: String): Boolean =
        normalizedPath(a).equals(normalizedPath(b), ignoreCase = true)

    private fun normalizedPath(value: String): String =
        Normalizer.normalize(value.trim('/'), Normalizer.Form.NFC)
}
