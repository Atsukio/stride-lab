package com.atsukio.runningcoach.pose

import com.atsukio.runningcoach.analysis.FramePose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameTimingTest {
    /** A pose whose x is the time it was taken, in seconds, so positions show which moment a frame holds. */
    private fun poseAt(seconds: Double) =
        FramePose(FloatArray(33) { seconds.toFloat() }, FloatArray(33) { 0.5f }, FloatArray(33) { 1f })

    private fun regularise(timesUs: List<Long>) =
        FrameTiming.regularise(timesUs.map { poseAt(it / 1e6) }, timesUs.toLongArray())!!

    @Test
    fun steadySixtyFpsIsUnchanged() {
        val times = (0 until 600).map { it * 1_000_000L / 60 }
        val regular = regularise(times)
        assertEquals(60f, regular.fps, 0.5f)
        assertEquals(600, regular.frames.size)
        assertNull(regular.droppedToFps)
        assertEquals(times[300] / 1e6, regular.frames[300]!!.x[0].toDouble(), 1e-4)
    }

    @Test
    fun aClipThatSlowsToThirtyStaysInStep() {
        // 3 s at 60 fps, then 3 s at 30 fps, then 3 s at 60 fps again.
        val times = (0 until 180).map { it * 1_000_000L / 60 } +
            (0 until 90).map { 3_000_000L + it * 1_000_000L / 30 } +
            (0 until 180).map { 6_000_000L + it * 1_000_000L / 60 }
        val regular = regularise(times)
        assertEquals(60f, regular.fps, 0.5f)
        assertNotNull(regular.droppedToFps)
        assertEquals(30f, regular.droppedToFps!!, 1f)
        // Frame i is at i/60 s all the way through, where counting frames would have drifted by 1.5 s.
        for (i in listOf(100, 250, 251, 400, 535)) {
            assertEquals(i / 60.0, regular.frames[i]!!.x[0].toDouble(), 0.002)
        }
    }

    @Test
    fun aLateFirstFrameKeepsVideoTime() {
        val times = (0 until 300).map { 50_000L + it * 1_000_000L / 60 }
        val regular = regularise(times)
        assertEquals(0.05 + 10 / 60.0, regular.frames[13]!!.x[0].toDouble(), 0.01)
    }
}
