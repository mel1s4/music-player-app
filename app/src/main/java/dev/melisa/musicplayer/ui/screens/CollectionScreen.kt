package dev.melisa.musicplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.ReorderableTrackList
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongRow

/**
 * One album or one artist: a header plus the tracks that belong to it.
 *
 * Both pages are the same screen because the only difference is how the tracks were
 * selected — grouping happens in the caller, so there is no duplicated layout.
 */
@Composable
fun CollectionScreen(
    kicker: String,
    title: String,
    tracks: List<Track>,
    currentTrackUri: String?,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onManageTags: (Track) -> Unit,
    onEditMetadata: (Track) -> Unit,
    onCompress: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onEdit: () -> Unit,
    onReorder: ((List<Track>) -> Unit)? = null,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    SectionKicker(text = kicker, accent = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.song_count, tracks.size, tracks.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(
                            if (false) R.string.action_edit_artist else R.string.action_edit_album,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { if (tracks.isNotEmpty()) onPlayAll(tracks, false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.playlist_play_all), maxLines = 1)
                }
                OutlinedButton(
                    onClick = { if (tracks.isNotEmpty()) onPlayAll(tracks, true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_shuffle), maxLines = 1)
                }
            }

            ReorderableTrackList(
                tracks = tracks,
                onOrderChanged = { onReorder?.invoke(it) },
                reorderable = onReorder != null,
                modifier = Modifier.fillMaxWidth(),
            ) { index, track, dragHandle ->
                SongRow(
                    track = track,
                    selected = track.uri.toString() == currentTrackUri,
                    onClick = { onPlay(track, tracks) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onManageTags = { onManageTags(track) },
                    onEditMetadata = { onEditMetadata(track) },
                    onCompress = { onCompress(track) },
                    onLike = { onLike(track) },
                    onDislike = { onDislike(track) },
                    onDelete = { onDelete(track) },
                    trackNumber = index + 1,
                    dragHandle = dragHandle,
                )
            }
        }
    }
}
