package dev.melisa.musicplayer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.text.Normalizer

/** The four orders shared by every browsable list. */
enum class TrackSort { DateAdded, Title, Artist, Duration }

@StringRes
fun TrackSort.labelRes(): Int = when (this) {
    TrackSort.DateAdded -> R.string.sort_date_added
    TrackSort.Title -> R.string.sort_title
    TrackSort.Artist -> R.string.field_artist
    TrackSort.Duration -> R.string.sort_duration
}

private val diacritics = Regex("\\p{Mn}+")

/** Lowercase, accent-free sort key so "Él" sorts with E instead of after Z. */
fun sortKey(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replace(diacritics, "").lowercase()

fun sectionLabelFor(text: String): String {
    val first = sortKey(text).firstOrNull()
    return if (first != null && first.isLetter()) first.uppercaseChar().toString() else "#"
}

/** Shared sort affordance: icon, menu and a check beside the active order. */
@Composable
fun TrackSortButton(
    sort: TrackSort,
    onSort: (TrackSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Filled.Sort,
                contentDescription = stringResource(R.string.sort_by),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            TrackSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        onSort(option)
                        menuOpen = false
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

/** Sticky initial separator shared by Songs and the other alphabetical browsers. */
@Composable
fun AlphabetSectionHeader(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
fun AlphabetRail(
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
