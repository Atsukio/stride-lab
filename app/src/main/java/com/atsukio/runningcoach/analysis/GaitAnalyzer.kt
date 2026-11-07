package com.atsukio.runningcoach.analysis

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Running-form measurements from one clip. Any metric that couldn't be measured is null. */
@Serializable
data class GaitMetrics(
    val cadenceSpm: Double?,
    /** How far ahead of the hips the ankle is at foot landing. */
    val overstrideCm: Double?,
    /** [overstrideCm] as a fraction of leg length, so it can be compared between runners. */
    val overstrideLegRatio: Double?,
    /** Shin angle from vertical at landing; positive when the foot is ahead of the knee. */
    val shinAngleDeg: Double?,
    val kneeFlexionAtContactDeg: Double?,
    val peakStanceKneeFlexionDeg: Double?,
    val verticalOscillationCm: Double?,
    /** Trunk angle from vertical; positive is leaning forward. */
    val trunkLeanDeg: Double?,
    val elbowAngleDeg: Double?,
    /** Difference in landing distance between the two legs. */
    val overstrideAsymmetryCm: Double?,
    val stepCount: Int,
    val contactFrames: List<Int>,
    /** When each landing happened, in frames and between them, so a landing's figures don't jump with the frame rate. */
    val contactTimes: List<Double> = emptyList(),
    /** Runner height worked out from the treadmill speed, when one was given. */
    val estimatedHeightCm: Double? = null,
    /** True when distances were scaled from the treadmill belt rather than the runner's height setting. */
    val scaledFromTreadmill: Boolean = false,
)

sealed interface GaitResult {
    data class Success(val metrics: GaitMetrics, val warnings: List<String>) : GaitResult
    data class Failure(val reason: String) : GaitResult
}

/**
 * Measures running form from a side-on pose track.
 *
 * Distances are worked out in pixels, then converted to centimetres. With a treadmill speed,
 * the scale comes from the belt: a planted foot moves backwards across the image at belt speed.
 * Otherwise the runner's leg length in the image is compared with the leg length expected for
 * their height.
 * Left/right landmark labels are not trusted, because pose models often swap the legs
 * in side-on footage; the landing leg is always taken to be the one whose heel is lowest.
 */
object GaitAnalyzer {
    /** Hip-joint-to-ankle length as a fraction of standing height. */
    private const val LEG_TO_HEIGHT = 0.49

    private enum class Side(
        val shoulder: Int, val elbow: Int, val wrist: Int,
        val hip: Int, val knee: Int, val ankle: Int, val heel: Int, val toe: Int,
    ) {
        LEFT(
            Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST,
            Landmark.LEFT_HIP, Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX,
        ),
        RIGHT(
            Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST,
            Landmark.RIGHT_HIP, Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX,
        ),
    }

