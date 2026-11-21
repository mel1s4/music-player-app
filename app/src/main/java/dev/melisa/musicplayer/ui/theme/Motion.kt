package dev.melisa.musicplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One place for timing so the app breathes with a single rhythm.
 *
 * Physical, spring-based motion for anything the finger touches; short easing
 * curves for anything the eye follows. Nothing here runs longer than 420ms —
 * a UI that feels "fun to watch" still has to feel instant to use.
 */
object Motion {
    const val Fast = 150
    const val Medium = 260
    const val Slow = 400

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> spring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = 0.7f,
        stiffness = 1200f,
    )
}

/**
 * Clickable with a tactile squash. The scale is the cheapest way to tell a finger
 * "yes, I got that" without layout work, and it reads well for users with reduced
 * dexterity who cannot always tell whether a tap landed.
 */
@Composable
fun Modifier.cyberClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.snappySpring(),
        label = "press-scale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * Fade-and-rise entrance, staggered by list index. Used for cards and headers
 * (not for long lists, where LazyColumn recycling would replay it on every scroll).
 */
@Composable
fun Modifier.enterUp(
    index: Int = 0,
    step: Int = 40,
    distance: Dp = 18.dp,
    duration: Int = 460,
): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 12) * step).toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(duration, easing = Motion.Emphasized),
        label = "enter-up",
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * distance.toPx()
    }
}

/** Coloured ambient shadow. API 28+ shows the colour, older devices get a plain shadow. */
fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 16.dp,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    clip: Boolean = false,
): Modifier = shadow(
    elevation = radius,
    shape = shape,
    clip = clip,
    ambientColor = color.copy(alpha = 0.55f),
    spotColor = color.copy(alpha = 0.8f),
)

/** CRT scanlines. Kept at ~4% so it is texture, never a readability tax. */
fun Modifier.scanlines(color: Color = Color.Black, alpha: Float = 0.05f, step: Dp = 3.dp): Modifier = drawBehind {
    val gap = step.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(0f, y),
            size = Size(size.width, 1f),
        )
        y += gap
    }
}

/** Slow breathing alpha for "live" dots. */
@Composable
fun pulsingAlpha(min: Float = 0.35f, max: Float = 1f, duration: Int = 1400): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    return alpha
}
