package dev.melisa.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.AlphabetRail
import dev.melisa.musicplayer.ui.components.AlphabetSectionHeader
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.ScanningState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongEditAction
import dev.melisa.musicplayer.ui.components.SongRow
import dev.melisa.musicplayer.ui.components.TrackSort
import dev.melisa.musicplayer.ui.components.TrackSortButton
import dev.melisa.musicplayer.ui.components.labelRes
import dev.melisa.musicplayer.ui.components.sectionLabelFor
import dev.melisa.musicplayer.ui.components.sortKey
import java.text.Normalizer
import kotlinx.coroutines.launch

/** A run of songs that share an initial, with "#" for anything that is not a letter. */
private data class SongSection(val label: String, val tracks: List<Track>)

@Composable
fun SongsScreen(
    tracks: List<Track>?,
    addedAt: Map<String, Long>,
    currentTrackUri: String?,
    isPlaying: Boolean,
    hasLibraries: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    onShuffleAll: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditSong: (Track, SongEditAction) -> Unit,
    onCompress: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    onDelete: (Track) -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(TrackSort.DateAdded) }

    val sorted = remember(tracks, sort, addedAt) {
        when (sort) {
            TrackSort.DateAdded -> tracks.orEmpty().sortedWith(
                compareByDescending<Track> { addedAt[it.uri.toString()] ?: 0L }
                    .thenBy { sortKey(it.title) },
            )
            TrackSort.Title -> tracks.orEmpty().sortedBy { sortKey(it.title) }
            TrackSort.Artist -> tracks.orEmpty().sortedWith(
                compareBy({ sortKey(it.artist.orEmpty()) }, { sortKey(it.title) }),
            )
            TrackSort.Duration -> tracks.orEmpty().sortedBy { it.durationMs }
        }
    }

    // Sections and the letter rail only exist for the alphabetical orders; duration
    // and date sorting have no alphabet to jump through.
    val sections = remember(sorted, sort) {
        if (sort == TrackSort.Duration || sort == TrackSort.DateAdded) {
            emptyList()
        } else {
            val grouped = linkedMapOf<String, MutableList<Track>>()
            sorted.forEach { track ->
                val label = when (sort) {
                    TrackSort.Title -> sectionLabelFor(track.title)
                    TrackSort.Artist -> sectionLabelFor(track.artist.orEmpty())
                    TrackSort.Duration, TrackSort.DateAdded -> "#"
                }
                grouped.getOrPut(label) { mutableListOf() }.add(track)
            }
            grouped.keys
                .sortedWith(compareBy({ it == "#" }, { it }))
                .map { label -> SongSection(label, grouped.getValue(label)) }
        }
    }
    val letters = sections.map { it.label }

    // The rows in exactly the order they appear on screen: section by section for the
    // alphabetical sorts, the plain sorted list for date / duration. Tapping a song
    // queues this list, so the next song played is the row directly below that song.
    val displayOrder = remember(sorted, sections) {
        if (sections.isEmpty()) sorted else sections.flatMap { it.tracks }
    }

    // Item index of each section header: the sort/shuffle controls row comes first,
    // then every section contributes its header plus its songs.
    val sectionStart = remember(sections) {
        buildMap {
            var index = 1
            sections.forEach { section ->
                put(section.label, index)
                index += 1 + section.tracks.size
            }
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentLetter by remember(sectionStart) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            sectionStart.entries.lastOrNull { it.value <= first }?.key
        }
    }
    val showIndex = letters.size > 2

    when {
        !hasLibraries -> MessageState(
            title = stringResource(R.string.songs_no_library_title),
            body = stringResource(R.string.songs_no_library_body),
        )

        tracks == null -> ScanningState()

        tracks.isEmpty() -> MessageState(
            title = stringResource(R.string.songs_empty_title),
            body = stringResource(R.string.songs_empty_body),
        )

        else -> Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                // Leave a gutter for the letter rail so it never covers the row menus.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (showIndex) 24.dp else 0.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                item(key = "controls") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionKicker(text = stringResource(sort.labelRes()))
                        Spacer(Modifier.width(8.dp))
                        CyberBadge(text = sorted.size.toString())
                        Spacer(Modifier.weight(1f))
                        if (sorted.isNotEmpty()) {
                            TextButton(
                                onClick = onShuffleAll,
                                modifier = Modifier.heightIn(min = 44.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_shuffle), maxLines = 1)
                            }
                        }
                        TrackSortButton(
                            sort = sort,
                            onSort = { sort = it },
                        )
                    }
                }

                if (sections.isEmpty()) {
                    items(sorted, key = { it.uri.toString() }) { track ->
                        SongRow(
                            track = track,
                            selected = track.uri.toString() == currentTrackUri,
                            isPlaying = isPlaying,
                            onClick = { onPlay(track, displayOrder) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onEdit = { action -> onEditSong(track, action) },
                            onCompress = { onCompress(track) },
                            onLike = { onLike(track) },
                            onDislike = { onDislike(track) },
                            onDelete = { onDelete(track) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    sections.forEach { section ->
                        item(key = "section:${section.label}") {
                            AlphabetSectionHeader(section.label)
                        }
                        items(section.tracks, key = { it.uri.toString() }) { track ->
                            SongRow(
                                track = track,
                                selected = track.uri.toString() == currentTrackUri,
                                isPlaying = isPlaying,
                                onClick = { onPlay(track, displayOrder) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onEdit = { action -> onEditSong(track, action) },
                                onCompress = { onCompress(track) },
                                onLike = { onLike(track) },
                                onDislike = { onDislike(track) },
                                onDelete = { onDelete(track) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            if (showIndex) {
                AlphabetRail(
                    letters = letters,
                    current = currentLetter,
                    onPick = { letter ->
                        sectionStart[letter]?.let { header ->
                            scope.launch { listState.scrollToItem(header) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                )
            }
        }
    }
}
