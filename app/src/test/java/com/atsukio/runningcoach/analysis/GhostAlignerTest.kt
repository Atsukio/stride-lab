package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostAlignerTest {
    private fun contactsOf(track: PoseTrack): List<Int> =
        (GaitAnalyzer.analyze(track, 175.0) as GaitResult.Success).metrics.contactFrames

    @Test
    fun anEarlierClipOfTheSameRunnerLandsOnTopOfTheCurrentOne() {
        val track = SyntheticRunner().track(12.0)
        val contacts = contactsOf(track)
        val ghost = GhostAligner.create(track, contacts, track, contacts)
        assertNotNull(ghost)
        val frame = contacts[2] + 3
        val pose = ghost!!.frameAt(frame)!!
        val cur = track.frames[frame]!!
        assertEquals(cur.x[Landmark.LEFT_ANKLE], pose.x[Landmark.LEFT_ANKLE], 0.01f)
        assertEquals(cur.y[Landmark.RIGHT_ANKLE], pose.y[Landmark.RIGHT_ANKLE], 0.01f)
    }

    @Test
    fun aLongerStrideShowsUpAsAFootFurtherForward() {
        val current = SyntheticRunner(landingAheadPx = 30.0).track(12.0)
        val earlier = SyntheticRunner(landingAheadPx = 110.0).track(12.0)
        val ghost = GhostAligner.create(current, contactsOf(current), earlier, contactsOf(earlier))!!
        val contacts = contactsOf(current)
        val frame = contacts[3]
        val pose = ghost.frameAt(frame)!!
        val cur = current.frames[frame]!!
        // The foot that has just landed is the lower heel; its ankle is further ahead in the earlier clip.
        val heel = if (cur.y[Landmark.LEFT_HEEL] >= cur.y[Landmark.RIGHT_HEEL]) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE
        assertTrue(pose.x[heel] > cur.x[heel] + 0.01f)
    }

    @Test
    fun anEarlierClipFacingTheOtherWayIsMirrored() {
        val current = SyntheticRunner(facingRight = true).track(12.0)
        val earlier = SyntheticRunner(facingRight = false).track(12.0)
        val ghost = GhostAligner.create(current, contactsOf(current), earlier, contactsOf(earlier))!!
        val frame = contactsOf(current)[2] + 2
        val pose = ghost.frameAt(frame)!!
        val hipX = (pose.x[Landmark.LEFT_HIP] + pose.x[Landmark.RIGHT_HIP]) / 2
        assertTrue("nose should be in front of the hips", pose.x[Landmark.NOSE] > hipX)
    }

    @Test
    fun givesNoGhostWithoutEnoughLandings() {
        val track = SyntheticRunner().track(12.0)
        assertNull(GhostAligner.create(track, listOf(10, 40), track, contactsOf(track)))
    }
}
