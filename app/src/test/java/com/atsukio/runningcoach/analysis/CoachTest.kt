package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachScaleTest {
    private fun metrics(overstrideCm: Double, ratio: Double, cadence: Double) = GaitMetrics(
        cadenceSpm = cadence, overstrideCm = overstrideCm, overstrideLegRatio = ratio, shinAngleDeg = 5.0,
        kneeFlexionAtContactDeg = 15.0, peakStanceKneeFlexionDeg = 40.0, verticalOscillationCm = 7.0,
        trunkLeanDeg = 6.0, elbowAngleDeg = 85.0, overstrideAsymmetryCm = 3.0, stepCount = 20, contactFrames = emptyList(),
    )

    @Test
    fun barsAgreeWithTheVerdict() {
        for (feedback in Coach.review(metrics(overstrideCm = 30.0, ratio = 0.35, cadence = 150.0), speedKmh = 10.0)) {
            val scale = feedback.scale ?: continue
            val inGoodRange = scale.value in scale.goodLow..scale.goodHigh
            // A measurement in the good band is never flagged to improve, and one outside it never marked good.
            if (feedback.status == FeedbackStatus.IMPROVE) assertTrue(feedback.topic, !inGoodRange)
            if (feedback.status == FeedbackStatus.GOOD) assertTrue(feedback.topic, inGoodRange)
            assertTrue(feedback.topic, scale.min < scale.goodLow || scale.goodLow == scale.min)
            assertTrue(feedback.topic, scale.goodHigh <= scale.max)
        }
    }

    @Test
    fun overstrideBarUsesTheSameLimitInCentimetres() {
        // Leg 85.7 cm (30 / 0.35); the limit is 18% of it at easy pace.
        val overstride = Coach.review(metrics(30.0, 0.35, 170.0), speedKmh = 10.0).first { it.topic == "Overstriding" }
        assertEquals(85.7 * 0.18, overstride.scale!!.goodHigh, 0.1)
    }

    @Test
    fun footStrikeFeedbackGivesHelpfulContextWithoutMedicalClaims() {
        val m = metrics(overstrideCm = 30.0, ratio = 0.35, cadence = 150.0).copy(
            heelStrikeRatio = 0.85,
            midfootStrikeRatio = 0.15,
            forefootStrikeRatio = 0.0,
        )
        val feedback = Coach.review(m, speedKmh = 10.0).first { it.topic == "Foot strike" }
        assertEquals("85% Heel · 15% Midfoot", feedback.value)
        assertEquals(FeedbackStatus.INFO, feedback.status)
        assertTrue(feedback.message.contains("heel"))
    }
}
