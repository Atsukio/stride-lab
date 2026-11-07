package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBestsTest {
    private fun metrics(overstrideCm: Double, ratio: Double, cadence: Double) = GaitMetrics(
        cadenceSpm = cadence, overstrideCm = overstrideCm, overstrideLegRatio = ratio, shinAngleDeg = 5.0,
        kneeFlexionAtContactDeg = 15.0, peakStanceKneeFlexionDeg = 40.0, verticalOscillationCm = 7.0,
        trunkLeanDeg = 6.0, elbowAngleDeg = 85.0, overstrideAsymmetryCm = 3.0, stepCount = 20, contactFrames = emptyList(),
    )

    @Test
    fun noBadgesWithoutAnEarlierClip() {
        assertTrue(PersonalBests.badges(metrics(10.0, 0.12, 170.0), 10.0, emptyList()).isEmpty())
    }

    @Test
    fun firstTimeInRangeIsRecognised() {
        val earlier = listOf(EarlierClip(metrics(30.0, 0.35, 150.0), 10.0))
        val badges = PersonalBests.badges(metrics(10.0, 0.12, 170.0), 10.0, earlier)
        assertTrue(badges.toString(), badges.any { it.topic == "Cadence" && it.kind == Badge.Kind.FIRST_IN_RANGE })
        assertTrue(badges.toString(), badges.any { it.topic == "Overstride" && it.kind == Badge.Kind.FIRST_IN_RANGE })
    }

    @Test
    fun staysQuietWhenItWasAlreadyInRange() {
        val earlier = listOf(EarlierClip(metrics(10.0, 0.12, 170.0), 10.0))
        assertTrue(PersonalBests.badges(metrics(10.0, 0.12, 170.0), 10.0, earlier).isEmpty())
    }

    @Test
    fun closestYetNeedsARealImprovementWhileStillOutsideTheRange() {
        val earlier = listOf(EarlierClip(metrics(40.0, 0.45, 165.0), 10.0))
        val better = PersonalBests.badges(metrics(28.0, 0.33, 165.0), 10.0, earlier)
        assertEquals(Badge.Kind.CLOSEST_YET, better.single { it.topic == "Overstride" }.kind)
        // A hair better is noise, not a milestone.
        val hair = PersonalBests.badges(metrics(39.5, 0.445, 165.0), 10.0, earlier)
        assertTrue(hair.none { it.topic == "Overstride" })
    }
}
