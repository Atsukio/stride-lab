package com.atsukio.runningcoach.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

class GattParsersTest {
    @Test
    fun heartRate8Bit() = assertEquals(72, GattParsers.heartRate(bytes(0x00, 72)))

    @Test
    fun heartRate16Bit() = assertEquals(300, GattParsers.heartRate(bytes(0x01, 0x2C, 0x01)))

    @Test
    fun heartRateTooShort() = assertNull(GattParsers.heartRate(bytes(0x01, 0x2C)))

    @Test
    fun rscWithStrideLength() {
        // 3.0 m/s = 768/256, 170 steps/min, stride 1.20 m, then total distance.
        val m = GattParsers.rsc(bytes(0x03, 0x00, 0x03, 170, 120, 0, 0x10, 0x27, 0, 0))!!
        assertEquals(3.0, m.speedMps, 0.001)
        assertEquals(170, m.cadenceSpm)
        assertEquals(1.2, m.strideLengthM!!, 0.001)
    }

    @Test
    fun rscStridesPerMinuteAreDoubled() {
        val m = GattParsers.rsc(bytes(0x00, 0x00, 0x03, 85))!!
        assertEquals(170, m.cadenceSpm)
        assertNull(m.strideLengthM)
    }

    @Test
    fun rscStandingStillKeepsZeroCadence() {
        assertEquals(0, GattParsers.rsc(bytes(0x00, 0x00, 0x00, 0))!!.cadenceSpm)
    }

    @Test
    fun rscTooShort() = assertNull(GattParsers.rsc(bytes(0x00, 0x00, 0x03)))

    @Test
    fun standardUuids() {
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", GattIds.HEART_RATE_SERVICE.toString())
        assertEquals("00002a53-0000-1000-8000-00805f9b34fb", GattIds.RSC_MEASUREMENT.toString())
    }
}

class SensorSummaryTest {
    @Test
    fun averagesReadings() {
        val summary = SensorSummary.of(
            listOf(
                SensorSample(0.0, heartRate = 150, speedKmh = 10.0, cadenceSpm = 168),
                SensorSample(1.0, heartRate = 152, speedKmh = 10.4, cadenceSpm = 170),
            ),
        )!!
        assertEquals(151, summary.heartRate)
        assertEquals(10.2, summary.speedKmh!!, 0.001)
        assertEquals(169.0, summary.cadenceSpm!!, 0.001)
    }

    @Test
    fun heartRateOnlyDevice() {
        val summary = SensorSummary.of(listOf(SensorSample(0.0, heartRate = 140)))!!
        assertEquals(140, summary.heartRate)
        assertNull(summary.speedKmh)
        assertNull(summary.cadenceSpm)
    }

    @Test
    fun ignoresZeroSpeedAndCadence() {
        val summary = SensorSummary.of(listOf(SensorSample(0.0, heartRate = 120, speedKmh = 0.0, cadenceSpm = 0)))!!
        assertNull(summary.speedKmh)
        assertNull(summary.cadenceSpm)
    }

    @Test
    fun noReadings() = assertNull(SensorSummary.of(emptyList()))
}
