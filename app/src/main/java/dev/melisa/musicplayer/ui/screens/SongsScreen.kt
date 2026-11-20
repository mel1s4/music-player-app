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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.ScanningState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongRow
import dev.melisa.musicplayer.ui.theme.cyberClick
import java.text.Normalizer
import kotlinx.coroutines.launch

private enum class TrackSort { Title, Artist, Duration }

/** A run of songs that share an initial, with "#" for anything that is not a letter. */
private data class SongSection(val label: String, val tracks: List<Track>)

private val diacritics = Regex("\\p{Mn}+")

/** Lowercase, accent-free sort key so "Él" sorts with E instead of after Z. */
private fun sortKey(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(diacritics, "").lowercase()

private fun sectionLabelFor(text: String): String {
    val first = sortKey(text).firstOrNull()
    return if (first != null && first.isLetter()) first.uppercaseChar().toString() else "#"
}

@Composable
fun SongsScreen(
    tracks: List<Track>?,
    currentTrackUri: String?,
    isPlaying: Boolean,
    hasLibraries: Boolean,
    onPlay: (Track) -> Unit,
    onShuffleAll: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onManageTags: (Track) -> Unit,
    onEditMetadata: (Track) -> Unit,
    onCompress: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(TrackSort.Title) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }

    val sorted = remember(tracks, sort) {
        when (sort) {
            TrackSort.Title -> tracks.orEmpty().sortedBy { sortKey(it.title) }
            TrackSort.Artist -> tracks.orEmpty().sortedWith(
                compareBy({ sortKey(it.artist.orEmpty()) }, { sortKey(it.title) }),
            )
            TrackSort.Duration -> tracks.orEmpty().sortedBy { it.durationMs }
        }
    }

    // Sections and the letter rail only exist for the alphabetical orders; duration
    // sorting has no alphabet to jump through.
    val sections = remember(sorted, sort) {
        if (sort == TrackSort.Duration) {
            emptyList()
        } else {
            val grouped = linkedMapOf<String, MutableList<Track>>()
            sorted.forEach { track ->
                val label = when (sort) {
                    TrackSort.Title -> sectionLabelFor(track.title)
                    TrackSort.Artist -> sectionLabelFor(track.artist.orEmpty())
                    TrackSort.Duration -> "#"
                }
                grouped.getOrPut(label) { mutableListOf() }.add(track)
            }
            grouped.keys
                .sortedWith(compareBy({ it == "#" }, { it }))
                .map { label -> SongSection(label, grouped.getValue(label)) }
        }
    }
    val letters = sections.map { it.label }

    // Item index of each section header: two fixed header rows come first, then every
    // section contributes its header plus its songs.
    val sectionStart = remember(sections) {
        buildMap {
            var index = 2
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
                item(key = "browse") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrowseTile(
                            icon = Icons.Filled.Folder,
                            label = stringResource(R.string.nav_files),
                            accent = MaterialTheme.colorScheme.secondary,
                            onClick = onOpenFiles,
                            modifier = Modifier.weight(1f),
                        )
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
                        Box {
                            IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort_by),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                TrackSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes())) },
                                        onClick = {
                                            sort = option
                                            sortMenu = false
                                        },
                                        trailingIcon = if (sort == option) {
                                            {
                                                CyberBadge(
                                                    text = "✓",
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (sections.isEmpty()) {
                    items(sorted, key = { it.uri.toString() }) { track ->
                        SongRow(
                            track = track,
                            selected = track.uri.toString() == currentTrackUri,
                            isPlaying = isPlaying,
                            onClick = { onPlay(track) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onManageTags = { onManageTags(track) },
                            onEditMetadata = { onEditMetadata(track) },
                            onCompress = { onCompress(track) },
                            onLike = { onLike(track) },
                            onDislike = { onDislike(track) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    sections.forEach { section ->
                        item(key = "section:${section.label}") {
                            SectionHeader(section.label)
                        }
                        items(section.tracks, key = { it.uri.toString() }) { track ->
                            SongRow(
                                track = track,
                                selected = track.uri.toString() == currentTrackUri,
                                isPlaying = isPlaying,
                                onClick = { onPlay(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onManageTags = { onManageTags(track) },
                                onEditMetadata = { onEditMetadata(track) },
                                onCompress = { onCompress(track) },
                                onLike = { onLike(track) },
                                onDislike = { onDislike(track) },
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

/** Sticky initial separator. The opaque background keeps rows from showing through. */
@Composable
private fun SectionHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
    }
}

/**
 * The letter rail: tap a letter to jump, or drag along it to scrub. Letters are spaced
 * by a fixed height, so the finger position maps cleanly to an index.
 */
@Composable
private fun AlphabetRail(
    letters: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = 24.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
            .padding(vertical = 4.dp)
            // One gesture handler for taps and drags: the finger position is mapped to
            // an index by the same formula, so touching a letter always lands on it.
            .pointerInput(letters, rowHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastIndex = -1
                    fun pickAt(y: Float) {
                        val index = (y / rowHeightPx).toInt().coerceIn(0, letters.lastIndex)
                        if (index != lastIndex) {
                            lastIndex = index
                            onPick(letters[index])
                        }
                    }
                    pickAt(down.position.y)
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull() ?: break
                        pickAt(change.position.y)
                        change.consume()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(rowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (letter == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (letter == current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Big label-plus-icon target; three of these replace the old outlined pills. */
@Composable
private fun BrowseTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeonPanel(
        modifier = modifier
            .heightIn(min = 82.dp)
            .cyberClick(pressedScale = 0.96f, onClick = onClick),
        container = MaterialTheme.colorScheme.surfaceContainer,
        corner = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

private fun TrackSort.labelRes(): Int = when (this) {
    TrackSort.Title -> R.string.sort_title
    TrackSort.Artist -> R.string.field_artist
    TrackSort.Duration -> R.string.sort_duration
}
