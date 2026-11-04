package com.atsukio.runningcoach.pose

import com.atsukio.runningcoach.analysis.FramePose
import kotlin.math.abs
import kotlin.math.min

/**
 * Lines up poses found live during recording with the frames of the saved video.
 *
 * The live analysis stream and the video come from the same camera frames, but the video's timestamps
 * start at zero while the live ones use the camera clock. A tiny greyscale thumbnail of every live frame
 * is kept, and the first frames of the video are matched against them to find the exact offset.
 */
internal object LiveAlignment {
    class Thumb(val timeNs: Long, val width: Int, val height: Int, val gray: ByteArray)
    class TimedPose(val timeNs: Long, val pose: FramePose?)

    /**
     * Camera-clock time of video time zero, or null if the video's frames can't be found among the live ones.
     * [video] thumbnails carry the video's own timestamps; [beginNs] is roughly when recording started.
     */
    fun videoStartNs(video: List<Thumb>, live: List<Thumb>, beginNs: Long): Long? {
        if (video.isEmpty() || live.size < 3) return null
        val sorted = live.sortedBy { it.timeNs }
        val times = LongArray(sorted.size) { sorted[it].timeNs }
        val first = video.first().timeNs
        val candidates = sorted.filter { it.timeNs in (beginNs - SEARCH_BEFORE_NS)..(beginNs + SEARCH_AFTER_NS) }
            .map { it.timeNs - first }
        val costs = candidates.mapNotNull { start ->
            var total = 0.0
            var matched = 0
            for (v in video) {
                val l = nearest(times, start + v.timeNs, MATCH_TOLERANCE_NS) ?: continue
                val thumb = sorted[l]
                if (thumb.width != v.width || thumb.height != v.height) continue
                total += difference(v.gray, thumb.gray)
                matched++
            }
            if (matched < video.size * MIN_MATCHED_FRACTION) null else start to total / matched
        }
        if (costs.size < 3) return null
        val best = costs.minBy { it.second }
        val typical = costs.map { it.second }.sorted()[costs.size / 2]
        // The true offset matches the very same frames, so it should stand out clearly from the rest.
        return if (best.second < typical * DISTINCT_RATIO) best.first else null
    }

    /**
     * One pose per video frame, frame i being at [firstFrameNs] + i / [fps] on the camera clock. Uses the
     * pose found for that frame, or interpolates between poses up to [maxGapNs] apart; null otherwise.
     */
    fun resample(poses: List<TimedPose>, firstFrameNs: Long, frameCount: Int, fps: Float, maxGapNs: Long): List<FramePose?> {
        val sorted = poses.sortedBy { it.timeNs }
        val times = LongArray(sorted.size) { sorted[it].timeNs }
        val tolerance = (1e9 / fps / 4).toLong()
        return List(frameCount) { i ->
            val t = firstFrameNs + (i * 1e9 / fps).toLong()
            nearest(times, t, tolerance)?.let { return@List sorted[it].pose }
            val next = upperIndex(times, t)
            val prev = next - 1
            if (prev < 0 || next >= sorted.size) return@List null
            val a = sorted[prev]
            val b = sorted[next]
            if (a.pose == null || b.pose == null || b.timeNs - a.timeNs > maxGapNs) return@List null
            lerp(a.pose, b.pose, (t - a.timeNs).toFloat() / (b.timeNs - a.timeNs))
        }
    }

    /** Mean squared difference after removing each image's average brightness. */
    fun difference(a: ByteArray, b: ByteArray): Double {
        val n = min(a.size, b.size)
        if (n == 0) return Double.MAX_VALUE
        var sumA = 0L
        var sumB = 0L
        for (i in 0 until n) {
            sumA += a[i].toInt() and 0xff
            sumB += b[i].toInt() and 0xff
        }
        val shift = (sumA - sumB).toDouble() / n
        var total = 0.0
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff) - shift
            total += d * d
        }
        return total / n
    }

    private fun lerp(a: FramePose, b: FramePose, t: Float) = FramePose(
        x = FloatArray(a.x.size) { a.x[it] + (b.x[it] - a.x[it]) * t },
        y = FloatArray(a.y.size) { a.y[it] + (b.y[it] - a.y[it]) * t },
        visibility = FloatArray(a.visibility.size) { min(a.visibility[it], b.visibility[it]) },
    )

    /** Index of the time closest to [t] if it's within [tolerance]. */
    private fun nearest(times: LongArray, t: Long, tolerance: Long): Int? {
        val next = upperIndex(times, t)
        val best = listOf(next - 1, next).filter { it in times.indices }.minByOrNull { abs(times[it] - t) } ?: return null
        return best.takeIf { abs(times[it] - t) <= tolerance }
    }

    /** First index whose time is at or after [t]. */
    private fun upperIndex(times: LongArray, t: Long): Int {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private const val SEARCH_BEFORE_NS = 500_000_000L
    private const val SEARCH_AFTER_NS = 2_500_000_000L
    private const val MATCH_TOLERANCE_NS = 3_000_000L
    private const val MIN_MATCHED_FRACTION = 0.4
    private const val DISTINCT_RATIO = 0.5
}
