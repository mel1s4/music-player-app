package dev.melisa.musicplayer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.melisa.musicplayer.ui.theme.Cyber
import dev.melisa.musicplayer.ui.theme.Motion
import dev.melisa.musicplayer.ui.theme.pulsingAlpha
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The repeated furniture of the redesign: readout labels, panels, and the
 * procedural art tile that stands in for cover images we do not have.
 */

/**
 * Section label in the readout voice. Colour and the mono face do the work; no
 * accent tick, because a 3dp bar next to every heading is decoration that competes
 * with the text for attention.
 */
@Composable
fun SectionKicker(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * The standard container. Separation comes from the surface tone, not from an
 * outline: a 1dp neon edge around every card is exactly the kind of decoration
 * that makes a dense screen louder without making it clearer.
 */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    corner: Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(container),
        content = content,
    )
}

/** Small status pill: "EN VIVO", a score, a count. */
@Composable
fun CyberBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, letterSpacing = 0.3.sp),
            color = if (filled) MaterialTheme.colorScheme.onPrimary else color,
            maxLines = 1,
        )
    }
}

/** Breathing dot used for "currently playing". */
@Composable
fun LiveDot(
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val alpha = pulsingAlpha(min = 0.3f, max = 1f, duration = 1200)
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = alpha), RoundedCornerShape(size / 2)),
    )
}

/**
 * Four bars that dance while a track plays and fall flat when it pauses.
 * Cheaper than a whole spectrum and instantly readable as "audio is moving".
 */
@Composable
fun EqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 4,
    barWidth: Dp = 3.dp,
    height: Dp = 16.dp,
) {
    if (playing) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        val values = List(barCount) { index ->
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 360 + index * 90,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 60),
                ),
                label = "bar-$index",
            )
        }
        Canvas(modifier.size(width = barWidth * barCount * 2.2f, height = height)) {
            val step = size.width / barCount
            values.forEachIndexed { index, value ->
                val barHeight = size.height * value.value.coerceIn(0.18f, 1f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * step + step / 4f, size.height - barHeight),
                    size = Size(step / 2.2f, barHeight),
                    cornerRadius = CornerRadius(step / 4f, step / 4f),
                )
            }
        }
    } else {
        Canvas(modifier.size(width = barWidth * barCount * 2.2f, height = height)) {
            val step = size.width / barCount
            repeat(barCount) { index ->
                val barHeight = size.height * 0.22f
                drawRoundRect(
                    color = color.copy(alpha = 0.45f),
                    topLeft = Offset(index * step + step / 4f, size.height - barHeight),
                    size = Size(step / 2.2f, barHeight),
                    cornerRadius = CornerRadius(step / 4f, step / 4f),
                )
            }
        }
    }
}

/** Hairline divider that fades out at the edges instead of cutting the screen. */
@Composable
fun CyberDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant,
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Horizontal group of two or more quick stats. */
@Composable
fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}
