package com.atsukio.runningcoach.analysis

import com.atsukio.runningcoach.analysis.GaitAnalyzer.Side
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Measures a clip filmed from behind or in front of the runner: hip drop, knee alignment, step width and
 * trunk sway, plus cadence and bounce, which work from any side.
 *
 * Everything sideways is measured against the runner's own body (which hip is on which side of the image),
 * so the same code serves both views and a mislabelled view only changes which leg is called left.
 * Distances are scaled from the runner's height: the belt moves towards or away from the camera here, so the
 * treadmill speed can't give a scale.
 */
internal object FrontalAnalyzer {
    /** Below this shoulder spread the camera is well off to one side, which flattens every sideways figure. */
    private const val SQUARE_ON = 0.62

    /** One foot landing: the frame, the foot, and the frames while that foot carries the runner's weight. */
    private class Stance(val frame: Int, val side: Side, val window: IntRange)

    fun analyze(track: PoseTrack, s: Signals, legPx: Double, spread: Double, runnerHeightCm: Double): GaitResult {
        val fps = track.fps.toDouble()
        val warnings = mutableListOf<String>()
        val view = view(track, s, warnings)
        warnings += "Front and rear views are new. Treat these figures as a guide, and compare a few clips before changing how you run."
        if (spread < SQUARE_ON) {
            warnings += "The camera doesn't look square on to the runner, so sideways figures may read low. " +
                "Put the phone directly ${if (view == CameraView.FRONT) "in front of" else "behind"} the treadmill."
        }

        // Each foot's landings keep their own label here: unlike side-on footage, the legs don't hide each other.
        val labelled = Side.entries
            .flatMap { side -> GaitAnalyzer.detectContacts(s.y(side.heel), legPx).map { it to side } }
            .sortedBy { it.first }
        val minGap = (0.2 * fps).roundToInt()
        val merged = mutableListOf<Pair<Int, Side>>()
        for (c in labelled) if (merged.isEmpty() || c.first - merged.last().first >= minGap) merged += c
        val steady = GaitAnalyzer.steadyStretch(merged.map { it.first })
        val landings = if (steady.isEmpty()) emptyList() else merged.filter { it.first in steady.first()..steady.last() }
        if (landings.size < 4) {
            return GaitResult.Failure(
                "Couldn't find enough foot landings. Record at least 10 seconds with the feet visible the whole time.",
            )
        }
        if (landings.size < merged.size - 1) {
            warnings += "Measured the steady part of the clip, from %.1f s to %.1f s. The start and end, where your pace was still changing, were left out."
                .format(landings.first().first / fps, landings.last().first / fps)
        }

        val contacts = landings.map { it.first }
        val intervals = contacts.zipWithNext { a, b -> (b - a).toDouble() }
        val typical = intervals.median()!!
        val isRegular = { interval: Double -> interval in 0.6 * typical..1.6 * typical }
        val stepFrames = intervals.filter(isRegular).average()
        val cadence = 60.0 * fps / stepFrames
        val cmPerPx = GaitAnalyzer.LEG_TO_HEIGHT * runnerHeightCm / legPx

        // The foot is under the body from just after landing until a little past the middle of the step.
        val stances = landings.mapIndexed { i, (frame, side) ->
            val step = intervals.getOrNull(i)?.takeIf(isRegular) ?: stepFrames
            val from = frame + max(1, (0.1 * step).roundToInt())
            val to = (frame + (0.6 * step).roundToInt()).coerceAtMost(s.size - 1)
            Stance(frame, side, from..to)
        }.filter { !it.window.isEmpty() }

        fun perSide(side: Side, measure: (Stance) -> Double?) =
            stances.filter { it.side == side }.mapNotNull(measure).median()

        val dropLeft = perSide(Side.LEFT) { pelvicDrop(s, it) }
        val dropRight = perSide(Side.RIGHT) { pelvicDrop(s, it) }
        val kneeLeft = perSide(Side.LEFT) { kneeInward(s, it) }
        val kneeRight = perSide(Side.RIGHT) { kneeInward(s, it) }

        val bounce = intervals.indices.mapNotNull { i ->
            if (!isRegular(intervals[i])) return@mapNotNull null
            val ys = (contacts[i]..contacts[i + 1]).map { s.hipMid(it).y }.filter { !it.isNaN() }
            if (ys.size < 3) null else ys.max() - ys.min()
        }

        if (fps < 50) {
            warnings += "Recorded at ${fps.roundToInt()} fps. Filming at 60 fps or more gives more accurate landing measurements."
        }
        if (cadence < 120) {
            warnings += "The step rate looks very low. If this was a slow-motion video, the timings may be off."
        }

        val metrics = GaitMetrics(
            cadenceSpm = cadence,
            overstrideCm = null,
            overstrideLegRatio = null,
            shinAngleDeg = null,
            kneeFlexionAtContactDeg = null,
            peakStanceKneeFlexionDeg = null,
            verticalOscillationCm = bounce.median()?.let { it * cmPerPx },
            trunkLeanDeg = null,
            elbowAngleDeg = null,
            overstrideAsymmetryCm = null,
            stepCount = contacts.size,
            contactFrames = contacts,
            contactTimes = contacts.map { it.toDouble() },
            view = view,
            frontal = FrontalMetrics(
                // The average of the two legs: a phone that isn't level adds to one and takes from the other.
                pelvicDropDeg = listOfNotNull(dropLeft, dropRight).takeIf { it.isNotEmpty() }?.average(),
                pelvicDropLeftDeg = dropLeft,
                pelvicDropRightDeg = dropRight,
                kneeInwardDeg = listOfNotNull(kneeLeft, kneeRight).maxOrNull(),
                kneeInwardLeftDeg = kneeLeft,
                kneeInwardRightDeg = kneeRight,
                stepWidthCm = stepWidth(s, stances)?.let { it * cmPerPx },
                trunkSwayDeg = trunkSway(s, contacts.first()..contacts.last()),
                leftContacts = landings.map { it.second == Side.LEFT },
            ),
        )
        return GaitResult.Success(metrics, warnings)
    }

