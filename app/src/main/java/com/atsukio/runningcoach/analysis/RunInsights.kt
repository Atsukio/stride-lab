package com.atsukio.runningcoach.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

/** One analysed clip within a whole run. */
data class RunClip(val minute: Double, val metrics: GaitMetrics, val heartRate: Int?)

/** Compares clips from one run to show how form changes as the runner tires. */
object RunInsights {

    private class Trend(
        val name: String,
        val unit: String,
        val minChange: Double,
        val higherIsWorse: Boolean,
        val value: (GaitMetrics) -> Double?,
    )

    private val trends = listOf(
        Trend("Overstride", " cm", 3.0, higherIsWorse = true) { it.overstrideCm },
        Trend("Cadence", " steps/min", 4.0, higherIsWorse = false) { it.cadenceSpm },
        Trend("Bounce", " cm", 1.5, higherIsWorse = true) { it.verticalOscillationCm },
        Trend("Forward lean", "°", 3.0, higherIsWorse = true) { it.trunkLeanDeg },
    )

    fun summarize(clips: List<RunClip>): List<String> {
        val sorted = clips.sortedBy { it.minute }
        if (sorted.size < 2) return listOf("Take at least two clips during a run to see how your form changes.")
        val first = sorted.first()
        val last = sorted.last()
        val heartRates = if (first.heartRate != null && last.heartRate != null) {
            " (heart rate ${first.heartRate} → ${last.heartRate})"
        } else {
            ""
        }

        val lines = mutableListOf<String>()
        var faded = false
        for (trend in trends) {
            val from = trend.value(first.metrics) ?: continue
            val to = trend.value(last.metrics) ?: continue
            val change = to - from
            if (abs(change) < trend.minChange) continue
            val worse = (change > 0) == trend.higherIsWorse
            if (worse) faded = true
            val verb = if (change > 0) "rose" else "fell"
            var line = "${trend.name} $verb from ${from.roundToInt()} to ${to.roundToInt()}${trend.unit} " +
                "between minute ${first.minute.roundToInt()} and minute ${last.minute.roundToInt()}$heartRates."
            if (worse) onset(sorted, trend, from)?.let { line += " $it" }
            lines += line
        }
        if (lines.isEmpty()) lines += "Your form held steady across the run."
        if (faded) {
            lines += "Form tends to fade as you tire. Keep one cue in mind for the last third of the run, " +
                "such as \"quick, light steps\"."
        }
        return lines
    }

    /** When a worsening first showed up, if that was before the last clip. */
    private fun onset(clips: List<RunClip>, trend: Trend, baseline: Double): String? {
        val clip = clips.drop(1).dropLast(1).firstOrNull { c ->
            val value = trend.value(c.metrics) ?: return@firstOrNull false
            val change = value - baseline
            abs(change) >= trend.minChange && (change > 0) == trend.higherIsWorse
        } ?: return null
        val heartRate = clip.heartRate?.let { ", with heart rate around $it" } ?: ""
        return "It had started by minute ${clip.minute.roundToInt()}$heartRate."
    }
}

object CrossCheck {
    /** A warning when the video and the watch disagree on cadence by more than 8%. */
    fun cadence(videoSpm: Double, watchSpm: Double): String? {
        if (watchSpm <= 0) return null
        if (abs(videoSpm - watchSpm) / watchSpm <= 0.08) return null
        return "Your watch measured ${watchSpm.roundToInt()} steps/min but the video measured ${videoSpm.roundToInt()}. " +
            "The video may have missed or doubled some landings, so treat this clip's numbers with caution."
    }
}
