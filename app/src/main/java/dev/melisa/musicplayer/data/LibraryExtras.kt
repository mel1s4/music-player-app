package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Everything the user layers on top of the scanned library: favourites, ratings, tags,
 * playlists, first-seen stamps and listening history.
 *
 * Backed by [LibraryStore] (SQLite) instead of a preferences blob, so the lists can grow
 * and are queried by URI. The in-memory Compose state stays the UI's source of truth for
 * rendering; every mutation is written through to the database immediately.
 *
 * Changes that have a file-level equivalent (rating, tags, favourite) are mirrored into
 * the audio file on a debounced background job by [SongFileSync]. Playlists have no
 * per-file representation and stay database-only.
 */
class LibraryExtras(context: Context) {

    private val appContext = context.applicationContext
    private val store = LibraryStore(appContext)

    /** Per-file metadata cache used by the scanner; survives app restarts. */
    val trackMetaCache: TrackMetaCache get() = store

    /**
     * The library as of the last completed scan, for painting the first frame before
     * the launch scan finishes. Empty on a first run or when nothing has been found yet.
     */
    fun cachedTracks(): List<Track> = store.cachedTracks()

    /** Controlled from Settings; when false, no audio file is rewritten. */
    var syncToFiles: Boolean = true

    /**
     * Invoked after a background tag write rewrote a file, with its URI. The player
     * uses it to reopen the current media item instead of reading a stale file range.
     */
    var onFileRewritten: ((String) -> Unit)? = null

    var favorites by mutableStateOf(store.favorites())
        private set

    /** Folder paths pinned in the File Explorer, e.g. "Music/SyncMusic/Albums". */
    var starredFolders by mutableStateOf(store.starredFolders())
        private set

    /** Pinned album and artist names, shown in a starred section above the archive. */
    var starredAlbums by mutableStateOf(store.starredCollections("album"))
        private set
    var starredArtists by mutableStateOf(store.starredCollections("artist"))
        private set

    var tags by mutableStateOf(store.tags())
        private set

    var playlists by mutableStateOf(store.playlists())
        private set

    /** Per-track signed score. Absent means [DEFAULT_RATING] (0). */
    var ratings by mutableStateOf(store.ratings())
        private set

    /**
     * When a track first entered the library, in epoch millis. Seeded from the file's
     * modification time the first time a URI is seen; rescans never move it.
     */
    var addedAt by mutableStateOf(store.addedAt())
        private set

    /**
     * When each track was last heard, in epoch millis. Absent means never played; this
     * is what the "Unheard songs" smart list filters on.
     */
    var listenedAt by mutableStateOf(store.listenedAt())
        private set

    /**
     * Metadata edits waiting for their track to stop playing before the file is
     * rewritten. Persisted, so closing and reopening the app still applies them.
     */
    var pendingMetadata by mutableStateOf(store.pendingMetadata())
        private set

    /** User-defined play order for albums, keyed by normalized album name. */
    var albumOrders by mutableStateOf(store.albumOrders())
        private set

    fun isFavorite(uri: String): Boolean = favorites.contains(uri)

    fun isFolderStarred(path: String): Boolean = starredFolders.contains(path)

    fun isAlbumStarred(name: String): Boolean = starredAlbums.contains(name)

    fun isArtistStarred(name: String): Boolean = starredArtists.contains(name)

    fun toggleAlbumStarred(name: String) {
        val starred = !starredAlbums.contains(name)
        starredAlbums = if (starred) starredAlbums + name else starredAlbums - name
        store.setCollectionStarred("album", name, starred)
    }

    fun toggleArtistStarred(name: String) {
        val starred = !starredArtists.contains(name)
        starredArtists = if (starred) starredArtists + name else starredArtists - name
        store.setCollectionStarred("artist", name, starred)
    }

