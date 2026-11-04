package com.atsukio.runningcoach.analysis

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Landmark positions in pixels over time, with short dropouts filled and jitter smoothed. */
internal class Signals private constructor(
    private val xs: Array<DoubleArray>,
    private val ys: Array<DoubleArray>,
    val size: Int,
) {
    fun point(landmark: Int, frame: Int) = Vec2(xs[landmark][frame], ys[landmark][frame])

    fun mid(a: Int, b: Int, frame: Int) = midpoint(point(a, frame), point(b, frame))

    fun hipMid(frame: Int) = mid(Landmark.LEFT_HIP, Landmark.RIGHT_HIP, frame)

    fun shoulderMid(frame: Int) = mid(Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER, frame)

    fun y(landmark: Int): DoubleArray = ys[landmark]

    companion object {
        fun from(track: PoseTrack): Signals {
            val n = track.frames.size
            val xs = Array(Landmark.COUNT) { DoubleArray(n) { Double.NaN } }
            val ys = Array(Landmark.COUNT) { DoubleArray(n) { Double.NaN } }
            track.frames.forEachIndexed { f, pose ->
                if (pose == null) return@forEachIndexed
                for (i in 0 until Landmark.COUNT) {
                    xs[i][f] = pose.x[i].toDouble() * track.width
                    ys[i][f] = pose.y[i].toDouble() * track.height
                }
            }
            val maxGap = max(1, (track.fps * 0.1).roundToInt())
            val halfWindow = max(1, (track.fps / 60).roundToInt())
            for (i in 0 until Landmark.COUNT) {
                fillGaps(xs[i], maxGap)
                fillGaps(ys[i], maxGap)
                xs[i] = smooth(xs[i], halfWindow)
                ys[i] = smooth(ys[i], halfWindow)
            }
            return Signals(xs, ys, n)
        }
    }
}

/** Linearly interpolates runs of NaN no longer than [maxGap] frames. */
internal fun fillGaps(values: DoubleArray, maxGap: Int) {
    var last = -1
    for (i in values.indices) {
        if (values[i].isNaN()) continue
        val gap = i - last - 1
        if (last >= 0 && gap in 1..maxGap) {
            for (j in last + 1 until i) {
                val t = (j - last).toDouble() / (i - last)
                values[j] = values[last] + (values[i] - values[last]) * t
            }
        }
        last = i
    }
}

/** Centred moving average over ±[halfWindow] frames; NaN frames stay NaN. */
internal fun smooth(values: DoubleArray, halfWindow: Int): DoubleArray = DoubleArray(values.size) { i ->
    if (values[i].isNaN()) return@DoubleArray Double.NaN
    var sum = 0.0
    var count = 0
    for (j in max(0, i - halfWindow)..min(values.lastIndex, i + halfWindow)) {
        if (!values[j].isNaN()) {
            sum += values[j]
            count++
        }
    }
    sum / count
}
