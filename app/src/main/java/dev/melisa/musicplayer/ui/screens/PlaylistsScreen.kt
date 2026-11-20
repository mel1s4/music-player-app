package dev.melisa.musicplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.LibraryExtras
import dev.melisa.musicplayer.data.Playlist
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.data.resolveTracks
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.DeletePlaylistDialog
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.PlaylistEditorDialog
import dev.melisa.musicplayer.ui.components.formatTime
import dev.melisa.musicplayer.ui.theme.cyberClick

@Composable
fun PlaylistsDestination(
    extras: LibraryExtras,
    tracks: List<Track>?,
    currentTrackUri: String?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var openTag by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    val open = extras.playlists.firstOrNull { it.id == openId }

    // Every tag carried by at least one current track, with its song count.
    val tagEntries = remember(tracks, extras.tags) {
        buildTagIndex(tracks.orEmpty(), extras.tags)
    }

    fun tracksForTag(tag: String): List<Track> = tracks.orEmpty()
        .filter { track ->
            extras.tags[track.uri.toString()].orEmpty().any { it.equals(tag, ignoreCase = true) }
        }
        .sortedBy { it.title.lowercase() }

    val tag = openTag
    if (tag != null) {
        BackHandler { openTag = null }
        TagDetail(
            tag = tag,
            tracks = tracksForTag(tag),
            currentTrackUri = currentTrackUri,
            onBack = { openTag = null },
            onPlayTrack = onPlayTrack,
            onPlayAll = onPlayAll,
        )
    } else if (open == null) {
        PlaylistsList(
            playlists = extras.playlists,
            tracks = tracks,
            tags = extras.tags,
            tagEntries = tagEntries,
            onOpen = { openId = it.id },
            onOpenTag = { picked ->
                val resolved = tracksForTag(picked)
                if (resolved.isNotEmpty()) {
                    // A tag tap is a play action: queue the songs, then show the queue.
                    onPlayAll(resolved, false)
                    openTag = picked
                }
            },
            onCreate = {
                editorTarget = null
                editorOpen = true
            },
        )
    } else {
        BackHandler { openId = null }
        PlaylistDetail(
            playlist = open,
            tracks = tracks,
            tags = extras.tags,
            currentTrackUri = currentTrackUri,
            onBack = { openId = null },
            onEdit = {
                editorTarget = open
                editorOpen = true
            },
            onDelete = { deleteTarget = open },
            onRemoveTrack = { extras.removeFromPlaylist(open.id, it.uri.toString()) },
            onPlayTrack = onPlayTrack,
            onPlayAll = onPlayAll,
        )
    }

    if (editorOpen) {
        PlaylistEditorDialog(
            initial = editorTarget,
            onDismiss = { editorOpen = false },
            onSave = { name, tag ->
                val target = editorTarget
                if (target == null) {
                    extras.createPlaylist(name, tag)
                } else {
                    extras.updatePlaylist(target.id, name, tag)
                }
                editorOpen = false
            },
        )
    }

    val pendingDelete = deleteTarget
    if (pendingDelete != null) {
        DeletePlaylistDialog(
            name = pendingDelete.name,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                extras.deletePlaylist(pendingDelete.id)
                if (openId == pendingDelete.id) openId = null
                deleteTarget = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    tracks: List<Track>?,
    tags: Map<String, List<String>>,
    tagEntries: List<TagEntry>,
    onOpen: (Playlist) -> Unit,
    onOpenTag: (String) -> Unit,
    onCreate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            NeonPanel(
                container = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                corner = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(60.dp)
                    .cyberClick(pressedScale = 0.97f, onClick = onCreate),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.playlist_new),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Tags come first: they are the fastest way to press play on a slice of the
        // library, and they exist even for people who never made a playlist.
        item {
            SectionHeaderRow(
                text = stringResource(R.string.action_tags),
                accent = MaterialTheme.colorScheme.tertiary,
                count = tagEntries.size,
                modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
            )
        }
        if (tagEntries.isEmpty()) {
            item { TagsEmptyCard() }
        } else {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tagEntries.forEach { entry ->
                        TagChip(entry = entry, onClick = { onOpenTag(entry.label) })
                    }
                }
            }
        }

        item {
            SectionHeaderRow(
                text = stringResource(R.string.nav_playlists),
                accent = MaterialTheme.colorScheme.primary,
                count = playlists.size,
                modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
            )
        }
        if (playlists.isEmpty()) {
            item { PlaylistsEmptyCard() }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                val count = playlist.resolveTracks(tracks.orEmpty(), tags).size
                PlaylistRow(playlist = playlist, count = count, onClick = { onOpen(playlist) })
            }
        }
    }
}

