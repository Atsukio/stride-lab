package com.atsukio.runningcoach.analysis

/** Pose landmarks for one video frame, in normalised image coordinates (0..1). */
class FramePose(
    val x: FloatArray,
    val y: FloatArray,
    val visibility: FloatArray,
)

/**
 * The detected pose for every frame of a clip, with null where no runner was found.
 *
 * [fps] is the real-world frame rate, so slow-motion clips still give correct timings.
 * [width] and [height] are the upright video dimensions in pixels.
 */
class PoseTrack(
    val fps: Float,
    val width: Int,
    val height: Int,
    val frames: List<FramePose?>,
)
