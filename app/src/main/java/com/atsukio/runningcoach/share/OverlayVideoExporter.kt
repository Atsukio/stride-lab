package com.atsukio.runningcoach.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.LandingAnnotation
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.pose.CodecFrameSource
import com.atsukio.runningcoach.ui.landingAnnotation
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Re-encodes a clip with the skeleton drawn on every frame and each landing's measurements pinned for a
 * moment after the foot lands. Runs entirely on the phone. Slow (about the length of the clip, or more),
 * so call it off the main thread.
 */
internal object OverlayVideoExporter {
    /** The long side of the exported video. Smaller than the original to keep encoding quick and the file shareable. */
    private const val MAX_SIDE = 1280

    /** How long a landing's measurements stay on screen, in seconds. */
    private const val HOLD_SECONDS = 0.4f

    suspend fun export(
        video: File,
        track: PoseTrack,
        metrics: GaitMetrics,
        playbackFps: Float,
        out: File,
        onProgress: (Float) -> Unit = {},
    ) {
        out.delete()
        val rotation = readRotation(video)
        val hold = (HOLD_SECONDS * playbackFps).roundToInt().coerceAtLeast(1)
        val landingWindow = (playbackFps * 0.05f).roundToInt().coerceAtLeast(1)
        // Each landing's measurements are worked out once, then drawn on the frames that follow it.
        val annotations: List<Pair<Int, LandingAnnotation>> = metrics.contactFrames.mapNotNull { frame ->
            landingAnnotation(track, metrics, frame)?.let { frame to it }
        }
        val total = track.frames.size.coerceAtLeast(1)
        var encoder: VideoEncoder? = null
        var firstTimestamp = -1L
        var written = 0
        try {
            CodecFrameSource(video, rotation, MAX_SIDE).frames { frame ->
                val active = encoder ?: VideoEncoder(frame.bitmap.width, frame.bitmap.height, playbackFps, out).also { encoder = it }
                if (firstTimestamp < 0) firstTimestamp = frame.timestampUs
                val canvasBitmap = if (frame.bitmap.isMutable) frame.bitmap else frame.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                paint(canvasBitmap, frame.index, track, metrics.contactFrames, annotations, hold, landingWindow)
                active.add(canvasBitmap, frame.timestampUs - firstTimestamp)
                // Two full frames per video frame: freed now rather than left for the garbage collector.
                if (canvasBitmap !== frame.bitmap) canvasBitmap.recycle()
                frame.bitmap.recycle()
                written++
                onProgress((frame.index + 1f) / total)
            }
            val finished = checkNotNull(encoder) { "The clip has no frames to export" }
            finished.finish()
        } finally {
            encoder?.close()
        }
        check(written > 0) { "The clip has no frames to export" }
    }

    private fun paint(
        bitmap: Bitmap,
        index: Int,
        track: PoseTrack,
        contacts: List<Int>,
        annotations: List<Pair<Int, LandingAnnotation>>,
        hold: Int,
        landingWindow: Int,
    ) {
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        track.frames.getOrNull(index)?.let { pose ->
            OverlayPainter.drawPose(canvas, pose, w, h, landing = contacts.any { abs(it - index) <= landingWindow })
        }
        // The most recent landing, while it is still fresh.
        annotations.lastOrNull { (frame, _) -> index >= frame && index - frame < hold }?.let { (_, annotation) ->
            OverlayPainter.drawLanding(canvas, annotation, w, h)
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 11f * minOf(w, h) / 360f
        }
        canvas.drawText("Stride Lab", 8f * w / 360f, h - 8f * w / 360f, mark)
    }

    private fun readRotation(video: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } finally {
            retriever.release()
        }
    }
}
