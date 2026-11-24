package dev.melisa.musicplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.BrowseTile
import dev.melisa.musicplayer.ui.components.LibraryHero
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.ScanningState

/**
 * Browse hub, opening with the library summary.
 *
 * "Your library" reports what the folder scan found (songs, storage, hours) and holds
 * the two upkeep actions; below it the four tiles lead to Songs, Files, Albums and
 * Artists. Ratings still feed the weighted shuffle, but they are no longer surfaced as
 * "Recently added" / "Most liked" / "Most disliked" lists here.
 */
@Composable
fun DashboardScreen(
    tracks: List<Track>?,
    isScanning: Boolean,
    onRescan: () -> Unit,
    onFreeSpace: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val albums = remember(tracks) {
        tracks.orEmpty()
            .mapNotNull { it.album?.takeIf { name -> name.isNotBlank() } }
            .distinct()
            .size
    }
    val hours = remember(tracks) { tracks.orEmpty().sumOf { it.durationMs } / 3_600_000.0 }
    val sizeBytes = remember(tracks) { tracks.orEmpty().sumOf { it.sizeBytes } }

    when {
        tracks == null -> ScanningState()
        tracks.isEmpty() -> MessageState(
            title = stringResource(R.string.songs_empty_title),
            body = stringResource(R.string.songs_empty_body),
            icon = Icons.Filled.PlayArrow,
            action = {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.nav_settings))
                }
            },
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "library") {
                LibraryHero(
                    songs = tracks.size,
                    albums = albums,
                    sizeBytes = sizeBytes,
                    hours = hours,
                    isScanning = isScanning,
                    onRescan = onRescan,
                    onFreeSpace = onFreeSpace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item(key = "browse") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrowseTile(
                            icon = Icons.Filled.PlayArrow,
                            label = stringResource(R.string.nav_songs),
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onOpenSongs,
                            modifier = Modifier.weight(1f),
                        )
                        BrowseTile(
                            icon = Icons.Filled.Folder,
                            label = stringResource(R.string.nav_files),
                            accent = MaterialTheme.colorScheme.secondary,
                            onClick = onOpenFiles,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrowseTile(
                            icon = Icons.Filled.Album,
                            label = stringResource(R.string.nav_albums),
                            accent = MaterialTheme.colorScheme.tertiary,
                            onClick = onOpenAlbums,
                            modifier = Modifier.weight(1f),
                        )
                        BrowseTile(
                            icon = Icons.Filled.Person,
                            label = stringResource(R.string.nav_artists),
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = onOpenArtists,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
