package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunInsightsTest {
    private fun clip(minute: Double, overstride: Double, cadence: Double = 170.0, heartRate: Int? = null) = RunClip(
        minute = minute,
        heartRate = heartRate,
        metrics = GaitMetrics(
            cadenceSpm = cadence,
            overstrideCm = overstride,
            overstrideLegRatio = overstride / 86,
            shinAngleDeg = 4.0,
            kneeFlexionAtContactDeg = 18.0,
            peakStanceKneeFlexionDeg = 42.0,
            verticalOscillationCm = 7.0,
            trunkLeanDeg = 6.0,
            elbowAngleDeg = 90.0,
            overstrideAsymmetryCm = 2.0,
            stepCount = 40,
            contactFrames = emptyList(),
        ),
    )

    @Test
    fun needsTwoClips() {
        assertTrue(RunInsights.summarize(listOf(clip(5.0, 12.0))).single().contains("at least two clips"))
    }

    @Test
    fun steadyForm() {
        val lines = RunInsights.summarize(listOf(clip(5.0, 12.0), clip(40.0, 13.0)))
        assertEquals(listOf("Your form held steady across the run."), lines)
    }

    @Test
    fun reportsFadingOverstrideWithHeartRateAndOnset() {
        val lines = RunInsights.summarize(
            listOf(
                clip(40.0, 19.0, heartRate = 168),
                clip(5.0, 12.0, heartRate = 142),
                clip(20.0, 16.0, heartRate = 160),
            ),
        )
        assertEquals(
            "Overstride rose from 12 to 19 cm between minute 5 and minute 40 (heart rate 142 → 168). " +
                "It had started by minute 20, with heart rate around 160.",
            lines[0],
        )
        assertTrue(lines.last().contains("fade"))
    }

    @Test
    fun improvementIsNotCalledFading() {
        val lines = RunInsights.summarize(listOf(clip(5.0, 12.0, cadence = 160.0), clip(30.0, 12.0, cadence = 168.0)))
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("Cadence rose from 160 to 168 steps/min"))
    }
}

class CrossCheckTest {
    @Test
    fun closeEnough() = assertNull(CrossCheck.cadence(videoSpm = 172.0, watchSpm = 168.0))

    @Test
    fun disagreement() = assertNotNull(CrossCheck.cadence(videoSpm = 140.0, watchSpm = 170.0))
}
