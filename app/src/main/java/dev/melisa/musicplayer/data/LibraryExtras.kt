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

    fun isFavorite(uri: String): Boolean = favorites.contains(uri)

    fun isFolderStarred(path: String): Boolean = starredFolders.contains(path)

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
     * rescan never reshuffles the "recently added" list.
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
