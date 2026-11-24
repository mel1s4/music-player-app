package dev.melisa.musicplayer.data

/**
 * The built-in dynamic playlists shown on the Playlists screen.
 *
 * Unlike a [Playlist], nothing is stored for these: every one of them is a query over
 * data the app already keeps (ratings, listening history), so they stay correct without
 * a refresh job and cannot drift out of sync with the library.
 */
enum class SmartPlaylist {
    /** Tracks rated above neutral. */
    Liked,

    /** Tracks rated below neutral. */
    Disliked,

    /** Tracks that are still at the neutral score. */
    NoRating,

    /** Tracks that have never been heard long enough to count as played. */
    Unheard;

    /**
     * Evaluates the list against the current library.
     *
     * Liked and disliked lists lead with the strongest opinion; unheard songs are
     * alphabetical so the list stays predictable while it shrinks.
     */
    fun resolveTracks(
        tracks: List<Track>,
        ratings: Map<String, Int>,
        listenedAt: Map<String, Long>,
    ): List<Track> {
        val score: (Track) -> Int = { track ->
            ratings[track.uri.toString()] ?: LibraryExtras.DEFAULT_RATING
        }
        return when (this) {
            Liked -> tracks
                .filter { score(it) > LibraryExtras.DEFAULT_RATING }
                .sortedWith(compareByDescending<Track> { score(it) }.thenBy { it.title.lowercase() })

            Disliked -> tracks
                .filter { score(it) < LibraryExtras.DEFAULT_RATING }
                .sortedWith(compareBy<Track> { score(it) }.thenBy { it.title.lowercase() })

            // Neutral is the app's "not rated" state, whether the row is absent or was
            // written back to 0 by undoing a like/dislike.
            NoRating -> tracks
                .filter { score(it) == LibraryExtras.DEFAULT_RATING }
                .sortedBy { it.title.lowercase() }

            Unheard -> tracks
                .filterNot { listenedAt.containsKey(it.uri.toString()) }
                .sortedBy { it.title.lowercase() }
        }
    }
}
