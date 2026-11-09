package com.atsukio.runningcoach.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FeedbackStatus
import com.atsukio.runningcoach.analysis.Scale
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.ui.goodRangeText
import java.io.File
import kotlin.math.roundToInt

/**
 * A written report of one clip as an A4 PDF: the measurements against their good ranges, up to three
 * landings from the video with their measurements drawn on, and what to work on. Made on the phone with
 * Android's own PDF support, on a white page so it prints.
 */
internal object ReportPdf {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val BAR_W = 150f

    private val Ink = "#1B2A24".toColorInt()
    private val Muted = "#5E6E67".toColorInt()
    private val Brand = "#1E8E66".toColorInt()
    private val TrackColour = "#DDE5E1".toColorInt()

    private fun statusColour(status: FeedbackStatus) = when (status) {
        FeedbackStatus.GOOD -> "#2E9E50".toColorInt()
        FeedbackStatus.IMPROVE -> "#E08A00".toColorInt()
        FeedbackStatus.INFO -> "#3D7FD0".toColorInt()
    }

    private fun text(size: Float, colour: Int = Ink, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = colour
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** [stills] are landings from the clip, already sized for the page (see [stillFor]). */
    fun write(out: File, session: Session, feedback: List<Feedback>, date: String, stills: List<LandingStill>) {
        val doc = PdfDocument()
        try {
            ReportWriter(doc).write(session, feedback, date, stills)
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** A landing frame with its measurements drawn on, scaled to [width] pixels wide. */
    fun stillFor(still: LandingStill, width: Int): Bitmap {
        val scale = width.toFloat() / still.frame.width
        val copy = still.frame.scale(width, (still.frame.height * scale).roundToInt().coerceAtLeast(1))
            .copy(Bitmap.Config.ARGB_8888, true)
        still.annotation?.let { OverlayPainter.drawLanding(Canvas(copy), it, copy.width.toFloat(), copy.height.toFloat(), scale = 2f) }
        return copy
    }

    private class ReportWriter(private val doc: PdfDocument) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private val contentW = PAGE_W - 2 * MARGIN

        private fun startPage() {
            page?.let { doc.finishPage(it) }
            pageNumber++
            val started = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            page = started
            canvas = started.canvas
            y = MARGIN
        }

        private fun ensure(height: Float) {
            if (page == null || y + height > PAGE_H - MARGIN) startPage()
        }

        private fun layout(value: String, paint: TextPaint, width: Float = contentW) =
            StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        private fun paragraph(value: String, paint: TextPaint, gap: Float = 4f) {
            val l = layout(value, paint)
            ensure(l.height.toFloat())
            canvas.withTranslation(MARGIN, y) { l.draw(this) }
            y += l.height + gap
        }

        fun write(session: Session, feedback: List<Feedback>, date: String, stills: List<LandingStill>) {
            startPage()
            val brand = text(10f, Brand, bold = true).apply { letterSpacing = 0.2f }
            canvas.drawText("STRIDE LAB", MARGIN, y + 10f, brand)
            y += 20f
            paragraph("Running form report", text(24f, bold = true), gap = 2f)
            val details = listOfNotNull(
                date,
                session.speedKmh?.let { "%.1f km/h".format(it) },
                session.metrics?.let { "${it.stepCount} steps analysed" },
            ).joinToString(" · ")
            paragraph(details, text(11f, Muted), gap = 14f)

            val focus = Coach.mainFocus(feedback)
            val headline = if (focus != null) {
                "Main focus: ${focus.topic.lowercase()}" + focus.cue?.let { ". Think: \"$it\"" }.orEmpty()
            } else {
                "Nothing major to fix."
            }
            paragraph(headline, text(13f, bold = true), gap = 14f)

            if (stills.isNotEmpty()) landings(stills)

            paragraph("Measurements", text(14f, bold = true), gap = 8f)
            feedback.forEach(::measurement)

            if (session.warnings.isNotEmpty()) {
                y += 6f
                paragraph("About this recording", text(12f, bold = true))
                session.warnings.forEach { paragraph("• $it", text(10f, Muted), gap = 2f) }
            }
            y += 14f
            paragraph(
                "Coaching suggestions based on video, not medical advice. If something hurts, see a physiotherapist or doctor. " +
                    "Analysed on the phone with Stride Lab; nothing was uploaded.",
                text(9f, Muted),
            )
            page?.let { doc.finishPage(it) }
            page = null
        }

        private fun landings(stills: List<LandingStill>) {
            val gap = 10f
            val width = (contentW - gap * (stills.size - 1)) / stills.size
            val images = stills.map { ReportPdf.stillFor(it, (width * 3).toInt()) }
            val height = images.maxOf { it.height * width / it.width }
            ensure(height + 30f)
            paragraph("Landings from your clip", text(14f, bold = true), gap = 8f)
            val captionPaint = text(9f, Muted)
            stills.forEachIndexed { i, still ->
                val left = MARGIN + i * (width + gap)
                val image = images[i]
                val target = RectF(left, y, left + width, y + image.height * width / image.width)
                canvas.drawBitmap(image, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.drawText("Landing ${still.number} of ${still.total}", left, target.bottom + 11f, captionPaint)
            }
            y += height + 24f
        }

        private fun measurement(item: Feedback) {
            val body = layout(item.message, text(10f))
            val extras = listOfNotNull(
                item.cue?.let { "Think: \"$it\"" },
                item.drill?.let { "Try: $it" },
            ).map { layout(it, text(10f, Muted)) }
            val height = 26f + body.height + extras.sumOf { it.height + 2 } + 14f
            ensure(height)

            canvas.drawText(item.topic, MARGIN, y + 12f, text(12f, bold = true))
            canvas.drawText(item.value, MARGIN + 150f, y + 12f, text(12f))
            item.scale?.let { bar(it, MARGIN + contentW - BAR_W, y + 4f, statusColour(item.status)) }
            val chip = ShareSummary.status(item.status)
            canvas.drawText(chip, MARGIN + 150f, y + 24f, text(9f, statusColour(item.status), bold = true))
            item.scale?.let {
                canvas.drawText("Good: ${goodRangeText(it)}", MARGIN + contentW - BAR_W, y + 24f, text(8f, Muted))
            }
            y += 30f
            canvas.withTranslation(MARGIN, y) { body.draw(this) }
            y += body.height + 2f
            extras.forEach { l ->
                canvas.withTranslation(MARGIN, y) { l.draw(this) }
                y += l.height + 2f
            }
            y += 12f
        }

        /** The measurement as a marker on a bar, with the good range shaded. */
        private fun bar(scale: Scale, left: Float, top: Float, colour: Int) {
            val span = (scale.max - scale.min).takeIf { it > 0 } ?: 1.0
            fun x(v: Double) = left + (((v - scale.min) / span).coerceIn(0.0, 1.0) * BAR_W).toFloat()
            canvas.drawRoundRect(RectF(left, top, left + BAR_W, top + 6f), 3f, 3f, Paint().apply { color = TrackColour })
            canvas.drawRoundRect(
                RectF(x(scale.goodLow), top, x(scale.goodHigh), top + 6f), 3f, 3f,
                Paint().apply { color = "#9ADAB0".toColorInt() },
            )
            canvas.drawCircle(x(scale.value), top + 3f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
        }
    }
}
