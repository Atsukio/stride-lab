package com.atsukio.runningcoach.analysis

/**
 * Spots foot landings as live poses arrive, one frame at a time, only to flash a pulse on the camera preview.
 * The measurements use [GaitAnalyzer] on the whole clip instead, so this can be quick and rough.
 *
 * The lowest heel on screen rises and falls once per step, because the two feet take turns being lowest. A landing
 * is when it comes down to the bottom of its recent range.
 */
class LiveStrikeDetector(
    private val windowMs: Long = 1500,
    private val minGapMs: Long = 220,
) {
    /** Where the landing foot's ankle is, in normalised image coordinates. */
    class Strike(val x: Float, val y: Float)

    private val history = ArrayDeque<Pair<Long, Float>>()
    private var low = false
    private var lastStrikeMs = Long.MIN_VALUE / 2

    /** Feeds the next pose, taken at [nowMs]; returns the landing if this frame is where one begins. */
    fun update(pose: FramePose?, nowMs: Long): Strike? {
        if (pose == null) {
            low = false
            return null
        }
        val left = pose.y[Landmark.LEFT_HEEL]
        val right = pose.y[Landmark.RIGHT_HEEL]
        if (left.isNaN() && right.isNaN()) return null
        val useLeft = right.isNaN() || (!left.isNaN() && left >= right)
        val heel = if (useLeft) left else right

        history.addLast(nowMs to heel)
        while (history.isNotEmpty() && nowMs - history.first().first > windowMs) history.removeFirst()
        val lo = history.minOf { it.second }
        val hi = history.maxOf { it.second }
        val span = hi - lo
        // Standing still, or the detector wobbling, moves the heel too little to be a stride.
        if (span < MIN_SPAN) {
            low = false
            return null
        }
        val arrived = heel >= lo + ARRIVE * span
        val departed = heel <= lo + LEAVE * span
        if (!low && arrived) {
            low = true
            if (nowMs - lastStrikeMs >= minGapMs) {
                lastStrikeMs = nowMs
                val ankle = if (useLeft) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE
                return Strike(pose.x[ankle], pose.y[ankle])
            }
        } else if (low && departed) {
            low = false
        }
        return null
    }

    private companion object {
        /** Smallest heel movement, as a share of the frame height, that counts as running. */
        const val MIN_SPAN = 0.03f

        /** How far down its range the heel must be to count as landed, and how far up before the next landing can count. */
        const val ARRIVE = 0.75f
        const val LEAVE = 0.55f
    }
}
