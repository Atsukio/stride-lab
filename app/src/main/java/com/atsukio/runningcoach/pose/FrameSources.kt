package com.atsukio.runningcoach.pose

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** An upright, downscaled video frame ready for pose detection. */
internal class DecodedFrame(val bitmap: Bitmap, val timestampUs: Long, val index: Int) {
    val timestampMs get() = timestampUs / 1000
}

internal interface FrameSource {
    val name: String

    /** Emits every frame in order. Throws if this source can't read the video. */
    suspend fun frames(emit: suspend (DecodedFrame) -> Unit)
}

/**
 * Decodes the whole video in one forward pass with the hardware decoder. Much faster than asking
 * [MediaMetadataRetriever] for frames, which seeks back to a keyframe for every batch.
 */
internal class CodecFrameSource(
    private val video: File,
    private val rotation: Int,
    private val maxSide: Int,
    /** Stop after this many frames. */
    private val limit: Int = Int.MAX_VALUE,
    /** Which frames (by position in the file) to convert and emit; the rest are decoded and skipped. */
    private val wanted: (Int) -> Boolean = { true },
    /** Stop once past this frame. */
    private val lastWanted: Int = Int.MAX_VALUE,
    /** Called with every frame's position and presentation time, wanted or not. */
    private val onFrameTime: (index: Int, timeUs: Long) -> Unit = { _, _ -> },
) : FrameSource {
    override val name = "codec"

    override suspend fun frames(emit: suspend (DecodedFrame) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(video.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("No video track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val converter = ImageConverter(rotation, maxSide)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var idleAfterInput = 0
            var emitted = 0
            var frameIndex = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val size = extractor.readSampleData(checkNotNull(decoder.getInputBuffer(inIndex)), 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex < 0) {
                    // Some decoders never signal the end; stop once they've gone quiet.
                    if (inputDone && ++idleAfterInput > MAX_IDLE_POLLS) break
                    continue
                }
                idleAfterInput = 0
                val timestampUs = info.presentationTimeUs
                val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                var index = -1
                var bitmap: Bitmap? = null
                if (info.size > 0) {
                    index = frameIndex++
                    onFrameTime(index, timestampUs)
                    // Skipped frames never leave the decoder, which keeps a partial pass cheap.
                    if (wanted(index)) bitmap = decoder.getOutputImage(outIndex)?.use(converter::toBitmap)
                }
                decoder.releaseOutputBuffer(outIndex, false)
                if (bitmap != null) {
                    emit(DecodedFrame(bitmap, timestampUs, index))
                    if (++emitted >= limit) break
                }
                if (end || index >= lastWanted) break
            }
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val MAX_IDLE_POLLS = 300
    }
}

/** Copies decoder output into reusable arrays and converts it to an upright ARGB bitmap. */
private class ImageConverter(private val rotation: Int, private val maxSide: Int) {
    private var y = ByteArray(0)
    private var u = ByteArray(0)
    private var v = ByteArray(0)
    private var pixels = IntArray(0)

    fun toBitmap(image: Image): Bitmap {
        // 10-bit HDR (P010) stores 16-bit samples; reading each sample's high byte gives 8-bit values.
        val highByte = when (image.format) {
            ImageFormat.YUV_420_888 -> 0
            YCBCR_P010 -> 1
            else -> error("Unsupported decoder output format ${image.format}")
        }
        val crop = image.cropRect
        val planes = image.planes
        y = copy(planes[0].buffer, y)
        u = copy(planes[1].buffer, u)
        v = copy(planes[2].buffer, v)
        val frame = YuvFrame(
            width = crop.width(),
            height = crop.height(),
            y = y,
            yRowStride = planes[0].rowStride,
            yPixelStride = planes[0].pixelStride,
            u = u,
            v = v,
            uvRowStride = planes[1].rowStride,
            uvPixelStride = planes[1].pixelStride,
            left = crop.left,
            top = crop.top,
            sampleOffset = highByte,
        )
        val size = Yuv.uprightSize(frame.width, frame.height, rotation, maxSide)
        if (pixels.size != size.width * size.height) pixels = IntArray(size.width * size.height)
        Yuv.toArgb(frame, rotation, size, pixels)
        return Bitmap.createBitmap(pixels, size.width, size.height, Bitmap.Config.ARGB_8888)
    }

    private fun copy(buffer: ByteBuffer, reuse: ByteArray): ByteArray {
        val n = buffer.remaining()
        val target = if (reuse.size >= n) reuse else ByteArray(n)
        buffer.get(target, 0, n)
        return target
    }
}

/** The original, slower path: asks [MediaMetadataRetriever] for frames in small batches. Works on any file it can open. */
internal class RetrieverFrameSource(
    private val retriever: MediaMetadataRetriever,
    private val info: VideoInfo,
    private val maxSide: Int,
) : FrameSource {
    override val name = "retriever"

    override suspend fun frames(emit: suspend (DecodedFrame) -> Unit) {
        val batchSize = if (info.width * info.height > 2_500_000) 2 else 6
        var index = 0
        while (index < info.frameCount) {
            currentCoroutineContext().ensureActive()
            val count = min(batchSize, info.frameCount - index)
            // Frame counts in metadata can overshoot slightly; stop at the last readable frame.
            val bitmaps = runCatching { fetch(index, count) }.getOrNull()
            if (bitmaps.isNullOrEmpty()) break
            for ((i, bitmap) in bitmaps.withIndex()) {
                val input = prepare(bitmap)
                if (input !== bitmap) bitmap.recycle()
                emit(DecodedFrame(input, ((index + i) * 1_000_000.0 / info.playbackFps).toLong(), index + i))
            }
            index += bitmaps.size
        }
    }

    private fun fetch(start: Int, count: Int): List<Bitmap> {
        if (info.indexed) {
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            return retriever.getFramesAtIndex(start, count, params)
        }
        return (start until start + count).mapNotNull { i ->
            val timeUs = (i * 1_000_000L / info.playbackFps).toLong()
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }
    }

    /** Makes the frame upright and small enough for fast detection; the model works at 256 px anyway. */
    private fun prepare(bitmap: Bitmap): Bitmap {
        var frame = bitmap
        val needsRotation = info.rotation != 0 && frame.width != info.width && frame.width == info.height
        if (needsRotation) {
            val matrix = Matrix().apply { postRotate(info.rotation.toFloat()) }
            frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
        }
        val scale = maxSide.toFloat() / max(frame.width, frame.height)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                frame, (frame.width * scale).roundToInt(), (frame.height * scale).roundToInt(), true,
            )
            if (frame !== bitmap) frame.recycle()
            frame = scaled
        }
        if (frame.config != Bitmap.Config.ARGB_8888) {
            val converted = frame.copy(Bitmap.Config.ARGB_8888, false)
            if (frame !== bitmap) frame.recycle()
            frame = converted
        }
        return frame
    }
}

/** ImageFormat.YCBCR_P010, named here because the constant needs API 33. */
private const val YCBCR_P010 = 0x36
