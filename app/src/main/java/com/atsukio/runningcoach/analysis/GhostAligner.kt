package com.atsukio.runningcoach.analysis

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Lines up an earlier clip's skeleton with the current clip so the two can be drawn together: the earlier
 * runner is placed at the same step of the stride (so both feet land at the same moment), at the same size,
 * hips over hips, and facing the same way. What differs is then the form, not the camera or the timing.
 */
class GhostAligner private constructor(
    private val current: PoseTrack,
    private val currentContacts: List<Int>,
    private val previous: PoseTrack,
    private val previousContacts: List<Int>,
    private val currentLegPx: Double,
    private val previousLegPx: Double,
    private val mirror: Boolean,
) {
    /** The earlier pose for [frame] of the current clip, in the current clip's normalised coordinates. */
    fun frameAt(frame: Int): FramePose? {
        val cur = current.frames.getOrNull(frame) ?: return null
        val curHip = hip(cur, current.width, current.height) ?: return null

        // Which stride we are in, and how far through it.
        val i = currentContacts.indexOfLast { it <= frame }
        if (i < 0 || i >= currentContacts.size - 1) return null
        val span = (currentContacts[i + 1] - currentContacts[i]).coerceAtLeast(1)
        val phase = (frame - currentContacts[i]).toDouble() / span

        // The same step in the earlier clip, cycling through its strides.
        val j = i % (previousContacts.size - 1)
        val prevSpan = previousContacts[j + 1] - previousContacts[j]
        val prevFrame = (previousContacts[j] + phase * prevSpan).roundToInt()
        val prev = previous.frames.getOrNull(prevFrame) ?: return null
        val prevHip = hip(prev, previous.width, previous.height) ?: return null

        val scale = currentLegPx / previousLegPx
        val flip = if (mirror) -1.0 else 1.0
        val x = FloatArray(Landmark.COUNT)
        val y = FloatArray(Landmark.COUNT)
        for (k in 0 until Landmark.COUNT) {
            val dx = (prev.x[k] * previous.width - prevHip.first) * scale * flip
            val dy = (prev.y[k] * previous.height - prevHip.second) * scale
            x[k] = ((curHip.first + dx) / current.width).toFloat()
            y[k] = ((curHip.second + dy) / current.height).toFloat()
        }
        return FramePose(x, y, prev.visibility)
    }

    companion object {
        /** Null when either clip has too few landings or no readable body to compare. */
        fun create(
            current: PoseTrack,
            currentContacts: List<Int>,
            previous: PoseTrack,
            previousContacts: List<Int>,
        ): GhostAligner? {
            if (currentContacts.size < 3 || previousContacts.size < 3) return null
            val currentLeg = legPx(current) ?: return null
            val previousLeg = legPx(previous) ?: return null
            // Mirror the earlier clip if the runner faced the other way on the treadmill.
            val mirror = facing(current) * facing(previous) < 0
            return GhostAligner(current, currentContacts, previous, previousContacts, currentLeg, previousLeg, mirror)
        }

        private fun hip(pose: FramePose, width: Int, height: Int): Pair<Double, Double>? {
            val l = Landmark.LEFT_HIP
            val r = Landmark.RIGHT_HIP
            if (pose.x[l].isNaN() || pose.x[r].isNaN()) return null
            return Pair((pose.x[l] + pose.x[r]) / 2.0 * width, (pose.y[l] + pose.y[r]) / 2.0 * height)
        }

        /** Typical hip-to-ankle distance in pixels, from the longer half of the frames (legs are shortened by foreshortening). */
        private fun legPx(track: PoseTrack): Double? {
            val lengths = track.frames.filterNotNull().mapNotNull { pose ->
                val hip = hip(pose, track.width, track.height) ?: return@mapNotNull null
                listOf(Landmark.LEFT_ANKLE, Landmark.RIGHT_ANKLE).mapNotNull { a ->
                    if (pose.x[a].isNaN()) null
                    else hypot(pose.x[a] * track.width - hip.first, pose.y[a] * track.height - hip.second)
                }.maxOrNull()
            }.sorted()
            if (lengths.isEmpty()) return null
            return lengths[(lengths.size * 0.75).toInt().coerceAtMost(lengths.size - 1)].takeIf { it > 1.0 }
        }

        /** +1 when the runner faces right, -1 when left, from where the nose sits relative to the hips. */
        internal fun facing(track: PoseTrack): Double {
            val offsets = track.frames.filterNotNull().mapNotNull { pose ->
                val hip = hip(pose, track.width, track.height) ?: return@mapNotNull null
                if (pose.x[Landmark.NOSE].isNaN()) null else pose.x[Landmark.NOSE] * track.width - hip.first
            }
            return sign(offsets.sorted().getOrElse(offsets.size / 2) { 0.0 }).takeIf { abs(it) > 0 } ?: 1.0
        }
    }
}