    fun analyze(track: PoseTrack, runnerHeightCm: Double, beltSpeedKmh: Double? = null): GaitResult {
        val fps = track.fps.toDouble()
        val n = track.frames.size
        if (fps <= 0 || n < fps * 4) {
            return GaitResult.Failure("The clip is too short. Record at least 10 seconds of steady running.")
        }
        if (track.frames.count { it != null } < n * 0.6) {
            return GaitResult.Failure(
                "The runner couldn't be seen clearly in most of the clip. Keep the whole body, head to feet, in frame.",
            )
        }

        val s = Signals.from(track)
        val warnings = mutableListOf<String>()

        val legPx = legLength(s)
            ?: return GaitResult.Failure("Couldn't make out the runner's legs. Make sure they are fully in frame.")
        if (legPx < 0.12 * max(track.width, track.height)) {
            return GaitResult.Failure("The runner is too small in the frame. Move the phone closer, about 2–3 m away.")
        }

        val spread = shoulderSpreadRatio(s)
        if (spread != null && spread > 0.5) {
            return GaitResult.Failure(
                "This looks like a front or rear view. This version analyses side-on footage, so film from the runner's side.",
            )
        }
        if (spread != null && spread > 0.3) {
            warnings += "The camera doesn't look fully side-on, so distances may be a little off."
        }

        val dir = facing(s)

        val allContacts = mergeContacts(
            Side.entries.flatMap { detectContacts(s.y(it.heel), legPx) },
            minGapFrames = (0.2 * fps).roundToInt(),
        )
        // Only the steady part of the clip is measured: the first steps while the pace settles, or a slowdown at
        // the end, would otherwise pull every figure towards a pace the runner wasn't holding.
        val contacts = steadyStretch(allContacts)
        if (contacts.size < allContacts.size - 1) {
            warnings += "Measured the steady part of the clip, from %.1f s to %.1f s. The start and end, where your pace was still changing, were left out."
                .format(contacts.first() / fps, contacts.last() / fps)
        }
        if (contacts.size < 4) {
            return GaitResult.Failure(
                "Couldn't find enough foot landings. Record at least 10 seconds with the feet visible the whole time.",
            )
        }

        val intervals = contacts.zipWithNext { a, b -> (b - a).toDouble() }
        val typical = intervals.median()!!
        val isRegular = { interval: Double -> interval in 0.6 * typical..1.6 * typical }
        val stepFrames = intervals.filter(isRegular).average()
        val cadence = 60.0 * fps / stepFrames

        var estimatedHeight: Double? = null
        val beltCmPerPx = beltSpeedKmh?.let { beltScale(s, contacts, intervals, isRegular, dir, fps, it) }
        val cmPerPx = beltCmPerPx?.let { belt ->
            val height = legPx * belt / LEG_TO_HEIGHT
            // Implied height grows in step with the speed typed, so a big gap almost always means the speed was
            // wrong (a misread display, or a treadmill that runs slower than it shows), and the fitting speed is easy to work out.
            val fittingSpeed = beltSpeedKmh * runnerHeightCm / height
            val gap = abs(height - runnerHeightCm) / runnerHeightCm
            if (height in 120.0..220.0) {
                estimatedHeight = height
                if (gap > SPEED_MISMATCH) {
                    warnings += speedMismatch(beltSpeedKmh, runnerHeightCm, fittingSpeed) +
                        " Distances use the speed you entered until you change it."
                } else if (gap > 0.08) {
                    warnings += "From the treadmill speed you look about ${height.roundToInt()} cm tall, but your height is " +
                        "${runnerHeightCm.roundToInt()} cm. Distances use the treadmill measurement; check the " +
                        "speed was right, or your height."
                }
                belt
            } else {
                warnings += speedMismatch(beltSpeedKmh, runnerHeightCm, fittingSpeed) +
                    " Until then, distances are based on your height instead."
                null
            }
        } ?: (LEG_TO_HEIGHT * runnerHeightCm / legPx)

        // Per-landing measurements. NaN keeps each list aligned with `contacts`.
        val sides = contacts.map { landingSide(s, it, dir) }
        // A landing is found on a whole frame, but the foot crosses the landing line somewhere between two.
        // At 30 fps the belt moves the foot ~6 cm per frame, so a small detection difference (GPU versus CPU)
        // could flip the frame and shift every landing figure. Measuring at the interpolated moment avoids that.
        val groundLevels = Side.entries.associateWith { side -> landingLevel(s, side, legPx) }
        val times = contacts.mapIndexed { i, c ->
            val side = sides[i]
            val level = side?.let { groundLevels[it] }
            if (side == null || level == null) c.toDouble() else landingTime(s.y(side.heel), c, level)
        }
        val overstride = times.mapIndexed { i, t ->
            val side = sides[i] ?: return@mapIndexed Double.NaN
            val ankle = s.pointAt(side.ankle, t)
            val hip = s.hipMidAt(t)
            if (ankle.isValid && hip.isValid) dir * (ankle.x - hip.x) else Double.NaN
        }
        val shin = times.mapIndexed { i, t ->
            val side = sides[i] ?: return@mapIndexed Double.NaN
            val knee = s.pointAt(side.knee, t)
            val ankle = s.pointAt(side.ankle, t)
            if (knee.isValid && ankle.isValid) {
                Math.toDegrees(atan2(dir * (ankle.x - knee.x), ankle.y - knee.y))
            } else {
                Double.NaN
            }
        }
        val kneeAtContact = times.mapIndexed { i, t ->
            sides[i]?.let { kneeFlexion(s, it, t) } ?: Double.NaN
        }

        val peakStanceKnee = intervals.indices.mapNotNull { i ->
            val side = sides[i] ?: return@mapNotNull null
            if (!isRegular(intervals[i])) return@mapNotNull null
            val start = contacts[i]
            val end = start + (0.5 * intervals[i]).roundToInt()
            (start..end).map { kneeFlexion(s, side, it.toDouble()) }.filter { !it.isNaN() }.maxOrNull()
        }

        val bounce = intervals.indices.mapNotNull { i ->
            if (!isRegular(intervals[i])) return@mapNotNull null
            val ys = (contacts[i]..contacts[i + 1]).map { s.hipMid(it).y }.filter { !it.isNaN() }
            if (ys.size < 3) null else ys.max() - ys.min()
        }

        val steadyFrames = contacts.first()..contacts.last()
        val lean = steadyFrames.mapNotNull { f ->
            val hip = s.hipMid(f)
            val shoulder = s.shoulderMid(f)
            if (!hip.isValid || !shoulder.isValid) return@mapNotNull null
            Math.toDegrees(atan2(dir * (shoulder.x - hip.x), hip.y - shoulder.y))
        }

        val elbow = steadyFrames.flatMap { f ->
            Side.entries.mapNotNull { side ->
                val sh = s.point(side.shoulder, f)
                val el = s.point(side.elbow, f)
                val wr = s.point(side.wrist, f)
                if (sh.isValid && el.isValid && wr.isValid) angleAt(el, sh, wr) else null
            }
        }

        if (fps < 50) {
            warnings += "Recorded at ${fps.roundToInt()} fps. Filming at 60 fps or more gives more accurate landing measurements."
        }
        if (cadence < 120) {
            warnings += "The step rate looks very low. If this was a slow-motion video, the timings may be off."
        }

        val overstrideMedian = overstride.median()
        val metrics = GaitMetrics(
            cadenceSpm = cadence,
            overstrideCm = overstrideMedian?.let { it * cmPerPx },
            overstrideLegRatio = overstrideMedian?.let { it / legPx },
            shinAngleDeg = shin.median(),
            kneeFlexionAtContactDeg = kneeAtContact.median(),
            peakStanceKneeFlexionDeg = peakStanceKnee.median(),
            verticalOscillationCm = bounce.median()?.let { it * cmPerPx },
            trunkLeanDeg = lean.median(),
            elbowAngleDeg = elbow.median(),
            overstrideAsymmetryCm = asymmetry(overstride, intervals, isRegular)?.let { it * cmPerPx },
            stepCount = contacts.size,
            contactFrames = contacts,
            contactTimes = times,
            estimatedHeightCm = estimatedHeight,
            scaledFromTreadmill = estimatedHeight != null,
        )
        return GaitResult.Success(metrics, warnings)
    }