    /** Pins or unpins a File Explorer folder. The root itself has no path to star. */
    fun toggleFolderStarred(path: String) {
        if (path.isBlank()) return
        val starred = !starredFolders.contains(path)
        starredFolders = if (starred) starredFolders + path else starredFolders - path
        store.setFolderStarred(path, starred)
    }

    fun rating(uri: String): Int = ratings[uri] ?: DEFAULT_RATING

    fun like(uri: String) = bumpRating(uri, STEP)

    fun dislike(uri: String) = bumpRating(uri, -STEP)

    /** Drops the override so the track is back to the neutral default. */
    fun resetRating(uri: String) {
        if (ratings.containsKey(uri)) {
            ratings = ratings - uri
            store.deleteRating(uri)
            syncFile(uri)
        }
    }

    private fun bumpRating(uri: String, delta: Int) {
        // Scores are signed: negative means "disliked" and has no floor. The shuffle
        // treats every non-positive score as the same minimum weight.
        val next = rating(uri) + delta
        ratings = ratings + (uri to next)
        store.setRating(uri, next)
        syncFile(uri)
    }

    fun toggleFavorite(uri: String) {
        val nowFavorite = !favorites.contains(uri)
        favorites = if (nowFavorite) favorites + uri else favorites - uri
        store.setFavorite(uri, nowFavorite)
        syncFile(uri)
    }

    fun tagsFor(uri: String): List<String> = tags[uri].orEmpty()

    fun addTag(uri: String, raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        val existing = tagsFor(uri)
        if (existing.any { it.equals(tag, ignoreCase = true) }) return
        tags = tags + (uri to (existing + tag))
        store.addTag(uri, tag)
        syncFile(uri)
    }

    fun removeTag(uri: String, tag: String) {
        val remaining = tagsFor(uri) - tag
        tags = if (remaining.isEmpty()) tags - uri else tags + (uri to remaining)
        store.removeTag(uri, tag)
        syncFile(uri)
    }

