package dev.melisa.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.CompressAudio
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.formatBytes
import dev.melisa.musicplayer.ui.theme.cyberClick

/**
 * The batch version of the compress action: a size-sorted view of the library.
 *
 * This is the screen that answers "what is eating my storage?" without making the user
 * open files one by one. It only lists tracks a lower bitrate can actually shrink.
 */
@Composable
fun FreeUpSpaceScreen(
    tracks: List<Track>,
    onBack: () -> Unit,
    onCompress: (List<Track>) -> Unit,
) {
    val candidates = remember(tracks) {
        tracks
            .filter { it.sizeBytes > 0L && it.durationMs > 0L }
            .filter { CompressAudio.estimatedSourceKbps(it.sizeBytes, it.durationMs) > 120 }
            .sortedByDescending { it.sizeBytes }
    }

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // A rescan can remove a URI (the original was replaced); drop those quietly so the
    // selection count never includes a file that no longer exists.
    LaunchedEffect(candidates) {
        val live = candidates.map { it.uri.toString() }.toSet()
        if (selected.any { it !in live }) selected = selected.intersect(live)
    }
    val selectedTracks = candidates.filter { it.uri.toString() in selected }
    val selectedBytes = selectedTracks.sumOf { it.sizeBytes }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
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
                    Text(
                        text = stringResource(R.string.free_space_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.free_space_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (candidates.isEmpty()) {
                MessageState(
                    title = stringResource(R.string.free_space_empty_title),
                    body = stringResource(R.string.free_space_empty_body),
                )
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatBytes(candidates.sumOf { it.sizeBytes }) + " · " +
                        pluralStringResource(
                            R.plurals.song_count,
                            candidates.size,
                            candidates.size,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        selected = if (selected.size == candidates.size) {
                            emptySet()
                        } else {
                            candidates.map { it.uri.toString() }.toSet()
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (selected.size == candidates.size && candidates.isNotEmpty()) {
                                R.string.free_space_clear
                            } else {
                                R.string.free_space_select_all
                            },
                        ),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(candidates, key = { it.uri.toString() }) { track ->
                    val uri = track.uri.toString()
                    val checked = uri in selected
                    CompressCandidateRow(
                        track = track,
                        checked = checked,
                        onToggle = {
                            selected = if (checked) selected - uri else selected + uri
                        },
                    )
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedTracks.isEmpty()) {
                                    stringResource(R.string.free_space_none)
                                } else {
                                    stringResource(R.string.free_space_selected, selectedTracks.size)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (selectedTracks.isNotEmpty()) {
                                Text(
                                    text = formatBytes(selectedBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onCompress(selectedTracks) },
                            enabled = selectedTracks.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.free_space_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressCandidateRow(
    track: Track,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val sourceKbps = CompressAudio.estimatedSourceKbps(track.sizeBytes, track.durationMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .cyberClick(pressedScale = 0.99f, onClick = onToggle)
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    track.artist?.takeIf { it.isNotBlank() },
                    "$sourceKbps kbps",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = formatBytes(track.sizeBytes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