    /**
     * The longest run of landings where each step takes within about a quarter of the clip's typical step
     * time. Returns every landing unless that run is long enough (6 landings) to measure on its own.
     */
    internal fun steadyStretch(contacts: List<Int>): List<Int> {
        if (contacts.size < 8) return contacts
        val intervals = contacts.zipWithNext { a, b -> (b - a).toDouble() }
        val typical = intervals.median() ?: return contacts
        val steady = intervals.map { it in 0.8 * typical..1.25 * typical }
        var bestStart = 0
        var bestLength = 0
        var start = 0
        for (i in 0..steady.size) {
            if (i < steady.size && steady[i]) continue
            if (i - start > bestLength) {
                bestStart = start
                bestLength = i - start
            }
            start = i + 1
        }
        // bestLength intervals span bestLength + 1 landings.
        return if (bestLength + 1 >= 6) contacts.subList(bestStart, bestStart + bestLength + 1) else contacts
    }

    /** How far the height implied by the belt may be from the runner's own before the speed is called into question. */
    internal const val SPEED_MISMATCH = 0.15

    /** The speed that would make the belt agree with a runner of [heightCm], given one that implied [impliedCm]. */
    fun fittingSpeedKmh(speedKmh: Double, heightCm: Double, impliedCm: Double): Double = speedKmh * heightCm / impliedCm

    private fun speedMismatch(typedKmh: Double, heightCm: Double, fittingKmh: Double) =
        "The treadmill speed entered (%.1f km/h) doesn't fit your height of %d cm: the video looks more like about %.1f km/h. "
            .format(typedKmh, heightCm.roundToInt(), fittingKmh) +
            "Check what the treadmill was set to, and change it under \"Scale for this clip\"."

