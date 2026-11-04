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
        fun at(track: PoseTrack, frame: Int, legCm: Double?): LandingAnnotation? {
            val pose = track.frames.getOrNull(frame) ?: return null
            fun px(i: Int) = Vec2(pose.x[i].toDouble() * track.width, pose.y[i].toDouble() * track.height)
            val left = pose.y[Landmark.LEFT_HEEL] >= pose.y[Landmark.RIGHT_HEEL]
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
