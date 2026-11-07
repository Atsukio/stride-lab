package com.atsukio.runningcoach.analysis

import kotlinx.serialization.Serializable

/** The overstride rule appears under two names depending on how bad it is; they are one measurement. */
fun measurementKey(topic: String) = if (topic == "Foot landing" || topic == "Overstriding") "Overstride" else topic

/** A few weeks spent on one thing: the cue and drill to practise, started from a result's main focus. */
@Serializable
data class FocusPlan(
    /** The coaching topic being worked on, e.g. "Overstriding". Compared through [measurementKey]. */
    val topic: String,
    val cue: String? = null,
    val drill: String? = null,
    val startedAt: Long,
    val weeks: Int = 3,
) {
    companion object {
        fun from(focus: Feedback, now: Long) = FocusPlan(focus.topic, focus.cue, focus.drill, now)
    }
}

/** A clip taken since the plan started, reduced to what progress needs. */
class PlanClip(val createdAt: Long, val metrics: GaitMetrics, val speedKmh: Double?)

/** Where a plan stands: how far through, the check-in clips so far, and how the latest one measured up. */
class PlanProgress(
    val day: Int,
    val totalDays: Int,
    val checkIns: Int,
    /** How the topic looked in the latest clip since the plan started, or null if there is none yet. */
    val latest: Feedback?,
    val timeIsUp: Boolean,
) {
    val reachedGoal: Boolean get() = latest?.status == FeedbackStatus.GOOD
    val finished: Boolean get() = timeIsUp || reachedGoal
    val fraction: Float get() = (day.toFloat() / totalDays).coerceIn(0f, 1f)
}

object PlanTracker {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun progress(plan: FocusPlan, now: Long, clips: List<PlanClip>): PlanProgress {
        val totalDays = plan.weeks * 7
        val elapsedDays = ((now - plan.startedAt) / DAY_MS).toInt().coerceAtLeast(0)
        val since = clips.filter { it.createdAt > plan.startedAt }.sortedBy { it.createdAt }
        val latest = since.lastOrNull()?.let { clip ->
            Coach.review(clip.metrics, clip.speedKmh).firstOrNull { measurementKey(it.topic) == measurementKey(plan.topic) }
        }
        return PlanProgress(
            day = (elapsedDays + 1).coerceAtMost(totalDays),
            totalDays = totalDays,
            checkIns = since.size,
            latest = latest,
            timeIsUp = elapsedDays >= totalDays,
        )
    }
}