    /** Thigh plus shin length, taken near the top of the range since 2D projection only shortens it. */
    private fun legLength(s: Signals): Double? = (0 until s.size).flatMap { f ->
        Side.entries.mapNotNull { side ->
            val hip = s.point(side.hip, f)
            val knee = s.point(side.knee, f)
            val ankle = s.point(side.ankle, f)
            if (hip.isValid && knee.isValid && ankle.isValid) (knee - hip).length + (ankle - knee).length else null
        }
    }.percentile(0.9)

    /**
     * Centimetres per pixel from the treadmill belt. Early in each stance the foot is planted on the
     * belt, so it moves backwards across the image at belt speed, whatever the runner's drift.
     * Returns null unless at least four steps agree.
     */
    private fun beltScale(
        s: Signals,
        contacts: List<Int>,
        intervals: List<Double>,
        isRegular: (Double) -> Boolean,
        dir: Double,
        fps: Double,
        beltSpeedKmh: Double,
    ): Double? {
        if (beltSpeedKmh < 3) return null
        val pxPerSecond = intervals.indices.mapNotNull { i ->
            if (!isRegular(intervals[i])) return@mapNotNull null
            val side = landingSide(s, contacts[i], dir) ?: return@mapNotNull null
            // Skip the moment of impact and stop before the heel lifts.
            val start = contacts[i] + max(1, (0.1 * intervals[i]).roundToInt())
            val end = contacts[i] + (0.45 * intervals[i]).roundToInt()
            val frames = (start..end).filter { it < s.size && s.point(side.ankle, it).isValid }
            if (frames.size < 3) return@mapNotNull null
            val slope = slope(frames.map { it.toDouble() }, frames.map { s.point(side.ankle, it).x })
            // A planted foot must travel backwards, against the direction the runner faces.
            if (dir * slope >= 0) null else -dir * slope * fps
        }
        if (pxPerSecond.size < 4) return null
        val median = pxPerSecond.median()!!
        val spread = (pxPerSecond.percentile(0.75)!! - pxPerSecond.percentile(0.25)!!) / median
        if (spread > 0.3) return null
        val beltCmPerSecond = beltSpeedKmh / 3.6 * 100
        return beltCmPerSecond / median
    }

    /** Least-squares slope of [ys] against [xs]. */
    private fun slope(xs: List<Double>, ys: List<Double>): Double {
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        return num / den
    }

    /** Shoulder width relative to torso length: near zero side-on, large from the front or back. */
    private fun shoulderSpreadRatio(s: Signals): Double? {
        val ratios = (0 until s.size).mapNotNull { f ->
            val l = s.point(Landmark.LEFT_SHOULDER, f)
            val r = s.point(Landmark.RIGHT_SHOULDER, f)
            val torso = (s.shoulderMid(f) - s.hipMid(f)).length
            if (l.isValid && r.isValid && torso > 0) abs(l.x - r.x) / torso else null
        }
        return ratios.median()
    }

    /** +1 if the runner faces right in the image, -1 if left. */
    private fun facing(s: Signals): Double {
        val toeAhead = (0 until s.size).flatMap { f ->
            Side.entries.mapNotNull { side ->
                val toe = s.point(side.toe, f)
                val heel = s.point(side.heel, f)
                if (toe.isValid && heel.isValid) toe.x - heel.x else null
            }
        }.median() ?: 0.0
        val noseAhead = (0 until s.size).mapNotNull { f ->
            val nose = s.point(Landmark.NOSE, f)
            val ears = s.mid(Landmark.LEFT_EAR, Landmark.RIGHT_EAR, f)
            if (nose.isValid && ears.isValid) nose.x - ears.x else null
        }.median() ?: 0.0
        return if (toeAhead + noseAhead >= 0) 1.0 else -1.0
    }

    /** Frames where a heel comes back down to the ground after being clearly in the air. */
    private fun detectContacts(heelY: DoubleArray, legPx: Double): List<Int> {
        val ground = heelY.toList().percentile(0.95) ?: return emptyList()
        val airLevel = ground - 0.12 * legPx
        val contactLevel = ground - 0.04 * legPx
        val contacts = mutableListOf<Int>()
        var airborne = false
        heelY.forEachIndexed { f, y ->
            if (y.isNaN()) return@forEachIndexed
            // Image y grows downwards, so smaller y means higher off the ground.
            if (y < airLevel) {
                airborne = true
            } else if (airborne && y >= contactLevel) {
                contacts += f
                airborne = false
            }
        }
        return contacts
    }

