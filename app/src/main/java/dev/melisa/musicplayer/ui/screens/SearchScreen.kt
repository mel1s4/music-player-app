package dev.melisa.musicplayer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.ScanningState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongEditAction
import dev.melisa.musicplayer.ui.components.SongRow
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.cyberClick
import java.text.Normalizer

private enum class SearchFilter { All, Songs, Albums, Artists }

private const val SECTION_LIMIT = 5

/**
 * One search across the three ways the library is organised.
 *
 * Matching ignores case and accents, so "telepatia" finds "Telepatía" — worth the few
 * lines because typing accents on a phone keyboard is exactly what people skip.
 * Recent queries stay one tap away so a search is repeatable without retyping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    tracks: List<Track>?,
    albums: List<CollectionEntry>,
    artists: List<CollectionEntry>,
    currentTrackUri: String?,
    hasLibraries: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditSong: (Track, SongEditAction) -> Unit,
    onCompress: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    autoFocus: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    var recent by remember { mutableStateOf(listOf<String>()) }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    fun rememberQuery(value: String) {
        val clean = value.trim()
        if (clean.length < 2) return
        recent = (listOf(clean) + recent.filterNot { it.equals(clean, ignoreCase = true) }).take(6)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        focusRequester.requestFocus()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                rememberQuery(query)
                focus.clearFocus()
            }),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    modifier = Modifier.heightIn(min = 44.dp),
                    label = {
                        Text(
                            text = option.label(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                !hasLibraries -> MessageState(
                    title = stringResource(R.string.songs_no_library_title),
                    body = stringResource(R.string.songs_no_library_body),
                )

                tracks == null -> ScanningState()

                else -> Results(
                    query = query,
                    filter = filter,
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
                    recent = recent,
                    currentTrackUri = currentTrackUri,
                    onQueryChange = { query = it },
                    onForgetRecent = { recent = emptyList() },
                    onPlay = onPlay,
                    onAddToPlaylist = onAddToPlaylist,
                    onEditSong = onEditSong,
                    onCompress = onCompress,
                    onLike = onLike,
                    onDislike = onDislike,
                    onDelete = onDelete,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                )
            }
        }
    }

    // Jump straight into the field once the page is actually committed. During a
    // pull-down preview the keyboard must stay out of the animation.
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focus.clearFocus()
        }
    }
}

@Composable
private fun Results(
    query: String,
    filter: SearchFilter,
    tracks: List<Track>,
    albums: List<CollectionEntry>,
    artists: List<CollectionEntry>,
    recent: List<String>,
    currentTrackUri: String?,
    onQueryChange: (String) -> Unit,
    onForgetRecent: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditSong: (Track, SongEditAction) -> Unit,
    onCompress: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onDislike: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val needle = remember(query) { normalize(query) }

    if (needle.isEmpty()) {
        SearchIdle(recent = recent, onPick = onQueryChange, onForget = onForgetRecent)
        return
    }

    // Normalizing every title on every keystroke is the one thing that makes search
    // stutter on a large library. Build the index once per library change instead.
    val indexedTracks = remember(tracks) {
        tracks.map { track ->
            IndexedTrack(
                track = track,
                title = normalize(track.title),
                artist = normalize(track.artist.orEmpty()),
                album = normalize(track.album.orEmpty()),
            )
        }
    }
    val songHits = remember(needle, indexedTracks) {
        indexedTracks
            .filter { indexed ->
                indexed.title.contains(needle) ||
                    indexed.artist.contains(needle) ||
                    indexed.album.contains(needle)
            }
            .map { it.track }
    }
    val albumHits = remember(needle, albums) {
        albums.filter { normalize(it.name).contains(needle) }
    }
    val artistHits = remember(needle, artists) {
        artists.filter { normalize(it.name).contains(needle) }
    }

    val showSongs = filter == SearchFilter.All || filter == SearchFilter.Songs
    val showAlbums = filter == SearchFilter.All || filter == SearchFilter.Albums
    val showArtists = filter == SearchFilter.All || filter == SearchFilter.Artists

    val songs = if (filter == SearchFilter.All) songHits.take(SECTION_LIMIT) else songHits
    val albumRows = if (filter == SearchFilter.All) albumHits.take(SECTION_LIMIT) else albumHits
    val artistRows = if (filter == SearchFilter.All) artistHits.take(SECTION_LIMIT) else artistHits

    if (songHits.isEmpty() && albumHits.isEmpty() && artistHits.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MessageState(
                title = stringResource(R.string.search_no_results, query),
                body = stringResource(R.string.search_try_another),
                icon = Icons.Filled.Search,
            )
        }
        return
    }

    val total = songHits.size + albumHits.size + artistHits.size

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionKicker(text = stringResource(R.string.search_results))
                Spacer(Modifier.width(8.dp))
                CyberBadge(text = total.toString())
            }
        }

        if (showArtists && artistRows.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.nav_artists), MaterialTheme.colorScheme.primary) }
            items(artistRows, key = { "artist:${it.name}" }) { entry ->
                CollectionResultRow(
                    name = entry.name,
                    subtitle = pluralStringResource(R.plurals.song_count, entry.count, entry.count),
                    isArtist = true,
                    onClick = { onOpenArtist(entry.name) },
                )
            }
        }

        if (showAlbums && albumRows.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.nav_albums), MaterialTheme.colorScheme.tertiary) }
            items(albumRows, key = { "album:${it.name}" }) { entry ->
                CollectionResultRow(
                    name = entry.name,
                    subtitle = entry.subtitle.orEmpty(),
                    isArtist = false,
                    onClick = { onOpenAlbum(entry.name) },
                )
            }
        }

        if (showSongs && songs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.nav_songs), MaterialTheme.colorScheme.secondary) }
            items(songs, key = { it.uri.toString() }) { track ->
                SongRow(
                    track = track,
                    selected = track.uri.toString() == currentTrackUri,
                    // Play through every match for this query, not the whole library:
                    // the next song is the next result in the list.
                    onClick = { onPlay(track, songHits) },
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

@Composable
private fun SectionHeader(text: String, accent: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
        SectionKicker(text = text, accent = accent)
    }
}

@Composable
private fun CollectionResultRow(
    name: String,
    subtitle: String,
    isArtist: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .cyberClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    (if (isArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.14f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isArtist) Icons.Filled.Person else Icons.Filled.Album,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the tab shows before a query: how to search, and what was searched before. */
@Composable
private fun SearchIdle(
    recent: List<String>,
    onPick: (String) -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 24.dp),
    ) {
        if (recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionKicker(text = stringResource(R.string.search_recent))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onForget) {
                    Text(stringResource(R.string.search_clear_recent))
                }
            }
            Spacer(Modifier.height(6.dp))
            recent.forEach { term ->
                RecentRow(term = term, onClick = { onPick(term) })
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.search_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.search_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(term: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .cyberClick(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = term,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchFilter.label(): String = stringResource(
    when (this) {
        SearchFilter.All -> R.string.search_all
        SearchFilter.Songs -> R.string.nav_songs
        SearchFilter.Albums -> R.string.nav_albums
        SearchFilter.Artists -> R.string.nav_artists
    },
)

/** Lowercase, no accents: "telepatia" should match "Telepatía". */
private val COMBINING_MARKS = Regex("\\p{Mn}+")

private fun normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .trim()

/** One track with its search fields already normalized and lowercased. */
private data class IndexedTrack(
    val track: Track,
    val title: String,
    val artist: String,
    val album: String,
)
