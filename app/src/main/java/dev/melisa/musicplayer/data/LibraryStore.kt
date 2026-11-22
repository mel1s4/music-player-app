package dev.melisa.musicplayer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * The app's own database: favourites, ratings, tags, playlists, first-seen stamps,
 * listening history and the pending metadata queue.
 *
 * It lives in SQLite rather than SharedPreferences because these are relational lists
 * that grow with the library and are queried/filtered constantly (tags by URI, tracks
 * per playlist). The public API mirrors [LibraryExtras] so the UI never talks SQL.
 *
 * Existing installs are migrated once from the old `extras` preferences blob; the old
 * file is left untouched as a backup.
 */
class LibraryStore(context: Context) : TrackMetaCache {

    private val appContext = context.applicationContext
    private val helper = Helper(appContext)

    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ---------------------------------------------------------------- reads

    fun favorites(): Set<String> = buildSet {
        db.query("favorites", arrayOf("uri"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) add(c.getString(0))
        }
    }

    fun ratings(): Map<String, Int> = buildMap {
        db.query("ratings", arrayOf("uri", "score"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) put(c.getString(0), c.getInt(1))
        }
    }

    fun tags(): Map<String, List<String>> {
        val out = linkedMapOf<String, MutableList<String>>()
        db.query("tags", arrayOf("uri", "tag"), null, null, null, null, "uri, tag").use { c ->
            while (c.moveToNext()) {
                out.getOrPut(c.getString(0)) { mutableListOf() }.add(c.getString(1))
            }
        }
        return out
    }

    fun addedAt(): Map<String, Long> = buildMap {
        db.query("tracks", arrayOf("uri", "added_at"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) put(c.getString(0), c.getLong(1))
        }
    }

    /**
     * When each track was last heard, in epoch millis. A URI with no row has never been
     * played, which is exactly what the "Unheard songs" smart list asks for.
     */
    fun listenedAt(): Map<String, Long> = buildMap {
        db.query("listens", arrayOf("uri", "last_played"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) put(c.getString(0), c.getLong(1))
        }
    }

    /** Edits saved while their track was playing, oldest first. */
    fun pendingMetadata(): List<PendingMetadata> = buildList {
        db.query(
            "pending_meta",
            arrayOf("uri", "payload", "created_at"),
            null,
            null,
            null,
            null,
            "created_at",
        ).use { c ->
            while (c.moveToNext()) {
                val uri = c.getString(0) ?: continue
                val payload = c.getString(1) ?: continue
                pendingMetadataFromJson(uri, payload, c.getLong(2))?.let(::add)
            }
        }
    }

    /** Folder paths the user pinned in the File Explorer; independent of the track URIs. */
    fun starredFolders(): Set<String> = buildSet {
        db.query("starred_folders", arrayOf("path"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) add(c.getString(0))
        }
    }

