package com.atsukio.runningcoach.analysis

import kotlin.math.max
import kotlin.math.min

/** Cleaned landmark positions in pixels over time. */
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
        /** Cleans [track] (see [PoseCleaner]) and converts it to pixels. */
        fun from(track: PoseTrack): Signals {
            val clean = PoseCleaner.pixels(track)
            return Signals(clean.xs, clean.ys, clean.size)
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
