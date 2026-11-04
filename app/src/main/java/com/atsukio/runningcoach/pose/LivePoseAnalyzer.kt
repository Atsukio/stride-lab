package com.atsukio.runningcoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What live analysis saw during one recording. Kept in memory only, until the clip is analysed. */
class LiveResult internal constructor(
    internal val thumbs: List<LiveAlignment.Thumb>,
    internal val poses: List<LiveAlignment.TimedPose>,
    internal val frameWidth: Int,
    internal val frameHeight: Int,
    internal val beginNs: Long,
    internal val gotHot: Boolean,
    internal val camera60Fps: Boolean,
    internal val usedGpu: Boolean,
)

/** Hands live results from the capture screen to the analysis screen. */
object LiveResults {
    private val results = ConcurrentHashMap<String, LiveResult>()

    fun put(sessionId: String, result: LiveResult) {
        results[sessionId] = result
    }

    fun take(sessionId: String): LiveResult? = results.remove(sessionId)
}

/**
 * Runs pose detection on camera frames while a clip records. Every frame gets a tiny thumbnail so the
 * poses can be matched to the saved video afterwards; frames that arrive while the detectors are busy
 * are skipped for detection. [begin] and [end] bracket one recording.
 */
class LivePoseAnalyzer(
    private val context: Context,
    private val useGpu: Boolean,
    private val camera60Fps: Boolean,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val lock = Any()
    private val thumbs = ArrayDeque<LiveAlignment.Thumb>()
    private val poses = ArrayList<LiveAlignment.TimedPose>()
    private var recording = false
    private var generation = 0
    private var beginNs = 0L
    private var lastFrameNs = 0L
    private var frameWidth = 0
    private var frameHeight = 0
    // One kind of detector per clip, as in PoseExtractor: mixing GPU and CPU results makes points jitter.
    private val workers = List(if (!useGpu && Runtime.getRuntime().availableProcessors() >= 6) 2 else 1) {
        Worker(preferGpu = useGpu)
    }

    fun begin() = synchronized(lock) {
        recording = true
        generation++
        poses.clear()
        beginNs = lastFrameNs
        trim()
    }

    /** Ends the recording and returns what was seen, or null if [begin] wasn't called. */
    fun end(): LiveResult? = synchronized(lock) {
        if (!recording) return null
        recording = false
        generation++
        val result = LiveResult(
            thumbs = thumbs.toList(),
            poses = poses.toList(),
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            beginNs = beginNs,
            gotHot = isHot(),
            camera60Fps = camera60Fps,
            usedGpu = useGpu,
        )
        Log.i(TAG, "Live: ${thumbs.size} frames seen, ${poses.size} analysed")
        thumbs.clear()
        poses.clear()
        result
    }

    override fun analyze(image: ImageProxy) {
        try {
            val timeNs = image.imageInfo.timestamp
            val upright = upright(image.toBitmap(), image.imageInfo.rotationDegrees)
            val thumb = Thumbnails.of(upright, timeNs)
            val gen: Int
            val active: Boolean
            synchronized(lock) {
                lastFrameNs = timeNs
                frameWidth = upright.width
                frameHeight = upright.height
                thumbs.addLast(thumb)
                if (!recording) trim()
                while (thumbs.size > MAX_THUMBS) thumbs.removeFirst()
                gen = generation
                active = recording
            }
            val handedOver = active && workers.any { it.tryDetect(upright, timeNs, gen) }
            if (!handedOver) upright.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Live frame failed", e)
        } finally {
            image.close()
        }
    }

    override fun close() = workers.forEach { it.close() }

    /** Before recording, keep only the last moment of frames. */
    private fun trim() {
        while (thumbs.isNotEmpty() && lastFrameNs - thumbs.first().timeNs > PRE_ROLL_NS) thumbs.removeFirst()
    }

    private fun isHot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    /** One detector on its own thread, taking a frame only when it's free. */
    private inner class Worker(preferGpu: Boolean) {
        private val executor = Executors.newSingleThreadExecutor()
        private val busy = AtomicBoolean(true)

        @Volatile
        private var landmarker: Landmarker? = null

        init {
            executor.execute {
                landmarker = runCatching { Landmarker(context, preferGpu) }
                    .onFailure { Log.w(TAG, "Live detector failed to start", it) }
                    .getOrNull()
                busy.set(false)
            }
        }

        fun tryDetect(bitmap: Bitmap, timeNs: Long, gen: Int): Boolean {
            val detector = landmarker ?: return false
            if (!busy.compareAndSet(false, true)) return false
            try {
                executor.execute {
                    try {
                        val pose = detector.detect(bitmap)
                        synchronized(lock) {
                            if (gen == generation && recording) poses += LiveAlignment.TimedPose(timeNs, pose)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Live detection failed", e)
                    } finally {
                        bitmap.recycle()
                        busy.set(false)
                    }
                }
            } catch (e: Exception) {
                busy.set(false)
                return false
            }
            return true
        }

        fun close() {
            runCatching { executor.execute { landmarker?.close() } }
            executor.shutdown()
        }
    }

    private companion object {
        const val TAG = "LivePoseAnalyzer"
        const val PRE_ROLL_NS = 500_000_000L
        const val MAX_THUMBS = 4000
        // Same as PoseExtractor.MAX_INPUT_SIDE, so live and video thumbnails are made from the same size.
        const val MAX_SIDE = 720
    }

    private fun upright(raw: Bitmap, rotation: Int): Bitmap {
        val scale = min(1f, MAX_SIDE.toFloat() / max(raw.width, raw.height))
        if (rotation == 0 && scale == 1f) return raw
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val out = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (out !== raw) raw.recycle()
        return out
    }
}

/** Tiny greyscale copies of frames, for matching live frames to video frames. */
internal object Thumbnails {
    private const val LONG_SIDE = 64

    fun of(bitmap: Bitmap, timeNs: Long): LiveAlignment.Thumb {
        val scale = LONG_SIDE.toFloat() / max(bitmap.width, bitmap.height)
        val w = max(1, (bitmap.width * scale).roundToInt())
        val h = max(1, (bitmap.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        val gray = ByteArray(w * h) { i ->
            val p = pixels[i]
            (((p shr 16 and 0xff) * 77 + (p shr 8 and 0xff) * 150 + (p and 0xff) * 29) shr 8).toByte()
        }
        return LiveAlignment.Thumb(timeNs, w, h, gray)
    }
}
