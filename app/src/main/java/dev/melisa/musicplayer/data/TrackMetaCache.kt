package dev.melisa.musicplayer.data

/**
 * The handful of fields a scan reads out of every file, cached across launches.
 *
 * Walking a large SAF tree is cheap compared with opening every file; the expensive
 * part is two parses per track (platform retriever plus the ID3 reader). A cache hit is
 * only accepted when the file's size and modification time still match, so an edit made
 * in this app or anywhere else invalidates exactly the affected track.
 */
data class CachedTrackMeta(
    val uri: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val year: String,
    val durationMs: Long,
)

/** Storage for [CachedTrackMeta]; implemented by the library database. */
interface TrackMetaCache {
    fun loadTrackMeta(): Map<String, CachedTrackMeta>

    fun saveTrackMeta(entries: Collection<CachedTrackMeta>)

    /**
     * Drops rows for files a completed scan no longer found.
     *
     * Without this the table would grow forever and the next launch would paint
     * deleted files from the snapshot until the scan corrected it.
     */
    fun pruneTrackMeta(keepUris: Collection<String>)
}
