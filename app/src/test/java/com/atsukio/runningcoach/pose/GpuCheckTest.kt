package com.atsukio.runningcoach.pose

import com.atsukio.runningcoach.analysis.FramePose
import org.junit.Assert.assertEquals
import org.junit.Test

class GpuCheckTest {
    private fun pose(shift: Float = 0f) = FramePose(
        x = FloatArray(33) { 0.3f + it * 0.01f + shift },
        y = FloatArray(33) { 0.2f + it * 0.02f },
        visibility = FloatArray(33) { 0.9f },
    )

    private val cpu = List(12) { pose() }

    @Test
    fun tinyDifferencesAgree() {
        assertEquals(GpuCheck.Verdict.AGREES, GpuCheck.compare(List(12) { pose(0.002f) }, cpu, aspect = 9.0 / 16))
    }

    @Test
    fun consistentlyOffLandmarksDisagree() {
        assertEquals(GpuCheck.Verdict.DISAGREES, GpuCheck.compare(List(12) { pose(0.04f) }, cpu, aspect = 1.0))
    }

    @Test
    fun aFewWildFramesDisagree() {
        val gpu = List(12) { if (it % 4 == 0) pose(0.2f) else pose() }
        assertEquals(GpuCheck.Verdict.DISAGREES, GpuCheck.compare(gpu, cpu, aspect = 1.0))
    }

    @Test
    fun missingTheRunnerDisagrees() {
        val gpu = List(12) { if (it < 4) null else pose() }
        assertEquals(GpuCheck.Verdict.DISAGREES, GpuCheck.compare(gpu, cpu, aspect = 1.0))
    }

    @Test
    fun tooFewFramesIsUnknown() {
        assertEquals(GpuCheck.Verdict.UNKNOWN, GpuCheck.compare(List(12) { null }, List(12) { null }, aspect = 1.0))
    }
}
