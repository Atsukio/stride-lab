package com.atsukio.runningcoach.pose

import com.atsukio.runningcoach.analysis.FramePose
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Puts poses on an even timeline. Phones often record at a variable frame rate (a "60 fps" clip slows to
 * 30 fps in dim light), but the analysis and the replay overlay count time in whole frames. Frame i of the
 * result is at i / [Regular.fps] seconds of video time, matching the player's position.
 */
internal object FrameTiming {
    class Regular(
        val frames: List<FramePose?>,
        val fps: Float,
        /** Lowest frame rate the camera dropped to, when it varied noticeably. */
        val droppedToFps: Float?,
    )

    fun regularise(frames: List<FramePose?>, timesUs: LongArray): Regular? {
        if (timesUs.size != frames.size) return null
        return regularise(timesUs, frames.indices.associateWith { frames[it] })
    }

    /**
     * [timesUs] holds every frame's time; [poses] only the frames that were analysed (a null pose means
     * the runner wasn't found). Frames that weren't analysed are filled in from their neighbours.
     */
    fun regularise(timesUs: LongArray, poses: Map<Int, FramePose?>): Regular? {
        if (timesUs.size < MIN_FRAMES || poses.size < MIN_FRAMES) return null
        val intervals = (1 until timesUs.size).map { timesUs[it] - timesUs[it - 1] }.filter { it > 0 }.sorted()
        if (intervals.size < MIN_FRAMES) return null
        // The camera's full rate is the short, common interval; slow stretches only add longer ones.
        val step = intervals[intervals.size / 5]
        val slow = intervals.count { it > step * 3 / 2 }
        val droppedTo = if (slow > intervals.size * VARIABLE_FRACTION) {
            1e6f / intervals[intervals.size * 9 / 10]
        } else {
            null
        }
        val count = (timesUs.last() / step.toDouble()).roundToInt() + 1
        val fps = 1e6f / step
        val timed = poses.entries.filter { it.key in timesUs.indices }
            .map { LiveAlignment.TimedPose(timesUs[it.key] * 1000, it.value) }
        val maxGapNs = max(MIN_GAP_NS, step * 1000 * 5 / 2)
        return Regular(LiveAlignment.resample(timed, 0, count, fps, maxGapNs), fps, droppedTo)
    }

    private const val MIN_FRAMES = 10
    private const val VARIABLE_FRACTION = 0.05
    private const val MIN_GAP_NS = 50_000_000L
}
