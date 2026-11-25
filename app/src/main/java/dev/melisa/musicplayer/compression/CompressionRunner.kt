package dev.melisa.musicplayer.compression

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.melisa.musicplayer.data.CompressAudio
import dev.melisa.musicplayer.data.CompressFailure
import dev.melisa.musicplayer.data.CompressOutcome
import dev.melisa.musicplayer.data.LibraryExtras
import dev.melisa.musicplayer.data.SongFileSync
import dev.melisa.musicplayer.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three resting points of the compression sheet: nothing, a run, a result. */
enum class CompressPhase { Idle, Running, Done }

/**
 * A track whose file moved during a run.
 *
 * The database has already been rekeyed by the time this is published; the player
 * uses it to point its queue at the new file without waiting for a rescan.
 */
data class CompressSwap(val track: Track, val newUri: Uri)

/**
 * Everything the sheet and the notification paint, owned outside the composition so
 * the run survives the activity being recreated or sent to the background.
 */
data class CompressJobState(
    val phase: CompressPhase = CompressPhase.Idle,
    /** The full selection the user confirmed, including tracks the run skips. */
    val tracks: List<Track> = emptyList(),
    val progress: Float = 0f,
    val currentTitle: String? = null,
    val completed: Int = 0,
    /** How many of [tracks] are actually converted; the rest are too small already. */
    val total: Int = 0,
    val freedBytes: Long = 0L,
    val failed: Int = 0,
    val kept: Int = 0,
    val skipped: Int = 0,
    val failure: CompressFailure? = null,
    val cancelled: Boolean = false,
    val swaps: List<CompressSwap> = emptyList(),
) {
    val running: Boolean get() = phase == CompressPhase.Running
    val done: Boolean get() = phase == CompressPhase.Done
}

/**
 * Runs compression away from the screen.
 *
 * The queue lives here rather than in a composable scope, and a foreground
 * [CompressService] keeps the process alive while the app is in the background. The
 * sheet renders this state, so rotating the device or coming back hours later shows
 * the run exactly where it is — not a restarted or vanished one.
 *
 * The loop stays on the main thread: [CompressAudio] moves the encoder to IO itself,
 * while the library rekey and the state updates stay serialized with the UI's writes.
 */
object CompressionRunner {

    private const val TAG = "CompressionRunner"

    /**
     * Library writes a run made, kept in order so a freshly created [LibraryExtras]
     * can be brought up to date when the activity is recreated.
     */
    private sealed interface LibraryChange {
        data class Rekey(val oldUri: String, val newUri: String) : LibraryChange
        data class Copy(val oldUri: String, val newUri: String) : LibraryChange
    }

    private val _state = MutableStateFlow(CompressJobState())
    val state: StateFlow<CompressJobState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var extras: LibraryExtras? = null
    private val libraryChanges = mutableListOf<LibraryChange>()

    val isRunning: Boolean get() = _state.value.phase == CompressPhase.Running

