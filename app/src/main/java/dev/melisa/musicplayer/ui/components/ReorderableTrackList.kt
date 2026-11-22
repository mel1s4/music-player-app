package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import dev.melisa.musicplayer.data.Track
import kotlinx.coroutines.delay

/**
 * A LazyColumn of tracks with a long-press drag handle supplied to the row.
 *
 * Reordering happens in a local copy while the finger is down, then the final order is
 * handed back once. The parent can therefore persist the album/playlist order in one
 * write instead of writing the database on every pixel.
 */
@Composable
fun ReorderableTrackList(
    tracks: List<Track>,
    onOrderChanged: (List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    reorderable: Boolean = true,
    row: @Composable (index: Int, track: Track, dragHandle: Modifier?) -> Unit,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val latestOnOrderChanged by rememberUpdatedState(onOrderChanged)

    var working by remember { mutableStateOf(tracks) }
    var original by remember { mutableStateOf(tracks) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(78f) }

    LaunchedEffect(tracks, draggingKey) {
        if (draggingKey == null) working = tracks
    }

    // Keep a dragged row scrolling when it reaches the top or bottom edge.
    LaunchedEffect(draggingKey) {
        val key = draggingKey ?: return@LaunchedEffect
        while (true) {
            delay(16)
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                ?: continue
            val top = info.offset + dragOffset
            val bottom = top + info.size
            val start = listState.layoutInfo.viewportStartOffset
            val end = listState.layoutInfo.viewportEndOffset
            val delta = when {
                top < start + 64 -> -14f
                bottom > end - 64 -> 14f
                else -> 0f
            }
            if (delta != 0f) listState.scrollBy(delta)
        }
    }

    fun finishDrag(commit: Boolean) {
        if (draggingKey == null) return
        draggingKey = null
        dragOffset = 0f
        if (!commit) {
            working = original
        } else if (working.map { it.uri.toString() } != original.map { it.uri.toString() }) {
            latestOnOrderChanged(working)
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(working, key = { _, track -> track.uri.toString() }) { index, track ->
            val key = track.uri.toString()
            val isDragging = draggingKey == key
            val handle = if (reorderable) {
                Modifier.pointerInput(key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            original = working
                            draggingKey = key
                            dragOffset = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = { finishDrag(commit = true) },
                        onDragCancel = { finishDrag(commit = false) },
                        onDrag = { change, amount ->
                            change.consume()
                            if (draggingKey == key) {
                                dragOffset += amount.y
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == key }
                                val height = (info?.size ?: itemHeight.toInt())
                                    .toFloat()
                                    .coerceAtLeast(1f)
                                itemHeight = height

                                val current = working.indexOfFirst {
                                    it.uri.toString() == key
                                }
                                if (current >= 0) {
                                    val mutable = working.toMutableList()
                                    var index = current
                                    var moved = false
                                    while (dragOffset > height / 2f &&
                                        index < mutable.lastIndex
                                    ) {
                                        mutable.add(index + 1, mutable.removeAt(index))
                                        index++
                                        dragOffset -= height
                                        moved = true
                                    }
                                    while (dragOffset < -height / 2f && index > 0) {
                                        mutable.add(index - 1, mutable.removeAt(index))
                                        index--
                                        dragOffset += height
                                        moved = true
                                    }
                                    if (moved) working = mutable
                                }
                            }
                        },
                    )
                }
            } else {
                null
            }

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                    },
            ) {
                row(index, track, handle)
            }
        }
    }
}
