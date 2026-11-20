package dev.melisa.musicplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.melisa.musicplayer.R
import dev.melisa.musicplayer.data.AudioFileDetails
import dev.melisa.musicplayer.data.CompressAudio
import dev.melisa.musicplayer.data.CompressFailure
import dev.melisa.musicplayer.data.CompressOutcome
import dev.melisa.musicplayer.data.CompressResult
import dev.melisa.musicplayer.data.HearDifference
import dev.melisa.musicplayer.data.PreviewClip
import dev.melisa.musicplayer.data.PreviewScope
import dev.melisa.musicplayer.data.TranscodeException
import dev.melisa.musicplayer.data.TranscodeFailure
import dev.melisa.musicplayer.data.Track
import dev.melisa.musicplayer.ui.components.CyberBadge
import dev.melisa.musicplayer.ui.components.NeonPanel
import dev.melisa.musicplayer.ui.components.SectionKicker
import dev.melisa.musicplayer.ui.components.failureText
import dev.melisa.musicplayer.ui.components.formatBytes
import dev.melisa.musicplayer.ui.components.formatTime
import dev.melisa.musicplayer.ui.theme.Motion
import dev.melisa.musicplayer.ui.theme.cyberClick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class CompareStep { Setup, Converting, Results, Saving, Saved }

private data class PreviewItem(
    val bitrateKbps: Int,
    val progress: Float = 0f,
    val file: File? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val errorRes: Int? = null,
    val done: Boolean = false,
)

/**
 * Auditioning a compression before committing to it.
 *
 * The setup step converts the same excerpt (or the whole song) at each chosen bitrate
 * into private cache files. The results step plays the original and those versions back
 * to back through a dedicated ExoPlayer, so the comparison does not disturb queue
 * playback. Choosing a keeper finally runs the real [CompressAudio] path on the full
 * song, which publishes the file, copies its tags and removes the original.
 */