    /**
     * Queues [batch] for conversion. Returns false when nothing in it would get
     * smaller, which is also when the sheet keeps its setup step on screen.
     */
    fun start(
        context: Context,
        extras: LibraryExtras,
        batch: List<Track>,
        bitrateKbps: Int,
        keepOriginal: Boolean,
    ): Boolean {
        if (isRunning || batch.isEmpty()) return false

        val eligible = batch.filter { track ->
            CompressAudio.willSave(track.sizeBytes, track.durationMs, bitrateKbps)
        }
        if (eligible.isEmpty()) return false

        // A queued tag write copies the whole file; letting it run while the transcoder
        // reads the same file can hand the decoder a half-rewritten one. Dropping the
        // queue is safe: the database stays correct and the next edit rewrites the tags.
        SongFileSync.cancelPending()

        val app = context.applicationContext
        this.extras = extras
        libraryChanges.clear()

        val skipped = batch.size - eligible.size
        _state.value = CompressJobState(
            phase = CompressPhase.Running,
            tracks = batch,
            progress = 0f,
            currentTitle = eligible.first().title,
            completed = 0,
            total = eligible.size,
            skipped = skipped,
        )

        // The notification is the only thing keeping Android from reclaiming the
        // process while the app is in the background. A refusal here must not stop
        // the conversion; it just loses that protection.
        runCatching { CompressService.start(app) }
            .onFailure { Log.w(TAG, "could not start compression service", it) }

        job = scope.launch {
            var freed = 0L
            var failed = 0
            var kept = 0
            var lastFailure: CompressFailure? = null
            var cancelled = false
            try {
                eligible.forEachIndexed { index, track ->
                    _state.update {
                        it.copy(currentTitle = track.title, completed = index, progress = 0f)
                    }
                    val outcome = CompressAudio.compress(
                        context = app,
                        source = track.uri,
                        targetBitrateKbps = bitrateKbps,
                        keepOriginal = keepOriginal,
                        sourceBytes = track.sizeBytes,
                        sourceDurationMs = track.durationMs,
                    ) { fraction ->
                        // Progress arrives from the encoder's IO thread.
                        _state.update {
                            it.copy(progress = (index + fraction) / eligible.size)
                        }
                    }

                    when (outcome) {
                        is CompressOutcome.Success -> {
                            val result = outcome.result
                            val oldUri = track.uri.toString()
                            val newUri = result.newUri.toString()
                            if (result.replaced) {
                                extrasOf().rekey(oldUri, newUri)
                                libraryChanges += LibraryChange.Rekey(oldUri, newUri)
                                freed += (result.sourceBytes - result.newBytes).coerceAtLeast(0L)
                                _state.update {
                                    it.copy(swaps = it.swaps + CompressSwap(track, result.newUri))
                                }
                            } else {
                                extrasOf().copyState(oldUri, newUri)
                                libraryChanges += LibraryChange.Copy(oldUri, newUri)
                                kept++
                            }
                        }

                        is CompressOutcome.Failure -> {
                            failed++
                            lastFailure = outcome.reason
                            Log.w(TAG, "failed: ${track.title} ${outcome.reason}")
                        }
                    }
                }
            } catch (_: CancellationException) {
                cancelled = true
            } catch (t: Throwable) {
                // Never leave the sheet spinning on an unexpected library error.
                failed++
                lastFailure = CompressFailure.Io
                Log.w(TAG, "compression queue failed", t)
            } finally {
                _state.update {
                    it.copy(
                        phase = CompressPhase.Done,
                        cancelled = cancelled,
                        progress = if (cancelled) it.progress else 1f,
                        completed = eligible.size,
                        freedBytes = freed,
                        failed = failed,
                        kept = kept,
                        skipped = skipped,
                        failure = if (eligible.size == 1 && failed == 1) lastFailure else null,
                    )
                }
                job = null
            }
        }
        return true
    }

    /**
     * Points the run at the live screen's [LibraryExtras].
     *
     * A rotation swaps the instance the UI reads while the job keeps going, so the
     * new one starts from the database and would miss the in-memory rekeys the run
     * already made. Replaying them keeps favourites, ratings, tags and playlists in
     * step with the files.
     */
    fun attach(target: LibraryExtras) {
        if (extras === target) return
        extras = target
        // The writes above are idempotent, so replaying a change the new instance
        // already read from the database is harmless.
        libraryChanges.forEach { change ->
            when (change) {
                is LibraryChange.Rekey -> target.rekey(change.oldUri, change.newUri)
                is LibraryChange.Copy -> target.copyState(change.oldUri, change.newUri)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    /** Drops a finished result once the summary has been shown. */
    fun acknowledge() {
        if (isRunning) return
        _state.value = CompressJobState()
        libraryChanges.clear()
        extras = null
    }

    private fun extrasOf(): LibraryExtras = extras
        ?: error("compression started without library state")
}
