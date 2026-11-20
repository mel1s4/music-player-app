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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.MessageState
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One album or artist in a browser list: a name plus how many tracks it holds. */
data class CollectionEntry(
    val name: String,
    val count: Int,
    val subtitle: String?,
    /** A track URI to pull embedded cover art from (albums only). */
    val coverUri: String? = null,
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

    if (isArtist) {
        ArtistGrid(entries = entries, version = artVersion, onOpen = onOpen)
    } else {
        AlbumGrid(entries = entries, libraries = libraries, artVersion = artVersion, onOpen = onOpen)
    }
}

@Composable
private fun AlbumGrid(
    entries: List<CollectionEntry>,
    libraries: List<String>,
    artVersion: Int,
    onOpen: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionKicker(
                    text = stringResource(R.string.nav_albums),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                CyberBadge(text = entries.size.toString(), color = MaterialTheme.colorScheme.tertiary)
            }
        }
        gridItems(entries, key = { "album:${it.name}" }) { entry ->
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
    }
}

@Composable
private fun ArtistGrid(
    entries: List<CollectionEntry>,
    version: Int,
    onOpen: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionKicker(text = stringResource(R.string.nav_artists))
                Spacer(Modifier.width(8.dp))
                CyberBadge(text = entries.size.toString())
            }
        }
        gridItems(entries, key = { "artist:${it.name}" }) { entry ->
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
