package dev.melisa.musicplayer.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.AlbumArt
import dev.melisa.musicplayer.data.ArtistArt
import dev.melisa.musicplayer.ui.components.AlphabetRail
import dev.melisa.musicplayer.ui.components.AlphabetSectionHeader
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.TrackSort
import dev.melisa.musicplayer.ui.components.TrackSortButton
import dev.melisa.musicplayer.ui.components.labelRes
import dev.melisa.musicplayer.ui.components.sectionLabelFor
import dev.melisa.musicplayer.ui.components.sortKey
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One album or artist in a browser list: a name plus how many tracks it holds. */
data class CollectionEntry(
    val name: String,
    val count: Int,
    val subtitle: String?,
    /** A track URI to pull embedded cover art from (albums only). */
    val coverUri: String? = null,
    /** Primary artist for an album, or the artist's own name. */
    val artist: String? = null,
    val addedAt: Long = 0L,
    val durationMs: Long = 0L,
)

private data class CollectionSection(
    val label: String,
    val entries: List<CollectionEntry>,
)

/**
 * Index of albums (as a grid with artwork) or of artists (as a list).
 *
 * Opening an entry shows [CollectionScreen] / [ArtistScreen], so there is one place that
 * knows how to lay out a collection and one that knows how to index it.
 */
@Composable
fun CollectionsBrowserScreen(
    entries: List<CollectionEntry>,
    emptyText: String,
    isArtist: Boolean,
    libraries: List<String>,
    artVersion: Int,
    starred: Set<String> = emptySet(),
    onOpen: (String) -> Unit,
) {
    if (entries.isEmpty()) {
        MessageState(
            title = emptyText,
            body = stringResource(R.string.songs_empty_body),
            icon = if (isArtist) Icons.Filled.Person else Icons.Filled.Album,
        )
        return
    }

    var sort by rememberSaveable { mutableStateOf(TrackSort.Title) }
    val sorted = remember(entries, sort) {
        when (sort) {
            TrackSort.DateAdded -> entries.sortedWith(
                compareByDescending<CollectionEntry> { it.addedAt }
                    .thenBy { sortKey(it.name) },
            )
            TrackSort.Title -> entries.sortedBy { sortKey(it.name) }
            TrackSort.Artist -> entries.sortedWith(
                compareBy({ sortKey(it.artist.orEmpty()) }, { sortKey(it.name) }),
            )
            TrackSort.Duration -> entries.sortedBy { it.durationMs }
        }
    }
    val starredEntries = remember(sorted, starred) {
        sorted.filter { starred.contains(it.name) }
    }
    val regularEntries = remember(sorted, starred) {
        sorted.filterNot { starred.contains(it.name) }
    }
    val sections = remember(regularEntries, sort) {
        sectionsFor(regularEntries, sort)
    }

    if (isArtist) {
        CollectionGrid(
            entries = regularEntries,
            totalCount = sorted.size,
            starredEntries = starredEntries,
            sections = sections,
            sort = sort,
            onSort = { sort = it },
            kicker = stringResource(sort.labelRes()),
            accent = MaterialTheme.colorScheme.primary,
        ) { entry ->
            ArtistItem(entry = entry, version = artVersion, onOpen = onOpen)
        }
    } else {
        CollectionGrid(
            entries = regularEntries,
            totalCount = sorted.size,
            starredEntries = starredEntries,
            sections = sections,
            sort = sort,
            onSort = { sort = it },
            kicker = stringResource(sort.labelRes()),
            accent = MaterialTheme.colorScheme.tertiary,
        ) { entry ->
            AlbumItem(
                entry = entry,
                libraries = libraries,
                artVersion = artVersion,
                onOpen = onOpen,
            )
        }
    }
}

private fun sectionsFor(
    entries: List<CollectionEntry>,
    sort: TrackSort,
): List<CollectionSection> {
    if (sort != TrackSort.Title && sort != TrackSort.Artist) return emptyList()
    val grouped = linkedMapOf<String, MutableList<CollectionEntry>>()
    entries.forEach { entry ->
        val key = if (sort == TrackSort.Artist) {
            entry.artist.orEmpty().ifBlank { entry.name }
        } else {
            entry.name
        }
        grouped.getOrPut(sectionLabelFor(key)) { mutableListOf() }.add(entry)
    }
    return grouped.keys
        .sortedWith(compareBy({ it == "#" }, { it }))
        .map { label -> CollectionSection(label, grouped.getValue(label)) }
}

