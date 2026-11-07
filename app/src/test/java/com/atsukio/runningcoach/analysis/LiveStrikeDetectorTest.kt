package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStrikeDetectorTest {
    private fun strikesIn(track: PoseTrack, fps: Float): List<Pair<Int, LiveStrikeDetector.Strike>> {
        val detector = LiveStrikeDetector()
        return track.frames.mapIndexedNotNull { i, pose ->
            detector.update(pose, (i * 1000 / fps).toLong())?.let { i to it }
        }
    }

    @Test
    fun findsAboutOneLandingPerStep() {
        val runner = SyntheticRunner(cadence = 170.0)
        val strikes = strikesIn(runner.track(10.0), runner.fps)
        // 170 steps a minute for 10 s is about 28 steps; the first second or so is spent learning the range.
        assertTrue("found ${strikes.size}", strikes.size in 22..30)
    }

    @Test
    fun landingsAreSpacedLikeSteps() {
        val runner = SyntheticRunner(cadence = 170.0)
        val frames = strikesIn(runner.track(10.0), runner.fps).map { it.first }
        val gaps = frames.zipWithNext { a, b -> (b - a) * 1000.0 / runner.fps }
        val stepMs = 60_000.0 / 170
        assertEquals(stepMs, gaps.sorted()[gaps.size / 2], 40.0)
    }

    @Test
    fun reportsWhereTheFootIs() {
        val runner = SyntheticRunner()
        val (_, strike) = strikesIn(runner.track(6.0), runner.fps).first()
        assertTrue(strike.x in 0f..1f)
        // The foot is near the ground, which is low in the frame.
        assertTrue("y = ${strike.y}", strike.y > 0.8f)
    }

    @Test
    fun staysQuietWhenStandingStill() {
        val still = SyntheticRunner().track(2.0).frames.first()
        val detector = LiveStrikeDetector()
        repeat(120) { assertEquals(null, detector.update(still, it * 16L)) }
    }

    @Test
    fun staysQuietWhenNobodyIsInFrame() {
        val detector = LiveStrikeDetector()
        repeat(30) { assertEquals(null, detector.update(null, it * 33L)) }
    }
}
