package com.atsukio.runningcoach.share

import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.data.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareSummaryTest {
    private fun metrics(overstrideCm: Double, ratio: Double, cadence: Double) = GaitMetrics(
        cadenceSpm = cadence, overstrideCm = overstrideCm, overstrideLegRatio = ratio, shinAngleDeg = 5.0,
        kneeFlexionAtContactDeg = 15.0, peakStanceKneeFlexionDeg = 40.0, verticalOscillationCm = 7.0,
        trunkLeanDeg = 6.0, elbowAngleDeg = 85.0, overstrideAsymmetryCm = 3.0, stepCount = 20, contactFrames = emptyList(),
    )

    private fun session(metrics: GaitMetrics, speed: Double?) = Session(
        id = "x", createdAt = 0, speedKmh = speed, runnerHeightCm = 175.0, fps = 60f, playbackFps = 60f,
        videoWidth = 1080, videoHeight = 1920, metrics = metrics,
    )

    @Test
    fun leadsWithTheMainFocusAndListsEveryMeasurement() {
        val m = metrics(overstrideCm = 30.0, ratio = 0.35, cadence = 150.0)
        val feedback = Coach.review(m, 10.5)
        val text = ShareSummary.text(session(m, 10.5), feedback, "5 Nov 2026")
        val lines = text.lines()
        assertEquals("My running form: 5 Nov 2026, 10.5 km/h", lines[0])
        assertTrue(lines[1], lines[1].startsWith("Main focus: ${Coach.mainFocus(feedback)!!.topic.lowercase()}"))
        feedback.forEach { assertTrue(it.topic, text.contains("• ${it.topic}: ${it.value}")) }
        assertTrue(text.contains("20 steps analysed."))
        assertTrue(text.endsWith("not medical advice."))
    }

    @Test
    fun saysSoWhenNothingNeedsFixingAndLeavesOutAMissingSpeed() {
        val m = metrics(overstrideCm = 5.0, ratio = 0.06, cadence = 175.0)
        val feedback = Coach.review(m, null)
        val text = ShareSummary.text(session(m, null), feedback, "5 Nov 2026")
        if (Coach.mainFocus(feedback) == null) assertTrue(text.contains("Nothing major to fix."))
        assertEquals("My running form: 5 Nov 2026", text.lines()[0])
    }
}
