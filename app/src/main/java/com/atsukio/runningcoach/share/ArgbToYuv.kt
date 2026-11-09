package com.atsukio.runningcoach.share

import java.nio.ByteBuffer

/**
 * Converts ARGB pixels into the planes of a YUV 4:2:0 encoder input image, using BT.709 limited-range
 * values (the encoder is told the same, so colours match on playback). Plane strides come from the
 * [android.media.Image], which is why the writes go to computed positions rather than in order.
 */
internal object ArgbToYuv {

    fun fill(
        argb: IntArray,
        width: Int,
        height: Int,
        y: ByteBuffer,
        yRowStride: Int,
        yPixelStride: Int,
        u: ByteBuffer,
        v: ByteBuffer,
        uvRowStride: Int,
        uvPixelStride: Int,
    ) {
        fun put(buffer: ByteBuffer, index: Int, value: Int) {
            // The last chroma sample of an interleaved plane can sit one byte past the buffer's end.
            if (index < buffer.limit()) buffer.put(index, value.coerceIn(0, 255).toByte())
        }
        for (row in 0 until height) {
            for (col in 0 until width) {
                val p = argb[row * width + col]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                put(y, row * yRowStride + col * yPixelStride, 16 + ((47 * r + 157 * g + 16 * b + 128) shr 8))
            }
        }
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val p = argb[(row * 2 + dy) * width + col * 2 + dx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                r /= 4
                g /= 4
                b /= 4
                val index = row * uvRowStride + col * uvPixelStride
                put(u, index, 128 + ((-26 * r - 87 * g + 112 * b + 128) shr 8))
                put(v, index, 128 + ((112 * r - 102 * g - 10 * b + 128) shr 8))
            }
        }
    }
}
