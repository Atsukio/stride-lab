package com.atsukio.runningcoach.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.toColorInt
import com.atsukio.runningcoach.analysis.FootStrikeType
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.LandingAnnotation
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the skeleton and landing measurements onto a bitmap canvas, for the shared video and the report.
 * The on-screen version is Compose ([com.atsukio.runningcoach.ui.drawLanding]); this one draws with
 * Android's canvas because video frames and PDF pages aren't Compose surfaces. Keep the colours in step.
 */
internal object OverlayPainter {
    private val Left = "#4FC3F7".toColorInt()
    private val Right = "#FFB74D".toColorInt()
    private val Landing = "#FFEB3B".toColorInt()
    private val Shin = "#FFB74D".toColorInt()
    private val Knee = "#4FC3F7".toColorInt()
    private const val SURE_VISIBILITY = 0.5f

    /** Line widths and text sizes are for a 360-unit-wide frame; they scale with the frame's short side. */
    private fun unit(width: Float, height: Float, scale: Float) = min(width, height) / 360f * scale

    private fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }

    /** The pose as coloured limbs, with a ring on the landing foot when [landing] is true. */
    fun drawPose(canvas: Canvas, pose: FramePose, width: Float, height: Float, landing: Boolean, scale: Float = 1f) {
        val u = unit(width, height, scale)
        val w = 3f * u
        fun x(i: Int) = pose.x[i] * width
        fun y(i: Int) = pose.y[i] * height
        fun limb(colour: Int, a: Int, b: Int) {
            if (pose.x[a].isNaN() || pose.x[b].isNaN()) return
            // Points the detector wasn't sure of (often the far leg) are drawn faintly, as on screen.
            val sure = pose.visibility[a] >= SURE_VISIBILITY && pose.visibility[b] >= SURE_VISIBILITY
            val paint = stroke(colour, w).apply { alpha = if (sure) 255 else 90 }
            canvas.drawLine(x(a), y(a), x(b), y(b), paint)
        }
        Landmark.LEFT_CONNECTIONS.forEach { (a, b) -> limb(Left, a, b) }
        Landmark.RIGHT_CONNECTIONS.forEach { (a, b) -> limb(Right, a, b) }
        Landmark.CENTER_CONNECTIONS.forEach { (a, b) -> limb(Color.WHITE, a, b) }
        if (!pose.x[Landmark.NOSE].isNaN()) {
            canvas.drawCircle(x(Landmark.NOSE), y(Landmark.NOSE), 5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        if (landing) {
            val heel = if (pose.y[Landmark.LEFT_HEEL] >= pose.y[Landmark.RIGHT_HEEL]) Landmark.LEFT_HEEL else Landmark.RIGHT_HEEL
            val ankle = if (pose.y[Landmark.LEFT_HEEL] >= pose.y[Landmark.RIGHT_HEEL]) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE
            val target = if (!pose.x[heel].isNaN()) heel else ankle
            if (!pose.x[target].isNaN()) {
                val cx = x(target)
                val cy = y(target)
                canvas.drawCircle(cx, cy, 8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Landing; alpha = 90 })
                canvas.drawCircle(cx, cy, 8f * u, stroke(Landing, w))
                canvas.drawCircle(cx, cy, 18f * u, stroke(Landing, 2f * u).apply { alpha = 180 })
            }
        }
    }

    /** The landing measurements: trunk lean, shin angle, knee bend and how far ahead of the hips the foot lands. */
    fun drawLanding(canvas: Canvas, a: LandingAnnotation, width: Float, height: Float, scale: Float = 1f) {
        val u = unit(width, height, scale)
        val thin = 2f * u
        val thick = 4f * u
        val dashes = DashPathEffect(floatArrayOf(10f * u, 8f * u), 0f)
        fun px(p: com.atsukio.runningcoach.analysis.Point) = floatArrayOf(p.x * width, p.y * height)
        val (sx, sy) = px(a.shoulder).let { it[0] to it[1] }
        val (hx, hy) = px(a.hip).let { it[0] to it[1] }
        val (kx, ky) = px(a.knee).let { it[0] to it[1] }
        val (ax, ay) = px(a.ankle).let { it[0] to it[1] }

        canvas.drawLine(sx, sy, hx, hy, stroke(Color.WHITE, thin))
        canvas.drawLine(hx, hy, kx, ky, stroke(Color.WHITE, thin))
        canvas.drawLine(kx, ky, ax, ay, stroke(Shin, thick))
        canvas.drawLine(hx, hy, hx, sy, stroke(Color.WHITE, thin).apply { alpha = 150; pathEffect = dashes })

        a.overstrideCm?.let { cm ->
            canvas.drawLine(hx, hy, hx, ay, stroke(Landing, thin).apply { pathEffect = dashes })
            canvas.drawLine(hx, ay, ax, ay, stroke(Landing, thick))
            val text = if (cm > 0) "${cm.roundToInt()} cm" else "under hips"
            label(canvas, text, (hx + ax) / 2 - 20f * u, ay + 8f * u, Color.WHITE, u)
        }

        canvas.drawLine(ax, ay, ax, ky, stroke(Shin, thin).apply { alpha = 180; pathEffect = dashes })
        label(canvas, "${abs(a.shinDeg).roundToInt()}°", kx + 10f * u, (ky + ay) / 2, Color.WHITE, u)
        canvas.drawCircle(kx, ky, 7f * u, stroke(Knee, thin))
        label(canvas, "${a.kneeFlexionDeg.roundToInt()}°", kx + 12f * u, ky - 22f * u, Knee, u)

        if (a.heel != null && a.toe != null) {
            val (hx, hy) = px(a.heel).let { it[0] to it[1] }
            val (tx, ty) = px(a.toe).let { it[0] to it[1] }
            canvas.drawLine(ax, ay, hx, hy, stroke(Color.WHITE, thin).apply { alpha = 200 })
            canvas.drawLine(hx, hy, tx, ty, stroke(Color.WHITE, thick).apply { alpha = 200 })
            val (cx, cy) = when (a.strike) {
                FootStrikeType.HEEL -> hx to hy
                FootStrikeType.FOREFOOT -> tx to ty
                FootStrikeType.MIDFOOT -> (hx + tx) / 2f to (hy + ty) / 2f
                null -> when {
                    a.heel.y - a.toe.y > 0.015f -> hx to hy
                    a.toe.y - a.heel.y > 0.015f -> tx to ty
                    else -> (hx + tx) / 2f to (hy + ty) / 2f
                }
            }
            canvas.drawCircle(cx, cy, 5f * u, stroke(Shin, thin))
            canvas.drawCircle(cx, cy, 2.5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
    }

    /** Small text on a dark plate, so it reads over any frame. Pass [x], [y] as the plate's top-left. */
    fun label(canvas: Canvas, text: String, x: Float, y: Float, colour: Int, u: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            textSize = 13f * u
            typeface = Typeface.DEFAULT_BOLD
        }
        val pad = 3f * u
        val bounds = paint.fontMetrics
        val plate = RectF(x - pad, y - pad, x + paint.measureText(text) + pad, y + (bounds.descent - bounds.ascent) + pad)
        canvas.drawRoundRect(plate, 3f * u, 3f * u, Paint().apply { color = Color.argb(166, 0, 0, 0) })
        canvas.drawText(text, x, y - bounds.ascent, paint)
    }
}
