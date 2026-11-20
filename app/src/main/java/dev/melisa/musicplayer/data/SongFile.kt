package dev.melisa.musicplayer.data

import android.net.Uri
import android.provider.DocumentsContract

/** Where a track's file actually lives, parsed from the SAF document id. */
data class SongFileInfo(val name: String, val folder: String)

/**
 * "primary:Music/SyncMusic/Album/06 - Song.mp3" -> name + "/Music/SyncMusic/Album".
 *
 * Pure string work, so it is cheap enough to call per track while building the tree.
 */
fun songFileInfo(uri: Uri): SongFileInfo {
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        ?: return SongFileInfo(uri.lastPathSegment.orEmpty(), "")

    val volume = documentId.substringBefore(':', "")
    val path = documentId.substringAfter(':', documentId)
    val name = path.substringAfterLast('/')
    val parent = path.substringBeforeLast('/', "")

    val folder = buildString {
        if (volume.isNotEmpty() && volume != "primary") append('/').append(volume)
        if (parent.isNotEmpty()) append('/').append(parent)
        if (isEmpty()) append('/')
    }
    return SongFileInfo(name = name, folder = folder)
}
