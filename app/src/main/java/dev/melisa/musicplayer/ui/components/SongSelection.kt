package dev.melisa.musicplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.melisa.musicplayer.data.Track

/**
 * The selected tracks for a long-press bulk action.
 *
 * A map keeps insertion order for the "N selected" count and makes toggling by URI
 * cheap. [start] is used by the first long-press; later taps only toggle.
 */
class SongSelectionState {
    var selected by mutableStateOf<Map<String, Track>>(emptyMap())
        private set

    val active: Boolean get() = selected.isNotEmpty()
    val tracks: List<Track> get() = selected.values.toList()

    fun isSelected(uri: String): Boolean = selected.containsKey(uri)

    fun start(track: Track) {
        selected = linkedMapOf(track.uri.toString() to track)
    }

    fun toggle(track: Track) {
        val key = track.uri.toString()
        selected = if (selected.containsKey(key)) {
            selected - key
        } else {
            selected + (key to track)
        }
    }

    fun clear() {
        selected = emptyMap()
    }
}

@Composable
fun rememberSongSelectionState(): SongSelectionState = remember { SongSelectionState() }

/**
 * Every track row reads this. The host provides it around the whole screen tree so a
 * long-press in Songs, Search, Files, an album page or a playlist all means the same
 * thing without each screen passing selection lambdas down.
 */
val LocalSongSelection = staticCompositionLocalOf<SongSelectionState?> { null }
