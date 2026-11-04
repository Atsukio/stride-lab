package com.atsukio.runningcoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Frame rate and upright size of a video file. */
class VideoInfo(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val playbackFps: Float,
    /** Real-world frame rate; higher than [playbackFps] for slow-motion clips. */
    val captureFps: Float,
    internal val rotation: Int,
    internal val indexed: Boolean,
)

/** Runs MediaPipe pose detection on every frame of a video, entirely on the device. */
class PoseExtractor(private val context: Context) {

    suspend fun extract(
        video: File,
        onInfo: (VideoInfo) -> Unit = {},
        onProgress: (Float) -> Unit,
    ): PoseTrack = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.path)
            val info = readInfo(retriever)
            onInfo(info)
            val batchSize = if (info.width * info.height > 2_500_000) 2 else 6
            val frames = ArrayList<FramePose?>(info.frameCount)
            Landmarker(context).use { landmarker ->
                var index = 0
                while (index < info.frameCount) {
                    ensureActive()
                    val count = min(batchSize, info.frameCount - index)
                    // Frame counts in metadata can overshoot slightly; stop at the last readable frame.
                    val bitmaps = runCatching { fetch(retriever, info, index, count) }.getOrNull()
                    if (bitmaps.isNullOrEmpty()) break
                    for ((i, bitmap) in bitmaps.withIndex()) {
                        val input = prepare(bitmap, info)
                        val timestampMs = ((index + i) * 1000.0 / info.playbackFps).toLong()
                        frames += landmarker.detect(input, timestampMs)
                        if (input !== bitmap) input.recycle()
                        bitmap.recycle()
                    }
                    index += bitmaps.size
                    onProgress(index.toFloat() / info.frameCount)
                }
            }
            PoseTrack(info.captureFps, info.width, info.height, frames)
        } finally {
            retriever.release()
        }
    }

    private fun readInfo(r: MediaMetadataRetriever): VideoInfo {
        fun meta(key: Int) = r.extractMetadata(key)
        val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: error("Couldn't read the video's length.")
        val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val rawWidth = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawHeight = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val sideways = rotation == 90 || rotation == 270
        val metaCount = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
        val indexed = metaCount != null && metaCount > 0
        val playbackFps = if (indexed) metaCount!! * 1000f / durationMs else FALLBACK_FPS
        val frameCount = if (indexed) metaCount!! else (durationMs * FALLBACK_FPS / 1000).toInt()
        // Slow-motion files record their true capture rate separately.
        val captureFps = meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            ?.takeIf { it > playbackFps * 1.5f } ?: playbackFps
        return VideoInfo(
            width = if (sideways) rawHeight else rawWidth,
            height = if (sideways) rawWidth else rawHeight,
            frameCount = frameCount,
            playbackFps = playbackFps,
            captureFps = captureFps,
            rotation = rotation,
            indexed = indexed,
        )
    }

    private fun fetch(r: MediaMetadataRetriever, info: VideoInfo, start: Int, count: Int): List<Bitmap> {
        if (info.indexed) {
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            return r.getFramesAtIndex(start, count, params)
        }
        return (start until start + count).mapNotNull { i ->
            val timeUs = (i * 1_000_000L / FALLBACK_FPS).toLong()
            r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }
    }

    /** Makes the frame upright and small enough for fast detection; the model works at 256 px anyway. */
    private fun prepare(bitmap: Bitmap, info: VideoInfo): Bitmap {
        var frame = bitmap
        val needsRotation = info.rotation != 0 && frame.width != info.width && frame.width == info.height
        if (needsRotation) {
            val matrix = Matrix().apply { postRotate(info.rotation.toFloat()) }
            frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
        }
        val scale = MAX_INPUT_SIDE.toFloat() / max(frame.width, frame.height)
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

    private companion object {
        const val FALLBACK_FPS = 30f
        const val MAX_INPUT_SIDE = 960
    }
}

/** Wraps PoseLandmarker, falling back from GPU to CPU on devices where the GPU delegate fails. */
private class Landmarker(private val context: Context) : AutoCloseable {
    private var usingGpu = true
    private var landmarker: PoseLandmarker = try {
        create(Delegate.GPU)
    } catch (e: Exception) {
        usingGpu = false
        create(Delegate.CPU)
    }

    fun detect(bitmap: Bitmap, timestampMs: Long): FramePose? {
        val image = BitmapImageBuilder(bitmap).build()
        val result = try {
            landmarker.detectForVideo(image, timestampMs)
        } catch (e: RuntimeException) {
            if (!usingGpu) throw e
            usingGpu = false
            landmarker.close()
            landmarker = create(Delegate.CPU)
            landmarker.detectForVideo(image, timestampMs)
        }
        val points = result.landmarks().firstOrNull() ?: return null
        if (points.size < Landmark.COUNT) return null
        return FramePose(
            x = FloatArray(Landmark.COUNT) { points[it].x() },
            y = FloatArray(Landmark.COUNT) { points[it].y() },
            visibility = FloatArray(Landmark.COUNT) { points[it].visibility().orElse(0f) },
        )
    }

    override fun close() = landmarker.close()

    private fun create(delegate: Delegate): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }
}
