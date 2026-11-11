package com.atsukio.runningcoach.analysis

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fixes the mistakes pose detection makes on side-on running footage, before anything is measured or drawn:
 * left and right legs swapped, single-frame jumps to the wrong place, short dropouts, and jitter.
 * The smoothing is centred on each frame, so unlike MediaPipe's own tracking filter it doesn't lag fast feet.
 */
object PoseCleaner {

    /** The cleaned track in normalised coordinates, for drawing. Landmarks that couldn't be recovered are NaN. */
    fun clean(track: PoseTrack): PoseTrack {
        val c = pixels(track)
        val frames = (0 until c.size).map { f ->
            if ((0 until Landmark.COUNT).all { c.xs[it][f].isNaN() }) return@map null
            FramePose(
                x = FloatArray(Landmark.COUNT) { (c.xs[it][f] / track.width).toFloat() },
                y = FloatArray(Landmark.COUNT) { (c.ys[it][f] / track.height).toFloat() },
                visibility = c.visibility[f] ?: FloatArray(Landmark.COUNT) { FILLED_VISIBILITY },
            )
        }
        return PoseTrack(track.fps, track.width, track.height, frames)
    }

    internal class Cleaned(
        val xs: Array<DoubleArray>,
        val ys: Array<DoubleArray>,
        val visibility: Array<FloatArray?>,
        val size: Int,
    )

    /** The cleaned track in pixels, as the gait analysis uses it. */
    internal fun pixels(track: PoseTrack): Cleaned {
        val n = track.frames.size
        val xs = Array(Landmark.COUNT) { DoubleArray(n) { Double.NaN } }
        val ys = Array(Landmark.COUNT) { DoubleArray(n) { Double.NaN } }
        val visibility = arrayOfNulls<FloatArray>(n)
        track.frames.forEachIndexed { f, pose ->
            if (pose == null) return@forEachIndexed
            visibility[f] = pose.visibility.copyOf()
            for (i in 0 until Landmark.COUNT) {
                xs[i][f] = pose.x[i].toDouble() * track.width
                ys[i][f] = pose.y[i].toDouble() * track.height
            }
        }
        val scale = max(track.width, track.height).toDouble()
        fixLegSwaps(xs, ys, visibility, n, scale)
        val spikeWindow = max(2, (track.fps * 0.05).roundToInt())
        for (i in 0 until Landmark.COUNT) removeSpikes(xs[i], ys[i], spikeWindow, SPIKE_MIN_FRACTION * scale)

        val maxGap = max(1, (track.fps * 0.1).roundToInt())
        val halfWindow = max(1, (track.fps / 60).roundToInt())
        for (i in 0 until Landmark.COUNT) {
            fillGaps(xs[i], maxGap)
            fillGaps(ys[i], maxGap)
            xs[i] = smooth(xs[i], halfWindow)
            ys[i] = smooth(ys[i], halfWindow)
        }
        return Cleaned(xs, ys, visibility, n)
    }

    /**
     * Seen from the side, the two legs look alike and the detector sometimes swaps them. Where the swapped
     * labels continue each leg's recent motion far better than the given ones, swap them back.
     */
    private fun fixLegSwaps(
        xs: Array<DoubleArray>,
        ys: Array<DoubleArray>,
        visibility: Array<FloatArray?>,
        n: Int,
        scale: Double,
    ) {
        fun point(i: Int, f: Int): Vec2? =
            if (f < 0 || xs[i][f].isNaN()) null else Vec2(xs[i][f], ys[i][f])

        fun predicted(i: Int, f: Int): Vec2? {
            val previous = point(i, f - 1) ?: return null
            val before = point(i, f - 2) ?: return previous
            return previous + (previous - before)
        }

        for (f in 1 until n) {
            var keep = 0.0
            var swap = 0.0
            var complete = true
            for (k in LEFT_LEG.indices) {
                val left = point(LEFT_LEG[k], f)
                val right = point(RIGHT_LEG[k], f)
                val leftNext = predicted(LEFT_LEG[k], f)
                val rightNext = predicted(RIGHT_LEG[k], f)
                if (left == null || right == null || leftNext == null || rightNext == null) {
                    complete = false
                    break
                }
                keep += (left - leftNext).length + (right - rightNext).length
                swap += (right - leftNext).length + (left - rightNext).length
            }
            if (!complete || swap >= SWAP_RATIO * keep || keep - swap < SWAP_MIN_FRACTION * scale) continue
            for (k in LEFT_LEG.indices) {
                val a = LEFT_LEG[k]
                val b = RIGHT_LEG[k]
                xs[a][f] = xs[b][f].also { xs[b][f] = xs[a][f] }
                ys[a][f] = ys[b][f].also { ys[b][f] = ys[a][f] }
                visibility[f]?.let { v -> v[a] = v[b].also { v[b] = v[a] } }
            }
        }
    }

    private val LEFT_LEG = intArrayOf(Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX)
    private val RIGHT_LEG = intArrayOf(Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX)

    private const val SWAP_RATIO = 0.5
    private const val SWAP_MIN_FRACTION = 0.02
    private const val SPIKE_MIN_FRACTION = 0.015
    private const val FILLED_VISIBILITY = 1f
}

/**
 * Removes points that jump away from their neighbours for a frame or two: further than [minPx] and far
 * outside the usual scatter around the median point of the surrounding ±[half] frames. Smooth fast
 * motion stays, because the median of a steady movement is the middle frame itself.
 */
internal fun removeSpikes(x: DoubleArray, y: DoubleArray, half: Int, minPx: Double) {
    val spikes = BooleanArray(x.size)
    // Scratch buffers, reused: this runs for every landmark on every frame, and building lists for each
    // window made millions of short-lived objects on a long clip.
    val wx = DoubleArray(2 * half + 1)
    val wy = DoubleArray(2 * half + 1)
    val sorted = DoubleArray(2 * half + 1)
    fun median(values: DoubleArray, n: Int): Double {
        values.copyInto(sorted, 0, 0, n)
        java.util.Arrays.sort(sorted, 0, n)
        return (sorted[(n - 1) / 2] + sorted[n / 2]) / 2
    }
    for (i in x.indices) {
        if (x[i].isNaN()) continue
        var n = 0
        for (j in max(0, i - half)..minOf(x.lastIndex, i + half)) {
            if (x[j].isNaN() || y[j].isNaN()) continue
            wx[n] = x[j]
            wy[n] = y[j]
            n++
        }
        if (n < 3) continue
        val cx = median(wx, n)
        val cy = median(wy, n)
        for (k in 0 until n) wx[k] = kotlin.math.hypot(wx[k] - cx, wy[k] - cy)
        val scatter = median(wx, n)
        val deviation = kotlin.math.hypot(x[i] - cx, y[i] - cy)
        if (deviation > max(minPx, 4 * 1.4826 * scatter)) spikes[i] = true
    }
    for (i in x.indices) if (spikes[i]) {
        x[i] = Double.NaN
        y[i] = Double.NaN
    }
}
