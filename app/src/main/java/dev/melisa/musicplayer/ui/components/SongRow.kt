package dev.melisa.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.Motion
import dev.melisa.musicplayer.ui.theme.cyberClick

/**
 * One song row, shared by every list that shows tracks (Songs, Files, Search…).
 *
 * The row now carries three pieces of state the old one could not show at a glance:
 * a procedural art tile (identity), a green rail plus equalizer (this is playing),
 * and a duration in mono type (scanning aid). Every target is at least 72dp tall,
 * which is the size a thumb actually needs.
 */
@Composable
fun SongRow(
    track: Track,
    selected: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEdit: (SongEditAction) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onCompress: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    trackNumber: Int? = null,
    dragHandle: Modifier? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selection = LocalSongSelection.current
    val selecting = selection?.active == true
    val bulkSelected = selection?.isSelected(track.uri.toString()) == true

    // Selection mode starts from the kebab menu, with this row as the first pick;
    // tapping rows then toggles membership.
    val onStartBulkSelection: (() -> Unit)? = selection?.let { state ->
        { state.start(track) }
    }

    val background by animateColorAsState(
        targetValue = if (selected || bulkSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(Motion.Medium),
        label = "row-bg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .background(background)
            .padding(
                start = if (trackNumber != null || dragHandle != null) 8.dp else 16.dp,
                end = 6.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null || dragHandle != null) {
            Row(
                modifier = Modifier.width(if (dragHandle != null) 62.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = trackNumber?.toString().orEmpty(),
                    modifier = Modifier.width(18.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .then(dragHandle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = stringResource(R.string.reorder_track),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 58.dp)
                .cyberClick(
                    onClick = {
                        if (selecting) selection?.toggle(track) else onClick()
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(
                track = track,
                side = 48.dp,
                playing = selected && isPlaying,
            )

            Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 10.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected || bulkSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(track.artist, track.album)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selecting) {
            Checkbox(
                checked = bulkSelected,
                onCheckedChange = { selection?.toggle(track) },
                modifier = Modifier.padding(end = 4.dp),
            )
        } else {
            if (selected && isPlaying) {
                EqualizerBars(
                    playing = true,
                    barCount = 3,
                    barWidth = 2.5.dp,
                    height = 14.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp),
                )
            } else {
                Text(
                    text = formatTime(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SongActionMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onAddToPlaylist = onAddToPlaylist,
                    onEdit = onEdit,
                    onLike = onLike,
                    onDislike = onDislike,
                    onCompress = onCompress,
                    onDelete = onDelete,
                    onStartBulkSelection = onStartBulkSelection,
                )
            }
        }
        }
    }
}