@Composable
fun HearDifferenceScreen(
    track: Track,
    details: AudioFileDetails?,
    onClose: () -> Unit,
    onSaved: (CompressResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sourceKbps = details?.bitrateKbps
        ?: CompressAudio.estimatedSourceKbps(track.sizeBytes, track.durationMs)
    val clip = remember(track.durationMs) { HearDifference.clipFor(track.durationMs) }
    val eligible = remember(track.uri, track.sizeBytes, track.durationMs) {
        HearDifference.eligibleBitrates(track)
    }

    var step by remember(track.uri) { mutableStateOf(CompareStep.Setup) }
    var selected by remember(track.uri) { mutableStateOf(eligible.toSet()) }
    var previewScope by remember(track.uri) { mutableStateOf(PreviewScope.Clip) }
    var previews by remember(track.uri) { mutableStateOf<List<PreviewItem>>(emptyList()) }
    var activeBitrate by remember { mutableStateOf<Int?>(null) }
    var chosen by remember(track.uri) { mutableStateOf<Int?>(null) }
    var saveProgress by remember { mutableFloatStateOf(0f) }
    var saveFailure by remember { mutableStateOf<CompressFailure?>(null) }
    var savedResult by remember { mutableStateOf<CompressResult?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val previewFiles = remember(track.uri) { mutableListOf<File>() }

    val player = remember { ExoPlayer.Builder(context).build() }
    var playingKey by remember { mutableStateOf<String?>(null) }
    var playerPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) playerPlaying = false
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    fun stopPreview() {
        player.pause()
        playerPlaying = false
        playingKey = null
    }

    fun clearPreviews() {
        previewFiles.forEach { runCatching { it.delete() } }
        previewFiles.clear()
        previews = emptyList()
        activeBitrate = null
    }

    fun requestClose() {
        if (step == CompareStep.Saving) return
        job?.cancel()
        job = null
        stopPreview()
        clearPreviews()
        onClose()
    }

    fun startComparison() {
        if (selected.isEmpty() || eligible.isEmpty()) return
        stopPreview()
        clearPreviews()
        val bitrates = selected.sortedDescending()
        previews = bitrates.map { PreviewItem(it) }
        chosen = null
        saveFailure = null
        savedResult = null
        step = CompareStep.Converting
        job = scope.launch {
            bitrates.forEach { bitrate ->
                activeBitrate = bitrate
                val output = File(
                    context.cacheDir,
                    "hear-${track.uri.hashCode()}-$bitrate-${System.nanoTime()}.m4a",
                )
                previewFiles += output
                try {
                    val result = HearDifference.convertPreview(
                        context = context,
                        source = track.uri,
                        targetBitrateKbps = bitrate,
                        scope = previewScope,
                        fullDurationMs = track.durationMs,
                        output = output,
                        onProgress = { value ->
                            previews = previews.map { item ->
                                if (item.bitrateKbps == bitrate) {
                                    item.copy(progress = value.coerceIn(0f, 1f))
                                } else {
                                    item
                                }
                            }
                        },
                    )
                    previews = previews.map { item ->
                        if (item.bitrateKbps == bitrate) {
                            item.copy(
                                progress = 1f,
                                done = true,
                                file = result.file,
                                durationMs = result.durationMs,
                                sizeBytes = result.file.length(),
                            )
                        } else {
                            item
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    previews = previews.map { item ->
                        if (item.bitrateKbps == bitrate) {
                            item.copy(progress = 1f, done = true, errorRes = previewError(t))
                        } else {
                            item
                        }
                    }
                }
            }
            activeBitrate = null
            step = CompareStep.Results
        }
    }

    fun backToSetup() {
        stopPreview()
        job?.cancel()
        job = null
        clearPreviews()
        chosen = null
        saveFailure = null
        step = CompareStep.Setup
    }

    fun keep(bitrate: Int) {
        stopPreview()
        chosen = bitrate
        saveFailure = null
        saveProgress = 0f
        step = CompareStep.Saving
        job = scope.launch {
            when (
                val outcome = CompressAudio.compress(
                    context = context,
                    source = track.uri,
                    targetBitrateKbps = bitrate,
                    keepOriginal = false,
                    sourceBytes = track.sizeBytes,
                    sourceDurationMs = track.durationMs,
                    onProgress = { saveProgress = it.coerceIn(0f, 1f) },
                )
            ) {
                is CompressOutcome.Success -> {
                    savedResult = outcome.result
                    onSaved(outcome.result)
                    step = CompareStep.Saved
                }

                is CompressOutcome.Failure -> {
                    saveFailure = outcome.reason
                    step = CompareStep.Results
                }
            }
        }
    }

    fun togglePlay(key: String, uri: Uri, clipRange: PreviewClip?) {
        if (playingKey == key) {
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
            return
        }
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply {
                if (clipRange != null && clipRange.durationMs > 0L) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipRange.startMs)
                            .setEndPositionMs(clipRange.endMs)
                            .build(),
                    )
                }
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
        playingKey = key
    }

    DisposableEffect(track.uri) {
        onDispose {
            job?.cancel()
            previewFiles.forEach { runCatching { it.delete() } }
            previewFiles.clear()
        }
    }

    BackHandler {
        // Never interrupt the publish-and-delete step halfway through.
        if (step != CompareStep.Saving) requestClose()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = ::requestClose,
                    enabled = step != CompareStep.Saving,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = if (step == CompareStep.Saving) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Hearing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.hear_difference),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (step) {
                CompareStep.Setup -> SetupStep(
                    track = track,
                    details = details,
                    sourceKbps = sourceKbps,
                    clip = clip,
                    previewScope = previewScope,
                    onScopeChange = { previewScope = it },
                    eligible = eligible,
                    selected = selected,
                    onToggleBitrate = { bitrate ->
                        selected = if (selected.contains(bitrate)) {
                            selected - bitrate
                        } else {
                            selected + bitrate
                        }
                    },
                    onStart = ::startComparison,
                )

                CompareStep.Converting -> ConvertingStep(
                    previews = previews,
                    activeBitrate = activeBitrate,
                    onCancel = ::backToSetup,
                )

                CompareStep.Results -> ResultsStep(
                    track = track,
                    details = details,
                    sourceKbps = sourceKbps,
                    previewScope = previewScope,
                    clip = clip,
                    previews = previews,
                    chosen = chosen,
                    onChoose = { chosen = it },
                    saveFailure = saveFailure,
                    playingKey = playingKey,
                    playerPlaying = playerPlaying,
                    onPlayOriginal = {
                        togglePlay(
                            key = ORIGINAL_KEY,
                            uri = track.uri,
                            clipRange = if (previewScope == PreviewScope.Clip) clip else null,
                        )
                    },
                    onPlayPreview = { item ->
                        item.file?.let { file ->
                            togglePlay(
                                key = "bitrate:${item.bitrateKbps}",
                                uri = Uri.fromFile(file),
                                clipRange = null,
                            )
                        }
                    },
                    onChange = ::backToSetup,
                    onKeep = { chosen?.let(::keep) },
                )

                CompareStep.Saving -> SavingStep(
                    bitrateKbps = chosen,
                    progress = saveProgress,
                )

                CompareStep.Saved -> SavedStep(
                    result = savedResult,
                    bitrateKbps = chosen,
                    onDone = ::requestClose,
                )
            }
        }
    }
}

