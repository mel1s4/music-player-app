package dev.melisa.musicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.compression.CompressJobState
import dev.melisa.musicplayer.data.AudioFileDetails
import dev.melisa.musicplayer.data.AudioFileInfo
import dev.melisa.musicplayer.data.CompressAudio
import dev.melisa.musicplayer.data.CompressFailure
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlin.math.roundToInt

/**
 * The one thing this dialog is allowed to be: honest about the trade.
 *
 * Size before and after, the generation-loss warning for lossy sources, and a keep-original
 * switch that is on by default. The conversion only starts after the user sees the number.
 *
 * [state] is owned by the background job, not by this composition: the sheet is a view
 * of a run that survives rotation and backgrounding.
 */
@Composable
fun CompressSheet(
    tracks: List<Track>,
    state: CompressJobState,
    onDismiss: () -> Unit,
    onStart: (bitrateKbps: Int, keepOriginal: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    if (tracks.isEmpty()) return

    val context = LocalContext.current
    val single = tracks.singleOrNull()
    var details by remember(single?.uri) { mutableStateOf<AudioFileDetails?>(null) }
    LaunchedEffect(single?.uri) {
        details = single?.let { AudioFileInfo.read(context, it.uri, it.sizeBytes) }
    }

    val sourceBytes = tracks.sumOf { it.sizeBytes }
    val durationMs = tracks.sumOf { it.durationMs }
    val sourceKbps = remember(sourceBytes, durationMs) {
        CompressAudio.estimatedSourceKbps(sourceBytes, durationMs)
    }
    var bitrate by remember(tracks) { mutableIntStateOf(defaultBitrate(sourceKbps)) }
    var keepOriginal by remember { mutableStateOf(true) }

    // A batch can contain tracks that are already below the chosen bitrate. They are
    // skipped rather than re-encoded into a bigger file, and the sheet says so.
    val eligible = remember(tracks, bitrate) {
        tracks.filter { CompressAudio.willSave(it.sizeBytes, it.durationMs, bitrate) }
    }
    val skipped = tracks.size - eligible.size
    val eligibleBytes = eligible.sumOf { it.sizeBytes }
    val eligibleDuration = eligible.sumOf { it.durationMs }

    val estimate = remember(eligibleBytes, eligibleDuration, bitrate) {
        CompressAudio.estimate(eligibleBytes, eligibleDuration, bitrate)
    }
    val canStart = !state.running && !state.done && eligible.isNotEmpty()

    Dialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .heightIn(max = maxHeight * 0.92f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
                Column(
                    modifier = Modifier
                        .padding(22.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                SectionKicker(
                    text = stringResource(R.string.compress_kicker),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (tracks.size == 1) {
                        stringResource(R.string.compress_title)
                    } else {
                        stringResource(R.string.compress_batch_title, tracks.size)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))

                when {
                    state.done -> CompressSummary(state = state, onDismiss = onDismiss)
                    state.running -> CompressProgress(state = state, onCancel = onCancel)
                    else -> {
                        CompressSourceLine(
                            single = single,
                            details = details,
                            trackCount = tracks.size,
                            sourceBytes = sourceBytes,
                            sourceKbps = sourceKbps,
                            durationMs = durationMs,
                        )

                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.compress_target),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        BitrateRow(
                            selected = bitrate,
                            sourceBytes = sourceBytes,
                            durationMs = durationMs,
                            onSelect = { bitrate = it },
                        )

                        Spacer(Modifier.height(16.dp))
                        NeonPanel(
                            container = MaterialTheme.colorScheme.surfaceContainer,
                            corner = 18.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = if (estimate.targetBytes > 0L) {
                                            stringResource(
                                                R.string.compress_estimate,
                                                formatBytes(estimate.targetBytes),
                                            )
                                        } else {
                                            "—"
                                        },
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (sourceBytes > 0L) {
                                            stringResource(
                                                R.string.compress_saves,
                                                formatBytes(estimate.savingsBytes),
                                                estimate.savingsPercent,
                                            )
                                        } else {
                                            ""
                                        },
                                        modifier = Modifier.padding(bottom = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (details?.lossless == true) {
                                        stringResource(R.string.compress_lossless_hint)
                                    } else {
                                        stringResource(R.string.compress_lossy_warning)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (eligible.isEmpty() && durationMs > 0L) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.compress_already_small),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (skipped > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = pluralStringResource(R.plurals.compress_skipped_note, skipped, skipped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .cyberClick(pressedScale = 0.99f) {
                                    keepOriginal = !keepOriginal
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.compress_keep_original),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.compress_keep_original_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = keepOriginal,
                                onCheckedChange = { keepOriginal = it },
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.compress_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { onStart(bitrate, keepOriginal) },
                                enabled = canStart,
                            ) {
                                Text(
                                    if (tracks.size == 1) {
                                        stringResource(R.string.compress_start)
                                    } else {
                                        stringResource(R.string.compress_start_batch, tracks.size)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CompressSourceLine(
    single: Track?,
    details: AudioFileDetails?,
    trackCount: Int,
    sourceBytes: Long,
    sourceKbps: Int,
    durationMs: Long,
) {
    val codec = details?.codec
        ?: single?.uri?.lastPathSegment?.substringAfterLast('.', "")?.uppercase()
    val lines = buildList {
        if (single != null && !codec.isNullOrBlank()) add(codec)
        if (sourceKbps > 0) add("$sourceKbps kbps")
        add(formatBytes(sourceBytes))
        if (durationMs > 0) add(formatTime(durationMs))
    }
    if (trackCount > 1) {
        Text(
            text = pluralStringResource(R.plurals.song_count, trackCount, trackCount) +
                " · " + formatBytes(sourceBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = lines.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BitrateRow(
    selected: Int,
    sourceBytes: Long,
    durationMs: Long,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompressAudio.TARGET_BITRATES.forEach { value ->
            val projected = CompressAudio.targetBytes(durationMs, value)
            val enabled = sourceBytes <= 0L || projected < sourceBytes * 0.95
            val isSelected = value == selected
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .cyberClick(enabled = enabled, pressedScale = 0.94f) { onSelect(value) },
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    enabled -> MaterialTheme.colorScheme.surfaceContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
                },
                contentColor = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "$value",
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.compress_kbps_unit),
            modifier = Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompressProgress(state: CompressJobState, onCancel: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.compress_running),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(10.dp))
    LinearProgressIndicator(
        progress = { state.progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = if (state.total > 1) {
            stringResource(
                R.string.compress_progress_batch,
                state.completed + 1,
                state.total,
                state.currentTitle.orEmpty(),
            )
        } else {
            stringResource(
                R.string.compress_progress,
                (state.progress * 100).roundToInt(),
                state.currentTitle.orEmpty(),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.compress_cancel_job))
        }
    }
}

@Composable
private fun CompressSummary(state: CompressJobState, onDismiss: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    Text(
        text = when {
            state.cancelled && state.freedBytes > 0L ->
                stringResource(R.string.compress_done_cancelled, formatBytes(state.freedBytes))
            state.cancelled -> stringResource(R.string.compress_done_cancelled_zero)
            state.freedBytes > 0L ->
                stringResource(R.string.compress_done_freed, formatBytes(state.freedBytes))
            else -> stringResource(R.string.compress_done_title)
        },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))

    val notes = buildList {
        if (state.kept > 0) add(stringResource(R.string.compress_done_kept_copy))
        if (state.kept > 0) add(stringResource(R.string.compress_done_kept_note))
        if (state.failed > 0) add(stringResource(R.string.compress_done_failures, state.failed))
        if (state.skipped > 0) add(pluralStringResource(R.plurals.compress_done_skipped, state.skipped, state.skipped))
        state.failure?.let { add(failureText(it)) }
        if (state.freedBytes > 0 && state.kept == 0) {
            add(stringResource(R.string.compress_done_replaced_note))
        }
    }
    notes.forEach { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
    }

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onDismiss) {
            Text(stringResource(R.string.compress_done_button))
        }
    }
}

@Composable
internal fun failureText(failure: CompressFailure): String = stringResource(
    when (failure) {
        CompressFailure.UnsupportedFormat -> R.string.compress_error_unsupported
        CompressFailure.Encoder -> R.string.compress_error_encoder
        CompressFailure.ReadOnly -> R.string.compress_error_read_only
        CompressFailure.NoSpace -> R.string.compress_error_no_space
        CompressFailure.Verification -> R.string.compress_error_verification
        CompressFailure.NoSavings -> R.string.compress_error_no_savings
        CompressFailure.NotFound, CompressFailure.Io -> R.string.compress_error_io
    },
)

private fun defaultBitrate(sourceKbps: Int): Int {
    if (sourceKbps <= 0) return 128
    val good = CompressAudio.TARGET_BITRATES.filter { it <= sourceKbps * 0.75 }
    return good.maxOrNull() ?: CompressAudio.TARGET_BITRATES.first()
}
