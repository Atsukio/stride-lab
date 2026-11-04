package com.atsukio.runningcoach.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuvTest {

    /** A 4×2 grey frame whose luma encodes each pixel's position, with padded rows like real decoders. */
    private fun frame(pixelStrideUv: Int = 2): YuvFrame {
        val width = 4
        val height = 2
        val rowStride = 8
        val y = ByteArray(rowStride * height)
        for (row in 0 until height) for (col in 0 until width) y[row * rowStride + col] = luma(col, row).toByte()
        val uv = ByteArray(rowStride) { 128.toByte() }
        return YuvFrame(width, height, y, rowStride, 1, uv, uv, rowStride, pixelStrideUv)
    }

    private fun luma(col: Int, row: Int) = 40 + col * 30 + row * 100

    private fun grey(luma: Int): Int {
        val c = ((298 * (luma - 16) + 128) shr 8).coerceIn(0, 255)
        return (0xff shl 24) or (c shl 16) or (c shl 8) or c
    }

    private fun convert(rotation: Int): Pair<Yuv.Size, IntArray> {
        val size = Yuv.uprightSize(4, 2, rotation, maxSide = 100)
        val pixels = IntArray(size.width * size.height)
        Yuv.toArgb(frame(), rotation, size, pixels)
        return size to pixels
    }

    @Test
    fun `no rotation keeps pixel order`() {
        val (size, pixels) = convert(0)
        assertEquals(Yuv.Size(4, 2), size)
        assertEquals(grey(luma(0, 0)), pixels[0])
        assertEquals(grey(luma(3, 1)), pixels[7])
    }

    @Test
    fun `rotating 90 degrees puts the top-left pixel at the top right`() {
        val (size, pixels) = convert(90)
        assertEquals(Yuv.Size(2, 4), size)
        assertEquals(grey(luma(0, 0)), pixels[1])
        assertEquals(grey(luma(0, 1)), pixels[0])
        assertEquals(grey(luma(3, 0)), pixels[7])
    }

    @Test
    fun `rotating 270 degrees puts the top-left pixel at the bottom left`() {
        val (size, pixels) = convert(270)
        assertEquals(Yuv.Size(2, 4), size)
        assertEquals(grey(luma(0, 0)), pixels[6])
        assertEquals(grey(luma(3, 0)), pixels[0])
    }

    @Test
    fun `rotating 180 degrees reverses the frame`() {
        val (_, pixels) = convert(180)
        assertEquals(grey(luma(3, 1)), pixels[0])
        assertEquals(grey(luma(0, 0)), pixels[7])
    }

    @Test
    fun `chroma gives colour`() {
        val y = ByteArray(4) { 81.toByte() }
        val u = ByteArray(1) { 90.toByte() }
        val v = ByteArray(1) { 240.toByte() }
        val size = Yuv.Size(2, 2)
        val pixels = IntArray(4)
        Yuv.toArgb(YuvFrame(2, 2, y, 2, 1, u, v, 1, 1), 0, size, pixels)
        val red = (pixels[0] shr 16) and 0xff
        val green = (pixels[0] shr 8) and 0xff
        val blue = pixels[0] and 0xff
        assertTrue("Expected red, got $red,$green,$blue", red > 200 && green < 60 && blue < 60)
    }

    @Test
    fun `downscales to the longest side and turns upright`() {
        assertEquals(Yuv.Size(540, 960), Yuv.uprightSize(1920, 1080, 90, 960))
        assertEquals(Yuv.Size(960, 540), Yuv.uprightSize(1920, 1080, 0, 960))
        assertEquals(Yuv.Size(640, 360), Yuv.uprightSize(640, 360, 0, 960))
    }
}

class YuvP010Test {
    @Test
    fun readsTheHighByteOfSixteenBitSamples() {
        // One 2x2 frame of 16-bit little-endian samples: Y high byte 200, chroma neutral (128).
        val y = byteArrayOf(0, 200.toByte(), 0, 200.toByte(), 0, 200.toByte(), 0, 200.toByte())
        val uv = byteArrayOf(0, 128.toByte(), 0, 128.toByte())
        val pixels = IntArray(4)
        Yuv.toArgb(YuvFrame(2, 2, y, 4, 2, uv, uv.copyOfRange(2, 4) + uv, 4, 4, sampleOffset = 1), 0, Yuv.Size(2, 2), pixels)
        val grey = ((298 * (200 - 16) + 128) shr 8).coerceIn(0, 255)
        assertEquals(grey, pixels[0] and 0xff)
        assertEquals(grey, (pixels[3] shr 16) and 0xff)
    }
}
