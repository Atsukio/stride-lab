package com.atsukio.runningcoach.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * What to draw on one stance of a front or rear clip: the hip line, the standing leg, the trunk, and the hip
 * drop and knee drift at that moment. Points are normalised (0..1) for drawing over the video frame.
 */
class StanceAnnotation(
    val standingHip: Point,
    val liftedHip: Point,
    val knee: Point,
    val ankle: Point,
    val shoulders: Point,
    /** True when the runner is standing on their left leg. */
    val left: Boolean,
    /** How far the lifted side's hip sits below the standing hip; negative when it sits above. */
    val dropDeg: Double,
    /** How far the knee bends in from the hip-to-ankle line; negative when it sits outside it. */
    val kneeInwardDeg: Double,
) {
    companion object {
        /**
         * The frame to show for landing [index]: about a third of the way to the next landing, when the foot is
         * under the body and carrying the runner's weight.
         */
        fun frameFor(metrics: GaitMetrics, index: Int): Int? {
            val contacts = metrics.contactFrames
            val start = contacts.getOrNull(index) ?: return null
            val step = contacts.getOrNull(index + 1)?.let { it - start }
                ?: contacts.zipWithNext { a, b -> (b - a).toDouble() }.median()?.roundToInt()
                ?: return start
            return start + (0.35 * step).roundToInt()
        }

        /** Measures [frame] of a cleaned [track], with the runner standing on the left leg if [left]. */
        fun at(track: PoseTrack, frame: Int, left: Boolean): StanceAnnotation? {
            val pose = track.frames.getOrNull(frame) ?: return null
            fun px(i: Int) = Vec2(pose.x[i].toDouble() * track.width, pose.y[i].toDouble() * track.height)
            fun point(v: Vec2) = Point((v.x / track.width).toFloat(), (v.y / track.height).toFloat())
            val standingHip = px(if (left) Landmark.LEFT_HIP else Landmark.RIGHT_HIP)
            val liftedHip = px(if (left) Landmark.RIGHT_HIP else Landmark.LEFT_HIP)
            val knee = px(if (left) Landmark.LEFT_KNEE else Landmark.RIGHT_KNEE)
            val ankle = px(if (left) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE)
            val shoulders = midpoint(px(Landmark.LEFT_SHOULDER), px(Landmark.RIGHT_SHOULDER))
            if (listOf(standingHip, liftedHip, knee, ankle, shoulders).any { !it.isValid } || ankle.y - standingHip.y < 1e-6) return null
            val onLine = standingHip.x + (ankle.x - standingHip.x) * (knee.y - standingHip.y) / (ankle.y - standingHip.y)
            val bend = 180.0 - angleAt(knee, standingHip, ankle)
            val inwards = sign(liftedHip.x - standingHip.x) * (knee.x - onLine) >= 0
            return StanceAnnotation(
                standingHip = point(standingHip),
                liftedHip = point(liftedHip),
                knee = point(knee),
                ankle = point(ankle),
                shoulders = point(shoulders),
                left = left,
                dropDeg = Math.toDegrees(atan2(liftedHip.y - standingHip.y, abs(liftedHip.x - standingHip.x))),
                kneeInwardDeg = if (inwards) bend else -bend,
            )
        }
    }
}
