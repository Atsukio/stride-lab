package com.atsukio.runningcoach.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ArgbToYuvTest {
    private class Planes(width: Int, height: Int, uvPixelStride: Int) {
        val yBuffer: ByteBuffer = ByteBuffer.allocate(width * height)
        // Interleaved (semi-planar) chroma has a pixel stride of 2, planar has 1.
        val uBuffer: ByteBuffer = ByteBuffer.allocate((width / 2) * (height / 2) * uvPixelStride)
        val vBuffer: ByteBuffer = ByteBuffer.allocate((width / 2) * (height / 2) * uvPixelStride)
    }

    private fun convert(colour: Int, uvPixelStride: Int = 1, width: Int = 4, height: Int = 4): Planes {
        val planes = Planes(width, height, uvPixelStride)
        ArgbToYuv.fill(
            IntArray(width * height) { colour }, width, height,
            planes.yBuffer, width, 1,
            planes.uBuffer, planes.vBuffer, (width / 2) * uvPixelStride, uvPixelStride,
        )
        return planes
    }

    private fun Byte.u() = toInt() and 0xFF

    private fun near(expected: Int, actual: Int) = assertTrue("expected $expected but was $actual", kotlin.math.abs(expected - actual) <= 1)

    @Test
    fun blackIsTheLimitedRangeFloor() {
        val p = convert(0xFF000000.toInt())
        assertEquals(16, p.yBuffer.get(0).u())
        assertEquals(128, p.uBuffer.get(0).u())
        assertEquals(128, p.vBuffer.get(0).u())
    }

    @Test
    fun whiteIsTheLimitedRangeCeiling() {
        val p = convert(0xFFFFFFFF.toInt())
        near(235, p.yBuffer.get(0).u())
        near(128, p.uBuffer.get(0).u())
        near(128, p.vBuffer.get(0).u())
    }

    @Test
    fun redPushesVUpAndUDown() {
        val p = convert(0xFFFF0000.toInt())
        assertTrue(p.vBuffer.get(0).u() > 200)
        assertTrue(p.uBuffer.get(0).u() < 110)
    }

    @Test
    fun bluePushesUUpAndVDown() {
        val p = convert(0xFF0000FF.toInt())
        assertTrue(p.uBuffer.get(0).u() > 200)
        assertTrue(p.vBuffer.get(0).u() < 128)
    }

    @Test
    fun interleavedChromaIsWrittenEverySecondByte() {
        val p = convert(0xFFFF0000.toInt(), uvPixelStride = 2)
        val first = p.vBuffer.get(0).u()
        assertEquals(first, p.vBuffer.get(2).u())
        // The bytes between are other samples' slots in the shared plane, left untouched.
        assertEquals(0, p.vBuffer.get(1).u())
    }

    @Test
    fun aChromaSampleAveragesItsTwoByTwoBlock() {
        val width = 2
        val height = 2
        val planes = Planes(width, height, 1)
        // Two black and two white pixels average to mid grey, whose chroma is neutral.
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        ArgbToYuv.fill(pixels, width, height, planes.yBuffer, width, 1, planes.uBuffer, planes.vBuffer, 1, 1)
        near(128, planes.uBuffer.get(0).u())
        near(128, planes.vBuffer.get(0).u())
        assertEquals(16, planes.yBuffer.get(0).u())
        near(235, planes.yBuffer.get(1).u())
    }

    @Test
    fun aChromaPlaneOneByteShortDoesNotOverflow() {
        val width = 4
        val height = 4
        val y = ByteBuffer.allocate(width * height)
        // Interleaved chroma whose buffer ends before the final sample's partner byte.
        val u = ByteBuffer.allocate(3 * 2)
        val v = ByteBuffer.allocate(3 * 2)
        ArgbToYuv.fill(IntArray(16) { 0xFF336699.toInt() }, width, height, y, width, 1, u, v, 4, 2)
        assertEquals(6, u.limit())
    }
}
