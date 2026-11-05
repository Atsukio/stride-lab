package com.atsukio.runningcoach.share

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FeedbackStatus
import com.atsukio.runningcoach.analysis.LandingAnnotation
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.ui.drawLanding
import com.atsukio.runningcoach.ui.formatSpeed
import com.atsukio.runningcoach.ui.goodRangeText
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A landing frame from the clip, with its measurements if the leg could be made out. */
internal class LandingStill(val frame: Bitmap, val annotation: LandingAnnotation?, val number: Int, val total: Int)

/**
 * Draws a result as a portrait image for sharing: the main focus, an optional landing still with its
 * measurements, and every measurement against its good range. Always dark, whatever the app theme,
 * so it looks the same wherever it's sent.
 */
internal object ShareCard {
    private val Background = Color(0xFF0E1A16)
    private val Panel = Color(0xFF172A23)
    private val Track = Color(0xFF2C4038)
    private val Ink = Color(0xFFF2F5F3)
    private val Muted = Color(0xFFA9B8B1)
    private val Mint = Color(0xFF8BE9C1)
    private val Good = Color(0xFF66BB6A)
    private val Improve = Color(0xFFFFA726)

    private const val WIDTH_DP = 360f
    private const val PX_PER_DP = 3f
    private const val MEASURE_HEIGHT = 100_000f

    fun render(context: Context, session: Session, feedback: List<Feedback>, date: String, landing: LandingStill?): Bitmap {
        val density = Density(PX_PER_DP, fontScale = 1f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val width = (WIDTH_DP * PX_PER_DP).roundToInt()
        fun paint(target: ImageBitmap, height: Float): Float {
            var bottom = 0f
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(target), Size(width.toFloat(), height)) {
                bottom = content(measurer, session, feedback, date, landing)
            }
            return bottom
        }
        // The first pass only measures, on a tall page backed by a 1-pixel canvas: drawing off its edge costs next to nothing.
        val height = ceil(paint(ImageBitmap(width, 1), MEASURE_HEIGHT)).toInt()
        val image = ImageBitmap(width, height)
        paint(image, height.toFloat())
        return image.asAndroidBitmap()
    }

