package com.atsukio.runningcoach.data

import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.sensors.SensorSample
import com.atsukio.runningcoach.sensors.SensorSummary
import kotlinx.serialization.Serializable

/** One analysed clip. [failure] is set instead of [metrics] when the clip couldn't be analysed. */
@Serializable
data class Session(
    val id: String,
    val createdAt: Long,
    val speedKmh: Double? = null,
    /** True when [speedKmh] came from a connected watch or foot pod rather than being typed in. */
    val speedFromSensor: Boolean = false,
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
    /** Watch or sensor readings averaged over the clip. */
    val sensor: SensorSummary? = null,
    /** Set when the clip was taken during a whole run. */
    val runId: String? = null,
    /** Seconds from the start of the run to the start of this clip. */
    val runOffsetSec: Double? = null,
    /** How long the analysis took after the clip was saved, in seconds. */
    val processingSec: Double? = null,
    /** Whether the poses came from analysing while recording. */
    val analysedLive: Boolean = false,
    /** True once the speed or height was changed by hand for this clip, so a re-analysis keeps them. */
    val scaleEdited: Boolean = false,
    /** Notes from reading the video (such as a dropped frame rate), kept so a recalculation can repeat them. */
    val extractionNotes: List<String> = emptyList(),
    /** Which version of the measuring code made [metrics]; see [SessionBuilder.MEASURE_VERSION]. */
    val measureVersion: Int = 0,
)

/** What was known when a clip was captured, saved until the clip has been analysed. */
@Serializable
data class CaptureInfo(
    val speedKmh: Double? = null,
    val speedFromSensor: Boolean = false,
    val sensor: SensorSummary? = null,
    val runId: String? = null,
    val runOffsetSec: Double? = null,
)

/** A run during which several clips were taken, with sensor readings for the whole run. */
@Serializable
data class WholeRun(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val samples: List<SensorSample> = emptyList(),
) {
    val durationSec: Double
        get() = endedAt?.let { (it - startedAt) / 1000.0 } ?: samples.lastOrNull()?.tSec ?: 0.0
}
