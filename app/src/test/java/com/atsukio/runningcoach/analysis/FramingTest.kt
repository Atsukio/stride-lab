package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingTest {
    /** A side-on runner standing at [x], from [top] to [bottom] of the frame (normalised). */
    private fun pose(top: Float = 0.15f, bottom: Float = 0.9f, x: Float = 0.5f, shoulderSpread: Float = 0.01f): FramePose {
        val xs = FloatArray(33) { x }
        val ys = FloatArray(33) { (top + bottom) / 2 }
        val h = bottom - top
        for (i in listOf(Landmark.NOSE, Landmark.LEFT_EAR, Landmark.RIGHT_EAR)) ys[i] = top
        for (i in listOf(Landmark.LEFT_HEEL, Landmark.RIGHT_HEEL, Landmark.LEFT_FOOT_INDEX, Landmark.RIGHT_FOOT_INDEX)) ys[i] = bottom
        ys[Landmark.LEFT_SHOULDER] = top + h * 0.15f
        ys[Landmark.RIGHT_SHOULDER] = top + h * 0.15f
        ys[Landmark.LEFT_HIP] = top + h * 0.5f
        ys[Landmark.RIGHT_HIP] = top + h * 0.5f
        xs[Landmark.LEFT_SHOULDER] = x - shoulderSpread / 2
        xs[Landmark.RIGHT_SHOULDER] = x + shoulderSpread / 2
        return FramePose(xs, ys, FloatArray(33) { 0.9f })
    }

    @Test
    fun goodFraming() = assertTrue(Framing.check(pose(), brightness = 120).good)

    @Test
    fun noRunner() = assertFalse(Framing.check(null, brightness = 120).good)

    @Test
    fun feetCutOff() = assertTrue(Framing.check(pose(bottom = 0.995f), 120).message.contains("feet"))

    @Test
    fun headCutOff() = assertTrue(Framing.check(pose(top = 0.005f), 120).message.contains("head"))

    @Test
    fun squareOnIsAccepted() {
        val advice = Framing.check(pose(shoulderSpread = 0.2f), 120)
        assertTrue(advice.good)
        assertTrue(advice.message.contains("rear"))
    }

    @Test
    fun atAnAngle() {
        val advice = Framing.check(pose(shoulderSpread = 0.15f), 120)
        assertFalse(advice.good)
        assertTrue(advice.message.contains("angle"))
    }

    @Test
    fun tooSmall() = assertTrue(Framing.check(pose(top = 0.4f, bottom = 0.7f), 120).message.contains("closer"))

    @Test
    fun offCentre() = assertTrue(Framing.check(pose(x = 0.1f), 120).message.contains("middle"))

    @Test
    fun tooDark() = assertTrue(Framing.check(pose(), brightness = 20).message.contains("dark"))
}
