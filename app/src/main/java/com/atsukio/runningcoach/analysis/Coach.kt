package com.atsukio.runningcoach.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

/** Declared in display order: things to work on first. */
enum class FeedbackStatus { IMPROVE, INFO, GOOD }

data class Feedback(
    val topic: String,
    val value: String,
    val status: FeedbackStatus,
    val message: String,
    val cue: String? = null,
    val drill: String? = null,
)

/**
 * Turns measurements into coaching comments using fixed rules.
 *
 * Thresholds are typical coaching ranges for recreational runners, not clinical limits,
 * and a few of them move with treadmill speed because form naturally changes with pace.
 */
object Coach {

    fun review(metrics: GaitMetrics, speedKmh: Double?, previous: GaitMetrics? = null): List<Feedback> =
        listOfNotNull(
            metrics.cadenceSpm?.let { cadence(it, speedKmh, previous?.cadenceSpm) },
            overstride(metrics, speedKmh, previous?.overstrideCm),
            metrics.shinAngleDeg?.let { shinAngle(it) },
            metrics.trunkLeanDeg?.let { trunkLean(it) },
            metrics.verticalOscillationCm?.let { bounce(it, previous?.verticalOscillationCm) },
            knees(metrics),
            metrics.elbowAngleDeg?.let { arms(it) },
            metrics.overstrideAsymmetryCm?.let { legBalance(it) },
        ).sortedBy { it.status.ordinal }

    /** The first thing to work on, or null if nothing needs changing. */
    fun mainFocus(feedback: List<Feedback>): Feedback? = feedback.firstOrNull { it.status == FeedbackStatus.IMPROVE }

    private fun cadence(spm: Double, speedKmh: Double?, previous: Double?): Feedback {
        val floor = when {
            speedKmh == null -> 160.0
            speedKmh < 9 -> 152.0
            speedKmh < 12 -> 158.0
            speedKmh < 15 -> 165.0
            else -> 172.0
        }
        val value = "${spm.roundToInt()} steps/min"
        val history = since(previous, "steps/min")
        if (spm >= floor) {
            val pace = if (speedKmh == null) "easy running" else "this pace"
            return Feedback("Cadence", value, FeedbackStatus.GOOD, "A good step rate for $pace.$history")
        }
        val target = (spm * 1.06).roundToInt()
        return Feedback(
            "Cadence", value, FeedbackStatus.IMPROVE,
            "Raising your step rate by 5–8%, to about $target steps/min, usually shortens each stride " +
                "and reduces overstriding.$history",
            cue = "Quick, light steps.",
            drill = "Alternate 1 minute with a metronome app set to $target bpm and 1 minute at your normal rhythm.",
        )
    }

    private fun overstride(m: GaitMetrics, speedKmh: Double?, previous: Double?): Feedback? {
        val cm = m.overstrideCm ?: return null
        val ratio = m.overstrideLegRatio ?: return null
        // Faster running naturally lands the foot a little further forward.
        val allowance = if (speedKmh != null && speedKmh > 13) 0.04 else 0.0
        val good = 0.18 + allowance
        val value = if (cm <= 0) "Under hips" else "${cm.roundToInt()} cm ahead of hips"
        val history = since(previous, "cm")
        return when {
            ratio <= good -> Feedback(
                "Foot landing", value, FeedbackStatus.GOOD,
                "Your foot lands close to underneath your body.$history",
            )
            ratio <= good + 0.10 -> Feedback(
                "Overstriding", value, FeedbackStatus.IMPROVE,
                "Your foot lands a little far in front of your hips. Aim to land closer to underneath you.$history",
                cue = "Land under your hips.",
                drill = "High-knee marching, 2 × 20 m, then 3 × 15 seconds of quick, small steps.",
            )
            else -> Feedback(
                "Overstriding", value, FeedbackStatus.IMPROVE,
                "Your foot lands well in front of your hips, which acts like a brake on every step. " +
                    "Landing closer to underneath you is the biggest improvement available.$history",
                cue = "Land under your hips.",
                drill = "High-knee marching, 2 × 20 m, then 3 × 15 seconds of quick, small steps.",
            )
        }
    }

    private fun shinAngle(deg: Double): Feedback {
        val value = "${deg.roundToInt()}°"
        return when {
            deg <= 6 -> Feedback(
                "Shin at landing", value, FeedbackStatus.GOOD,
                "Your shin is close to vertical when your foot lands.",
            )
            deg <= 12 -> Feedback(
                "Shin at landing", value, FeedbackStatus.IMPROVE,
                "Your shin angles forward when your foot lands, so the foot is reaching out in front.",
                cue = "Step down, not out.",
                drill = "A-skips, 3 × 20 m.",
            )
            else -> Feedback(
                "Shin at landing", value, FeedbackStatus.IMPROVE,
                "Your shin angles well forward when your foot lands, a clear sign of reaching with the foot.",
                cue = "Step down, not out.",
                drill = "A-skips, 3 × 20 m.",
            )
        }
    }