    /** Combines both feet's landings, dropping duplicates caused by swapped leg labels. */
    private fun mergeContacts(all: List<Int>, minGapFrames: Int): List<Int> {
        val merged = mutableListOf<Int>()
        for (c in all.sorted()) {
            if (merged.isEmpty() || c - merged.last() >= minGapFrames) merged += c
        }
        return merged
    }

    /**
     * The landing foot is the front one. "Lowest heel" was used before, but at first contact the other foot is
     * often still flat on the belt behind, pushing off, and was picked instead (about a third of the landings on
     * a real clip, which made left/right balance meaningless). Falls back to the lower heel if an ankle is missing.
     */
    private fun landingSide(s: Signals, frame: Int, dir: Double): Side? {
        val leftAnkle = s.point(Side.LEFT.ankle, frame).x
        val rightAnkle = s.point(Side.RIGHT.ankle, frame).x
        if (!leftAnkle.isNaN() && !rightAnkle.isNaN()) {
            return if (dir * leftAnkle >= dir * rightAnkle) Side.LEFT else Side.RIGHT
        }
        val left = s.point(Side.LEFT.heel, frame).y
        val right = s.point(Side.RIGHT.heel, frame).y
        return when {
            left.isNaN() && right.isNaN() -> null
            right.isNaN() || left >= right -> Side.LEFT
            else -> Side.RIGHT
        }
    }

    /** The heel height at which a foot counts as landed, as [detectContacts] sets it. */
    private fun landingLevel(s: Signals, side: Side, legPx: Double): Double? =
        s.y(side.heel).toList().percentile(0.95)?.let { it - 0.04 * legPx }

    /**
     * The moment, in frames, that the heel came down through [level]: between the frame before [frame] and
     * [frame] itself. Falls back to [frame] when the heel track doesn't cross cleanly.
     */
    internal fun landingTime(heelY: DoubleArray, frame: Int, level: Double): Double {
        val before = heelY.getOrNull(frame - 1) ?: return frame.toDouble()
        val after = heelY.getOrNull(frame) ?: return frame.toDouble()
        if (before.isNaN() || after.isNaN() || before >= level || after <= before) return frame.toDouble()
        return (frame - 1) + ((level - before) / (after - before)).coerceIn(0.0, 1.0)
    }

    /** A landmark between two frames, worked out from the frames either side. */
    private fun Signals.pointAt(landmark: Int, t: Double): Vec2 {
        val first = floor(t).toInt().coerceIn(0, size - 1)
        val second = (first + 1).coerceAtMost(size - 1)
        val a = point(landmark, first)
        val b = point(landmark, second)
        val w = (t - first).coerceIn(0.0, 1.0)
        return when {
            a.isValid && b.isValid -> a * (1 - w) + b * w
            // One neighbour is missing: use the nearer one only if it is there.
            w < 0.5 -> a
            else -> b
        }
    }

    private fun Signals.hipMidAt(t: Double) = midpoint(pointAt(Landmark.LEFT_HIP, t), pointAt(Landmark.RIGHT_HIP, t))

    private fun kneeFlexion(s: Signals, side: Side, t: Double): Double {
        if (t > s.size - 1) return Double.NaN
        val hip = s.pointAt(side.hip, t)
        val knee = s.pointAt(side.knee, t)
        val ankle = s.pointAt(side.ankle, t)
        if (!hip.isValid || !knee.isValid || !ankle.isValid) return Double.NaN
        return 180.0 - angleAt(knee, hip, ankle)
    }

    /**
     * Landings alternate between legs, so even and odd landings belong to different legs.
     * Only the longest run without a missed landing is used, since a miss would flip the pairing.
     */
    private fun asymmetry(values: List<Double>, intervals: List<Double>, isRegular: (Double) -> Boolean): Double? {
        var bestStart = 0
        var bestLength = 1
        var start = 0
        for (i in intervals.indices) {
            if (!isRegular(intervals[i])) start = i + 1
            val length = i + 2 - start
            if (length > bestLength) {
                bestStart = start
                bestLength = length
            }
        }
        val run = values.subList(bestStart, bestStart + bestLength)
        val even = run.filterIndexed { i, v -> i % 2 == 0 && !v.isNaN() }
        val odd = run.filterIndexed { i, v -> i % 2 == 1 && !v.isNaN() }
        if (even.size < 3 || odd.size < 3) return null
        // Medians, so one misread landing can't make a leg look far off.
        return abs(even.median()!! - odd.median()!!)
    }
}
