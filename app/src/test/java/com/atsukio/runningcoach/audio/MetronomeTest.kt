package com.atsukio.runningcoach.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeTest {
    @Test
    fun oneBeatLastsExactlyOneStep() {
        // 170 steps/min is 0.353 s a step; at 44.1 kHz that's 15,565 samples.
        assertEquals(15_565, Metronome.beat(170).size)
        assertEquals(17_640, Metronome.beat(150).size)
    }

    @Test
    fun clickIsAtTheStartThenSilence() {
        val beat = Metronome.beat(170)
        assertTrue(beat.take(200).any { it > 10_000 })
        assertTrue(beat.drop(2_000).all { it == 0.toShort() })
    }
}
