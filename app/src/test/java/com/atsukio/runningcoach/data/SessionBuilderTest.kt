package com.atsukio.runningcoach.data

import com.atsukio.runningcoach.analysis.SyntheticRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBuilderTest {
    private val runner = SyntheticRunner()
    private val track = runner.track(12.0)

    private fun build(heightCm: Double, speedKmh: Double?, edited: Boolean = false, notes: List<String> = emptyList()) =
        SessionBuilder.build(
            id = "a",
            createdAt = 1L,
            track = track,
            playbackFps = 60f,
            heightCm = heightCm,
            capture = CaptureInfo(speedKmh = speedKmh),
            extractionNotes = notes,
            processingSec = null,
            analysedLive = false,
            scaleEdited = edited,
        )

    @Test
    fun beltSpeedSetsTheScale() {
        val matching = runner.beltSpeedKmh(175.0)
        val right = build(175.0, matching).metrics!!
        val faster = build(175.0, matching * 1.2).metrics!!
        // A faster belt means each pixel is worth more centimetres, so the implied height and distances grow with it.
        assertEquals(175.0, right.estimatedHeightCm!!, 4.0)
        assertEquals(1.2, faster.estimatedHeightCm!! / right.estimatedHeightCm!!, 0.04)
        assertEquals(1.2, faster.overstrideCm!! / right.overstrideCm!!, 0.05)
    }

    @Test
    fun heightSetsTheScaleWhenThereIsNoSpeed() {
        val short = build(160.0, null).metrics!!
        val tall = build(190.0, null).metrics!!
        assertEquals(190.0 / 160.0, tall.overstrideCm!! / short.overstrideCm!!, 0.03)
        assertFalse(short.scaledFromTreadmill)
    }

    @Test
    fun keepsTheEditFlagAndTheVideoNotes() {
        val session = build(175.0, runner.beltSpeedKmh(175.0), edited = true, notes = listOf("Frame rate dropped."))
        assertTrue(session.scaleEdited)
        assertEquals(SessionBuilder.MEASURE_VERSION, session.measureVersion)
        assertEquals(listOf("Frame rate dropped."), session.extractionNotes)
        assertTrue("Frame rate dropped." in session.warnings)
        assertEquals(175.0, session.runnerHeightCm, 0.0)
        assertNotNull(session.speedKmh)
    }

    @Test
    fun limitsWhatCanBeTyped() {
        assertTrue(SessionBuilder.validSpeed(null))
        assertTrue(SessionBuilder.validSpeed(6.2))
        assertFalse(SessionBuilder.validSpeed(2.9))
        assertFalse(SessionBuilder.validSpeed(31.0))
        assertTrue(SessionBuilder.validHeight(165.0))
        assertFalse(SessionBuilder.validHeight(119.0))
        assertFalse(SessionBuilder.validHeight(221.0))
    }
}
