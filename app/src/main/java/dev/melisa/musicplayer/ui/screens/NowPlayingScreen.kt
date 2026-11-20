package dev.melisa.musicplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.LibraryExtras
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.LiveDot
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.PlayPauseGlyph
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.SongCover
import dev.melisa.musicplayer.ui.components.formatTime
import dev.melisa.musicplayer.ui.components.ratingColor
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.Motion
import dev.melisa.musicplayer.ui.theme.cyberClick
import dev.melisa.musicplayer.ui.theme.neonGlow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    isFavorite: Boolean,
    shuffleEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    // Swiping the artwork means "change song", so it always moves to the adjacent
    // item instead of seekToPrevious()'s restart-current-when-past-3s behaviour.
    onSwipeToPrevious: () -> Unit = onPrevious,
    onSwipeToNext: () -> Unit = onNext,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditMetadata: () -> Unit,
    rating: Int,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onGoToFolder: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // While dragging, the slider owns the position; polling must not fight the finger.
    var scrub by remember(track.uri) { mutableStateOf<Float?>(null) }
    val total = durationMs.coerceAtLeast(1L)
    val sliderValue = scrub ?: (positionMs.toFloat() / total).coerceIn(0f, 1f)
    val shownPosition = scrub?.let { (it * total).toLong() } ?: positionMs

    // Swipe down on the grab area or the artwork to hide the player, or swipe up to
    // open the song editor. The sheet follows the finger while dragging.
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissThreshold = with(LocalDensity.current) { 130.dp.toPx() }
    val upLimitPx = with(LocalDensity.current) { 180.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val latestClose by rememberUpdatedState(onClose)
    val latestEditMetadata by rememberUpdatedState(onEditMetadata)
    val sheetDrag = Modifier.pointerInput(track.uri) {
        detectVerticalDragGestures(
            onDragEnd = {
                val offset = dragY.value
                when {
                    offset > dismissThreshold -> latestClose()
                    offset < -dismissThreshold -> {
                        // Reset before the editor covers the player, so coming back
                        // does not leave the sheet parked halfway up the screen.
                        scope.launch {
                            dragY.animateTo(0f, animationSpec = tween(Motion.Fast))
                        }
                        latestEditMetadata()
                    }
                    else -> scope.launch {
                        dragY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    }
                }
            },
        ) { _, delta ->
            scope.launch {
                dragY.snapTo(
                    (dragY.value + delta).coerceIn(-upLimitPx, Float.MAX_VALUE),
                )
            }
        }
    }

    // Horizontal swipe over the artwork. The cover follows the finger, then either
    // snaps back or commits to the neighbouring track.
    val swipeX = remember { Animatable(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }
    val latestSwipeNext by rememberUpdatedState(onSwipeToNext)
    val latestSwipePrevious by rememberUpdatedState(onSwipeToPrevious)
    LaunchedEffect(track.uri) { swipeX.snapTo(0f) }

    // Disliking the current song immediately skips to the next one, so the player
    // would otherwise change without any feedback that the thumb-down registered.
    // The badge is keyed to the screen (not the song), so it survives that transition.
    val dislikeFeedback = remember { Animatable(0f) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // A pool of green light behind the artwork; the only large colour field.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.10f else 0.04f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                // Grab area: the whole header row responds to the vertical swipe.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(sheetDrag),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(width = 44.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            .align(Alignment.CenterHorizontally),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isPlaying) {
                                LiveDot(modifier = Modifier.padding(end = 8.dp))
                            }
                            Text(
                                text = stringResource(R.string.now_playing).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPlaying) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
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
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_go_to_folder)) },
                                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onGoToFolder()
                                    },
                                )
                                if (!track.album.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_go_to_album)) },
                                        leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            onGoToAlbum()
                                        },
                                    )
                                }
                                if (!track.artist.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_go_to_artist)) },
                                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            onGoToArtist()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val side = minOf(maxWidth * 0.74f, 330.dp)
                    val coverWidthPx = with(LocalDensity.current) { side.toPx() }
                    // Keep most of the artwork on screen while the hint slides out.
                    val maxSwipePx = coverWidthPx * 0.36f
                    val swipeProgress = (swipeX.value / swipeThresholdPx).coerceIn(-1f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(sheetDrag)
                            .pointerInput(track.uri) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val offset = swipeX.value
                                        if (abs(offset) >= swipeThresholdPx) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (offset < 0f) latestSwipeNext() else latestSwipePrevious()
                                        }
                                        scope.launch {
                                            swipeX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            swipeX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(Motion.Fast),
                                            )
                                        }
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        swipeX.snapTo(
                                            (swipeX.value + dragAmount).coerceIn(
                                                -maxSwipePx,
                                                maxSwipePx,
                                            ),
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        SwipeHint(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            label = stringResource(R.string.action_previous),
                            alpha = swipeProgress.coerceIn(0f, 1f),
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        SwipeHint(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            label = stringResource(R.string.action_next),
                            alpha = (-swipeProgress).coerceIn(0f, 1f),
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                        SongCover(
                            track = track,
                            side = side,
                            playing = false,
                            modifier = Modifier.graphicsLayer {
                                translationX = swipeX.value
                                rotationZ = (swipeX.value / coverWidthPx) * 6f
                                val scale = 1f - 0.04f * abs(swipeProgress)
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionKicker(
                            text = track.album?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.album_unknown),
                            accent = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = track.artist?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.no_artist),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onEditMetadata, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.action_edit_song),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { scrub = it },
                    onValueChangeFinished = {
                        scrub?.let { onSeek((it * total).toLong()) }
                        scrub = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    thumb = {
                        // A 22dp disc, not Material's 4dp hairline: this is the one
                        // control people drag with a thumb, and it must be hittable.
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime(shownPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToggleIcon(
                        icon = Icons.Filled.Shuffle,
                        description = stringResource(R.string.action_shuffle),
                        active = shuffleEnabled,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleShuffle()
                        },
                    )

                    IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.action_previous),
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // The one element that glows when audio is running.
                    val glow by animateColorAsState(
                        targetValue = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else Color.Transparent,
                        animationSpec = tween(Motion.Medium),
                        label = "play-glow",
                    )
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .neonGlow(glow, radius = 22.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .cyberClick(pressedScale = 0.92f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePlay()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                (scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) +
                                    fadeIn(tween(Motion.Fast))).togetherWith(
                                    scaleOut(tween(Motion.Fast)) + fadeOut(tween(Motion.Fast)),
                                )
                            },
                            label = "play-glyph",
                        ) { playing ->
                            PlayPauseGlyph(
                                isPlaying = playing,
                                contentDescription = stringResource(
                                    if (playing) R.string.action_pause else R.string.action_play,
                                ),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                barWidth = 8.dp,
                                barHeight = 30.dp,
                                gap = 6.dp,
                                iconSize = 42.dp,
                            )
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.action_next),
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    val favScale by animateFloatAsState(
                        targetValue = if (isFavorite) 1f else 0.92f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "fav-scale",
                    )
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleFavorite()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.action_love),
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayerScale(favScale),
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Score block: the number drives the weighted shuffle, so show the meter.
                NeonPanel(
                    modifier = Modifier.fillMaxWidth(),
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    corner = 18.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScoreAction(
                            icon = Icons.Filled.ThumbDown,
                            description = stringResource(R.string.action_dislike),
                            tint = MaterialTheme.colorScheme.error,
                            active = rating < LibraryExtras.DEFAULT_RATING,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDislike()
                                scope.launch {
                                    dislikeFeedback.stop()
                                    dislikeFeedback.snapTo(0f)
                                    dislikeFeedback.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = 1100,
                                            easing = Motion.Standard,
                                        ),
                                    )
                                    dislikeFeedback.snapTo(0f)
                                }
                            },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // The signed score is the number the shuffle actually reads, so
                            // it is shown verbatim between the two thumbs.
                            val scoreText = if (rating > 0) "+$rating" else rating.toString()
                            val scoreLabel = stringResource(R.string.rating_score)
                            Text(
                                text = scoreText,
                                modifier = Modifier.semantics {
                                    contentDescription = "$scoreLabel: $scoreText"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ratingColor(rating),
                            )
                        }
                        ScoreAction(
                            icon = Icons.Filled.ThumbUp,
                            description = stringResource(R.string.action_like),
                            tint = MaterialTheme.colorScheme.primary,
                            active = rating > LibraryExtras.DEFAULT_RATING,
                            onClick = onLike,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Transient confirmation: pops in, holds for a beat and fades out.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val progress = dislikeFeedback.value
                        alpha = when {
                            progress <= 0f -> 0f
                            progress < 0.16f -> progress / 0.16f
                            progress < 0.72f -> 1f
                            else -> ((1f - progress) / 0.28f).coerceIn(0f, 1f)
                        }
                        val pop = 0.7f + 0.3f * (progress / 0.22f).coerceIn(0f, 1f)
                        scaleX = pop
                        scaleY = pop
                        translationY = -30.dp.toPx() * progress
                    },
            ) {
                NeonPanel(
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    corner = 22.dp,
                    modifier = Modifier.neonGlow(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        radius = 16.dp,
                        shape = RoundedCornerShape(22.dp),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ThumbDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.rating_disliked),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Direction pill revealed from behind the artwork while it is being swiped. */
@Composable
private fun SwipeHint(
    icon: ImageVector,
    label: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                val scale = 0.86f + 0.14f * alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** Icon-only toggle (shuffle, favourite) with an animated accent when active. */
@Composable
private fun ToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(Motion.Fast),
        label = "toggle-tint",
    )
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}

@Composable
private fun ScoreAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    // Without the text label, the active side has to be obvious: a filled disc and a
    // coloured glyph. No outline — the fill is the state.
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (active) 0.20f else 0.06f))
            .cyberClick(pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(23.dp),
            tint = if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    },
)
