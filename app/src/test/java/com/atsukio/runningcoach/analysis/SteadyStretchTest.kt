package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadyStretchTest {
    private val heightCm = 175.0

    @Test
    fun measuresOnlyTheSteadyPartWhenThePaceChanges() {
        val slow = SyntheticRunner(cadence = 130.0).track(4.0)
        val steady = SyntheticRunner(cadence = 170.0).track(10.0)
        val track = PoseTrack(slow.fps, slow.width, slow.height, slow.frames + steady.frames)
        val result = GaitAnalyzer.analyze(track, heightCm)
        assertTrue("Expected success but got $result", result is GaitResult.Success)
        result as GaitResult.Success
        assertEquals(170.0, result.metrics.cadenceSpm!!, 4.0)
        assertTrue(result.warnings.toString(), result.warnings.any { it.contains("steady part") })
    }

    @Test
    fun keepsEveryLandingWhenThePaceIsSteady() {
        val result = GaitAnalyzer.analyze(SyntheticRunner().track(12.0), heightCm)
        assertTrue(result is GaitResult.Success)
        assertTrue((result as GaitResult.Success).warnings.none { it.contains("steady part") })
    }

    @Test
    fun picksTheLongestRegularRun() {
        val ramp = listOf(0, 40, 75, 100)
        val regular = (1..10).map { 100 + it * 21 }
        assertEquals(listOf(75, 100) + regular, GaitAnalyzer.steadyStretch(ramp + regular))
    }

    @Test
    fun leavesShortLandingListsAlone() {
        val short = listOf(0, 20, 40, 60, 80)
        assertEquals(short, GaitAnalyzer.steadyStretch(short))
    }
}