    /**
     * Front or rear. Facing the camera, the runner's left shoulder is on the right of the image; from behind it is
     * on the left. The face is the cross-check: it is hidden from behind.
     */
    private fun view(track: PoseTrack, s: Signals, warnings: MutableList<String>): CameraView {
        val leftOfRight = (0 until s.size).mapNotNull { f ->
            val l = s.point(Landmark.LEFT_SHOULDER, f).x
            val r = s.point(Landmark.RIGHT_SHOULDER, f).x
            if (l.isNaN() || r.isNaN()) null else l - r
        }.median() ?: 0.0
        val byShoulders = if (leftOfRight > 0) CameraView.FRONT else CameraView.REAR
        val faceSeen = track.frames.mapNotNull { it?.visibility?.get(Landmark.NOSE)?.toDouble() }.median()
        val byFace = faceSeen?.let { if (it >= 0.5) CameraView.FRONT else CameraView.REAR }
        if (byFace != null && byFace != byShoulders) {
            warnings += "Couldn't tell for sure whether this was filmed from the front or from behind, so left and right may be the wrong way round."
        }
        return byShoulders
    }

    private fun other(side: Side) = if (side == Side.LEFT) Side.RIGHT else Side.LEFT

    /** The most the lifted leg's hip sits below the standing leg's hip during one stance, in degrees. */
    private fun pelvicDrop(s: Signals, stance: Stance): Double? = stance.window.mapNotNull { f ->
        val standing = s.point(stance.side.hip, f)
        val lifted = s.point(other(stance.side).hip, f)
        if (!standing.isValid || !lifted.isValid) return@mapNotNull null
        // Image y grows downwards, so a lower hip has the larger y.
        Math.toDegrees(atan2(lifted.y - standing.y, abs(lifted.x - standing.x)))
    }.maxOrNull()

    /**
     * The most the standing knee sits inside the straight line from hip to ankle during one stance, as the angle
     * the leg bends through at the knee. Negative when the knee sits outside the line.
     */
    private fun kneeInward(s: Signals, stance: Stance): Double? = stance.window.mapNotNull { f ->
        val hip = s.point(stance.side.hip, f)
        val knee = s.point(stance.side.knee, f)
        val ankle = s.point(stance.side.ankle, f)
        val otherHip = s.point(other(stance.side).hip, f)
        if (!hip.isValid || !knee.isValid || !ankle.isValid || !otherHip.isValid || ankle.y - hip.y < 1e-6) return@mapNotNull null
        val onLine = hip.x + (ankle.x - hip.x) * (knee.y - hip.y) / (ankle.y - hip.y)
        val inwards = sign(otherHip.x - hip.x)
        val bend = 180.0 - angleAt(knee, hip, ankle)
        if (inwards * (knee.x - onLine) >= 0) bend else -bend
    }.maxOrNull()

    /**
     * Sideways gap between each planted foot and the one before it, in pixels. A treadmill keeps the runner in
     * one place, so the two can be compared directly. Negative when the feet land across each other.
     */
    private fun stepWidth(s: Signals, stances: List<Stance>): Double? {
        fun plantedX(stance: Stance) = stance.window.map { s.point(stance.side.ankle, it).x }.median()
        fun outwards(stance: Stance): Double? = stance.window.mapNotNull { f ->
            val own = s.point(stance.side.hip, f).x
            val opposite = s.point(other(stance.side).hip, f).x
            if (own.isNaN() || opposite.isNaN()) null else own - opposite
        }.median()?.let { sign(it) }
        return stances.zipWithNext().mapNotNull { (before, now) ->
            if (before.side == now.side) return@mapNotNull null
            val a = plantedX(before) ?: return@mapNotNull null
            val b = plantedX(now) ?: return@mapNotNull null
            val out = outwards(now) ?: return@mapNotNull null
            out * (b - a)
        }.takeIf { it.size >= 3 }?.median()
    }

    /** Half the range the trunk tilts through from side to side, ignoring the odd stray frame. */
    private fun trunkSway(s: Signals, frames: IntRange): Double? {
        val tilt = frames.mapNotNull { f ->
            val hip = s.hipMid(f)
            val shoulder = s.shoulderMid(f)
            if (!hip.isValid || !shoulder.isValid) null else Math.toDegrees(atan2(shoulder.x - hip.x, hip.y - shoulder.y))
        }
        val high = tilt.percentile(0.95) ?: return null
        val low = tilt.percentile(0.05) ?: return null
        return (high - low) / 2
    }
}
