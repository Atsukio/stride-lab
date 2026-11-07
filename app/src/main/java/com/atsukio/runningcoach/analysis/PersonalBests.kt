package com.atsukio.runningcoach.analysis

import kotlin.math.max

/** A milestone for one measurement in the latest clip, compared with every clip before it. */
data class Badge(val topic: String, val kind: Kind) {
    enum class Kind(val label: String) {
        /** In the good range, and no earlier clip was. */
        FIRST_IN_RANGE("First time in range"),

        /** Still outside the range, but closer to it than in any earlier clip. */
        CLOSEST_YET("Closest yet"),
    }
}

/** What an earlier clip measured, and the treadmill speed it was run at if known. */
class EarlierClip(val metrics: GaitMetrics, val speedKmh: Double?)

/**
 * Finds the measurements that improved on everything before them. Every measurement is judged against its
 * own good range as [Coach] sets it for the speed of that clip, so a clip is never compared on raw numbers
 * that naturally change with pace.
 */
object PersonalBests {
    /** How much closer to the range, as a share of the scale, counts as a real improvement rather than noise. */
    private const val MEANINGFUL_GAIN = 0.03

    fun badges(current: GaitMetrics, speedKmh: Double?, earlier: List<EarlierClip>): List<Badge> {
        if (earlier.isEmpty()) return emptyList()
        val past = earlier.map { gaps(it.metrics, it.speedKmh) }
        return gaps(current, speedKmh).mapNotNull { (key, gap) ->
            val before = past.mapNotNull { it[key] }
            if (before.isEmpty()) return@mapNotNull null
            when {
                gap == 0.0 && before.none { it == 0.0 } -> Badge(key, Badge.Kind.FIRST_IN_RANGE)
                gap > 0.0 && gap < before.min() - MEANINGFUL_GAIN -> Badge(key, Badge.Kind.CLOSEST_YET)
                else -> null
            }
        }
    }

    /** Distance outside the good range for each measurement, as a share of its scale; 0 when in range. */
    internal fun gaps(metrics: GaitMetrics, speedKmh: Double?): Map<String, Double> =
        Coach.review(metrics, speedKmh).mapNotNull { feedback ->
            val scale = feedback.scale ?: return@mapNotNull null
            val span = max(scale.max - scale.min, 1e-6)
            val outside = max(0.0, max(scale.goodLow - scale.value, scale.value - scale.goodHigh))
            measurementKey(feedback.topic) to outside / span
        }.toMap()
}
