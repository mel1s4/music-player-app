package dev.melisa.musicplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.ui.theme.Motion

/**
 * Play/Pause that needs no extra icon dependency.
 *
 * Material's Pause glyph only exists in material-icons-extended, which would add a
 * multi-thousand-vector artifact for one symbol — so the pause state is two bars.
 * The two states cross-fade with a small spring so the toggle reads as one control
 * changing shape rather than two icons swapping.
 */
@Composable
fun PlayPauseGlyph(
    isPlaying: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    barWidth: Dp = 4.dp,
    barHeight: Dp = 16.dp,
    gap: Dp = 4.dp,
    iconSize: Dp = 24.dp,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)) + fadeIn(tween(Motion.Fast)))
                .togetherWith(scaleOut(tween(Motion.Fast)) + fadeOut(tween(Motion.Fast)))
        },
        label = "play-pause",
        modifier = modifier,
    ) { playing ->
        if (playing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.size(width = barWidth * 2 + gap, height = barHeight),
            ) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(width = barWidth, height = barHeight)
                            .background(tint, RoundedCornerShape(barWidth / 2)),
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        }
    }
}
