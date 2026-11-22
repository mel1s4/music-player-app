package dev.melisa.musicplayer.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Loudness envelope for the Now Playing scrubber.
 *
 * The bars are RMS values sampled from the real PCM stream. Short tracks are decoded in
 * full; longer ones are sampled through short decoder windows spread across the song, so
 * the waveform is the song's quiet and loud sections without paying for a full decode on
 * every device.
 *
 * Results live in a small in-memory LRU cache. Switching tracks cancels the old decode,
 * and the player shows a quiet placeholder until the real envelope is ready.
 */
object AudioWaveform {

    /** Enough bars to show structure, few enough to keep the bar gaps readable. */
    const val BARS = 72

    private val cache = object : LruCache<String, FloatArray>(16) {}

    fun peek(uri: Uri): FloatArray? = cache.get(uri.toString())

    suspend fun load(
        context: Context,
        uri: Uri,
        durationMs: Long = 0L,
        bars: Int = BARS,
    ): FloatArray? {
        cache.get(uri.toString())?.let { return it }
        val amplitudes = withContext(Dispatchers.IO) {
            decode(context, uri, durationMs, bars)
        }
        if (amplitudes != null) cache.put(uri.toString(), amplitudes)
        return amplitudes
    }

    private suspend fun decode(
        context: Context,
        uri: Uri,
        durationMs: Long,
        bars: Int,
    ): FloatArray? {
        if (bars <= 0) return null
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = format.longOr(MediaFormat.KEY_DURATION, 0L)
                .takeIf { it > 0L }
                ?: (durationMs * 1000L).takeIf { it > 0L }

            val result = if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                val chunks = mutableListOf<Chunk>()
                readRaw(extractor, format, chunks)
                toBars(chunks, bars)
            } else {
                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()
                if (durationUs != null && durationUs > SAMPLED_THRESHOLD_US) {
                    decodeSampled(decoder, extractor, format, durationUs, bars)
                } else {
                    val chunks = mutableListOf<Chunk>()
                    decodeWithCodec(decoder, extractor, format, chunks)
                    toBars(chunks, bars)
                }
            }
            android.util.Log.d(
                "AudioWaveform",
                "waveform ready in " +
                    "${android.os.SystemClock.elapsedRealtime() - startedAt}ms " +
                    "(peak=${result?.maxOrNull()})",
            )
            return result
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Unsupported container/codec: the player keeps a usable, evenly-spaced
            // scrubber instead of failing to render.
            android.util.Log.w("AudioWaveform", "could not decode $uri", t)
            return null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private suspend fun decodeWithCodec(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        chunks: MutableList<Chunk>,
    ) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            currentCoroutineContext().ensureActive()

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val input = decoder.getInputBuffer(inIndex) ?: break
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(
                            inIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var draining = true
            while (draining) {
                when (val outIndex = decoder.dequeueOutputBuffer(info, 0L)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> draining = false

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                    else -> {
                        if (outIndex < 0) {
                            draining = false
                        } else {
                            if (info.size > 0) {
                                val outputFormat = decoder.outputFormat
                                val encoding = outputFormat.intOr(
                                    MediaFormat.KEY_PCM_ENCODING,
                                    AudioFormat.ENCODING_PCM_16BIT,
                                )
                                val channels = outputFormat.intOr(
                                    MediaFormat.KEY_CHANNEL_COUNT,
                                    inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2),
                                ).coerceIn(1, 8)
                                val buffer = decoder.getOutputBuffer(outIndex)
                                if (buffer != null) {
                                    buffer.order(ByteOrder.nativeOrder())
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    readChunk(buffer, info.size, channels, encoding)
                                        ?.let(chunks::add)
                                }
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                                draining = false
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Long songs are sampled instead of fully decoded: one short window per bar,
     * seeked to that bar's centre. That keeps the envelope representative while the
     * amount of decoded audio stays roughly [BARS] × [WINDOW_US] instead of the whole
     * track — important on devices without a hardware audio decoder.
     */
    private suspend fun decodeSampled(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        durationUs: Long,
        bars: Int,
    ): FloatArray? {
        val sampleRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
            .coerceAtLeast(8000)
        val windowUs = min(WINDOW_US, durationUs / bars)
            .coerceAtLeast(MIN_WINDOW_US)
        val windowFrames = ((windowUs * sampleRate) / 1_000_000L)
            .toInt()
            .coerceAtLeast(1)

        val values = FloatArray(bars)
        var previous = 0f

        for (bar in 0 until bars) {
            currentCoroutineContext().ensureActive()
            val centerUs = (durationUs * (2L * bar + 1L)) / (2L * bars)
            val startUs = (centerUs - windowUs / 2L).coerceAtLeast(0L)
            val seeked = runCatching {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                decoder.flush()
            }.isSuccess
            if (!seeked) {
                values[bar] = previous
                continue
            }

            val rms = readWindow(decoder, extractor, inputFormat, windowFrames)
            if (rms != null) {
                values[bar] = rms
                previous = rms
            } else {
                // A seek that lands past the end (or an unsupported window) keeps the
                // previous level instead of collapsing to silence.
                values[bar] = previous
            }
        }
        return normalize(values)
    }

    /** Decodes at most [targetFrames] samples and returns their RMS. */
    private suspend fun readWindow(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        targetFrames: Int,
    ): Float? {
        val info = MediaCodec.BufferInfo()
        var frames = 0
        var squareSum = 0.0
        var inputDone = false
        var outputDone = false
        var guard = 0
        val deadline = android.os.SystemClock.elapsedRealtime() + WINDOW_TIMEOUT_MS

        while (
            frames < targetFrames &&
            !outputDone &&
            guard++ < WINDOW_MAX_LOOPS &&
            android.os.SystemClock.elapsedRealtime() < deadline
        ) {
            currentCoroutineContext().ensureActive()

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val input = decoder.getInputBuffer(inIndex) ?: break
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(
                            inIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var draining = true
            while (draining) {
                when (val outIndex = decoder.dequeueOutputBuffer(info, 0L)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> draining = false

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                    else -> {
                        if (outIndex < 0) {
                            draining = false
                        } else {
                            if (info.size > 0) {
                                val outputFormat = runCatching { decoder.outputFormat }
                                    .getOrNull() ?: inputFormat
                                val encoding = outputFormat.intOr(
                                    MediaFormat.KEY_PCM_ENCODING,
                                    AudioFormat.ENCODING_PCM_16BIT,
                                )
                                val channels = outputFormat.intOr(
                                    MediaFormat.KEY_CHANNEL_COUNT,
                                    inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2),
                                ).coerceIn(1, 8)
                                val buffer = decoder.getOutputBuffer(outIndex)
                                if (buffer != null) {
                                    buffer.order(ByteOrder.nativeOrder())
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    readChunk(buffer, info.size, channels, encoding)
                                        ?.let { chunk ->
                                            frames += chunk.frames
                                            squareSum += chunk.rms * chunk.rms * chunk.frames
                                        }
                                }
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                                draining = false
                            }
                        }
                    }
                }
            }
        }
        if (frames <= 0) return null
        return sqrt(squareSum / frames).toFloat()
    }

    /**
     * WAV/PCM is already decoded by the extractor. Read its sample buffers directly;
     * the platform may not expose a decoder for "audio/raw".
     */
    private suspend fun readRaw(
        extractor: MediaExtractor,
        format: MediaFormat,
        chunks: MutableList<Chunk>,
    ) {
        val channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceIn(1, 8)
        val encoding = format.intOr(
            MediaFormat.KEY_PCM_ENCODING,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val buffer = ByteBuffer.allocate(RAW_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)

        while (true) {
            currentCoroutineContext().ensureActive()
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            buffer.position(0)
            buffer.limit(size)
            readChunk(buffer, size, channels, encoding)?.let(chunks::add)
            extractor.advance()
        }
    }

    private fun readChunk(
        buffer: ByteBuffer,
        size: Int,
        channels: Int,
        encoding: Int,
    ): Chunk? {
        val safeChannels = channels.coerceIn(1, 8)
        return when (encoding) {
            AudioFormat.ENCODING_PCM_32BIT -> {
                val samples = buffer.asIntBuffer()
                val frames = min(size / (4 * safeChannels), samples.remaining() / safeChannels)
                if (frames <= 0) return null
                var sum = 0.0
                for (frame in 0 until frames) {
                    var mixed = 0.0
                    for (channel in 0 until safeChannels) {
                        mixed += abs(samples.get().toDouble()) / 2_147_483_648.0
                    }
                    mixed /= safeChannels
                    sum += mixed * mixed
                }
                Chunk(frames, sqrt(sum / frames).toFloat())
            }

            AudioFormat.ENCODING_PCM_FLOAT -> {
                val samples = buffer.asFloatBuffer()
                val frames = min(size / (4 * safeChannels), samples.remaining() / safeChannels)
                if (frames <= 0) return null
                var sum = 0.0
                for (frame in 0 until frames) {
                    var mixed = 0.0
                    for (channel in 0 until safeChannels) {
                        mixed += abs(samples.get().toDouble()).coerceAtMost(1.0)
                    }
                    mixed /= safeChannels
                    sum += mixed * mixed
                }
                Chunk(frames, sqrt(sum / frames).toFloat())
            }

            AudioFormat.ENCODING_PCM_8BIT -> {
                val frames = min(size, buffer.remaining()) / safeChannels
                if (frames <= 0) return null
                var sum = 0.0
                for (frame in 0 until frames) {
                    var mixed = 0.0
                    for (channel in 0 until safeChannels) {
                        val sample = (buffer.get().toInt() and 0xFF) - 128
                        mixed += abs(sample) / 128.0
                    }
                    mixed /= safeChannels
                    sum += mixed * mixed
                }
                Chunk(frames, sqrt(sum / frames).toFloat())
            }

            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val bytesPerFrame = 3 * safeChannels
                val frames = min(size, buffer.remaining()) / bytesPerFrame
                if (frames <= 0) return null
                var sum = 0.0
                for (frame in 0 until frames) {
                    var mixed = 0.0
                    for (channel in 0 until safeChannels) {
                        val low = buffer.get().toInt() and 0xFF
                        val mid = buffer.get().toInt() and 0xFF
                        val high = buffer.get().toInt()
                        var sample = low or (mid shl 8) or (high shl 16)
                        if (sample and 0x800000 != 0) sample = sample or 0xFF000000.toInt()
                        mixed += abs(sample) / 8_388_608.0
                    }
                    mixed /= safeChannels
                    sum += mixed * mixed
                }
                Chunk(frames, sqrt(sum / frames).toFloat())
            }

            else -> {
                val samples = buffer.asShortBuffer()
                val frames = min(size / (2 * safeChannels), samples.remaining() / safeChannels)
                if (frames <= 0) return null
                var sum = 0.0
                for (frame in 0 until frames) {
                    var mixed = 0.0
                    for (channel in 0 until safeChannels) {
                        mixed += abs(samples.get().toInt()) / 32768.0
                    }
                    mixed /= safeChannels
                    sum += mixed * mixed
                }
                Chunk(frames, sqrt(sum / frames).toFloat())
            }
        }
    }

    /**
     * Distributes decoded chunks over a fixed number of bars and lifts quiet parts with
     * a square-root curve. Peak per bucket (not average) keeps transients visible.
     */
    private fun toBars(chunks: List<Chunk>, bars: Int): FloatArray? {
        val totalFrames = chunks.sumOf { it.frames.toLong() }
        if (totalFrames <= 0L) return null

        val peaks = FloatArray(bars)
        var position = 0L
        chunks.forEach { chunk ->
            var start = position
            val end = position + chunk.frames
            while (start < end) {
                val bar = ((start * bars) / totalFrames).toInt().coerceIn(0, bars - 1)
                val barEnd = ((bar + 1L) * totalFrames) / bars
                val takeUntil = min(end, max(barEnd, start + 1))
                if (takeUntil > start) {
                    peaks[bar] = max(peaks[bar], chunk.rms)
                }
                start = takeUntil
            }
            position = end
        }

        return normalize(peaks)
    }

    /** Scales values to 0..1 with a square-root curve so quiet sections stay visible. */
    private fun normalize(values: FloatArray): FloatArray {
        val loudest = values.maxOrNull() ?: 0f
        if (loudest <= 0.0001f) return FloatArray(values.size) { MIN_BAR }
        return FloatArray(values.size) { index ->
            val normalized = (values[index] / loudest).coerceIn(0f, 1f)
            (sqrt(normalized) * 0.96f + MIN_BAR).coerceIn(MIN_BAR, 1f)
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private fun MediaFormat.longOr(key: String, fallback: Long): Long =
        if (containsKey(key)) getLong(key) else fallback

    private data class Chunk(val frames: Int, val rms: Float)

    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val RAW_BUFFER_BYTES = 256 * 1024
    private const val WINDOW_US = 200_000L
    private const val MIN_WINDOW_US = 80_000L
    private const val WINDOW_MAX_LOOPS = 2000
    private const val WINDOW_TIMEOUT_MS = 800L
    private const val SAMPLED_THRESHOLD_US = 30_000_000L
    private const val MIN_BAR = 0.04f
}
