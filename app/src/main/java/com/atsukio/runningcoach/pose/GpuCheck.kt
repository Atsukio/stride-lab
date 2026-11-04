package com.atsukio.runningcoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import java.io.File
import kotlin.math.hypot

/**
 * Checks once per phone and system version that GPU pose detection agrees with the CPU, which is
 * MediaPipe's reference. Some graphics drivers give noticeably different landmarks (seen on a Pixel 10
 * Pro, where cadence came out 10% low), and then the CPU is used instead.
 */
object GpuCheck {
    enum class Verdict { UNKNOWN, AGREES, DISAGREES, UNAVAILABLE }

    fun verdict(context: Context): Verdict {
        val stored = prefs(context).getString(key(), null) ?: return Verdict.UNKNOWN
        return runCatching { Verdict.valueOf(stored) }.getOrDefault(Verdict.UNKNOWN)
    }

    /** Whether to use the GPU, given the runner's setting and what the check found. */
    fun shouldUseGpu(context: Context, userWantsGpu: Boolean): Boolean =
        userWantsGpu && verdict(context).let { it == Verdict.AGREES || it == Verdict.UNKNOWN }

    /** Runs the check on frames from [video] if it hasn't been run on this phone yet. */
    suspend fun ensureChecked(context: Context, video: File) {
        if (verdict(context) != Verdict.UNKNOWN) return
        val rotation = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull() ?: 0
        val frames = try {
            val sampled = ArrayList<Bitmap>()
            var index = 0
            CodecFrameSource(video, rotation, maxSide = 720, limit = SAMPLE_SPAN).frames { frame ->
                if (index++ % SAMPLE_EVERY == 0) sampled.add(frame.bitmap) else frame.bitmap.recycle()
            }
            sampled
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't decode frames for the GPU check", e)
            return
        }
        try {
            val gpu = Landmarker(context, preferGpu = true)
            val gpuPoses = gpu.use { landmarker ->
                if (!landmarker.usingGpu) null else frames.map { bitmap -> landmarker.detect(bitmap) }
            }
            val verdict = if (gpuPoses == null) {
                Verdict.UNAVAILABLE
            } else {
                val cpuPoses = Landmarker(context, preferGpu = false).use { cpu -> frames.map { bitmap -> cpu.detect(bitmap) } }
                compare(gpuPoses, cpuPoses, frames.firstOrNull()?.let { it.width.toDouble() / it.height } ?: 1.0)
            }
            Log.i(TAG, "GPU check on ${Build.MODEL}: $verdict")
            prefs(context).edit { putString(key(), verdict.name) }
        } catch (e: Exception) {
            Log.w(TAG, "GPU check failed", e)
        } finally {
            frames.forEach { it.recycle() }
        }
    }

    /**
     * Compares leg landmarks, in units of frame height. The CPU and GPU normally agree to well under 1%;
     * a driver problem shows up as a large typical difference, many badly-off frames, or missed runners.
     */
    internal fun compare(gpu: List<FramePose?>, cpu: List<FramePose?>, aspect: Double): Verdict {
        val pairs = gpu.zip(cpu)
        val cpuFound = pairs.count { it.second != null }
        if (cpuFound < 3) return Verdict.UNKNOWN
        val gpuMissed = pairs.count { it.second != null && it.first == null }
        if (gpuMissed > cpuFound * 0.2) return Verdict.DISAGREES
        val differences = pairs.mapNotNull { (g, c) ->
            if (g == null || c == null) return@mapNotNull null
            LEG.map { i -> hypot((g.x[i] - c.x[i]) * aspect, (g.y[i] - c.y[i]).toDouble()) }.average()
        }.sorted()
        val median = differences[differences.size / 2]
        val badlyOff = differences.count { it > BAD_FRAME_DIFFERENCE }
        return if (median > MAX_MEDIAN_DIFFERENCE || badlyOff > differences.size * 0.2) Verdict.DISAGREES else Verdict.AGREES
    }

    private val LEG = (Landmark.LEFT_HIP..Landmark.RIGHT_FOOT_INDEX).toList()
    private const val MAX_MEDIAN_DIFFERENCE = 0.01
    private const val BAD_FRAME_DIFFERENCE = 0.03
    private const val SAMPLE_SPAN = 120
    private const val SAMPLE_EVERY = 10
    private const val TAG = "GpuCheck"

    // MediaPipe or a system update (new graphics driver) can change the answer, so both are in the key.
    private fun key() = "gpu_check_${Build.FINGERPRINT}_mediapipe_0.10.26"

    private fun prefs(context: Context) = context.getSharedPreferences("gpu_check", Context.MODE_PRIVATE)
}
