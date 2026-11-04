package com.atsukio.runningcoach.pose

import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.GaitResult
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.analysis.SyntheticRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The two-pass extraction: detect every other frame, find the landings, then detect the skipped frames
 * near each landing. Checks it measures the same as detecting every frame, with far fewer detections.
 */
class TwoPassTest {
    private val runner = SyntheticRunner(cadence = 172.0, landingAheadPx = 70.0)
    private val full = runner.track(12.0)
    private val timesUs = LongArray(full.frames.size) { it * 1_000_000L / 60 }

    private fun metrics(track: PoseTrack) = (GaitAnalyzer.analyze(track, 175.0) as GaitResult.Success).metrics

    private fun fromSubset(frames: Set<Int>): PoseTrack {
        val regular = FrameTiming.regularise(timesUs, frames.associateWith { full.frames[it] })!!
        return PoseTrack(regular.fps, full.width, full.height, regular.frames)
    }

    private fun assertClose(expected: GaitMetrics, actual: GaitMetrics) {
        assertEquals(expected.cadenceSpm!!, actual.cadenceSpm!!, 1.0)
        assertEquals(expected.overstrideCm!!, actual.overstrideCm!!, 1.0)
        assertEquals(expected.shinAngleDeg!!, actual.shinAngleDeg!!, 1.0)
        assertEquals(expected.kneeFlexionAtContactDeg!!, actual.kneeFlexionAtContactDeg!!, 1.5)
        assertEquals(expected.verticalOscillationCm!!, actual.verticalOscillationCm!!, 0.5)
    }

    @Test
    fun secondPassAroundLandingsMatchesEveryFrame() {
        val reference = metrics(full)
        val even = full.frames.indices.filter { it % 2 == 0 }.toSet()
        val firstPass = metrics(fromSubset(even))
        val window = (0.07 * 60).roundToInt()
        val near = firstPass.contactFrames.flatMap { (it - window)..(it + window) }.filter { it in full.frames.indices }
        val both = even + near
        val twoPass = metrics(fromSubset(both))

        assertClose(reference, twoPass)
        // Landings land on the same frames as with every frame detected.
        val matched = reference.contactFrames.count { c -> twoPass.contactFrames.any { abs(it - c) <= 1 } }
        assertTrue(matched >= reference.contactFrames.size - 1)
        val share = both.size.toDouble() / full.frames.size
        assertTrue("Detected ${(share * 100).roundToInt()}% of frames", share < 0.8)
        println("Two-pass detected ${(share * 100).roundToInt()}% of frames")
    }
}
