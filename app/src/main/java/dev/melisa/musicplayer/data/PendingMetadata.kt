package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * A metadata edit waiting for its track to stop playing before the file is rewritten.
 *
 * Rewriting a file that ExoPlayer is reading from causes audible gaps, so the editor
 * stores the edit here and lets the player pause or move on first. The row is in the
 * database, not just in memory, so closing and reopening the app still applies it.
 */
data class PendingMetadata(
    val uri: String,
    val metadata: SongMetadata,
    /** The full file name the user asked for; empty means "keep the current name". */
    val fileName: String,
    val createdAt: Long,
)

internal fun PendingMetadata.toJson(): String = JSONObject().apply {
    put("title", metadata.title)
    put("artist", metadata.artist)
    put("album", metadata.album)
    put("albumArtist", metadata.albumArtist)
    put("year", metadata.year)
    put("track", metadata.track)
    put("disc", metadata.disc)
    put("genre", metadata.genre)
    put("comment", metadata.comment)
    put("fileName", fileName)
}.toString()

internal fun pendingMetadataFromJson(
    uri: String,
    payload: String,
    createdAt: Long,
): PendingMetadata? = runCatching {
    val json = JSONObject(payload)
    PendingMetadata(
        uri = uri,
        metadata = SongMetadata(
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            albumArtist = json.optString("albumArtist"),
            year = json.optString("year"),
            track = json.optString("track"),
            disc = json.optString("disc"),
            genre = json.optString("genre"),
            comment = json.optString("comment"),
        ),
        fileName = json.optString("fileName"),
        createdAt = createdAt,
    )
}.getOrNull()

enum class MetadataWriteFailure {
    Rename,
    NoWritePermission,
    Write,
}

sealed interface MetadataApplyOutcome {
    data class Success(val oldUri: Uri, val newUri: Uri) : MetadataApplyOutcome
    data class Failed(val failure: MetadataWriteFailure) : MetadataApplyOutcome
}

/**
 * Applies a queued edit to the real file: optional rename first, then the tag write.
 *
 * The caller decides *when* it is safe (track paused, changed, or app restarted); this
 * object only performs the I/O and reports what happened.
 */
object PendingMetadataWriter {

    suspend fun apply(context: Context, pending: PendingMetadata): MetadataApplyOutcome {
        val oldUri = Uri.parse(pending.uri)
        var target = oldUri

        // A queued rating/tag write would otherwise copy the old file and race this one;
        // the database keeps those values, so dropping the queued file write is safe.
        SongFileSync.cancelPending(oldUri)

        if (pending.fileName.isNotBlank() && pending.fileName != songFileInfo(oldUri).name) {
            target = TagEditor.rename(context, oldUri, pending.fileName)
                ?: return MetadataApplyOutcome.Failed(MetadataWriteFailure.Rename)
        }

        return when (val result = TagEditor.write(context, target, pending.metadata)) {
            TagWriteResult.Success -> MetadataApplyOutcome.Success(oldUri, target)
            TagWriteResult.NoWritePermission ->
                MetadataApplyOutcome.Failed(MetadataWriteFailure.NoWritePermission)
            is TagWriteResult.Failed ->
                MetadataApplyOutcome.Failed(MetadataWriteFailure.Write)
        }
    }
}
