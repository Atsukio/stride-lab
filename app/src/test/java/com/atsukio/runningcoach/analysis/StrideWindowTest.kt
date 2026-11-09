package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StrideWindowTest {
    private val contacts = listOf(10, 40, 70, 100, 130)

    @Test
    fun aWindowRunsFromOneLandingToTheLandingTwoStepsLater() {
        val window = StrideWindow.at(contacts, 1)!!
        assertEquals(40, window.startFrame)
        assertEquals(100, window.endFrame)
        assertEquals(60, window.length)
    }

    @Test
    fun theCountIsHowManyWindowsFit() {
        assertEquals(3, StrideWindow.count(contacts))
        assertEquals(0, StrideWindow.count(listOf(10, 40)))
        assertEquals(0, StrideWindow.count(emptyList()))
    }

    @Test
    fun theLastWindowEndsOnTheLastLandingAndNoneIsPastIt() {
        assertNotNull(StrideWindow.at(contacts, 2))
        assertEquals(130, StrideWindow.at(contacts, 2)!!.endFrame)
        assertNull(StrideWindow.at(contacts, 3))
        assertNull(StrideWindow.at(contacts, -1))
    }

    @Test
    fun aWindowWithNoLengthIsRejected() {
        assertNull(StrideWindow.at(listOf(5, 5, 5), 0))
    }

    @Test
    fun theOtherClipPlaysFasterWhenItsStrideIsLonger() {
        val first = StrideWindow(0, 60)
        val second = StrideWindow(0, 90)
        // 90 frames in the time 60 take: one and a half times as fast.
        assertEquals(0.75f, StrideWindow.matchingSpeed(0.5f, first, 60f, second, 60f), 1e-6f)
    }

    @Test
    fun frameRatesAreAccountedFor() {
        val first = StrideWindow(0, 60)
        val second = StrideWindow(0, 60)
        // The second clip is 30 fps, so its 60 frames last twice as long and must play twice as fast.
        assertEquals(2f, StrideWindow.matchingSpeed(1f, first, 60f, second, 30f), 1e-6f)
    }
}
