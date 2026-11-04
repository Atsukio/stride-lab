package com.atsukio.runningcoach.analysis

import kotlin.math.abs
import kotlin.math.min

/** Advice on how the runner is framed before recording, from one live pose. */
data class FramingAdvice(val message: String, val good: Boolean)

/**
 * Checks the things that most affect accuracy, most important first: the whole body in view, side-on,
 * big enough, roughly centred, and enough light. [brightness] is the frame's mean grey level, 0–255.
 */
object Framing {
    fun check(pose: FramePose?, brightness: Int?): FramingAdvice {
        if (brightness != null && brightness < DARK) {
            return FramingAdvice("It's quite dark. More light keeps the camera at 60 fps and the tracking accurate.", false)
        }
        if (pose == null) return FramingAdvice("Step into view, side-on to the camera.", false)
        fun seen(i: Int) = pose.visibility[i] >= SEEN && !pose.x[i].isNaN()
        val head = listOf(Landmark.NOSE, Landmark.LEFT_EAR, Landmark.RIGHT_EAR).filter(::seen)
        val feet = listOf(Landmark.LEFT_HEEL, Landmark.RIGHT_HEEL, Landmark.LEFT_FOOT_INDEX, Landmark.RIGHT_FOOT_INDEX).filter(::seen)
        val top = head.minOfOrNull { pose.y[it] }
        val bottom = feet.maxOfOrNull { pose.y[it] }
        if (top == null || top < EDGE) return FramingAdvice("Your head is out of frame. Move the phone back or tilt it up.", false)
        if (bottom == null || bottom > 1 - EDGE) return FramingAdvice("Your feet are out of frame. Move the phone back or tilt it down.", false)

        val shoulderSpread = abs(pose.x[Landmark.LEFT_SHOULDER] - pose.x[Landmark.RIGHT_SHOULDER])
        val hipY = (pose.y[Landmark.LEFT_HIP] + pose.y[Landmark.RIGHT_HIP]) / 2
        val shoulderY = (pose.y[Landmark.LEFT_SHOULDER] + pose.y[Landmark.RIGHT_SHOULDER]) / 2
        val torso = abs(hipY - shoulderY)
        if (torso > 0 && shoulderSpread / torso > FACING_CAMERA) {
            return FramingAdvice("Turn side-on: the camera should see you from the side.", false)
        }
        if (bottom - top < SMALL) return FramingAdvice("You're small in the frame. Move the phone closer.", false)
        val hipX = (pose.x[Landmark.LEFT_HIP] + pose.x[Landmark.RIGHT_HIP]) / 2
        if (min(hipX, 1 - hipX) < OFF_CENTRE) return FramingAdvice("Move to the middle of the frame.", false)
        return FramingAdvice("Framing looks good.", true)
    }

    private const val DARK = 45
    private const val SEEN = 0.5f
    private const val EDGE = 0.02f
    private const val FACING_CAMERA = 0.5f
    private const val SMALL = 0.4f
    private const val OFF_CENTRE = 0.2f
}
