package com.atsukio.runningcoach.pose

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One YUV 4:2:0 frame copied out of a decoder's output image. Android guarantees the U and V planes
 * share a row stride and pixel stride. [left] and [top] are the crop offset into the planes.
 */
internal class YuvFrame(
    val width: Int,
    val height: Int,
    val y: ByteArray,
    val yRowStride: Int,
    val yPixelStride: Int,
    val u: ByteArray,
    val v: ByteArray,
    val uvRowStride: Int,
    val uvPixelStride: Int,
    val left: Int = 0,
    val top: Int = 0,
)

internal object Yuv {
    data class Size(val width: Int, val height: Int)

    /** Size of the frame once turned upright, shrunk so its long side is at most [maxSide]. */
    fun uprightSize(width: Int, height: Int, rotation: Int, maxSide: Int): Size {
        val sideways = rotation == 90 || rotation == 270
        val w = if (sideways) height else width
        val h = if (sideways) width else height
        val scale = min(1f, maxSide.toFloat() / max(w, h))
        return Size(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
    }

    /**
     * Converts [frame] to ARGB pixels of size [out], rotating it clockwise by [rotation] degrees and
     * scaling in the same pass. Uses BT.601 limited-range colour, which is plenty for pose detection.
     */
    fun toArgb(frame: YuvFrame, rotation: Int, out: Size, pixels: IntArray) {
        val sideways = rotation == 90 || rotation == 270
        val uprightW = if (sideways) frame.height else frame.width
        val uprightH = if (sideways) frame.width else frame.height
        val lastX = frame.width - 1
        val lastY = frame.height - 1
        var i = 0
        for (oy in 0 until out.height) {
            val v = min(uprightH - 1, ((oy + 0.5f) * uprightH / out.height).toInt())
            for (ox in 0 until out.width) {
                val u = min(uprightW - 1, ((ox + 0.5f) * uprightW / out.width).toInt())
                val sx: Int
                val sy: Int
                when (rotation) {
                    90 -> { sx = v; sy = lastY - u }
                    180 -> { sx = lastX - u; sy = lastY - v }
                    270 -> { sx = lastX - v; sy = u }
                    else -> { sx = u; sy = v }
                }
                val px = frame.left + sx
                val py = frame.top + sy
                val luma = frame.y[py * frame.yRowStride + px * frame.yPixelStride].toInt() and 0xff
                val c = (py shr 1) * frame.uvRowStride + (px shr 1) * frame.uvPixelStride
                val cb = (frame.u[c].toInt() and 0xff) - 128
                val cr = (frame.v[c].toInt() and 0xff) - 128
                val l = 298 * (luma - 16) + 128
                val r = clamp((l + 409 * cr) shr 8)
                val g = clamp((l - 100 * cb - 208 * cr) shr 8)
                val b = clamp((l + 516 * cb) shr 8)
                pixels[i++] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun clamp(x: Int) = if (x < 0) 0 else if (x > 255) 255 else x
}
