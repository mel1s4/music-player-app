package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.data.SongArt
import dev.melisa.musicplayer.data.Track

/**
 * The folders the user granted. Cover lookup needs the SAF tree URIs, and the art is
 * drawn from rows several screens deep, so this travels as a composition local instead
 * of being threaded through every list signature.
 */
val LocalMusicLibraries = staticCompositionLocalOf<List<String>> { emptyList() }

/**
 * The "Show album art" setting. When off, covers are skipped entirely and the music
 * glyph is used, which also keeps decoding off slower devices.
 */
val LocalShowAlbumArt = staticCompositionLocalOf { true }

/**
 * One square song image: album cover, then artist portrait, then a music glyph.
 *
 * The fallback is drawn immediately, so rows never flash empty while the artwork is
 * decoded; the real image replaces it in place once it arrives.
 */
@Composable
fun SongCover(
    track: Track,
    side: Dp,
    playing: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = side * 0.28f,
) {
    val libraries = LocalMusicLibraries.current
    val showArt = LocalShowAlbumArt.current
    val context = LocalContext.current
    // Only the full-screen player pays for a large decode.
    val large = side >= 200.dp
    var cover by remember(track.uri) { mutableStateOf<ImageBitmap?>(null) }
    val revision = SongArt.revision

    LaunchedEffect(track.uri, libraries, revision, showArt) {
        cover = if (showArt) SongArt.load(context, libraries, track, large) else null
    }

    Box(
        modifier = modifier
            .size(side)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(side * 0.42f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        // The playing marker stays legible on top of a photo.
        if (playing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
            )
            EqualizerBars(
                playing = true,
                color = Color.White,
                barCount = 3,
                barWidth = (side.value * 0.05f).dp.coerceAtLeast(2.dp),
                height = side * 0.3f,
            )
        }
    }
}
