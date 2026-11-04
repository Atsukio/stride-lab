package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PoseCleanerTest {
    private val runner = SyntheticRunner()
    private val truth = runner.track(12.0)

    private val leftLeg = intArrayOf(Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX)
    private val rightLeg = intArrayOf(Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX)

    /** Applies [change] to a copy of each frame's coordinates. */
    private fun corrupt(change: (frame: Int, x: FloatArray, y: FloatArray) -> Unit): PoseTrack =
        PoseTrack(truth.fps, truth.width, truth.height, truth.frames.mapIndexed { f, pose ->
            pose!!
            val x = pose.x.copyOf()
            val y = pose.y.copyOf()
            change(f, x, y)
            FramePose(x, y, pose.visibility.copyOf())
        })

    /** Largest distance in pixels between a cleaned landmark and where it really was. */
    private fun worstErrorPx(cleaned: PoseTrack, landmark: Int, frames: IntRange = 2 until truth.frames.size - 2): Double =
        frames.maxOf { f ->
            val a = truth.frames[f]!!
            val b = cleaned.frames[f]!!
            hypot(
                ((a.x[landmark] - b.x[landmark]) * truth.width).toDouble(),
                ((a.y[landmark] - b.y[landmark]) * truth.height).toDouble(),
            )
        }

    @Test
    fun cleanTrackIsBarelyChanged() {
        val cleaned = PoseCleaner.clean(truth)
        // Only the light smoothing moves anything: a few pixels where the foot changes direction.
        assertTrue(worstErrorPx(cleaned, Landmark.LEFT_ANKLE) < 12)
        assertTrue(worstErrorPx(cleaned, Landmark.RIGHT_FOOT_INDEX) < 12)
    }

    @Test
    fun singleFrameJumpsAreRemoved() {
        val bad = setOf(100, 101, 250, 400)
        val track = corrupt { f, x, y ->
            if (f in bad) {
                x[Landmark.LEFT_ANKLE] += 0.2f
                y[Landmark.LEFT_ANKLE] -= 0.3f
            }
        }
        assertTrue(worstErrorPx(PoseCleaner.clean(track), Landmark.LEFT_ANKLE) < 15)
    }

    @Test
    fun swappedLegsAreSwappedBack() {
        val swapped = (200..260) + (500..503)
        val track = corrupt { f, x, y ->
            if (f in swapped) for (k in leftLeg.indices) {
                val a = leftLeg[k]
                val b = rightLeg[k]
                x[a] = x[b].also { x[b] = x[a] }
                y[a] = y[b].also { y[b] = y[a] }
            }
        }
        val cleaned = PoseCleaner.clean(track)
        // Away from the moments the two feet pass each other, every frame is back on the right leg.
        assertTrue(worstErrorPx(cleaned, Landmark.LEFT_ANKLE) < 15)
        assertTrue(worstErrorPx(cleaned, Landmark.RIGHT_HEEL) < 15)
    }

    @Test
    fun analysisSurvivesDetectorMistakes() {
        val track = corrupt { f, x, y ->
            if (f % 97 == 0) x[Landmark.RIGHT_ANKLE] += 0.25f
            if (f in 300..340) for (k in leftLeg.indices) {
                val a = leftLeg[k]
                val b = rightLeg[k]
                x[a] = x[b].also { x[b] = x[a] }
                y[a] = y[b].also { y[b] = y[a] }
            }
        }
        val clean = (GaitAnalyzer.analyze(truth, 175.0) as GaitResult.Success).metrics
        val noisy = (GaitAnalyzer.analyze(track, 175.0) as GaitResult.Success).metrics
        assertEquals(clean.cadenceSpm!!, noisy.cadenceSpm!!, 1.0)
        assertTrue(abs(clean.overstrideCm!! - noisy.overstrideCm!!) < 2)
    }
}
