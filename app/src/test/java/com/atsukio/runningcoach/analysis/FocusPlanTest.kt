package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPlanTest {
    private val day = 24L * 60 * 60 * 1000

    private fun metrics(overstrideCm: Double, ratio: Double) = GaitMetrics(
        cadenceSpm = 170.0, overstrideCm = overstrideCm, overstrideLegRatio = ratio, shinAngleDeg = 5.0,
        kneeFlexionAtContactDeg = 15.0, peakStanceKneeFlexionDeg = 40.0, verticalOscillationCm = 7.0,
        trunkLeanDeg = 6.0, elbowAngleDeg = 85.0, overstrideAsymmetryCm = 3.0, stepCount = 20, contactFrames = emptyList(),
    )

    private val plan = FocusPlan(topic = "Overstriding", cue = "Land under your hips.", drill = "A-skips", startedAt = 1_000 * day)

    @Test
    fun startsFromTheMainFocus() {
        val focus = Coach.mainFocus(Coach.review(metrics(35.0, 0.40), 10.0))!!
        val started = FocusPlan.from(focus, now = 5)
        assertEquals(focus.topic, started.topic)
        assertEquals(focus.cue, started.cue)
        assertEquals(3, started.weeks)
    }

    @Test
    fun countsDaysAndOnlyClipsAfterTheStart() {
        val clips = listOf(
            PlanClip(plan.startedAt - day, metrics(35.0, 0.40), 10.0), // before the plan
            PlanClip(plan.startedAt + 3 * day, metrics(33.0, 0.38), 10.0),
        )
        val progress = PlanTracker.progress(plan, plan.startedAt + 8 * day, clips)
        assertEquals(9, progress.day)
        assertEquals(21, progress.totalDays)
        assertEquals(1, progress.checkIns)
        assertFalse(progress.finished)
        assertNotNull(progress.latest)
        assertEquals(FeedbackStatus.IMPROVE, progress.latest!!.status)
    }

    @Test
    fun finishesWhenTheTopicIsInRange() {
        val clips = listOf(PlanClip(plan.startedAt + 10 * day, metrics(8.0, 0.09), 10.0))
        val progress = PlanTracker.progress(plan, plan.startedAt + 11 * day, clips)
        assertTrue(progress.reachedGoal)
        assertTrue(progress.finished)
    }

    @Test
    fun finishesWhenTheTimeIsUp() {
        val progress = PlanTracker.progress(plan, plan.startedAt + 22 * day, emptyList())
        assertTrue(progress.timeIsUp)
        assertTrue(progress.finished)
        assertNull(progress.latest)
        assertEquals(21, progress.day)
    }
}
