package dev.melisa.musicplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File

data class SongMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val track: String = "",
    val disc: String = "",
    val genre: String = "",
    val comment: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(title, artist, album, albumArtist, year, track, disc, genre, comment)
            .all { it.isBlank() }
}

sealed interface TagWriteResult {
    data object Success : TagWriteResult
    data object NoWritePermission : TagWriteResult
    data class Failed(val message: String) : TagWriteResult
}

/**
 * Reads and writes real tag frames (ID3v2 for MP3, Vorbis comments for FLAC/OGG,
 * MP4 atoms for M4A) through jaudiotagger.
 *
 * Content URIs are not files, so every edit is copy -> modify -> write back. The copy
 * is only written over the original after the new tag has been serialised successfully,
 * which keeps a failed edit from half-rewriting the file.
 */
object TagEditor {

    /**
     * Renames through SAF and returns the *new* URI — document URIs are derived from the
     * path, so a rename invalidates the old one. The extension is preserved because
     * stripping it would leave Android unable to guess the media type.
     */
    suspend fun rename(context: Context, uri: Uri, rawName: String): Uri? =
        withContext(Dispatchers.IO) {
            val originalName = songFileInfo(uri).name
            val extension = originalName.substringAfterLast('.', "")

            // SAF display names may not contain separators or control characters, and
            // "." / ".." would try to address a directory rather than a file name.
            var name = rawName.trim()
                .map { ch -> if (ch == '/' || ch == '\\' || ch.isISOControl()) '_' else ch }
                .joinToString("")
                .trim()
            if (name.isBlank() || name == "." || name == "..") return@withContext null
            if (extension.isNotEmpty() && !name.endsWith(".$extension", ignoreCase = true)) {
                name = "$name.$extension"
            }

            runCatching {
                DocumentsContract.renameDocument(context.contentResolver, uri, name)
            }.getOrNull()
        }

    suspend fun read(context: Context, uri: Uri): SongMetadata? = withContext(Dispatchers.IO) {
        val temp = copyToCache(context, uri) ?: return@withContext null
        try {
            val tag = AudioFileIO.read(temp).tagOrCreateAndSetDefault
            val id3 = readId3(context, uri)
            SongMetadata(
                title = id3?.title ?: repairMojibake(tag.first(FieldKey.TITLE)),
                artist = id3?.artist ?: repairMojibake(tag.first(FieldKey.ARTIST)),
                album = id3?.album ?: repairMojibake(tag.first(FieldKey.ALBUM)),
                albumArtist = id3?.albumArtist ?: repairMojibake(tag.first(FieldKey.ALBUM_ARTIST)),
                year = id3?.year ?: tag.first(FieldKey.YEAR),
                track = id3?.track ?: tag.first(FieldKey.TRACK),
                disc = id3?.disc ?: tag.first(FieldKey.DISC_NO),
                genre = id3?.genre ?: repairMojibake(tag.first(FieldKey.GENRE)),
                comment = id3?.comment ?: repairMojibake(tag.first(FieldKey.COMMENT)),
            )
        } catch (_: Throwable) {
            null
        } finally {
            temp.delete()
        }
    }

    suspend fun write(
        context: Context,
        uri: Uri,
        metadata: SongMetadata,
    ): TagWriteResult = withContext(Dispatchers.IO) {
        val temp = copyToCache(context, uri)
            ?: return@withContext TagWriteResult.Failed("copy")

        try {
            val audioFile = AudioFileIO.read(temp)
            val tag = audioFile.tagOrCreateAndSetDefault

            apply(tag, FieldKey.TITLE, metadata.title)
            apply(tag, FieldKey.ARTIST, metadata.artist)
            apply(tag, FieldKey.ALBUM, metadata.album)
            apply(tag, FieldKey.ALBUM_ARTIST, metadata.albumArtist)
            apply(tag, FieldKey.YEAR, metadata.year)
            apply(tag, FieldKey.TRACK, metadata.track)
            apply(tag, FieldKey.DISC_NO, metadata.disc)
            apply(tag, FieldKey.GENRE, metadata.genre)
            apply(tag, FieldKey.COMMENT, metadata.comment)

            audioFile.commit()

            // Only now touch the original file.
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext TagWriteResult.Failed("open")
            output.use { sink ->
                temp.inputStream().use { it.copyTo(sink) }
            }
            TagWriteResult.Success
        } catch (_: SecurityException) {
            TagWriteResult.NoWritePermission
        } catch (t: Throwable) {
            TagWriteResult.Failed(t.message ?: t::class.java.simpleName)
        } finally {
            temp.delete()
        }
    }

    private fun Tag.first(key: FieldKey): String =
        runCatching { getFirst(key) }.getOrNull().orEmpty()

    private fun apply(tag: Tag, key: FieldKey, value: String) {
        val trimmed = value.trim()
        runCatching {
            if (trimmed.isEmpty()) tag.deleteField(key) else tag.setField(key, trimmed)
        }
    }

    private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.length in 1..5 }
            ?: "audio"
        val temp = File.createTempFile("tag", ".$extension", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { input.copyTo(it) }
        } ?: return null
        temp
    }.getOrNull()
}
