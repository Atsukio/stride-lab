package com.atsukio.runningcoach.sensors

import java.util.UUID

/** Standard Bluetooth services used by heart-rate straps, foot pods and watches such as Garmin's Virtual Run. */
object GattIds {
    val HEART_RATE_SERVICE: UUID = uuid16(0x180D)
    val HEART_RATE_MEASUREMENT: UUID = uuid16(0x2A37)
    val RSC_SERVICE: UUID = uuid16(0x1814)
    val RSC_MEASUREMENT: UUID = uuid16(0x2A53)
    val CLIENT_CONFIG: UUID = uuid16(0x2902)

    private fun uuid16(value: Int): UUID = UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(value))
}

/** A Running Speed and Cadence measurement. */
data class RscMeasurement(
    val speedMps: Double,
    val cadenceSpm: Int,
    val strideLengthM: Double?,
)

object GattParsers {

    /** Heart Rate Measurement (0x2A37): flags, then an 8- or 16-bit rate. */
    fun heartRate(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        val wide = (u8(data, 0) and 0x01) != 0
        return when {
            wide && data.size >= 3 -> u16(data, 1)
            !wide && data.size >= 2 -> u8(data, 1)
            else -> null
        }
    }

    /** RSC Measurement (0x2A53): flags, speed in 1/256 m/s, cadence per minute, then optional stride length. */
    fun rsc(data: ByteArray): RscMeasurement? {
        if (data.size < 4) return null
        val flags = u8(data, 0)
        val speed = u16(data, 1) / 256.0
        val cadence = u8(data, 3)
        val hasStride = (flags and 0x01) != 0
        val stride = if (hasStride && data.size >= 6) u16(data, 4) / 100.0 else null
        return RscMeasurement(speed, normaliseCadence(cadence, speed), stride)
    }

    /**
     * Some foot pods report strides (one foot) rather than steps per minute.
     * Under 110 while running is implausible as steps, so it is doubled.
     */
    fun normaliseCadence(raw: Int, speedMps: Double): Int =
        if (raw in 1 until 110 && speedMps >= 1.8) raw * 2 else raw

    private fun u8(data: ByteArray, offset: Int) = data[offset].toInt() and 0xFF

    private fun u16(data: ByteArray, offset: Int) = u8(data, offset) or (u8(data, offset + 1) shl 8)
}
