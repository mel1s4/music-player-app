package dev.melisa.musicplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.LibraryExtras
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.data.deleteSongFile
import dev.melisa.musicplayer.ui.components.BrowseTile
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.CyberDivider
import dev.melisa.musicplayer.ui.components.EqualizerBars
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.ScanningState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongCover
import dev.melisa.musicplayer.ui.components.RatingIcon
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.Motion
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlinx.coroutines.launch

/**
 * Browse hub plus the rating lists that the weighted shuffle reads from.
 *
 * The four tiles lead to Songs, Files, Albums and Artists; the library summary itself
 * lives at the top of Songs now. "Most liked" and "Most disliked" are just the tails of
 * the same score distribution: everyone starts at [LibraryExtras.DEFAULT_RATING], so
 * anything above or below that is a deliberate like/dislike.
 */
@Composable
fun DashboardScreen(
    tracks: List<Track>?,
    extras: LibraryExtras,
    nowPlaying: Track?,
    isPlaying: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    onCompress: (List<Track>) -> Unit,
    onOpenSongs: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenSettings: () -> Unit,
    onFilesDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bulkMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val rated = tracks.orEmpty()
    val default = LibraryExtras.DEFAULT_RATING
    val score: (Track) -> Int = { extras.rating(it.uri.toString()) }

    val liked = rated.filter { score(it) > default }.sortedByDescending(score).take(5)
    val disliked = rated.filter { score(it) < default }.sortedBy(score).take(10)

    // Most recent first, by the moment each track first entered the library.
    val recent = rated
        .mapNotNull { track -> extras.addedAt(track.uri.toString())?.let { stamp -> stamp to track } }
        .sortedByDescending { it.first }
        .take(5)
        .map { it.second }

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
            item {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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

            if (recent.isNotEmpty()) {
                item {
                    SectionRow(
                        title = stringResource(R.string.recently_added),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
                item {
                    NeonPanel(corner = 20.dp) {
                        Column {
                            recent.forEachIndexed { index, track ->
                                RecentRow(
                                    track = track,
                                    addedAt = extras.addedAt(track.uri.toString()) ?: 0L,
                                    playing = isPlaying && nowPlaying?.uri == track.uri,
                                    // Play the rest of "Recently added", in this order.
                                    onClick = { onPlay(track, recent) },
                                )
                                if (index != recent.lastIndex) {
                                    CyberDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionRow(
                    title = stringResource(R.string.ratings_most_liked),
                    accent = MaterialTheme.colorScheme.primary,
                    badge = liked.size.toString(),
                )
            }
            item {
                if (liked.isEmpty()) {
                    EmptyLine(R.string.ratings_none)
                } else {
                    NeonPanel(corner = 20.dp) {
                        Column {
                            liked.forEachIndexed { index, track ->
                                RatingRow(
                                    rank = index + 1,
                                    track = track,
                                    score = score(track),
                                    accent = MaterialTheme.colorScheme.primary,
                                    onClick = { onPlay(track, liked) },
                                )
                                if (index != liked.lastIndex) {
                                    CyberDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionRow(
                        title = stringResource(R.string.ratings_most_disliked),
                        accent = MaterialTheme.colorScheme.error,
                        badge = disliked.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    if (disliked.isNotEmpty()) {
                        TextButton(onClick = {
                            bulkMode = !bulkMode
                            if (!bulkMode) selected = emptySet()
                        }) {
                            Icon(
                                imageVector = if (bulkMode) Icons.Filled.Close else Icons.Filled.Checklist,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (bulkMode) R.string.action_cancel else R.string.ratings_select,
                                ),
                            )
                        }
                    }
                }
            }

            if (disliked.isEmpty()) {
                item { EmptyLine(R.string.ratings_none) }
            } else {
                item {
                    NeonPanel(corner = 20.dp) {
                        Column {
                            disliked.forEachIndexed { index, track ->
                                RatingRow(
                                    rank = index + 1,
                                    track = track,
                                    score = score(track),
                                    accent = MaterialTheme.colorScheme.error,
                                    selectable = bulkMode,
                                    checked = selected.contains(track.uri.toString()),
                                    onCheckedChange = { isChecked ->
                                        val id = track.uri.toString()
                                        selected = if (isChecked) selected + id else selected - id
                                    },
                                    onClick = { onPlay(track, disliked) },
                                )
                                if (index != disliked.lastIndex) {
                                    CyberDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                                }
                            }
                        }
                    }
                }

                if (bulkMode) {
                    item {
                        NeonPanel(
                            container = MaterialTheme.colorScheme.surfaceContainerHigh,
                            corner = 16.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = {
                                    selected = if (selected.size == disliked.size) {
                                        emptySet()
                                    } else {
                                        disliked.map { it.uri.toString() }.toSet()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.DoneAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.ratings_select_all), maxLines = 1)
                                }

                                Spacer(Modifier.weight(1f))

                                IconButton(
                                    enabled = selected.isNotEmpty(),
                                    onClick = {
                                        selected.forEach { extras.resetRating(it) }
                                        selected = emptySet()
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RestartAlt,
                                        contentDescription = stringResource(R.string.ratings_reset),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                IconButton(
                                    enabled = selected.isNotEmpty(),
                                    onClick = {
                                        val selection = disliked.filter {
                                            it.uri.toString() in selected
                                        }
                                        if (selection.isNotEmpty()) {
                                            onCompress(selection)
                                            bulkMode = false
                                            selected = emptySet()
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Compress,
                                        contentDescription = stringResource(R.string.action_compress),
                                        tint = MaterialTheme.colorScheme.secondary,
                                    )
                                }

                                IconButton(
                                    enabled = selected.isNotEmpty(),
                                    onClick = { confirmDelete = true },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.ratings_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.ratings_delete)) },
            text = { Text(stringResource(R.string.ratings_delete_confirm, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val uris = selected.toList()
                    confirmDelete = false
                    bulkMode = false
                    selected = emptySet()
                    scope.launch {
                        uris.forEach { uriString ->
                            rated.firstOrNull { it.uri.toString() == uriString }?.let { track ->
                                if (deleteSongFile(context, track.uri)) {
                                    extras.resetRating(uriString)
                                }
                            }
                        }
                        onFilesDeleted()
                    }
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionRow(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionKicker(text = title, accent = accent)
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            CyberBadge(text = badge, color = accent)
        }
    }
}

@Composable
private fun EmptyLine(textRes: Int) {
    Text(
        text = stringResource(textRes),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One entry in "Recently added": identity, artist, and when it landed.
 * The relative time is locale-aware ("hace 2 h", "2 hr. ago").
 */
@Composable
private fun RecentRow(
    track: Track,
    addedAt: Long,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val whenText = remember(addedAt) {
        android.text.format.DateUtils.getRelativeTimeSpanString(
            addedAt,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS,
            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE or
                android.text.format.DateUtils.FORMAT_ABBREV_MONTH,
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .cyberClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(track = track, side = 46.dp, playing = playing)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The timestamp shares the secondary line so the title keeps the full
            // width — at 130% type a right-hand time column truncated song names.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.no_artist),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playing) {
                    EqualizerBars(
                        playing = true,
                        barCount = 3,
                        barWidth = 2.5.dp,
                        height = 13.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = whenText,
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingRow(
    rank: Int,
    track: Track,
    score: Int,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    selectable: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val rowColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = tween(Motion.Fast),
        label = "rating-row",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .background(rowColor)
            .cyberClick(onClick = { if (selectable) onCheckedChange(!checked) else onClick() })
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(4.dp))
        } else {
            Text(
                text = rank.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.8f),
                modifier = Modifier.width(34.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
        // Icon only: at list size the thumb says "liked / disliked" faster than any
        // label, and the bar that used to sit underneath read as a second border.
        RatingIcon(score = score, size = 22.dp)
    }
}