    /** Draws the card from the top and returns where it ends. */
    private fun DrawScope.content(
        measurer: TextMeasurer,
        session: Session,
        feedback: List<Feedback>,
        date: String,
        landing: LandingStill?,
    ): Float {
        drawRect(Background)
        val pad = 20.dp.toPx()
        val inner = size.width - 2 * pad
        var y = pad
        fun measure(text: String, style: TextStyle, maxWidth: Float = inner): TextLayoutResult =
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth.roundToInt().coerceAtLeast(1)))

        // Header: the app's name and the date.
        val brand = measure("STRIDE LAB", TextStyle(color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
        val day = measure(date, TextStyle(color = Muted, fontSize = 12.sp))
        drawText(brand, topLeft = Offset(pad, y))
        drawText(day, topLeft = Offset(pad + inner - day.size.width, y))
        y += maxOf(brand.size.height, day.size.height) + 14.dp.toPx()

        // The headline: the one thing to work on.
        val focus = Coach.mainFocus(feedback)
        val title = measure(
            focus?.let { "Main focus: ${it.topic.lowercase()}" } ?: "Nice running: nothing major to fix",
            TextStyle(color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
        )
        drawText(title, topLeft = Offset(pad, y))
        y += title.size.height + 4.dp.toPx()
        focus?.cue?.let { cue ->
            val line = measure("Think: “$cue”", TextStyle(color = Mint, fontSize = 16.sp))
            drawText(line, topLeft = Offset(pad, y))
            y += line.size.height + 6.dp.toPx()
        }
        val details = listOfNotNull(
            session.metrics?.let { "${it.stepCount} steps analysed" },
            session.speedKmh?.let { "${formatSpeed(it)} km/h" },
            "${session.fps.roundToInt()} fps",
        ).joinToString(" · ")
        val detailLine = measure(details, TextStyle(color = Muted, fontSize = 12.sp))
        drawText(detailLine, topLeft = Offset(pad, y))
        y += detailLine.size.height + 16.dp.toPx()

        // A landing from the clip, with its measurements drawn on.
        landing?.let { still ->
            val aspect = still.frame.width.toFloat() / still.frame.height.coerceAtLeast(1)
            val h = minOf(inner / aspect, 320.dp.toPx())
            val w = h * aspect
            val x0 = pad + (inner - w) / 2
            val panel = RoundRect(pad, y, pad + inner, y + h, CornerRadius(12.dp.toPx()))
            clipPath(Path().apply { addRoundRect(panel) }) {
                drawRect(Color.Black, Offset(pad, y), Size(inner, h))
                drawImage(
                    still.frame.asImageBitmap(),
                    dstOffset = IntOffset(x0.roundToInt(), y.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
                still.annotation?.let { drawLanding(it, measurer, Offset(x0, y), Size(w, h)) }
            }
            y += h + 6.dp.toPx()
            val notes = listOfNotNull(
                "Landing ${still.number} of ${still.total}",
                still.annotation?.overstrideCm?.let { if (it > 0) "foot ${it.roundToInt()} cm ahead of hips" else "foot under hips" },
                still.annotation?.let { "shin ${abs(it.shinDeg).roundToInt()}°" },
                still.annotation?.let { "knee bent ${it.kneeFlexionDeg.roundToInt()}°" },
            ).joinToString(" · ")
            val caption = measure(notes, TextStyle(color = Muted, fontSize = 12.sp))
            drawText(caption, topLeft = Offset(pad, y))
            y += caption.size.height + 16.dp.toPx()
        }

        // Every measurement, with where it sits against the good range.
        val rowPad = 12.dp.toPx()
        feedback.forEach { item ->
            val colour = when (item.status) {
                FeedbackStatus.GOOD -> Good
                FeedbackStatus.IMPROVE -> Improve
                FeedbackStatus.INFO -> Mint
            }
            val value = measure(item.value, TextStyle(color = Ink, fontSize = 14.sp), inner / 2)
            val dotSpace = 18.dp.toPx()
            val topic = measure(
                item.topic,
                TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                inner - 2 * rowPad - dotSpace - value.size.width - 8.dp.toPx(),
            )
            val status = measure(ShareSummary.status(item.status), TextStyle(color = colour, fontSize = 11.sp))
            val range = item.scale?.let { measure(goodRangeText(it), TextStyle(color = Muted, fontSize = 11.sp)) }
            val barHeight = 14.dp.toPx()
            val headHeight = maxOf(topic.size.height, value.size.height).toFloat()
            val rowHeight = rowPad + headHeight + status.size.height +
                (range?.let { 6.dp.toPx() + barHeight + 2.dp.toPx() + it.size.height } ?: 0f) + rowPad
            drawRoundRect(Panel, Offset(pad, y), Size(inner, rowHeight), CornerRadius(10.dp.toPx()))

            var ry = y + rowPad
            drawCircle(colour, 5.dp.toPx(), Offset(pad + rowPad + 5.dp.toPx(), ry + headHeight / 2))
            drawText(topic, topLeft = Offset(pad + rowPad + dotSpace, ry))
            drawText(value, topLeft = Offset(pad + inner - rowPad - value.size.width, ry))
            ry += headHeight
            drawText(status, topLeft = Offset(pad + rowPad + dotSpace, ry))
            ry += status.size.height
            item.scale?.let { scale ->
                ry += 6.dp.toPx()
                val left = pad + rowPad
                val barWidth = inner - 2 * rowPad
                val span = scale.max - scale.min
                fun x(v: Double) = left + (((v - scale.min) / span).coerceIn(0.0, 1.0) * barWidth).toFloat()
                val thickness = 6.dp.toPx()
                val top = ry + (barHeight - thickness) / 2
                val radius = CornerRadius(thickness / 2)
                drawRoundRect(Track, Offset(left, top), Size(barWidth, thickness), radius)
                drawRoundRect(Good.copy(alpha = 0.55f), Offset(x(scale.goodLow), top), Size(x(scale.goodHigh) - x(scale.goodLow), thickness), radius)
                val centre = Offset(x(scale.value), ry + barHeight / 2)
                drawCircle(Panel, 7.dp.toPx(), centre)
                drawCircle(colour, 5.dp.toPx(), centre)
                ry += barHeight + 2.dp.toPx()
                range?.let { drawText(it, topLeft = Offset(left, ry)) }
            }
            y += rowHeight + 8.dp.toPx()
        }

        val footer = measure(
            "Analysed on my phone with Stride Lab. Coaching suggestions from video, not medical advice.",
            TextStyle(color = Muted, fontSize = 11.sp),
        )
        y += 4.dp.toPx()
        drawText(footer, topLeft = Offset(pad, y))
        return y + footer.size.height + pad
    }
}
