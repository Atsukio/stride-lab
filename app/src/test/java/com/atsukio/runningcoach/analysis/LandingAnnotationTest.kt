package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LandingAnnotationTest {
    private fun check(facingRight: Boolean) {
        val runner = SyntheticRunner(landingAheadPx = 60.0, leanDeg = 6.0, facingRight = facingRight)
        // As in the app: the analysis cleans the raw track itself; the results screen draws a cleaned copy.
        val raw = runner.track(12.0)
        val metrics = (GaitAnalyzer.analyze(raw, 175.0) as GaitResult.Success).metrics
        val track = PoseCleaner.clean(raw)
        val legCm = metrics.overstrideCm!! / metrics.overstrideLegRatio!!
        val landing = metrics.contactFrames[metrics.contactFrames.size / 2]
        val a = LandingAnnotation.at(track, landing, legCm)
        assertNotNull(a)
        // The synthetic foot lands 60 px ahead on a 400 px leg, with the body leaning 6° forward.
        assertEquals(60.0 * legCm / 400, a!!.overstrideCm!!, 3.0)
        assertEquals(6.0, a.leanDeg, 1.5)
        // Across all landings, the drawn angles are the measurements the analysis reports (it takes the median).
        val all = metrics.contactFrames.mapIndexedNotNull { i, frame -> LandingAnnotation.at(track, frame, legCm, metrics.contactTimes[i]) }
        assertEquals(metrics.shinAngleDeg!!, all.map { it.shinDeg }.median()!!, 1.5)
        assertEquals(metrics.kneeFlexionAtContactDeg!!, all.map { it.kneeFlexionDeg }.median()!!, 1.5)
    }

    @Test
    fun measuresTheLandingFacingRight() = check(facingRight = true)

    @Test
    fun measuresTheLandingFacingLeft() = check(facingRight = false)

    @Test
    fun idealLandingPullsOverstrideUnderBodyWithGentleKneeFlexion() {
        val runner = SyntheticRunner(landingAheadPx = 80.0, leanDeg = 6.0, facingRight = true)
        val raw = runner.track(12.0)
        val metrics = (GaitAnalyzer.analyze(raw, 175.0) as GaitResult.Success).metrics
        val track = PoseCleaner.clean(raw)
        val legCm = metrics.overstrideCm!! / metrics.overstrideLegRatio!!
        val landing = metrics.contactFrames.first()
        val actual = LandingAnnotation.at(track, landing, legCm)!!
        val ideal = actual.ideal(targetOverstrideCm = 3.0, legCm = legCm)

        assertEquals("target overstride", 3.0, ideal.overstrideCm!!, 1.0)
        // Shin angle at ideal landing with soft knee flexion
        org.junit.Assert.assertTrue("shin angle near vertical", kotlin.math.abs(ideal.shinDeg) < 20.0)
        // Knee should be flexed, not straight
        org.junit.Assert.assertTrue("knee flexed: ${ideal.kneeFlexionDeg}", ideal.kneeFlexionDeg in 15.0..45.0)
        // Ground plane (ankle y) and hip position are unchanged
        assertEquals(actual.ankle.y, ideal.ankle.y, 1e-4f)
        assertEquals(actual.hip.x, ideal.hip.x, 1e-4f)
        assertEquals(actual.hip.y, ideal.hip.y, 1e-4f)
    }

    @Test
    fun classifiesStrikeOnLandingAnnotation() {
        val runner = SyntheticRunner(heelStrikeOffsetPx = 15.0)
        val raw = runner.track(12.0)
        val metrics = (GaitAnalyzer.analyze(raw, 175.0) as GaitResult.Success).metrics
        val track = PoseCleaner.clean(raw)
        val legCm = metrics.overstrideCm!! / metrics.overstrideLegRatio!!
        val landing = metrics.contactFrames.first()
        val a = LandingAnnotation.at(track, landing, legCm, metrics.contactTimes.first())!!
        assertEquals(FootStrikeType.HEEL, a.strike)
        assertEquals(FootStrikeType.MIDFOOT, a.ideal(legCm = legCm).strike)
    }
}
