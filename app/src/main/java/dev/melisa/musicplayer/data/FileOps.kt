package dev.melisa.musicplayer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes the actual audio file through SAF.
 *
 * Destructive and irreversible, so the caller confirms first. Returns false when the
 * provider refuses (read-only grant, file already gone).
 */
suspend fun deleteSongFile(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    }.getOrDefault(false)
}
