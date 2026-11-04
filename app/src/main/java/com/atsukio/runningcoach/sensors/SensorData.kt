package com.atsukio.runningcoach.sensors

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Latest readings from a connected watch or sensor. Null fields aren't sent by that device. */
data class LiveStats(
    val heartRate: Int? = null,
    val speedMps: Double? = null,
    val cadenceSpm: Int? = null,
    val updatedAtMs: Long = 0,
) {
    val speedKmh: Double? get() = speedMps?.let { it * 3.6 }

    fun toSample(tSec: Double) = SensorSample(tSec, heartRate, speedKmh, cadenceSpm)
}

/** One reading, [tSec] seconds after the start of a clip or run. */
@Serializable
data class SensorSample(
    val tSec: Double,
    val heartRate: Int? = null,
    val speedKmh: Double? = null,
    val cadenceSpm: Int? = null,
)

/** Average sensor readings over a clip. */
@Serializable
data class SensorSummary(
    val heartRate: Int? = null,
    val speedKmh: Double? = null,
    val cadenceSpm: Double? = null,
) {
    companion object {
        /** Averages the readings, ignoring zero speed or cadence from a device that is standing still. */
        fun of(samples: List<SensorSample>): SensorSummary? {
            val heartRates = samples.mapNotNull { it.heartRate }.filter { it > 0 }
            val speeds = samples.mapNotNull { it.speedKmh }.filter { it > 0.5 }
            val cadences = samples.mapNotNull { it.cadenceSpm }.filter { it > 0 }
            if (heartRates.isEmpty() && speeds.isEmpty() && cadences.isEmpty()) return null
            return SensorSummary(
                heartRate = heartRates.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                speedKmh = speeds.takeIf { it.isNotEmpty() }?.average(),
                cadenceSpm = cadences.takeIf { it.isNotEmpty() }?.average(),
            )
        }
    }
}