    /**
     * The most-used tags across the library, excluding [exclude] (case-insensitive) and
     * capped at [limit]. Counting is per track and the first spelling wins, so the same
     * list backs the editor's "Frequently used" pills and the quick tags popup.
     */
    fun frequentTags(exclude: Collection<String> = emptyList(), limit: Int = 8): List<String> {
        val skip = exclude.map { it.lowercase() }.toSet()
        val counts = mutableMapOf<String, Int>()
        val labels = mutableMapOf<String, String>()
        for (list in tags.values) {
            for (raw in list) {
                val label = raw.trim()
                if (label.isEmpty()) continue
                val key = label.lowercase()
                counts[key] = (counts[key] ?: 0) + 1
                labels.putIfAbsent(key, label)
            }
        }
        return counts.entries
            .filterNot { skip.contains(it.key) }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { labels[it.key].orEmpty().lowercase() },
            )
            .mapNotNull { labels[it.key] }
            .take(limit)
    }

    /**
     * Drops every app-side trace of a file that has been deleted from the device.
     * Ratings, favourites, tags, first-seen stamps and playlist memberships all point
     * at a URI that can never resolve again, so they are removed now instead of being
     * left for the UI to filter out on every read.
     */
    fun forget(uri: String) {
        if (favorites.contains(uri)) {
            favorites = favorites - uri
            store.setFavorite(uri, false)
        }
        tags[uri]?.let { ownedTags ->
            tags = tags - uri
            ownedTags.forEach { tag -> store.removeTag(uri, tag) }
        }
        if (ratings.containsKey(uri)) {
            ratings = ratings - uri
            store.deleteRating(uri)
        }
        if (addedAt.containsKey(uri)) {
            addedAt = addedAt - uri
            store.deleteAdded(uri)
        }
        if (listenedAt.containsKey(uri)) {
            listenedAt = listenedAt - uri
            store.deleteListen(uri)
        }
        if (pendingMetadata.any { it.uri == uri }) {
            pendingMetadata = pendingMetadata.filterNot { it.uri == uri }
            store.deletePendingMetadata(uri)
        }
        if (albumOrders.isNotEmpty()) {
            val next = albumOrders.mapValues { (_, uris) -> uris.filterNot { it == uri } }
            val changed = next.filter { (key, uris) -> uris != albumOrders[key] }
            if (changed.isNotEmpty()) {
                albumOrders = next.filterValues { it.isNotEmpty() }
                changed.forEach { (key, uris) ->
                    if (uris.isEmpty()) {
                        store.deleteAlbumOrder(key)
                    } else {
                        store.setAlbumOrder(key, uris)
                    }
                }
            }
        }
        val affected = playlists.filter { it.trackUris.contains(uri) }
        if (affected.isNotEmpty()) {
            playlists = playlists.map { playlist ->
                if (playlist.trackUris.contains(uri)) {
                    playlist.copy(trackUris = playlist.trackUris - uri)
                } else {
                    playlist
                }
            }
            affected.forEach { playlist ->
                store.setPlaylistTracks(playlist.id, playlist.trackUris - uri)
            }
        }
    }

    /**
     * Drops the pins on a folder that has been deleted, including pins on folders that
     * lived inside it. Left behind, those paths would silently reappear in Starred the
     * next time a folder with the same name is created.
     */
    fun forgetFolder(path: String) {
        val deleted = path.trim('/')
        if (deleted.isEmpty()) return
        val gone = starredFolders.filter { starred ->
            val normalized = starred.trim('/')
            normalized.equals(deleted, ignoreCase = true) ||
                normalized.startsWith("$deleted/", ignoreCase = true)
        }
        if (gone.isEmpty()) return
        starredFolders = starredFolders - gone.toSet()
        gone.forEach { store.setFolderStarred(it, false) }
    }

    /**
     * A playlist with a non-blank [tagRule] is dynamic: it stores the rule, not the tracks.
     */
    fun createPlaylist(name: String, tagRule: String? = null): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            trackUris = emptyList(),
            tagRule = tagRule?.trim()?.takeIf { it.isNotEmpty() },
        )
        playlists = playlists + playlist
        store.insertPlaylist(playlist)
        return playlist
    }

    fun updatePlaylist(playlistId: String, name: String, tagRule: String?) {
        val rule = tagRule?.trim()?.takeIf { it.isNotEmpty() }
        val updated = playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                // Switching to dynamic drops the stored list; switching back keeps whatever
                // was there, which is why the URIs are left untouched.
                playlist.copy(name = name.trim(), tagRule = rule)
            }
        }
        playlists = updated
        updated.firstOrNull { it.id == playlistId }?.let(store::updatePlaylist)
    }

    fun addToPlaylist(playlistId: String, uri: String) {
        val updated = playlists.map { playlist ->
            if (playlist.id == playlistId && !playlist.trackUris.contains(uri)) {
                playlist.copy(trackUris = playlist.trackUris + uri)
            } else {
                playlist
            }
        }
        playlists = updated
        updated.firstOrNull { it.id == playlistId }?.let {
            store.setPlaylistTracks(playlistId, it.trackUris)
        }
    }

    fun removeFromPlaylist(playlistId: String, uri: String) {
        val updated = playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(trackUris = playlist.trackUris - uri)
            } else {
                playlist
            }
        }
        playlists = updated
        updated.firstOrNull { it.id == playlistId }?.let {
            store.setPlaylistTracks(playlistId, it.trackUris)
        }
    }

    fun deletePlaylist(playlistId: String) {
        playlists = playlists.filterNot { it.id == playlistId }
        store.deletePlaylist(playlistId)
    }

    /**
     * A rename changes the document URI, so everything keyed by URI has to follow it —
     * otherwise favourites, ratings, tags, stamps and playlist memberships would
     * silently disappear.
     */
    fun rekey(oldUri: String, newUri: String) {
        if (oldUri == newUri) return

        favorites = if (favorites.contains(oldUri)) favorites - oldUri + newUri else favorites
        tags = tags[oldUri]?.let { tags - oldUri + (newUri to it) } ?: tags
        addedAt = addedAt[oldUri]?.let { addedAt - oldUri + (newUri to it) } ?: addedAt
        ratings = ratings[oldUri]?.let { ratings - oldUri + (newUri to it) } ?: ratings
        listenedAt = listenedAt[oldUri]?.let { listenedAt - oldUri + (newUri to it) } ?: listenedAt
        pendingMetadata = pendingMetadata.map { pending ->
            if (pending.uri == oldUri) pending.copy(uri = newUri) else pending
        }
        if (albumOrders.isNotEmpty()) {
            val next = albumOrders.mapValues { (_, uris) ->
                uris.map { if (it == oldUri) newUri else it }
            }
            val changed = next.filter { (key, uris) -> uris != albumOrders[key] }
            if (changed.isNotEmpty()) {
                albumOrders = next
                changed.forEach { (key, uris) -> store.setAlbumOrder(key, uris) }
            }
        }
        playlists = playlists.map { playlist ->
            if (playlist.trackUris.contains(oldUri)) {
                playlist.copy(trackUris = playlist.trackUris.map { if (it == oldUri) newUri else it })
            } else {
                playlist
            }
        }
        store.rekey(oldUri, newUri)
    }

    /**
     * Gives a new file the app state of the one it was derived from.
     *
     * Used when a compressed copy is saved next to the original instead of replacing it:
     * the copy is a new URI, but it is the same song to the listener, so it keeps the
     * rating, tags, favourite flag and first-seen stamp. The original keeps its own.
     */
    fun copyState(fromUri: String, toUri: String) {
        if (fromUri == toUri) return

        if (favorites.contains(fromUri)) {
            favorites = favorites + toUri
            store.setFavorite(toUri, true)
        }
        tags[fromUri]?.let { list ->
            tags = tags + (toUri to list)
            list.forEach { tag -> store.addTag(toUri, tag) }
        }
        ratings[fromUri]?.let { score ->
            ratings = ratings + (toUri to score)
            store.setRating(toUri, score)
        }
        addedAt[fromUri]?.let { stamp ->
            addedAt = addedAt + (toUri to stamp)
            store.insertAdded(listOf(toUri to stamp))
        }
        listenedAt[fromUri]?.let { stamp ->
            listenedAt = listenedAt + (toUri to stamp)
            store.copyListen(fromUri, toUri)
        }
    }

    fun addedAt(uri: String): Long? = addedAt[uri]

    /** Album names are matched case-insensitively across the app. */
    fun albumOrderKey(album: String): String = album.trim().lowercase()

    /**
     * Applies a saved album order, appending tracks that were added after the last
     * drag (and anything whose URI changed) in title order.
     */
    fun albumTracksInOrder(album: String, tracks: List<Track>): List<Track> {
        val byUri = tracks.associateBy { it.uri.toString() }
        val ordered = albumOrders[albumOrderKey(album)].orEmpty()
            .mapNotNull { byUri[it] }
        if (ordered.isEmpty()) return tracks.sortedBy { it.title.lowercase() }

        val seen = ordered.mapTo(mutableSetOf()) { it.uri.toString() }
        return ordered + tracks
            .filter { it.uri.toString() !in seen }
            .sortedBy { it.title.lowercase() }
    }

    fun setAlbumOrder(album: String, uris: List<String>) {
        val key = albumOrderKey(album)
        val cleaned = uris.filter { it.isNotBlank() }.distinct()
        albumOrders = if (cleaned.isEmpty()) {
            albumOrders - key
        } else {
            albumOrders + (key to cleaned)
        }
        if (cleaned.isEmpty()) {
            store.deleteAlbumOrder(key)
        } else {
            store.setAlbumOrder(key, cleaned)
        }
    }

    /** Rewrites a manual playlist in the exact order the user dropped the rows. */
    fun reorderPlaylist(playlistId: String, uris: List<String>) {
        val target = playlists.firstOrNull { it.id == playlistId && !it.isDynamic } ?: return
        val allowed = target.trackUris.toSet()
        val reordered = uris.filter { it in allowed }.distinct()
        val trailing = target.trackUris.filterNot { it in reordered }
        val finalOrder = reordered + trailing
        if (finalOrder == target.trackUris) return

        playlists = playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(trackUris = finalOrder)
            } else {
                playlist
            }
        }
        store.setPlaylistTracks(playlistId, finalOrder)
    }

    fun pendingMetadataFor(uri: String): PendingMetadata? =
        pendingMetadata.firstOrNull { it.uri == uri }

    /** Queues an edit that must not touch the file until its track stops playing. */
    fun queueMetadataEdit(uri: String, metadata: SongMetadata, fileName: String) {
        val pending = PendingMetadata(
            uri = uri,
            metadata = metadata,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
        )
        pendingMetadata = pendingMetadata.filterNot { it.uri == uri } + pending
        store.setPendingMetadata(pending)
    }

    /** Drops a queued edit after it was applied, or when its file disappears. */
    fun clearPendingMetadata(uri: String) {
        if (pendingMetadata.none { it.uri == uri }) return
        pendingMetadata = pendingMetadata.filterNot { it.uri == uri }
        store.deletePendingMetadata(uri)
    }

    /**
     * Writes the edited fields into the scan cache under the current URI.
     *
     * The file itself has not been touched yet, so its size and modification time still
     * match; a rescan between the edit and the deferred write will therefore keep the
     * new metadata instead of reverting to the old tags.
     */
    fun cacheEditedMetadata(
        track: Track,
        title: String,
        artist: String?,
        album: String?,
        year: String,
    ) {
        store.saveTrackMeta(
            listOf(
                CachedTrackMeta(
                    uri = track.uri.toString(),
                    sizeBytes = track.sizeBytes,
                    modifiedMs = track.lastModifiedMs,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    durationMs = track.durationMs,
                ),
            ),
        )
    }

    /**
     * Records that [uri] has been heard. The player calls this once a track has actually
     * been playing for a while, not when a row is tapped, so browsing or quick-skipping
     * through a list cannot mark songs as heard.
     */
    fun markListened(uri: String) {
        if (uri.isBlank()) return
        val now = System.currentTimeMillis()
        listenedAt = listenedAt + (uri to now)
        store.markListened(uri, now)
    }

    /**
     * Stamps tracks that are new since the last scan. [entries] pairs each URI with the
     * file's modification time (0 when unknown) — only unknown URIs are touched, so a
     * rescan never reshuffles the date-added order.
     */
    fun registerAdded(entries: List<Pair<String, Long>>) {
        if (entries.isEmpty()) return

        // Refreshes happen on every tag edit and conversion, so do not copy the whole
        // stamp map unless a genuinely new URI shows up.
        val now = System.currentTimeMillis()
        var next: LinkedHashMap<String, Long>? = null
        val fresh = mutableListOf<Pair<String, Long>>()
        entries.forEach { (uri, fileTime) ->
            val current = next ?: addedAt
            if (current.containsKey(uri)) return@forEach

            val target = next ?: LinkedHashMap(addedAt).also { next = it }
            val stamp = if (fileTime > 0L && fileTime <= now) fileTime else now
            target[uri] = stamp
            fresh += uri to stamp
        }
        if (fresh.isNotEmpty()) {
            addedAt = next ?: addedAt
            store.insertAdded(fresh)
        }
    }

    private fun syncFile(uri: String) {
        if (!syncToFiles) return
        SongFileSync.schedule(
            context = appContext,
            uri = Uri.parse(uri),
            rating = rating(uri),
            tags = tagsFor(uri),
            favorite = isFavorite(uri),
            onWritten = { onFileRewritten?.invoke(uri) },
        )
    }

    companion object {
        /** Unrated. Likes push above it, dislikes below it. */
        const val DEFAULT_RATING = 0
        const val STEP = 1
    }
}