    override fun loadTrackMeta(): Map<String, CachedTrackMeta> = buildMap {
        db.query(
            "track_meta",
            arrayOf("uri", "size", "modified", "title", "artist", "album", "year", "duration"),
            null,
            null,
            null,
            null,
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val uri = c.getString(0) ?: continue
                put(
                    uri,
                    CachedTrackMeta(
                        uri = uri,
                        sizeBytes = c.getLong(1),
                        modifiedMs = c.getLong(2),
                        title = c.getString(3).orEmpty(),
                        artist = c.getString(4),
                        album = c.getString(5),
                        year = c.getString(6).orEmpty(),
                        durationMs = c.getLong(7),
                    ),
                )
            }
        }
    }

    override fun saveTrackMeta(entries: Collection<CachedTrackMeta>) {
        if (entries.isEmpty()) return
        db.beginTransaction()
        try {
            entries.forEach { entry ->
                db.insertWithOnConflict(
                    "track_meta",
                    null,
                    ContentValues().apply {
                        put("uri", entry.uri)
                        put("size", entry.sizeBytes)
                        put("modified", entry.modifiedMs)
                        put("title", entry.title)
                        put("artist", entry.artist)
                        put("album", entry.album)
                        put("year", entry.year)
                        put("duration", entry.durationMs)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Deletes cache rows for files the scan did not see.
     *
     * A temp table keeps the statement inside SQLite's bound-variable limit even for
     * a library with thousands of tracks, and it does not need to rewrite the rows a
     * scan did see (the common case for a refresh).
     */
    override fun pruneTrackMeta(keepUris: Collection<String>) {
        db.beginTransaction()
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS scan_keep (uri TEXT PRIMARY KEY NOT NULL)")
            db.execSQL("DELETE FROM scan_keep")
            val insert = db.compileStatement("INSERT OR IGNORE INTO scan_keep (uri) VALUES (?)")
            try {
                keepUris.forEach { uri ->
                    insert.clearBindings()
                    insert.bindString(1, uri)
                    insert.executeInsert()
                }
            } finally {
                insert.close()
            }
            db.execSQL("DELETE FROM track_meta WHERE uri NOT IN (SELECT uri FROM scan_keep)")
            db.execSQL("DROP TABLE scan_keep")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * The last completed scan, rebuilt from [track_meta].
     *
     * Read synchronously at launch so the first frame can paint the library while a
     * fresh scan runs behind it. Fields the cache does not store (the folder) are
     * derived from the URI, which is pure string work.
     */
    fun cachedTracks(): List<Track> = buildList {
        db.query(
            "track_meta",
            arrayOf("uri", "title", "artist", "album", "year", "duration", "size", "modified"),
            null,
            null,
            null,
            null,
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(0) ?: continue
                val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
                add(
                    Track(
                        uri = uri,
                        title = c.getString(1).orEmpty(),
                        artist = c.getString(2),
                        album = c.getString(3),
                        durationMs = c.getLong(5),
                        folder = songFileInfo(uri).folder,
                        year = c.getString(4).orEmpty(),
                        lastModifiedMs = c.getLong(7),
                        sizeBytes = c.getLong(6),
                    ),
                )
            }
        }
    }.sortedBy { it.title.lowercase() }

    fun playlists(): List<Playlist> {
        val tracksByList = linkedMapOf<String, MutableList<String>>()
        db.query(
            "playlist_tracks",
            arrayOf("playlist_id", "uri"),
            null,
            null,
            null,
            null,
            "playlist_id, position",
        ).use { c ->
            while (c.moveToNext()) {
                tracksByList.getOrPut(c.getString(0)) { mutableListOf() }.add(c.getString(1))
            }
        }

        val out = mutableListOf<Playlist>()
        db.query("playlists", arrayOf("id", "name", "tag_rule"), null, null, null, null, "rowid").use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                out += Playlist(
                    id = id,
                    name = c.getString(1),
                    trackUris = tracksByList[id].orEmpty(),
                    tagRule = c.getString(2),
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------- writes

    fun setFavorite(uri: String, value: Boolean) {
        if (value) {
            db.insertWithOnConflict(
                "favorites",
                null,
                ContentValues().apply { put("uri", uri) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } else {
            db.delete("favorites", "uri = ?", arrayOf(uri))
        }
    }

    fun setFolderStarred(path: String, starred: Boolean) {
        if (starred) {
            db.insertWithOnConflict(
                "starred_folders",
                null,
                ContentValues().apply { put("path", path) },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } else {
            db.delete("starred_folders", "path = ?", arrayOf(path))
        }
    }

    fun setRating(uri: String, score: Int) {
        db.insertWithOnConflict(
            "ratings",
            null,
            ContentValues().apply {
                put("uri", uri)
                put("score", score)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deleteRating(uri: String) {
        db.delete("ratings", "uri = ?", arrayOf(uri))
    }

    fun addTag(uri: String, tag: String) {
        db.insertWithOnConflict(
            "tags",
            null,
            ContentValues().apply {
                put("uri", uri)
                put("tag", tag)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun removeTag(uri: String, tag: String) {
        db.delete("tags", "uri = ? AND tag = ?", arrayOf(uri, tag))
    }

    /** Inserts a first-seen stamp only when the URI is new; existing stamps are stable. */
    fun insertAdded(entries: List<Pair<String, Long>>) {
        if (entries.isEmpty()) return
        db.beginTransaction()
        try {
            entries.forEach { (uri, stamp) ->
                db.insertWithOnConflict(
                    "tracks",
                    null,
                    ContentValues().apply {
                        put("uri", uri)
                        put("added_at", stamp)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Forgets the first-seen stamp of a file that no longer exists. */
    fun deleteAdded(uri: String) {
        db.delete("tracks", "uri = ?", arrayOf(uri))
    }

    /**
     * Records one play. The counter is incremented in SQL rather than read-modify-written
     * in Kotlin, so a play recorded while the UI is rebuilding cannot be lost.
     *
     * UPSERT syntax needs SQLite 3.24 (Android 9), while the app supports Android 8, so
     * the increment is expressed as an INSERT ... SELECT with a fallback of 1.
     */
    fun markListened(uri: String, at: Long) {
        db.execSQL(
            "INSERT OR REPLACE INTO listens (uri, plays, last_played) " +
                "SELECT ?, COALESCE((SELECT plays + 1 FROM listens WHERE uri = ?), 1), ?",
            arrayOf<Any>(uri, uri, at),
        )
    }

    /** Forgets the listening history of a file that no longer exists. */
    fun deleteListen(uri: String) {
        db.delete("listens", "uri = ?", arrayOf(uri))
    }

    /** Carries a play count over to a copy of the same song (compression, keep both). */
    fun copyListen(fromUri: String, toUri: String) {
        db.execSQL(
            "INSERT OR IGNORE INTO listens (uri, plays, last_played) " +
                "SELECT ?, plays, last_played FROM listens WHERE uri = ?",
            arrayOf(toUri, fromUri),
        )
    }

    fun setPendingMetadata(pending: PendingMetadata) {
        db.insertWithOnConflict(
            "pending_meta",
            null,
            ContentValues().apply {
                put("uri", pending.uri)
                put("payload", pending.toJson())
                put("created_at", pending.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun deletePendingMetadata(uri: String) {
        db.delete("pending_meta", "uri = ?", arrayOf(uri))
    }

    // ------------------------------------------------------------- playlists

    fun insertPlaylist(playlist: Playlist) {
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "playlists",
                null,
                ContentValues().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("tag_rule", playlist.tagRule)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            writePlaylistTracks(playlist.id, playlist.trackUris)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        db.update(
            "playlists",
            ContentValues().apply {
                put("name", playlist.name)
                put("tag_rule", playlist.tagRule)
            },
            "id = ?",
            arrayOf(playlist.id),
        )
    }

    fun setPlaylistTracks(playlistId: String, uris: List<String>) {
        db.beginTransaction()
        try {
            writePlaylistTracks(playlistId, uris)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deletePlaylist(playlistId: String) {
        db.beginTransaction()
        try {
            db.delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId))
            db.delete("playlists", "id = ?", arrayOf(playlistId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun writePlaylistTracks(playlistId: String, uris: List<String>) {
        db.delete("playlist_tracks", "playlist_id = ?", arrayOf(playlistId))
        uris.forEachIndexed { index, uri ->
            db.insertWithOnConflict(
                "playlist_tracks",
                null,
                ContentValues().apply {
                    put("playlist_id", playlistId)
                    put("uri", uri)
                    put("position", index)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    // --------------------------------------------------------------- renames

    /**
     * A SAF rename changes the document URI. Every table keyed by URI follows it in one
     * transaction, which is also why ratings now survive a rename (they used to be lost).
     */
    fun rekey(oldUri: String, newUri: String) {
        if (oldUri == newUri) return
        val tables = listOf(
            "favorites",
            "ratings",
            "tags",
            "playlist_tracks",
            "tracks",
            "listens",
            "pending_meta",
        )
        db.beginTransaction()
        try {
            tables.forEach { table ->
                db.updateWithOnConflict(
                    table,
                    ContentValues().apply { put("uri", newUri) },
                    "uri = ?",
                    arrayOf(oldUri),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --------------------------------------------------------------- schema

    private class Helper(private val appContext: Context) :
        SQLiteOpenHelper(appContext, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE favorites (uri TEXT PRIMARY KEY NOT NULL)")
            db.execSQL("CREATE TABLE ratings (uri TEXT PRIMARY KEY NOT NULL, score INTEGER NOT NULL)")
            db.execSQL(
                "CREATE TABLE tags (uri TEXT NOT NULL, tag TEXT NOT NULL, " +
                    "PRIMARY KEY (uri, tag))",
            )
            db.execSQL(
                "CREATE TABLE playlists (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                    "tag_rule TEXT)",
            )
            db.execSQL(
                "CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, uri TEXT NOT NULL, " +
                    "position INTEGER NOT NULL, PRIMARY KEY (playlist_id, uri))",
            )
            db.execSQL("CREATE TABLE tracks (uri TEXT PRIMARY KEY NOT NULL, added_at INTEGER NOT NULL)")
            createTrackMeta(db)
            createStarredFolders(db)
            createListens(db)
            createPendingMetadata(db)
            db.execSQL("CREATE INDEX tags_uri ON tags(uri)")
            db.execSQL("CREATE INDEX playlist_tracks_list ON playlist_tracks(playlist_id)")
            migrateFromPreferences(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                // Ratings used to be 0..100 around a neutral 48, in steps of 2. Rebase
                // them onto the signed scale (0 neutral, steps of 1) so an old "liked
                // twice" (+4) becomes +2 instead of turning into a runaway score.
                db.execSQL("UPDATE ratings SET score = (score - 48) / 2")
            }
            if (oldVersion < 3) {
                createTrackMeta(db)
            }
            if (oldVersion < 4) {
                createStarredFolders(db)
            }
            if (oldVersion < 5) {
                createListens(db)
            }
            if (oldVersion < 6) {
                createPendingMetadata(db)
            }
        }

        private fun createPendingMetadata(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_meta (" +
                    "uri TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL)",
            )
        }

        private fun createListens(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS listens (" +
                    "uri TEXT PRIMARY KEY NOT NULL, plays INTEGER NOT NULL, " +
                    "last_played INTEGER NOT NULL)",
            )
        }

        private fun createStarredFolders(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS starred_folders (path TEXT PRIMARY KEY NOT NULL)",
            )
        }

        private fun createTrackMeta(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS track_meta (" +
                    "uri TEXT PRIMARY KEY NOT NULL, size INTEGER NOT NULL, " +
                    "modified INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT, album TEXT, " +
                    "year TEXT NOT NULL, duration INTEGER NOT NULL)",
            )
        }

        /** One-time import of the pre-database SharedPreferences blob. */
        private fun migrateFromPreferences(db: SQLiteDatabase) {
            val prefs = appContext.getSharedPreferences("extras", Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) return

            db.beginTransaction()
            try {
                prefs.getString("favorites", null)
                    ?.split("\n")
                    ?.filter { it.isNotBlank() }
                    ?.forEach { uri ->
                        db.insertWithOnConflict(
                            "favorites",
                            null,
                            ContentValues().apply { put("uri", uri) },
                            SQLiteDatabase.CONFLICT_IGNORE,
                        )
                    }

                prefs.getString("ratings", null)?.let { raw ->
                    runCatching {
                        val json = JSONObject(raw)
                        json.keys().forEach { uri ->
                            db.insertWithOnConflict(
                                "ratings",
                                null,
                                ContentValues().apply {
                                    put("uri", uri)
                                    put("score", (json.getInt(uri) - 48) / 2)
                                },
                                SQLiteDatabase.CONFLICT_IGNORE,
                            )
                        }
                    }
                }

                prefs.getString("tags", null)?.let { raw ->
                    runCatching {
                        val json = JSONObject(raw)
                        json.keys().forEach { uri ->
                            val list = json.getJSONArray(uri)
                            List(list.length()) { list.getString(it) }.forEach { tag ->
                                db.insertWithOnConflict(
                                    "tags",
                                    null,
                                    ContentValues().apply {
                                        put("uri", uri)
                                        put("tag", tag)
                                    },
                                    SQLiteDatabase.CONFLICT_IGNORE,
                                )
                            }
                        }
                    }
                }

                prefs.getString("added_at", null)?.let { raw ->
                    runCatching {
                        val json = JSONObject(raw)
                        json.keys().forEach { uri ->
                            db.insertWithOnConflict(
                                "tracks",
                                null,
                                ContentValues().apply {
                                    put("uri", uri)
                                    put("added_at", json.getLong(uri))
                                },
                                SQLiteDatabase.CONFLICT_IGNORE,
                            )
                        }
                    }
                }

                prefs.getString("playlists", null)?.let { raw ->
                    runCatching {
                        val array = JSONArray(raw)
                        for (index in 0 until array.length()) {
                            val obj = array.getJSONObject(index)
                            val id = obj.getString("id")
                            db.insertWithOnConflict(
                                "playlists",
                                null,
                                ContentValues().apply {
                                    put("id", id)
                                    put("name", obj.getString("name"))
                                    put("tag_rule", obj.optString("tag", "").takeIf { it.isNotBlank() })
                                },
                                SQLiteDatabase.CONFLICT_IGNORE,
                            )
                            val uris = obj.optJSONArray("uris") ?: JSONArray()
                            for (i in 0 until uris.length()) {
                                db.insertWithOnConflict(
                                    "playlist_tracks",
                                    null,
                                    ContentValues().apply {
                                        put("playlist_id", id)
                                        put("uri", uris.getString(i))
                                        put("position", i)
                                    },
                                    SQLiteDatabase.CONFLICT_IGNORE,
                                )
                            }
                        }
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private companion object {
        const val DB_NAME = "library.db"
        const val DB_VERSION = 6
    }
}
