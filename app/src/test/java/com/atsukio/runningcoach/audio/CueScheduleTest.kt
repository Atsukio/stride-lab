package com.atsukio.runningcoach.audio

import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.FocusPlan
import com.atsukio.runningcoach.analysis.GaitMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CueScheduleTest {
    private fun metrics(overstrideCm: Double, ratio: Double) = GaitMetrics(
        cadenceSpm = 170.0, overstrideCm = overstrideCm, overstrideLegRatio = ratio, shinAngleDeg = 5.0,
        kneeFlexionAtContactDeg = 15.0, peakStanceKneeFlexionDeg = 40.0, verticalOscillationCm = 7.0,
        trunkLeanDeg = 6.0, elbowAngleDeg = 85.0, overstrideAsymmetryCm = 3.0, stepCount = 20, contactFrames = emptyList(),
    )

    @Test
    fun speaksAfterAMinuteThenEveryFiveMinutes() {
        assertEquals(-1, CueSchedule.dueIndex(0.0))
        assertEquals(-1, CueSchedule.dueIndex(59.0))
        assertEquals(0, CueSchedule.dueIndex(60.0))
        assertEquals(0, CueSchedule.dueIndex(359.0))
        assertEquals(1, CueSchedule.dueIndex(360.0))
        assertEquals(3, CueSchedule.dueIndex(60.0 + 3 * 300))
    }

    @Test
    fun theFocusPlanCueComesFirst() {
        val plan = FocusPlan(topic = "Bounce", cue = "Run quietly.", startedAt = 0)
        val feedback = Coach.review(metrics(35.0, 0.40), 10.0)
        assertEquals("Run quietly.", CueSchedule.cue(plan, feedback))
    }

    @Test
    fun withoutAPlanTheLatestMainFocusCueIsUsed() {
        val feedback = Coach.review(metrics(35.0, 0.40), 10.0)
        assertEquals(Coach.mainFocus(feedback)!!.cue, CueSchedule.cue(null, feedback))
    }

    @Test
    fun saysNothingWhenThereIsNothingToWorkOn() {
        val feedback = Coach.review(metrics(5.0, 0.06), null)
        if (Coach.mainFocus(feedback) == null) assertNull(CueSchedule.cue(null, feedback))
    }
}