/** A tag plus how many songs carry it, as shown on the Listas screen. */
private data class TagEntry(val label: String, val count: Int)

/**
 * Tags are stored per track, so the index has to be inverted. Case-insensitive merge:
 * "Rock" and "rock" are the same tag, and the first spelling seen is the one displayed.
 */
private fun buildTagIndex(
    tracks: List<Track>,
    tags: Map<String, List<String>>,
): List<TagEntry> {
    val index = linkedMapOf<String, TagEntry>()
    tracks.forEach { track ->
        tags[track.uri.toString()].orEmpty()
            .distinctBy { it.lowercase() }
            .forEach { raw ->
                val label = raw.trim()
                if (label.isEmpty()) return@forEach
                val key = label.lowercase()
                val existing = index[key]
                index[key] = if (existing == null) {
                    TagEntry(label, 1)
                } else {
                    existing.copy(count = existing.count + 1)
                }
            }
    }
    return index.values.sortedBy { it.label.lowercase() }
}

@Composable
private fun SectionHeaderRow(
    text: String,
    accent: Color,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SectionKicker(text = text, accent = accent)
        Spacer(Modifier.width(8.dp))
        CyberBadge(text = count.toString(), color = accent)
    }
}

/**
 * One tag pill. Tapping it plays every song carrying the tag; the play glyph makes
 * that promise visible before the tap, so nobody expects a filter menu.
 */
@Composable
private fun TagChip(entry: TagEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .cyberClick(pressedScale = 0.95f, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.action_play),
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "#${entry.label}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun TagsEmptyCard() {
    NeonPanel(corner = 16.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Label,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.tags_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistsEmptyCard() {
    NeonPanel(corner = 16.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.playlists_empty),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.playlists_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Songs that carry one tag: the queue the tag was tapped for. */
@Composable
private fun TagDetail(
    tag: String,
    tracks: List<Track>,
    currentTrackUri: String?,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
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
                SectionKicker(
                    text = stringResource(R.string.action_tags),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.song_count, tracks.size, tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onPlayAll(tracks, false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
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
                    onClick = { onPlayAll(tracks, true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
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
            Spacer(Modifier.height(4.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                items(tracks, key = { it.uri.toString() }) { track ->
                    PlaylistTrackRow(
                        track = track,
                        selected = track.uri.toString() == currentTrackUri,
                        onRemove = null,
                        onClick = { onPlayTrack(track, tracks) },
                    )
                }
            }
        } else {
            MessageState(
                title = stringResource(R.string.playlist_dynamic_empty),
                body = stringResource(R.string.tags_empty_hint),
                icon = Icons.Filled.Label,
            )
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, count: Int, onClick: () -> Unit) {
    NeonPanel(
        corner = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .cyberClick(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playlist.isDynamic) {
                        Icons.Filled.Shuffle
                    } else {
                        Icons.AutoMirrored.Filled.List
                    },
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle(playlist, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    tracks: List<Track>?,
    tags: Map<String, List<String>>,
    currentTrackUri: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
) {
    val resolved = playlist.resolveTracks(tracks.orEmpty(), tags)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle(playlist, resolved.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.action_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (resolved.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onPlayAll(resolved, false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
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
                    onClick = { onPlayAll(resolved, true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
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
            Spacer(Modifier.height(4.dp))
        }

        if (resolved.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (playlist.isDynamic) R.string.playlist_dynamic_empty else R.string.playlist_empty,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(resolved, key = { it.uri.toString() }) { track ->
                    PlaylistTrackRow(
                        track = track,
                        selected = track.uri.toString() == currentTrackUri,
                        // A dynamic playlist has no stored members: remove the tag instead.
                        onRemove = if (playlist.isDynamic) null else ({ onRemoveTrack(track) }),
                        onClick = { onPlayTrack(track, resolved) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    track: Track,
    selected: Boolean,
    onRemove: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
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
        Text(
            text = formatTime(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.playlist_remove_track),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun subtitle(playlist: Playlist, count: Int): String {
    val countText = pluralStringResource(R.plurals.song_count, count, count)
    return if (playlist.isDynamic) {
        "$countText · ${stringResource(R.string.playlist_dynamic)} #${playlist.tagRule}"
    } else {
        countText
    }
}