private const val ORIGINAL_KEY = "original"

@Composable
private fun ColumnScope.SetupStep(
    track: Track,
    details: AudioFileDetails?,
    sourceKbps: Int,
    clip: PreviewClip,
    previewScope: PreviewScope,
    onScopeChange: (PreviewScope) -> Unit,
    eligible: List<Int>,
    selected: Set<Int>,
    onToggleBitrate: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.hear_difference_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SourcePanel(track = track, details = details, sourceKbps = sourceKbps)

        SectionKicker(
            text = stringResource(R.string.hear_scope),
            accent = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScopePill(
                label = stringResource(R.string.hear_scope_clip),
                selected = previewScope == PreviewScope.Clip,
                onClick = { onScopeChange(PreviewScope.Clip) },
                modifier = Modifier.weight(1f),
            )
            ScopePill(
                label = stringResource(R.string.hear_scope_full),
                selected = previewScope == PreviewScope.Full,
                onClick = { onScopeChange(PreviewScope.Full) },
                modifier = Modifier.weight(1f),
            )
        }
        if (previewScope == PreviewScope.Clip && clip.durationMs > 0L && track.durationMs > clip.durationMs) {
            Text(
                text = stringResource(
                    R.string.hear_scope_clip_hint,
                    formatTime(clip.startMs),
                    formatTime(clip.endMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionKicker(
            text = stringResource(R.string.hear_bitrates),
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.hear_bitrates_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (eligible.isEmpty()) {
            Text(
                text = stringResource(R.string.hear_no_bitrates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            eligible.forEach { bitrate ->
                BitrateChoice(
                    bitrateKbps = bitrate,
                    checked = selected.contains(bitrate),
                    estimatedBytes = CompressAudio.targetBytes(track.durationMs, bitrate),
                    onToggle = { onToggleBitrate(bitrate) },
                )
            }
        }

        Text(
            text = stringResource(
                if (previewScope == PreviewScope.Clip) {
                    R.string.hear_clip_note
                } else {
                    R.string.hear_full_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Button(
                onClick = onStart,
                enabled = eligible.isNotEmpty() && selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Hearing,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.hear_start))
            }
        }
    }
}

@Composable
private fun SourcePanel(
    track: Track,
    details: AudioFileDetails?,
    sourceKbps: Int,
) {
    val line = buildList {
        val codec = details?.codec
            ?: track.uri.lastPathSegment?.substringAfterLast('.', "")?.uppercase()
        if (!codec.isNullOrBlank()) add(codec)
        if (sourceKbps > 0) add(stringResource(R.string.metadata_kbps, sourceKbps))
        if (track.sizeBytes > 0L) add(formatBytes(track.sizeBytes))
        if (track.durationMs > 0L) add(formatTime(track.durationMs))
    }.joinToString(" · ")

    NeonPanel(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        corner = 18.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionKicker(
                    text = stringResource(R.string.metadata_audio_file),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.weight(1f))
                details?.let {
                    CyberBadge(
                        text = stringResource(
                            if (it.lossless) R.string.metadata_lossless else R.string.metadata_lossy,
                        ),
                        color = if (it.lossless) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = line.ifBlank { stringResource(R.string.metadata_codec_unknown) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ScopePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .cyberClick(pressedScale = 0.96f, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun BitrateChoice(
    bitrateKbps: Int,
    checked: Boolean,
    estimatedBytes: Long,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onToggle)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hear_bitrate_label, bitrateKbps),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.compress_estimate, formatBytes(estimatedBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.ConvertingStep(
    previews: List<PreviewItem>,
    activeBitrate: Int?,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(
                R.string.hear_converting,
                activeBitrate ?: previews.firstOrNull { !it.done }?.bitrateKbps ?: 0,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        previews.forEach { item ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.hear_bitrate_label, item.bitrateKbps),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${(item.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainer,
                )
                item.errorRes?.let { error ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    TextButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.action_cancel))
    }
}

@Composable
private fun ColumnScope.ResultsStep(
    track: Track,
    details: AudioFileDetails?,
    sourceKbps: Int,
    previewScope: PreviewScope,
    clip: PreviewClip,
    previews: List<PreviewItem>,
    chosen: Int?,
    onChoose: (Int) -> Unit,
    saveFailure: CompressFailure?,
    playingKey: String?,
    playerPlaying: Boolean,
    onPlayOriginal: () -> Unit,
    onPlayPreview: (PreviewItem) -> Unit,
    onChange: () -> Unit,
    onKeep: () -> Unit,
) {
    val ready = previews.filter { it.file != null }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.hear_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        VersionRow(
            key = ORIGINAL_KEY,
            title = stringResource(R.string.hear_original),
            subtitle = buildList {
                val codec = details?.codec
                if (!codec.isNullOrBlank()) add(codec)
                if (sourceKbps > 0) add(stringResource(R.string.metadata_kbps, sourceKbps))
                if (track.sizeBytes > 0L) add(formatBytes(track.sizeBytes))
            }.joinToString(" · "),
            playing = playingKey == ORIGINAL_KEY && playerPlaying,
            selectable = false,
            selected = false,
            onPlay = onPlayOriginal,
            onSelect = {},
        )

        ready.forEach { item ->
            VersionRow(
                key = "bitrate:${item.bitrateKbps}",
                title = stringResource(R.string.hear_bitrate_label, item.bitrateKbps),
                subtitle = buildList {
                    add(stringResource(R.string.compress_estimate, formatBytes(item.sizeBytes)))
                    if (item.durationMs > 0L) add(formatTime(item.durationMs))
                }.joinToString(" · "),
                playing = playingKey == "bitrate:${item.bitrateKbps}" && playerPlaying,
                selectable = true,
                selected = chosen == item.bitrateKbps,
                onPlay = { onPlayPreview(item) },
                onSelect = { onChoose(item.bitrateKbps) },
            )
        }

        previews.filter { it.done && it.errorRes != null }.forEach { failed ->
            Text(
                text = stringResource(R.string.hear_preview_failed, failed.bitrateKbps) +
                    " " + stringResource(failed.errorRes ?: R.string.compress_error_io),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        saveFailure?.let { failure ->
            Text(
                text = failureText(failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            text = stringResource(
                if (previewScope == PreviewScope.Clip) {
                    R.string.hear_clip_note
                } else {
                    R.string.hear_full_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (previewScope == PreviewScope.Clip && clip.durationMs > 0L) {
            Text(
                text = stringResource(
                    R.string.hear_scope_clip_hint,
                    formatTime(clip.startMs),
                    formatTime(clip.endMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onChange) {
                    Text(stringResource(R.string.hear_change))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onKeep,
                    enabled = chosen != null,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = chosen?.let {
                            stringResource(R.string.hear_keep_bitrate, it)
                        } ?: stringResource(R.string.hear_keep),
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    key: String,
    title: String,
    subtitle: String,
    playing: Boolean,
    selectable: Boolean,
    selected: Boolean,
    onPlay: () -> Unit,
    onSelect: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            playing -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(Motion.Fast),
        label = "version-row-$key",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(enabled = selectable, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPlay, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.action_pause else R.string.action_play,
                ),
                tint = if (playing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(26.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selectable) {
            RadioButton(selected = selected, onClick = onSelect)
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ColumnScope.SavingStep(
    bitrateKbps: Int?,
    progress: Float,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.hear_saving, bitrateKbps ?: 0),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.SavedStep(
    result: CompressResult?,
    bitrateKbps: Int?,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.hear_saved, bitrateKbps ?: 0),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (result?.replaced == false) {
                    R.string.hear_saved_copy_hint
                } else {
                    R.string.hear_saved_hint
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        result?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatBytes(it.newBytes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}

private fun previewError(t: Throwable): Int = when ((t as? TranscodeException)?.failure) {
    TranscodeFailure.UnsupportedFormat,
    TranscodeFailure.NoAudioTrack,
    -> R.string.compress_error_unsupported

    TranscodeFailure.Encoder,
    TranscodeFailure.Muxer,
    -> R.string.compress_error_encoder

    TranscodeFailure.Io, null -> R.string.compress_error_io
}
