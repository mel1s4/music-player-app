package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.LibraryExtras

/**
 * The score itself is an implementation detail for the weighted shuffle; what the user
 * cares about is which side of "neutral" a song is on.
 */
@Composable
fun ratingLabel(score: Int): String = stringResource(
    when {
        score > LibraryExtras.DEFAULT_RATING -> R.string.rating_liked
        score < LibraryExtras.DEFAULT_RATING -> R.string.rating_disliked
        else -> R.string.rating_neutral
    },
)

/**
 * Visual shorthand for the rating: a thumb, tinted by side.
 *
 * The redesign drops the words "Liked"/"Disliked" from dense lists — at small sizes the
 * thumb reads faster than the phrase and does not need to be translated — but the label
 * stays as the accessibility description, so TalkBack still says it.
 *
 * Nothing is drawn for a neutral score: there is no thumb that means "no opinion".
 */
@Composable
fun RatingIcon(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val liked = score > LibraryExtras.DEFAULT_RATING
    val disliked = score < LibraryExtras.DEFAULT_RATING
    if (!liked && !disliked) return

    Icon(
        imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
        contentDescription = ratingLabel(score),
        tint = ratingColor(score),
        modifier = modifier.size(size),
    )
}

/** Green when liked, magenta when disliked, muted for neutral. */
@Composable
fun ratingColor(score: Int): Color = when {
    score > LibraryExtras.DEFAULT_RATING -> MaterialTheme.colorScheme.primary
    score < LibraryExtras.DEFAULT_RATING -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
