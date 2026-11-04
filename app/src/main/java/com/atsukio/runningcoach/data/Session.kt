package com.atsukio.runningcoach.data

import com.atsukio.runningcoach.analysis.GaitMetrics
import kotlinx.serialization.Serializable

/** One analysed clip. [failure] is set instead of [metrics] when the clip couldn't be analysed. */
@Serializable
data class Session(
    val id: String,
    val createdAt: Long,
    val speedKmh: Double? = null,
    val runnerHeightCm: Double,
    /** Real-world frame rate used for timings. */
    val fps: Float,
    /** Frame rate of the file as played back; differs from [fps] for slow-motion clips. */
    val playbackFps: Float,
    val videoWidth: Int,
    val videoHeight: Int,
    val metrics: GaitMetrics? = null,
    val warnings: List<String> = emptyList(),
    val failure: String? = null,
)