    private fun trunkLean(deg: Double): Feedback {
        val value = if (deg < 0) "${abs(deg).roundToInt()}° back" else "${deg.roundToInt()}° forward"
        return when {
            deg < 0 -> Feedback(
                "Posture", value, FeedbackStatus.IMPROVE,
                "You're leaning back slightly, which tends to push the foot out in front.",
                cue = "Run tall and lean slightly from the ankles.",
            )
            deg < 2 -> Feedback(
                "Posture", value, FeedbackStatus.INFO,
                "You run very upright. A small forward lean from the ankles, about 3–8°, can make it easier " +
                    "to land under your body.",
            )
            deg <= 12 -> Feedback("Posture", value, FeedbackStatus.GOOD, "A slight forward lean. Good.")
            else -> Feedback(
                "Posture", value, FeedbackStatus.IMPROVE,
                "You're leaning forward a lot, which usually means bending at the waist.",
                cue = "Hips under shoulders; lean from the ankles, not the waist.",
                drill = "Wall lean: stand tall, fall forward from the ankles onto a wall, then run 20 m holding that line.",
            )
        }
    }

    private fun bounce(cm: Double, previous: Double?): Feedback {
        val value = "${cm.roundToInt()} cm"
        val history = since(previous, "cm")
        return when {
            cm <= 8 -> Feedback(
                "Bounce", value, FeedbackStatus.GOOD,
                "Your hips move up and down very little, which is efficient.$history",
            )
            cm <= 10 -> Feedback(
                "Bounce", value, FeedbackStatus.IMPROVE,
                "Some of your energy is going up rather than forward.$history",
                cue = "Run quietly; push the ground back, not down.",
                drill = "3 × 20 seconds of quick, quiet steps, keeping your head level.",
            )
            else -> Feedback(
                "Bounce", value, FeedbackStatus.IMPROVE,
                "You bounce quite a lot with each step. A slightly quicker step rate usually reduces this.$history",
                cue = "Run quietly; push the ground back, not down.",
                drill = "3 × 20 seconds of quick, quiet steps, keeping your head level.",
            )
        }
    }

    private fun knees(m: GaitMetrics): Feedback? {
        val atContact = m.kneeFlexionAtContactDeg
        val peak = m.peakStanceKneeFlexionDeg
        if (atContact == null && peak == null) return null
        val value = listOfNotNull(
            atContact?.let { "${it.roundToInt()}° at landing" },
            peak?.let { "${it.roundToInt()}° max" },
        ).joinToString(", ")
        return when {
            atContact != null && atContact < 10 -> Feedback(
                "Knee bend", value, FeedbackStatus.IMPROVE,
                "Your knee is almost straight when your foot lands. A slightly bent knee absorbs the impact better.",
                cue = "Land with a soft knee.",
                drill = "Ankle hops, 3 × 20 seconds, staying light and springy.",
            )
            peak != null && peak < 30 -> Feedback(
                "Knee bend", value, FeedbackStatus.IMPROVE,
                "Your knee bends only a little while your foot is on the ground, which makes the stride stiff.",
                cue = "Soft, springy legs.",
                drill = "Ankle hops, 3 × 20 seconds, staying light and springy.",
            )
            peak != null && peak > 55 -> Feedback(
                "Knee bend", value, FeedbackStatus.INFO,
                "Your knee bends deeply while your foot is on the ground, which can be a sign of sinking into each step. " +
                    "Single-leg squats and calf raises help build support.",
            )
            else -> Feedback(
                "Knee bend", value, FeedbackStatus.GOOD,
                "Your knee is softly bent at landing and bends well to absorb each step.",
            )
        }
    }

    private fun arms(deg: Double): Feedback {
        val value = "${deg.roundToInt()}° at elbow"
        return when {
            deg > 110 -> Feedback(
                "Arms", value, FeedbackStatus.IMPROVE,
                "Your arms are fairly straight. Bending the elbows to about 90° makes the arm swing quicker " +
                    "and easier to match to your legs.",
                cue = "Elbows bent, hands relaxed, swing from the shoulder.",
            )
            deg < 60 -> Feedback(
                "Arms", value, FeedbackStatus.INFO,
                "Your elbows are tightly bent. That's fine as long as your shoulders and hands stay relaxed.",
            )
            else -> Feedback("Arms", value, FeedbackStatus.GOOD, "Elbows bent at about a right angle. Good.")
        }
    }

    private fun legBalance(cm: Double): Feedback {
        val value = "${cm.roundToInt()} cm difference"
        return if (cm < 8) {
            Feedback("Left/right balance", value, FeedbackStatus.GOOD, "Both legs land a similar distance in front of you.")
        } else {
            Feedback(
                "Left/right balance", value, FeedbackStatus.INFO,
                "One leg lands about ${cm.roundToInt()} cm further in front than the other. A small difference is normal; " +
                    "if it shows up every session or one side hurts, consider seeing a physiotherapist.",
            )
        }
    }

    private fun since(previous: Double?, unit: String): String =
        if (previous == null) "" else " Last session: ${previous.roundToInt()} $unit."
}
