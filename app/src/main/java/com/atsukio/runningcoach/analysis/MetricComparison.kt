package com.atsukio.runningcoach.analysis

import kotlin.math.roundToInt

/** One measurement from two clips, side by side. [difference] is [first] minus [second]. */
data class ComparedMetric(val label: String, val unit: String, val first: Double?, val second: Double?) {
    val difference: Double? get() = if (first != null && second != null) first - second else null

    fun format(value: Double?): String = value?.let { "${it.roundToInt()}" } ?: "–"

    /** The difference with its sign, or a dash when either clip lacks the measurement. */
    fun formatDifference(): String {
        val d = difference?.roundToInt() ?: return "–"
        return when {
            d > 0 -> "+$d"
            d < 0 -> "−${-d}"
            else -> "0"
        }
    }
}

/** Lines two clips' measurements up for the compare screen. Says nothing about which is better. */
object MetricComparison {
    fun rows(first: GaitMetrics, second: GaitMetrics): List<ComparedMetric> = listOf(
        ComparedMetric("Cadence", "steps/min", first.cadenceSpm, second.cadenceSpm),
        ComparedMetric("Foot ahead of hips", "cm", first.overstrideCm, second.overstrideCm),
        ComparedMetric("Shin angle", "°", first.shinAngleDeg, second.shinAngleDeg),
        ComparedMetric("Knee bend at landing", "°", first.kneeFlexionAtContactDeg, second.kneeFlexionAtContactDeg),
        ComparedMetric("Deepest knee bend", "°", first.peakStanceKneeFlexionDeg, second.peakStanceKneeFlexionDeg),
        ComparedMetric("Bounce", "cm", first.verticalOscillationCm, second.verticalOscillationCm),
        ComparedMetric("Trunk lean", "°", first.trunkLeanDeg, second.trunkLeanDeg),
        ComparedMetric("Elbow angle", "°", first.elbowAngleDeg, second.elbowAngleDeg),
        ComparedMetric("Left/right difference", "cm", first.overstrideAsymmetryCm, second.overstrideAsymmetryCm),
        ComparedMetric("Heel strike", "%", first.heelStrikeRatio?.let { it * 100 }, second.heelStrikeRatio?.let { it * 100 }),
        ComparedMetric("Midfoot strike", "%", first.midfootStrikeRatio?.let { it * 100 }, second.midfootStrikeRatio?.let { it * 100 }),
        ComparedMetric("Hip drop", "°", first.frontal?.pelvicDropDeg, second.frontal?.pelvicDropDeg),
        ComparedMetric("Knee drift inwards", "°", first.frontal?.kneeInwardDeg, second.frontal?.kneeInwardDeg),
        ComparedMetric("Step width", "cm", first.frontal?.stepWidthCm, second.frontal?.stepWidthCm),
        ComparedMetric("Trunk sway", "°", first.frontal?.trunkSwayDeg, second.frontal?.trunkSwayDeg),
    ).filter { it.first != null || it.second != null }
}
