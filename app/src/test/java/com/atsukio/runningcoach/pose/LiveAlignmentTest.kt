package com.atsukio.runningcoach.pose

import com.atsukio.runningcoach.analysis.FramePose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class LiveAlignmentTest {
    private val frameNs = 1_000_000_000L / 60
    private val cameraClockStart = 987_654_321_000L
    private val width = 36
    private val height = 64

    /**
     * A treadmill runner repeats almost the same picture every stride (42 frames here), so each frame is
     * mostly its stride phase plus a little of its own detail, which is what tells strides apart.
     */
    private fun scene(frame: Int, noise: Random? = null): ByteArray {
        val own = Random(frame)
        val phase = frame % 42
        return ByteArray(width * height) { i ->
            val base = 120 + 80 * sin(i * 0.3 + phase * 0.15)
            val detail = own.nextInt(-12, 12)
            val compression = noise?.nextInt(-3, 3) ?: 0
            (base + detail + compression).toInt().coerceIn(0, 255).toByte()
        }
    }

    private fun liveThumbs(frames: IntRange, step: Int = 1) = frames.step(step).map {
        LiveAlignment.Thumb(cameraClockStart + it * frameNs, width, height, scene(it))
    }

    /** The video's first frames: camera frames from [firstFrame] on, timestamped from zero in whole ms. */
    private fun videoThumbs(firstFrame: Int, count: Int = 30): List<LiveAlignment.Thumb> {
        val noise = Random(7)
        return (0 until count).map { i ->
            val ptsMs = (i * frameNs) / 1_000_000
            LiveAlignment.Thumb(ptsMs * 1_000_000, width, height, scene(firstFrame + i, noise))
        }
    }

    @Test
    fun findsTheCameraTimeOfTheFirstVideoFrame() {
        val live = liveThumbs(0..900)
        val begin = cameraClockStart + 30 * frameNs
        val start = LiveAlignment.videoStartNs(videoThumbs(firstFrame = 47), live, begin)
        assertEquals(cameraClockStart + 47 * frameNs, start)
    }

    @Test
    fun worksWhenTheLiveStreamOnlyGetsEveryOtherFrame() {
        val live = liveThumbs(0..900, step = 2)
        val begin = cameraClockStart + 20 * frameNs
        val start = LiveAlignment.videoStartNs(videoThumbs(firstFrame = 40), live, begin)
        assertEquals(cameraClockStart + 40 * frameNs, start)
    }

    @Test
    fun givesUpWhenTheVideoIsntAmongTheLiveFrames() {
        val live = liveThumbs(0..900)
        val unrelated = (0 until 30).map { i ->
            LiveAlignment.Thumb(i * frameNs, width, height, ByteArray(width * height) { Random(1000 + i).nextInt(256).toByte() })
        }
        assertNull(LiveAlignment.videoStartNs(unrelated, live, cameraClockStart + 30 * frameNs))
    }

    private fun pose(x: Float) = FramePose(FloatArray(33) { x }, FloatArray(33) { x / 2 }, FloatArray(33) { 0.9f })

    @Test
    fun resampleUsesExactFramesAndBridgesShortGaps() {
        val start = 5_000_000_000L
        val poses = listOf(
            LiveAlignment.TimedPose(start, pose(0.1f)),
            LiveAlignment.TimedPose(start + 2 * frameNs, pose(0.3f)),
            LiveAlignment.TimedPose(start + 3 * frameNs, null),
            LiveAlignment.TimedPose(start + 10 * frameNs, pose(0.5f)),
        )
        val frames = LiveAlignment.resample(poses, start, frameCount = 12, fps = 60f, maxGapNs = 50_000_000L)
        assertEquals(0.1f, frames[0]!!.x[0], 1e-4f)
        assertEquals(0.2f, frames[1]!!.x[0], 1e-3f) // halfway between the frames either side
        assertEquals(0.3f, frames[2]!!.x[0], 1e-4f)
        assertNull(frames[3]) // analysed, but no runner found
        assertNull(frames[6]) // 7 frames between poses is more than 50 ms
        assertEquals(0.5f, frames[10]!!.x[0], 1e-4f)
        assertNull(frames[11])
    }
}
