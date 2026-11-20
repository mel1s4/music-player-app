package dev.melisa.musicplayer.data

import kotlin.random.Random

/**
 * Weighted random permutation (Efraimidis–Spirakis): each item gets the key
 * `U^(1/w)` and the list is sorted by that key descending.
 *
 * A higher weight pushes the key towards 1, so heavier songs land near the front of
 * the shuffled queue more often — while every song still appears exactly once, which
 * is what people expect from a shuffle.
 */
fun weightedShuffle(tracks: List<Track>, weightOf: (Track) -> Int): List<Track> =
    tracks.sortedByDescending { track ->
        val weight = weightOf(track).coerceAtLeast(1).toDouble()
        Math.pow(Random.nextDouble(), 1.0 / weight)
    }
