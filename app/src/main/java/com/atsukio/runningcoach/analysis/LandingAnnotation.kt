package com.atsukio.runningcoach.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

/** A point in normalised image coordinates (0..1), for drawing over a video frame. */
data class Point(val x: Float, val y: Float)

/**
 * What to draw on one foot landing: the landing leg, the hip, and the measurements at that moment.
 * Distances are in centimetres when the leg length is known.
 */
data class LandingAnnotation(
    val shoulder: Point,
    val hip: Point,
    val knee: Point,
    val ankle: Point,
    /** How far in front of the hips the foot lands; negative means under or behind them. */
    val overstrideCm: Double?,
    /** Shin tilt from vertical, positive when the foot is ahead of the knee. */
    val shinDeg: Double,
    /** Knee bend: 0° is a straight leg. */
    val kneeFlexionDeg: Double,
    /** Trunk lean from vertical, positive forwards. */
    val leanDeg: Double,
) {
    companion object {
        /**
         * The annotation for [frame] of a cleaned track, using the lower foot as the landing one.
         * [legCm] (hip to ankle) turns pixels into centimetres. Null if a needed point is missing.
         */
        fun at(track: PoseTrack, frame: Int, legCm: Double?, time: Double = frame.toDouble()): LandingAnnotation? {
            val pose = track.frames.getOrNull(frame) ?: return null
            val left = frontFootIsLeft(pose)
            // The skeleton is drawn on the video frame, but the figures are read at the moment of landing,
            // which falls between frames, so they match what the analysis reports.
            val drawn = measure(track, frame.toDouble(), left, legCm) ?: return null
            if (time == frame.toDouble()) return drawn
            val measured = measure(track, time, left, legCm) ?: return drawn
            return drawn.copy(
                overstrideCm = measured.overstrideCm,
                shinDeg = measured.shinDeg,
                kneeFlexionDeg = measured.kneeFlexionDeg,
                leanDeg = measured.leanDeg,
            )
        }

        /** The landing foot is the front one, as in [GaitAnalyzer]; the lower heel only if an ankle is missing. */
        private fun frontFootIsLeft(pose: FramePose): Boolean {
            val l = pose.x[Landmark.LEFT_ANKLE]
            val r = pose.x[Landmark.RIGHT_ANKLE]
            if (l.isNaN() || r.isNaN()) return pose.y[Landmark.LEFT_HEEL] >= pose.y[Landmark.RIGHT_HEEL]
            val earsX = (pose.x[Landmark.LEFT_EAR] + pose.x[Landmark.RIGHT_EAR]) / 2
            val noseAhead = pose.x[Landmark.NOSE] - earsX
            val leanAhead = (pose.x[Landmark.LEFT_SHOULDER] + pose.x[Landmark.RIGHT_SHOULDER]) / 2 -
                (pose.x[Landmark.LEFT_HIP] + pose.x[Landmark.RIGHT_HIP]) / 2
            val facing = if (!noseAhead.isNaN() && abs(noseAhead) > 0.001f) sign(noseAhead) else if (leanAhead < 0) -1f else 1f
            return facing * l >= facing * r
        }

        /** The landing leg and measurements at [t] frames, with each landmark worked out between its two frames. */
        private fun measure(track: PoseTrack, t: Double, left: Boolean, legCm: Double?): LandingAnnotation? {
            val first = kotlin.math.floor(t).toInt()
            val a = track.frames.getOrNull(first) ?: return null
            val b = track.frames.getOrNull(first + 1) ?: a
            val w = t - first
            fun px(i: Int) = Vec2(
                (a.x[i] * (1 - w) + b.x[i] * w) * track.width,
                (a.y[i] * (1 - w) + b.y[i] * w) * track.height,
            )
            val hipI = if (left) Landmark.LEFT_HIP else Landmark.RIGHT_HIP
            val kneeI = if (left) Landmark.LEFT_KNEE else Landmark.RIGHT_KNEE
            val ankleI = if (left) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE
            val hip = midpoint(px(Landmark.LEFT_HIP), px(Landmark.RIGHT_HIP))
            val shoulder = midpoint(px(Landmark.LEFT_SHOULDER), px(Landmark.RIGHT_SHOULDER))
            val knee = px(kneeI)
            val ankle = px(ankleI)
            val nose = px(Landmark.NOSE)
            val ears = midpoint(px(Landmark.LEFT_EAR), px(Landmark.RIGHT_EAR))
            if (listOf(hip, shoulder, knee, ankle, px(hipI)).any { !it.isValid }) return null

            // Side-on, the nose is ahead of the ears; fall back to the shoulders leaning ahead of the hips.
            val facing = when {
                nose.isValid && ears.isValid && abs(nose.x - ears.x) > 1 -> sign(nose.x - ears.x)
                else -> sign(shoulder.x - hip.x).takeIf { it != 0.0 } ?: 1.0
            }
            val legPx = (px(hipI) - knee).length + (knee - ankle).length
            val overstride = legCm?.takeIf { legPx > 1 }?.let { (ankle.x - hip.x) * facing * it / legPx }
            val shin = Math.toDegrees(atan2((ankle.x - knee.x) * facing, ankle.y - knee.y))
            val lean = Math.toDegrees(atan2((shoulder.x - hip.x) * facing, hip.y - shoulder.y))
            fun norm(v: Vec2) = Point((v.x / track.width).toFloat(), (v.y / track.height).toFloat())
            return LandingAnnotation(
                shoulder = norm(shoulder),
                hip = norm(hip),
                knee = norm(knee),
                ankle = norm(ankle),
                overstrideCm = overstride,
                shinDeg = shin,
                kneeFlexionDeg = 180 - angleAt(knee, px(hipI), ankle),
                leanDeg = lean,
            )
        }
    }
}
