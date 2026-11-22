package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.AudioWaveform
import kotlin.math.max

/**
 * A seek bar shaped like the song's loudness envelope.
 *
 * Tapping or dragging anywhere on the bars seeks there; the played side fills with the
 * accent colour and a vertical playhead marks the exact position. When the waveform is
 * still being analysed (or the codec cannot be decoded) the same control draws a quiet
 * placeholder, so seeking never has to wait for the analysis.
 */
@Composable
fun WaveformSeekBar(
    progress: Float,
    amplitudes: FloatArray?,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val playedColor = MaterialTheme.colorScheme.primary
    val restColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val centerLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val seekLabel = stringResource(R.string.seek_song)
    val currentScrub by rememberUpdatedState(onScrub)
    val currentSeek by rememberUpdatedState(onSeek)
    val clamped = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics {
                contentDescription = seekLabel
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                setProgress { target ->
                    currentSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
            .focusable(enabled)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    var fraction = (down.position.x / width).coerceIn(0f, 1f)
                    currentScrub(fraction)
                    down.consume()

                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        fraction = (change.position.x / width).coerceIn(0f, 1f)
                        currentScrub(fraction)
                        change.consume()
                    }
                    currentSeek(fraction)
                }
            },
    ) {
        val count = amplitudes?.size ?: AudioWaveform.BARS
        if (count <= 0 || size.width <= 0f) return@Canvas

        val slot = size.width / count
        val barWidth = (slot * 0.58f).coerceAtLeast(1f)
        val barInset = (slot - barWidth) / 2f
        val minBar = 2.dp.toPx()
        val maxBar = (size.height - 10.dp.toPx()).coerceAtLeast(minBar)
        val centerY = size.height / 2f
        val activeX = clamped * size.width

        drawLine(
            color = centerLineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx(),
        )

        for (index in 0 until count) {
            val amplitude = amplitudes?.getOrNull(index) ?: placeholder(index)
            val height = max(minBar, amplitude.coerceIn(0f, 1f) * maxBar)
            val left = index * slot + barInset
            val color = if (left + barWidth / 2f <= activeX) playedColor else restColor
            drawRoundRect(
                color = color,
                topLeft = Offset(left, centerY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }

        // The playhead is a vertical line plus a small glowing disc, so the position is
        // readable even where the bars happen to be short.
        val playheadX = activeX.coerceIn(1.5.dp.toPx(), size.width - 1.5.dp.toPx())
        drawCircle(
            color = playedColor.copy(alpha = 0.16f),
            radius = 12.dp.toPx(),
            center = Offset(playheadX, centerY),
        )
        drawLine(
            color = playedColor,
            start = Offset(playheadX, 1.dp.toPx()),
            end = Offset(playheadX, size.height - 1.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = playedColor,
            radius = 4.dp.toPx(),
            center = Offset(playheadX, centerY),
        )
    }
}

/** A low, quiet pattern while the real envelope is being decoded. */
private fun placeholder(index: Int): Float = 0.06f + 0.025f * (index % 3)
