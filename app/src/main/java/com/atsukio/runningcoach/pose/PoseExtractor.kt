package com.atsukio.runningcoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

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
    internal val durationMs: Long,
    internal val slowMotionFps: Float?,
) {
    /** The same video once every frame has been decoded and counted. */
    internal fun withFrameCount(count: Int): VideoInfo {
        val fps = count * 1000f / durationMs
        return VideoInfo(width, height, count, fps, captureFps(fps, slowMotionFps), rotation, indexed, durationMs, slowMotionFps)
    }
}

private fun captureFps(playbackFps: Float, slowMotionFps: Float?) =
    slowMotionFps?.takeIf { it > playbackFps * 1.5f } ?: playbackFps

/**
 * Runs MediaPipe pose detection on every frame of a video, entirely on the device. Frames are decoded
 * on one thread while the previous frames are being detected on another.
 */
class PoseExtractor(private val context: Context) {

    suspend fun extract(
        video: File,
        onInfo: (VideoInfo) -> Unit = {},
        onProgress: (Float) -> Unit,
    ): PoseTrack = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.path)
            var info = readInfo(retriever)
            onInfo(info)
            val started = SystemClock.elapsedRealtime()
            val fast = try {
                detectAll(CodecFrameSource(video, info.rotation, MAX_INPUT_SIDE), info, onProgress)
                    .takeIf { it.frames.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fast decoding failed, using the slower frame-by-frame path", e)
                null
            }
            val result = fast ?: detectAll(RetrieverFrameSource(retriever, info, MAX_INPUT_SIDE), info, onProgress)
            if (fast != null && !info.indexed) {
                // Without a frame count the estimate assumed 30 fps; now we know the real number.
                info = info.withFrameCount(result.frames.size)
                onInfo(info)
            }
            val seconds = (SystemClock.elapsedRealtime() - started) / 1000f
            Log.i(
                TAG,
                "Analysed ${result.frames.size} frames in %.1f s (%.0f fps) using ${result.source}, frames per detector: ${result.workers}; runner missing in ${result.frames.count { it == null }}"
                    .format(seconds, result.frames.size / max(seconds, 0.001f)),
            )
            PoseTrack(info.captureFps, info.width, info.height, result.frames)
        } finally {
            retriever.release()
        }
    }

    private class Detected(val frames: List<FramePose?>, val source: String, val workers: String)

    private class Numbered(val index: Int, val frame: DecodedFrame)

    /**
     * Decodes on one thread and detects on [workerCount] others. Every frame is independent in image mode,
     * so a GPU detector and a CPU detector can share the work; results are put back in frame order.
     */
    private suspend fun detectAll(source: FrameSource, info: VideoInfo, onProgress: (Float) -> Unit): Detected =
        coroutineScope {
            val decoded = Channel<Numbered>(BUFFERED_FRAMES)
            launch {
                try {
                    var index = 0
                    source.frames { decoded.send(Numbered(index++, it)) }
                } finally {
                    decoded.close()
                }
            }
            val results = ArrayList<FramePose?>(info.frameCount)
            val done = AtomicInteger()
            val workers = List(workerCount()) { i ->
                async {
                    // Each MediaPipe instance stays on its own thread.
                    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
                        withContext(thread) {
                            Landmarker(context, preferGpu = i == 0).use { landmarker ->
                                var count = 0
                                for (item in decoded) {
                                    val pose = landmarker.detect(item.frame.bitmap)
                                    item.frame.bitmap.recycle()
                                    synchronized(results) {
                                        while (results.size <= item.index) results.add(null)
                                        results[item.index] = pose
                                    }
                                    count++
                                    onProgress(min(1f, done.incrementAndGet().toFloat() / info.frameCount))
                                }
                                "${if (landmarker.usingGpu) "GPU" else "CPU"} $count"
                            }
                        }
                    }
                }
            }.awaitAll()
            Detected(results, source.name, workers.joinToString(", "))
        }

    /** A second, CPU detector only pays off when there are cores to spare beside the decoder. */
    private fun workerCount() = if (Runtime.getRuntime().availableProcessors() >= 6) 2 else 1

    private fun readInfo(r: MediaMetadataRetriever): VideoInfo {
        fun meta(key: Int) = r.extractMetadata(key)
        val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
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
        val slowMotionFps = meta(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
        return VideoInfo(
            width = if (sideways) rawHeight else rawWidth,
            height = if (sideways) rawWidth else rawHeight,
            frameCount = frameCount,
            playbackFps = playbackFps,
            captureFps = captureFps(playbackFps, slowMotionFps),
            rotation = rotation,
            indexed = indexed,
            durationMs = durationMs,
            slowMotionFps = slowMotionFps,
        )
    }

    private companion object {
        const val TAG = "PoseExtractor"
        const val FALLBACK_FPS = 30f
        const val MAX_INPUT_SIDE = 960
        const val BUFFERED_FRAMES = 4
    }
}

/** Wraps PoseLandmarker, falling back from GPU to CPU on devices where the GPU delegate fails. */
private class Landmarker(private val context: Context, preferGpu: Boolean) : AutoCloseable {
    var usingGpu = preferGpu
        private set

    private var landmarker: PoseLandmarker = if (!preferGpu) create(Delegate.CPU) else try {
        create(Delegate.GPU)
    } catch (e: Exception) {
        usingGpu = false
        create(Delegate.CPU)
    }

    fun detect(bitmap: Bitmap): FramePose? {
        val image = BitmapImageBuilder(bitmap).build()
        val result = try {
            landmarker.detect(image)
        } catch (e: RuntimeException) {
            if (!usingGpu) throw e
            usingGpu = false
            landmarker.close()
            landmarker = create(Delegate.CPU)
            landmarker.detect(image)
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
            // Image mode: each frame on its own. Video mode's tracking filter makes fast feet trail behind;
            // PoseCleaner smooths the whole clip afterwards without that lag.
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }
}
