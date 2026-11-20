package dev.melisa.musicplayer.data

/**
 * A playlist is either manual (a stored list of track URIs) or dynamic (a tag filter
 * evaluated against the current library every time it is shown).
 *
 * Dynamic playlists store only the rule, never the matches — so a song you tag later
 * appears automatically, and one you untag disappears.
 */
data class Playlist(
    val id: String,
    val name: String,
    val trackUris: List<String>,
    val tagRule: String? = null,
) {
    val isDynamic: Boolean get() = !tagRule.isNullOrBlank()
}

/** Manual playlists resolve stored URIs; dynamic ones are recomputed from tags. */
fun Playlist.resolveTracks(
    tracks: List<Track>,
    tags: Map<String, List<String>>,
): List<Track> {
    val rule = tagRule
    return if (!rule.isNullOrBlank()) {
        tracks.filter { track ->
            tags[track.uri.toString()].orEmpty().any { it.equals(rule, ignoreCase = true) }
        }
    } else {
        val byUri = tracks.associateBy { it.uri.toString() }
        trackUris.mapNotNull { byUri[it] }
    }
}
