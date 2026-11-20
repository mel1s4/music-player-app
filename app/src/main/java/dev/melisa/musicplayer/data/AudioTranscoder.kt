package dev.melisa.musicplayer.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

enum class TranscodeFailure { UnsupportedFormat, NoAudioTrack, Encoder, Muxer, Io }

class TranscodeException(
    val failure: TranscodeFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: failure.name, cause)

data class TranscodeOutput(
    val file: File,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRate: Int,
    val channels: Int,
)

/**
 * Re-encodes one audio file to AAC-LC inside an MPEG-4 container (.m4a).
 *
 * This is deliberately platform-only: [MediaExtractor] decodes whatever the device can
 * read, [MediaCodec]'s AAC encoder is mandatory on every Android device, and
 * [MediaMuxer] writes the MP4. No native library, no APK weight, no codec licensing.
 * The trade-off is output format: MediaMuxer cannot write MP3 or Ogg, so an MP3 in
 * becomes an M4A out.
 *
 * Lossy to lossy is generation loss. The caller is responsible for telling the user and
 * for refusing bitrates that would not actually save space.
 */
object AudioTranscoder {

    private val AAC_SAMPLE_RATES = intArrayOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)
    private const val AAC_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * Decodes [source] and writes AAC into [output]. [onProgress] receives 0f..1f based
     * on the decoder's presentation time. Cancelling the calling coroutine aborts the
     * conversion and deletes [output].
     */
    suspend fun transcode(
        context: Context,
        source: Uri,
        targetBitrateKbps: Int,
        output: File,
        expectedDurationMs: Long = 0L,
        /**
         * Optional excerpt, in milliseconds. Both default to the whole file, which is
         * what the compression flow uses; the comparison helper passes a 20 s window.
         */
        startMs: Long = 0L,
        endMs: Long = 0L,
        onProgress: (Float) -> Unit = {},
    ): TranscodeOutput = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var succeeded = false

        try {
            try {
                extractor.setDataSource(context, source, null)
            } catch (t: Throwable) {
                throw TranscodeException(TranscodeFailure.UnsupportedFormat, t.message, t)
            }

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                mime?.startsWith("audio/") == true
            } ?: throw TranscodeException(TranscodeFailure.NoAudioTrack)

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val sourceMime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw TranscodeException(TranscodeFailure.UnsupportedFormat)
            val sourceRate = inputFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, 0)
                .takeIf { it > 0 } ?: 44100
            val sourceChannels = inputFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, 0)
                .takeIf { it > 0 } ?: 2
            val sourceDurationUs = inputFormat.longOr(MediaFormat.KEY_DURATION, 0L)
                .takeIf { it > 0L }
                ?: (expectedDurationMs * 1000L).takeIf { it > 0L }
                ?: 0L

            // A comparison can ask for a slice instead of the whole track. Seeking to
            // the closest sync point can land a few frames early, so the loop below
            // skips samples until the requested start is reached.
            val clipStartUs = (startMs.coerceAtLeast(0L) * 1000L)
                .coerceAtMost(sourceDurationUs.takeIf { it > 0L } ?: Long.MAX_VALUE)
            val requestedEndUs = endMs.takeIf { it > startMs }?.times(1000L)
            val clipEndUs = when {
                requestedEndUs != null ->
                    requestedEndUs.coerceAtMost(sourceDurationUs.takeIf { it > 0L } ?: requestedEndUs)
                sourceDurationUs > clipStartUs -> sourceDurationUs
                else -> 0L
            }
            val durationUs = when {
                clipEndUs > clipStartUs -> clipEndUs - clipStartUs
                expectedDurationMs > 0L -> expectedDurationMs * 1000L
                else -> sourceDurationUs
            }

            val outRate = outputSampleRate(sourceRate)
            val outChannels = sourceChannels.coerceIn(1, 2)

            if (clipStartUs > 0L) {
                extractor.seekTo(clipStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                var time = extractor.sampleTime
                while (time >= 0L && time < clipStartUs) {
                    extractor.advance()
                    time = extractor.sampleTime
                }
            }

            val decoderFormat = inputFormat
            try {
                decoder = MediaCodec.createDecoderByType(sourceMime)
                decoder.configure(decoderFormat, null, null, 0)
                decoder.start()
            } catch (t: Throwable) {
                throw TranscodeException(TranscodeFailure.UnsupportedFormat, t.message, t)
            }

            val encoderFormat = MediaFormat.createAudioFormat(AAC_MIME, outRate, outChannels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateKbps * 1000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            }
            try {
                encoder = MediaCodec.createEncoderByType(AAC_MIME)
                encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()
            } catch (t: Throwable) {
                throw TranscodeException(TranscodeFailure.Encoder, t.message, t)
            }

            try {
                muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (t: Throwable) {
                throw TranscodeException(TranscodeFailure.Muxer, t.message, t)
            }

            val pipe = PcmPipe(outRate, outChannels)
            val jobContext = coroutineContext
            val sink = EncoderSink(
                encoder = encoder,
                muxer = muxer,
                sampleRate = outRate,
                channels = outChannels,
                durationUs = durationUs,
                onProgress = onProgress,
                checkActive = { jobContext.ensureActive() },
            )

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                            ?: throw TranscodeException(TranscodeFailure.Io, "decoder input")
                        val size = extractor.readSampleData(buffer, 0)
                        val sampleTime = extractor.sampleTime
                        // Only an explicit excerpt end stops early; a full conversion
                        // must not drop the last samples if their PTS overshoots the
                        // container duration slightly.
                        val pastEnd = requestedEndUs != null && clipEndUs > 0L &&
                            sampleTime > clipEndUs
                        if (size < 0 || pastEnd) {
                            decoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var drainingDecoder = true
                while (drainingDecoder) {
                    when (val outIndex = decoder.dequeueOutputBuffer(info, 0L)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> drainingDecoder = false

                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                        else -> {
                            if (outIndex >= 0) {
                                val buffer = decoder.getOutputBuffer(outIndex)
                                    ?: throw TranscodeException(TranscodeFailure.Io, "decoder output")
                                if (info.size > 0) {
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    val encoding = decoder.outputFormat.intOr(
                                        MediaFormat.KEY_PCM_ENCODING,
                                        AudioFormat.ENCODING_PCM_16BIT,
                                    )
                                    val bytes = pipe.convert(
                                        buffer = buffer,
                                        size = info.size,
                                        encoding = encoding,
                                        sourceRate = decoder.outputFormat.intOr(
                                            MediaFormat.KEY_SAMPLE_RATE,
                                            sourceRate,
                                        ),
                                        sourceChannels = decoder.outputFormat.intOr(
                                            MediaFormat.KEY_CHANNEL_COUNT,
                                            sourceChannels,
                                        ),
                                    )
                                    sink.feed(bytes)
                                    if (durationUs > 0L) {
                                        val elapsedUs =
                                            (info.presentationTimeUs - clipStartUs).coerceAtLeast(0L)
                                        onProgress(
                                            (elapsedUs.toFloat() / durationUs)
                                                .coerceIn(0f, 0.99f),
                                        )
                                    }
                                }
                                decoder.releaseOutputBuffer(outIndex, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    outputDone = true
                                    drainingDecoder = false
                                }
                            } else {
                                drainingDecoder = false
                            }
                        }
                    }
                }
            }

            sink.finish()
            if (!sink.muxerStarted) {
                throw TranscodeException(TranscodeFailure.Muxer, "no encoded audio")
            }
            muxer.stop()
            succeeded = true
            onProgress(1f)

            val durationMs = outputDurationMs(output).takeIf { it > 0L }
                ?: (durationUs / 1000L)
            TranscodeOutput(
                file = output,
                durationMs = durationMs,
                bitrateKbps = targetBitrateKbps,
                sampleRate = outRate,
                channels = outChannels,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: TranscodeException) {
            throw t
        } catch (t: Throwable) {
            throw TranscodeException(TranscodeFailure.Io, t.message, t)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            if (!succeeded) runCatching { output.delete() }
        }
    }

    /** The encoder accepts a fixed set of rates; anything else is resampled. */
    private fun outputSampleRate(sourceRate: Int): Int = when {
        sourceRate in AAC_SAMPLE_RATES -> sourceRate
        sourceRate > 48000 -> 48000
        else -> 44100
    }

    private fun outputDurationMs(file: File): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Converts decoder output to the encoder's fixed format: 16-bit interleaved PCM,
     * [channels] channels at [sampleRate]. Downmixes to mono/stereo and resamples when
     * the source rate is outside what the AAC encoder accepts.
     */
    private class PcmPipe(
        private val sampleRate: Int,
        private val channels: Int,
    ) {
        private var sourceRate = 0
        private var sourceChannels = 0
        private var resampler: StreamResampler? = null

        fun convert(
            buffer: ByteBuffer,
            size: Int,
            encoding: Int,
            sourceRate: Int,
            sourceChannels: Int,
        ): ByteArray {
            val srcChannels = sourceChannels.coerceAtLeast(1)
            if (sourceRate != this.sourceRate || srcChannels != this.sourceChannels) {
                this.sourceRate = sourceRate
                this.sourceChannels = srcChannels
                resampler = if (sourceRate != sampleRate) {
                    StreamResampler(channels, sourceRate, sampleRate)
                } else {
                    null
                }
            }

            val pcm: ShortArray
            val frames: Int
            if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                val floats = buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val samples = size / 4
                frames = samples / srcChannels
                pcm = ShortArray(frames * srcChannels) { index ->
                    val value = floats.get(index).coerceIn(-1f, 1f)
                    (value * Short.MAX_VALUE).toInt().toShort()
                }
            } else {
                val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val samples = size / 2
                frames = samples / srcChannels
                pcm = ShortArray(frames * srcChannels) { index -> shorts.get(index) }
            }
            if (frames <= 0) return ByteArray(0)

            val mixed = downmix(pcm, frames, srcChannels)
            val resampled = resampler?.process(mixed, frames) ?: mixed
            return resampled.toLittleEndianBytes()
        }

        private fun downmix(input: ShortArray, frames: Int, srcChannels: Int): ShortArray {
            if (srcChannels == channels) return input
            val out = ShortArray(frames * channels)
            if (channels == 1) {
                for (frame in 0 until frames) {
                    var sum = 0
                    for (channel in 0 until srcChannels) {
                        sum += input[frame * srcChannels + channel].toInt()
                    }
                    out[frame] = (sum / srcChannels).toShort()
                }
            } else {
                for (frame in 0 until frames) {
                    val left = input[frame * srcChannels]
                    val right = if (srcChannels > 1) input[frame * srcChannels + 1] else left
                    out[frame * 2] = left
                    out[frame * 2 + 1] = right
                }
            }
            return out
        }
    }

    /**
     * Streaming linear resampler. Keeps the previous frame and a fractional output
     * position so a chunk boundary is not a click, and tracks the global frame index so
     * the phase does not drift across chunks.
     */
    private class StreamResampler(
        private val channels: Int,
        sourceRate: Int,
        targetRate: Int,
    ) {
        private val step = sourceRate.toDouble() / targetRate
        private var previous: ShortArray? = null
        private var frameIndex = 0L
        private var nextOutput = 0.0

        fun process(input: ShortArray, frames: Int): ShortArray {
            val capacity = (frames / step).toInt() + 4
            val out = ShortArray(capacity * channels)
            var produced = 0
            var frame = 0

            if (previous == null) {
                previous = ShortArray(channels) { input[it] }
                frameIndex = 0
                for (channel in 0 until channels) {
                    out[channel] = previous!![channel]
                }
                produced = 1
                nextOutput = step
                frame = 1
            }

            while (frame < frames) {
                val current = ShortArray(channels) { input[frame * channels + it] }
                frameIndex++
                while (nextOutput <= frameIndex.toDouble() && produced < capacity) {
                    val fraction = (nextOutput - (frameIndex - 1)).coerceIn(0.0, 1.0)
                    for (channel in 0 until channels) {
                        val from = previous!![channel].toInt()
                        val to = current[channel].toInt()
                        out[produced * channels + channel] =
                            (from + (to - from) * fraction).toInt().toShort()
                    }
                    produced++
                    nextOutput += step
                }
                previous = current
                frame++
            }
            return out.copyOf(produced * channels)
        }
    }

    /** Feeds PCM to the AAC encoder and muxes whatever it produces. */
    private class EncoderSink(
        private val encoder: MediaCodec,
        private val muxer: MediaMuxer,
        private val sampleRate: Int,
        private val channels: Int,
        private val durationUs: Long,
        private val onProgress: (Float) -> Unit,
        private val checkActive: () -> Unit,
    ) {
        private val frameSize = channels * 2
        private val info = MediaCodec.BufferInfo()
        private var framesFed = 0L
        private var trackIndex = -1
        var muxerStarted = false
            private set
        private var encoderDone = false

        fun feed(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                checkActive()
                val inIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex < 0) {
                    drain()
                    continue
                }
                val buffer = encoder.getInputBuffer(inIndex) ?: continue
                buffer.clear()
                var size = minOf(buffer.capacity(), bytes.size - offset)
                size -= size % frameSize
                if (size < frameSize) break
                buffer.put(bytes, offset, size)
                encoder.queueInputBuffer(inIndex, 0, size, presentationTimeUs(), 0)
                framesFed += size / frameSize
                offset += size
                drain()
            }
        }

        fun finish() {
            while (true) {
                checkActive()
                val inIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(
                        inIndex,
                        0,
                        0,
                        presentationTimeUs(),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                    )
                    break
                }
                drain()
            }
            while (!encoderDone) {
                checkActive()
                drain()
            }
        }

        /** Drains every available encoder output; returns true on end of stream. */
        private fun drain(): Boolean {
            while (true) {
                when (val outIndex = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return false

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }

                    else -> {
                        if (outIndex < 0) return false
                        val buffer = encoder.getOutputBuffer(outIndex)
                            ?: throw TranscodeException(TranscodeFailure.Encoder, "encoder output")
                        if (info.size > 0 && muxerStarted) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                            if (durationUs > 0L) {
                                onProgress(
                                    (info.presentationTimeUs.toFloat() / durationUs)
                                        .coerceIn(0f, 0.99f),
                                )
                            }
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                            return true
                        }
                    }
                }
            }
        }

        private fun presentationTimeUs(): Long =
            framesFed * 1_000_000L / sampleRate

    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private fun MediaFormat.longOr(key: String, fallback: Long): Long =
        if (containsKey(key)) getLong(key) else fallback
}

private fun ShortArray.toLittleEndianBytes(): ByteArray {
    val bytes = ByteArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt()
        bytes[index * 2] = (value and 0xFF).toByte()
        bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return bytes
}

/** Distance between two durations, used by the caller's verification step. */
internal fun durationDeltaMs(first: Long, second: Long): Long = abs(first - second)
