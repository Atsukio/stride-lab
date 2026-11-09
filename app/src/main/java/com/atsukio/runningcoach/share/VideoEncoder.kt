package com.atsukio.runningcoach.share

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.roundToInt

/**
 * Writes bitmaps to an H.264 MP4 file, one frame at a time with explicit timestamps. Frames go in as
 * YUV buffers rather than through an input surface because a surface stamps frames with the clock,
 * and these frames are processed faster or slower than they play.
 */
internal class VideoEncoder(
    width: Int,
    height: Int,
    fps: Float,
    out: File,
) : AutoCloseable {
    /** Even sizes only: 4:2:0 video stores one colour sample per 2×2 pixels. */
    val width = width and 1.inv()
    val height = height and 1.inv()

    private val codec = MediaCodec.createEncoderByType(MIME)
    private val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private val pixels = IntArray(this.width * this.height)
    private var track = -1
    private var muxing = false
    private var codecStarted = false

    init {
        val rate = fps.roundToInt().coerceIn(1, 120)
        val format = MediaFormat.createVideoFormat(MIME, this.width, this.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, (this@VideoEncoder.width.toLong() * this@VideoEncoder.height * rate / 12).coerceIn(1_500_000L, 12_000_000L).toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, rate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        codecStarted = true
    }

    /** Adds [bitmap] (at least this encoder's size; extra edge pixels are cropped) at [timestampUs]. */
    fun add(bitmap: Bitmap, timestampUs: Long) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val index = nextInput()
        val image = checkNotNull(codec.getInputImage(index)) { "The encoder gave no input image" }
        image.use {
            val planes = it.planes
            ArgbToYuv.fill(
                pixels, width, height,
                planes[0].buffer, planes[0].rowStride, planes[0].pixelStride,
                planes[1].buffer, planes[2].buffer, planes[1].rowStride, planes[1].pixelStride,
            )
        }
        codec.queueInputBuffer(index, 0, width * height * 3 / 2, timestampUs, 0)
        drain(untilEnd = false)
    }

    /** Flushes the encoder and closes the file. Call once, after the last frame. */
    fun finish() {
        val index = nextInput()
        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(untilEnd = true)
    }

    private fun nextInput(): Int {
        repeat(MAX_WAITS) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) return index
            // The encoder's input is full: let it hand back some output.
            drain(untilEnd = false)
        }
        error("The video encoder stopped responding")
    }

    private fun drain(untilEnd: Boolean) {
        var idle = 0
        while (true) {
            val out = codec.dequeueOutputBuffer(info, if (untilEnd) TIMEOUT_US else 0L)
            when {
                out == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEnd || ++idle > MAX_WAITS) return
                }
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                }
                out >= 0 -> {
                    idle = 0
                    val data = codec.getOutputBuffer(out)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (data != null && info.size > 0 && !isConfig && muxing) {
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    override fun close() {
        if (codecStarted) runCatching { codec.stop() }
        codec.release()
        if (muxing) runCatching { muxer.stop() }
        muxer.release()
    }

    private companion object {
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        const val TIMEOUT_US = 10_000L
        const val MAX_WAITS = 500
    }
}
