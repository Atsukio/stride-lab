package com.atsukio.runningcoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
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
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.math.abs
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
    internal val durationMs: Long,
    internal val slowMotionFps: Float?,
) {
    /** The same video once every frame has been decoded and counted. */
    internal fun withFrameCount(count: Int): VideoInfo {
        val fps = count * 1000f / durationMs
        return VideoInfo(width, height, count, fps, captureFps(fps, slowMotionFps), rotation, indexed, durationMs, slowMotionFps)
    }

    /** The same video put on an even timeline of [count] frames at [fps]; slow motion keeps its ratio. */
    internal fun withTiming(count: Int, fps: Float): VideoInfo {
        val slowMotion = captureFps / playbackFps
        return VideoInfo(width, height, count, fps, fps * slowMotion, rotation, indexed, durationMs, slowMotionFps)
    }
}

private fun captureFps(playbackFps: Float, slowMotionFps: Float?) =
    slowMotionFps?.takeIf { it > playbackFps * 1.5f } ?: playbackFps

/**
 * Runs MediaPipe pose detection on every frame of a video, entirely on the device. Frames are decoded
 * on one thread while the previous frames are being detected on another.
 */
class PoseExtractor(private val context: Context, private val useGpu: Boolean = true) {

    /**
     * The pose track, plus any problems live analysis had (shown to the runner so they can turn it off)
     * and [notes] about the video worth adding to the clip's warnings.
     */
    class Extraction(
        val track: PoseTrack,
        val usedLive: Boolean,
        val liveProblems: List<String>,
        val notes: List<String> = emptyList(),
    )

