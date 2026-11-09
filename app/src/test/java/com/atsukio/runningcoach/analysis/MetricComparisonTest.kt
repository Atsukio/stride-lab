package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricComparisonTest {
    private fun metrics(cadence: Double? = 160.0, overstride: Double? = 20.0, bounce: Double? = 8.0) = GaitMetrics(
        cadenceSpm = cadence, overstrideCm = overstride, overstrideLegRatio = null, shinAngleDeg = null,
        kneeFlexionAtContactDeg = null, peakStanceKneeFlexionDeg = null, verticalOscillationCm = bounce,
        trunkLeanDeg = null, elbowAngleDeg = null, overstrideAsymmetryCm = null, stepCount = 20,
        contactFrames = emptyList(),
    )

    @Test
    fun theDifferenceIsFirstMinusSecond() {
        val row = MetricComparison.rows(metrics(cadence = 170.0), metrics(cadence = 160.0)).first { it.label == "Cadence" }
        assertEquals(10.0, row.difference!!, 1e-9)
        assertEquals("+10", row.formatDifference())
    }

    @Test
    fun aFallShowsAMinusSign() {
        val row = MetricComparison.rows(metrics(overstride = 12.0), metrics(overstride = 20.0)).first { it.label == "Foot ahead of hips" }
        assertEquals("−8", row.formatDifference())
    }

    @Test
    fun noChangeShowsZero() {
        val row = MetricComparison.rows(metrics(), metrics()).first { it.label == "Bounce" }
        assertEquals("0", row.formatDifference())
    }

    @Test
    fun aMeasurementMissingFromOneClipHasNoDifference() {
        val row = MetricComparison.rows(metrics(bounce = null), metrics(bounce = 8.0)).first { it.label == "Bounce" }
        assertNull(row.difference)
        assertEquals("–", row.formatDifference())
        assertEquals("–", row.format(row.first))
        assertEquals("8", row.format(row.second))
    }

    @Test
    fun measurementsMissingFromBothClipsAreLeftOut() {
        val labels = MetricComparison.rows(metrics(), metrics()).map { it.label }
        assertTrue("Shin angle" !in labels)
        assertTrue("Cadence" in labels)
    }
}
