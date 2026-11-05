package com.atsukio.runningcoach.share

import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FeedbackStatus
import com.atsukio.runningcoach.data.Session
import kotlin.math.roundToInt

/** The plain-text version of a result, sent alongside the image for apps that show a caption. */
object ShareSummary {

    fun text(session: Session, feedback: List<Feedback>, date: String): String = buildString {
        appendLine("My running form: $date" + session.speedKmh?.let { ", ${speed(it)} km/h" }.orEmpty())
        val focus = Coach.mainFocus(feedback)
        if (focus != null) {
            append("Main focus: ${focus.topic.lowercase()}")
            focus.cue?.let { append(". Think: \"$it\"") }
            appendLine()
        } else {
            appendLine("Nothing major to fix.")
        }
        feedback.forEach { appendLine("• ${it.topic}: ${it.value} (${status(it.status)})") }
        session.metrics?.let { appendLine("${it.stepCount} steps analysed.") }
        append("Analysed on my phone with Stride Lab. Coaching suggestions, not medical advice.")
    }

    fun status(status: FeedbackStatus) = when (status) {
        FeedbackStatus.GOOD -> "good"
        FeedbackStatus.IMPROVE -> "to work on"
        FeedbackStatus.INFO -> "note"
    }

    private fun speed(kmh: Double): String {
        val tenths = (kmh * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