@Composable
private fun CollectionGrid(
    entries: List<CollectionEntry>,
    totalCount: Int,
    starredEntries: List<CollectionEntry>,
    sections: List<CollectionSection>,
    sort: TrackSort,
    onSort: (TrackSort) -> Unit,
    kicker: String,
    accent: Color,
    itemContent: @Composable (CollectionEntry) -> Unit,
) {
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var starredExpanded by rememberSaveable { mutableStateOf(true) }
    val sectionStart = remember(sections, starredEntries, starredExpanded) {
        buildMap {
            var index = 1 // the count/sort header
            if (starredEntries.isNotEmpty()) {
                index += 1 // the collapsible Starred header
                if (starredExpanded) index += starredEntries.size
            }
            sections.forEach { section ->
                put(section.label, index)
                index += 1 + section.entries.size
            }
        }
    }
    val letters = sections.map { it.label }
    val showRail = letters.size > 2
    val currentLetter by remember(sectionStart) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            sectionStart.entries.lastOrNull { it.value <= first }?.key
        }
    }

    LaunchedEffect(sort) { state.scrollToItem(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (showRail) 24.dp else 0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionKicker(text = kicker, accent = accent)
                    Spacer(Modifier.width(8.dp))
                    CyberBadge(text = totalCount.toString(), color = accent)
                    Spacer(Modifier.weight(1f))
                    TrackSortButton(sort = sort, onSort = onSort)
                }
            }

            if (starredEntries.isNotEmpty()) {
                item(
                    key = "starred-header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    StarredSectionHeader(
                        count = starredEntries.size,
                        expanded = starredExpanded,
                        accent = accent,
                        onToggle = { starredExpanded = !starredExpanded },
                    )
                }
                if (starredExpanded) {
                    gridItems(starredEntries, key = { "starred:${it.name}" }) { entry ->
                        itemContent(entry)
                    }
                }
            }

            if (sections.isEmpty()) {
                gridItems(entries, key = { "collection:${it.name}" }) { entry ->
                    itemContent(entry)
                }
            } else {
                sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AlphabetSectionHeader(section.label)
                    }
                    gridItems(section.entries, key = { "collection:${it.name}" }) { entry ->
                        itemContent(entry)
                    }
                }
            }
        }

        if (showRail) {
            AlphabetRail(
                letters = letters,
                current = currentLetter,
                onPick = { letter ->
                    sectionStart[letter]?.let { index ->
                        scope.launch { state.scrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun StarredSectionHeader(
    count: Int,
    expanded: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = accent,
        )
        Spacer(Modifier.width(8.dp))
        SectionKicker(text = stringResource(R.string.collections_starred), accent = accent)
        Spacer(Modifier.width(8.dp))
        CyberBadge(text = count.toString(), color = accent)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumItem(
    entry: CollectionEntry,
    libraries: List<String>,
    artVersion: Int,
    starred: Set<String> = emptySet(),
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.cyberClick(onClick = { onOpen(entry.name) })) {
        AlbumArt(
            uriString = entry.coverUri,
            libraries = libraries,
            version = artVersion,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(entry.subtitle?.takeIf { it.isNotBlank() })
                .plus(entry.count.toString())
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistItem(
    entry: CollectionEntry,
    version: Int,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier.cyberClick(onClick = { onOpen(entry.name) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtistAvatar(
            artist = entry.name,
            version = version,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = entry.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ArtistAvatar(artist: String, version: Int, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(artist, version) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artist, version) {
        bitmap = withContext(Dispatchers.IO) {
            val key = "$artist#v$version"
            artistArtCache.get(key) ?: ArtistArt.load(context, artist)?.asImageBitmap()?.also {
                artistArtCache.put(key, it)
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val palette = remember(artist) {
                val h = kotlin.math.abs(artist.hashCode())
                val hue = 120f + (h % 70)
                listOf(
                    Color.hsv(hue, 0.55f, 0.34f),
                    Color.hsv((hue + 30f) % 360f, 0.7f, 0.12f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(palette)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = artist.firstOrNull()?.uppercase() ?: "·",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun AlbumArt(
    uriString: String?,
    libraries: List<String>,
    version: Int,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uriString, version) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uriString, version) {
        if (uriString != null) {
            bitmap = withContext(Dispatchers.IO) {
                // A cover.jpg written by the editor wins over the embedded one.
                val stored = AlbumArt.load(context, libraries, Uri.parse(uriString))
                stored?.asImageBitmap() ?: loadEmbeddedArt(context, "$uriString#v$version")
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Decoded covers are expensive, so keep a small in-memory cache per process. */
private val albumArtCache = LruCache<String, ImageBitmap>(48)
private val artistArtCache = LruCache<String, ImageBitmap>(64)

private fun loadEmbeddedArt(context: Context, cacheKey: String): ImageBitmap? {
    albumArtCache.get(cacheKey)?.let { return it }
    val uriString = cacheKey.substringBefore("#v")

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(uriString))
        val bytes = retriever.embeddedPicture ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outWidth > 0) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        decoded.asImageBitmap().also { albumArtCache.put(cacheKey, it) }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