    suspend fun extract(
        video: File,
        live: LiveResult? = null,
        onInfo: (VideoInfo) -> Unit = {},
        onProgress: (Float) -> Unit,
    ): Extraction = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.path)
            var info = readInfo(retriever)
            onInfo(info)
            val problems = mutableListOf<String>()
            if (live != null) {
                val track = fromLive(video, info, live, problems)
                onProgress(1f)
                if (track != null) return@withContext Extraction(track, usedLive = true, problems)
            }
            val started = SystemClock.elapsedRealtime()
            val fast = try {
                detectFromCodec(video, info, onProgress).takeIf { it.poses.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fast decoding failed, using the slower frame-by-frame path", e)
                null
            }
            val result = fast ?: detect(RetrieverFrameSource(retriever, info, MAX_INPUT_SIDE), info.frameCount, onProgress)
            val notes = mutableListOf<String>()
            // The fast path knows each frame's real time, so variable frame rates and late starts are handled,
            // and frames the second pass skipped are filled in.
            // The slow path counts frames, so take their real times from the file's index instead.
            val timesUs = fast?.timesUs ?: sampleTimesUs(video)?.takeIf { abs(it.size - result.poses.size) <= 2 }
            val regular = timesUs?.let { FrameTiming.regularise(it, result.poses) }
            val frames: List<FramePose?>
            if (regular != null) {
                frames = regular.frames
                info = info.withTiming(frames.size, regular.fps)
                onInfo(info)
                regular.droppedToFps?.let {
                    notes += "The camera's frame rate dropped to about ${it.roundToInt()} fps in parts of this clip, " +
                        "usually because of dim light. More light gives more precise results."
                }
            } else {
                val count = fast?.timesUs?.size ?: ((result.poses.keys.maxOrNull() ?: -1) + 1)
                frames = List(count) { result.poses[it] }
                if (fast != null && !info.indexed) {
                    // Without a frame count the estimate assumed 30 fps; now we know the real number.
                    info = info.withFrameCount(count)
                    onInfo(info)
                }
            }
            val seconds = (SystemClock.elapsedRealtime() - started) / 1000f
            Log.i(
                TAG,
                "Analysed ${frames.size} frames in %.1f s (%.0f fps), detected ${result.poses.size} using ${result.source}, frames per detector: ${result.workers}; runner missing in ${frames.count { it == null }}"
                    .format(seconds, frames.size / max(seconds, 0.001f)),
            )
            Extraction(PoseTrack(info.captureFps, info.width, info.height, frames), usedLive = false, problems, notes)
        } finally {
            retriever.release()
        }
    }

    /**
     * Builds the track from poses found while recording, or returns null (adding the reason to [problems])
     * if they can't be used, so the clip is analysed the usual way instead.
     */
    private suspend fun fromLive(video: File, info: VideoInfo, live: LiveResult, problems: MutableList<String>): PoseTrack? {
        val started = SystemClock.elapsedRealtime()
        if (live.camera60Fps && info.playbackFps < 50) {
            problems += "Recording ran at ${info.playbackFps.roundToInt()} fps instead of 60 while analysing live."
        }
        if (live.gotHot) problems += "Your phone got hot."
        val liveAspect = live.frameWidth.toFloat() / max(1, live.frameHeight)
        val videoAspect = info.width.toFloat() / max(1, info.height)
        if (live.frameWidth == 0 || abs(liveAspect / videoAspect - 1) > 0.03f) {
            Log.w(TAG, "Live frames ${live.frameWidth}x${live.frameHeight} don't match video ${info.width}x${info.height}")
            problems += "The live camera view didn't match the video's framing, so this clip was analysed after recording."
            return null
        }
        val videoThumbs = try {
            buildList<LiveAlignment.Thumb> {
                CodecFrameSource(video, info.rotation, MAX_INPUT_SIDE, limit = ALIGN_FRAMES).frames {
                    add(Thumbnails.of(it.bitmap, it.timestampMs * 1_000_000))
                    it.bitmap.recycle()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't decode the start of the video for live matching", e)
            emptyList()
        }
        val start = LiveAlignment.videoStartNs(videoThumbs, live.thumbs, live.beginNs)
        if (start == null) {
            Log.w(TAG, "Couldn't match ${videoThumbs.size} video frames to ${live.thumbs.size} live frames")
            problems += "Live results couldn't be matched to the video, so this clip was analysed after recording."
            return null
        }
        val frames = LiveAlignment.resample(
            live.poses, start + videoThumbs.first().timeNs, info.frameCount, info.playbackFps, MAX_LIVE_GAP_NS,
        )
        val coverage = frames.count { it != null }.toFloat() / max(1, frames.size)
        Log.i(
            TAG,
            "Live: ${live.poses.size} poses for ${frames.size} frames, %.0f%% covered, matched in %d ms"
                .format(coverage * 100, SystemClock.elapsedRealtime() - started),
        )
        val percent = (coverage * 100).roundToInt()
        if (coverage < MIN_LIVE_COVERAGE) {
            problems += "Live analysis kept up with only $percent% of frames, so this clip was analysed after recording."
            return null
        }
        if (coverage < GOOD_LIVE_COVERAGE) {
            problems += "Live analysis kept up with only $percent% of frames, so results may be less precise."
        }
        return PoseTrack(info.captureFps, info.width, info.height, frames)
    }

    /** Every video frame's presentation time in order, read from the file without decoding, or null. */
    private fun sampleTimesUs(video: File): LongArray? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(video.path)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            extractor.selectTrack(track)
            FrameTiming.sampleTimesUs(
                generateSequence { extractor.sampleTime.takeIf { it >= 0 }?.also { extractor.advance() } },
            )
        } finally {
            extractor.release()
        }
    }.onFailure { Log.w(TAG, "Couldn't read frame times", it) }.getOrNull()

    /** Poses by frame position, for the frames that were analysed; [timesUs] is every frame's time (fast path only). */
    private class Detected(
        val poses: Map<Int, FramePose?>,
        val source: String,
        val workers: String,
        val timesUs: LongArray = LongArray(0),
    )

    /**
     * Two passes for 60 fps clips: every other frame first, then the skipped frames around each foot landing,
     * where 60 fps matters. The rest of the stride is filled in from neighbouring frames. Falls back to every
     * frame if the first pass can't find the landings.
     */
    private suspend fun detectFromCodec(video: File, info: VideoInfo, onProgress: (Float) -> Unit): Detected {
        val times = ArrayList<Long>(info.frameCount)
        fun source(wanted: (Int) -> Boolean, lastWanted: Int = Int.MAX_VALUE, recordTimes: Boolean = false) =
            CodecFrameSource(
                video, info.rotation, MAX_INPUT_SIDE,
                wanted = wanted,
                lastWanted = lastWanted,
                onFrameTime = { index, timeUs -> if (recordTimes && index == times.size) times += timeUs },
            )

        if (info.playbackFps < TWO_PASS_MIN_FPS || info.frameCount < TWO_PASS_MIN_FRAMES) {
            val all = detect(source({ true }, recordTimes = true), info.frameCount, onProgress)
            return Detected(all.poses, all.source, all.workers, times.toLongArray())
        }
        val first = detect(source({ it % 2 == 0 }, recordTimes = true), info.frameCount / 2) {
            onProgress(it * FIRST_PASS_SHARE)
        }
        val timesUs = times.toLongArray()
        val near = landingNeighbourhoods(first.poses, timesUs, info)
        val todo = (near ?: timesUs.indices.toSet()) - first.poses.keys
        Log.i(TAG, if (near != null) "Second pass: ${todo.size} frames around landings" else "Second pass: landings not found, every frame")
        if (todo.isEmpty()) return Detected(first.poses, first.source, first.workers, timesUs)
        val second = detect(source({ it in todo }, lastWanted = todo.max()), todo.size) {
            onProgress(FIRST_PASS_SHARE + it * (1 - FIRST_PASS_SHARE))
        }
        return Detected(first.poses + second.poses, first.source, "${first.workers}; ${second.workers}", timesUs)
    }

    /** Frame positions within [LANDING_WINDOW_S] of each foot landing found in the first pass, or null. */
    private fun landingNeighbourhoods(poses: Map<Int, FramePose?>, timesUs: LongArray, info: VideoInfo): Set<Int>? {
        val regular = FrameTiming.regularise(timesUs, poses) ?: return null
        val slowMotion = info.captureFps / info.playbackFps
        val provisional = PoseTrack(regular.fps * slowMotion, info.width, info.height, regular.frames)
        // Only the landing times are used here, so the height doesn't matter.
        val metrics = (GaitAnalyzer.analyze(provisional, runnerHeightCm = 175.0) as? GaitResult.Success)?.metrics
            ?: return null
        if (metrics.contactFrames.size < 4) return null
        val window = (LANDING_WINDOW_S * regular.fps).roundToInt()
        val near = HashSet<Int>()
        for (contact in metrics.contactFrames) {
            val timeUs = (contact * 1e6 / regular.fps).toLong()
            val centre = timesUs.indices.minBy { abs(timesUs[it] - timeUs) }
            for (i in centre - window..centre + window) if (i in timesUs.indices) near += i
        }
        return near
    }

    /**
     * Decodes on one thread and detects on others. Every frame is independent in image mode, so detectors
     * share the work; results are keyed by frame position. [expected] is how many frames will come.
     */
    private suspend fun detect(source: FrameSource, expected: Int, onProgress: (Float) -> Unit): Detected =
        coroutineScope {
            val decoded = Channel<DecodedFrame>(BUFFERED_FRAMES)
            launch {
                try {
                    source.frames { decoded.send(it) }
                } finally {
                    decoded.close()
                }
            }
            val results = HashMap<Int, FramePose?>(expected * 2)
            val done = AtomicInteger()
            // One kind of detector per clip: GPU and CPU results differ slightly, and mixing them makes points jitter.
            val count = workerCount()
            val onGpu = List(count) { CompletableDeferred<Boolean>() }
            val workers = List(count) { i ->
                async {
                    // Each MediaPipe instance stays on its own thread.
                    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { thread ->
                        withContext(thread) {
                            Landmarker(context, preferGpu = useGpu).use { landmarker ->
                                onGpu[i].complete(landmarker.usingGpu)
                                // A detector that couldn't get the GPU sits out if another one did.
                                if (!landmarker.usingGpu && onGpu.awaitAll().any { it }) return@withContext "CPU skipped"
                                var handled = 0
                                for (frame in decoded) {
                                    val pose = landmarker.detect(frame.bitmap)
                                    frame.bitmap.recycle()
                                    synchronized(results) { results[frame.index] = pose }
                                    handled++
                                    onProgress(min(1f, done.incrementAndGet().toFloat() / max(1, expected)))
                                }
                                "${if (landmarker.usingGpu) "GPU" else "CPU"} $handled"
                            }
                        }
                    }
                }
            }.awaitAll()
            Detected(results, source.name, workers.joinToString(", "))
        }

    /**
     * Two detectors overlap one frame's upload and read-back with another's work, on the GPU or the CPU,
     * when there are cores to spare beside the decoder.
     */
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
        /** Frames are shrunk to this before detection; the model itself works at 256 px. */
        const val MAX_INPUT_SIDE = 720
        const val TWO_PASS_MIN_FPS = 50f
        const val TWO_PASS_MIN_FRAMES = 240
        const val FIRST_PASS_SHARE = 0.7f
        const val LANDING_WINDOW_S = 0.07
        const val BUFFERED_FRAMES = 4
        const val ALIGN_FRAMES = 30
        const val MAX_LIVE_GAP_NS = 50_000_000L
        const val MIN_LIVE_COVERAGE = 0.5f
        const val GOOD_LIVE_COVERAGE = 0.75f
    }
}

/** Wraps PoseLandmarker, falling back from GPU to CPU on devices where the GPU delegate fails. */
internal class Landmarker(private val context: Context, preferGpu: Boolean) : AutoCloseable {
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
