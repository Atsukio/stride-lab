package com.atsukio.runningcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private class ProgressMetric(
    val title: String,
    val unit: String,
    /** Coaching topics whose scale gives this metric's good range. */
    val topics: Set<String>,
    val value: (GaitMetrics) -> Double?,
)

private val PROGRESS_METRICS = listOf(
    ProgressMetric("Cadence", "steps/min", setOf("Cadence")) { it.cadenceSpm },
    ProgressMetric("Overstride", "cm", setOf("Foot landing", "Overstriding")) { it.overstrideCm },
    ProgressMetric("Shin at landing", "°", setOf("Shin at landing")) { it.shinAngleDeg },
    ProgressMetric("Bounce", "cm", setOf("Bounce")) { it.verticalOscillationCm },
    ProgressMetric("Lean", "°", setOf("Posture")) { it.trunkLeanDeg },
    ProgressMetric("Elbow angle", "°", setOf("Arms")) { it.elbowAngleDeg },
    ProgressMetric("Heel strike", "%", setOf("Foot strike")) { it.heelStrikeRatio?.let { r -> r * 100 } },
    // From front and rear clips only, so these chart on their own.
    ProgressMetric("Hip drop", "°", setOf("Hip drop")) { it.frontal?.pelvicDropDeg },
    ProgressMetric("Knee drift inwards", "°", setOf("Knee alignment")) { it.frontal?.kneeInwardDeg },
    ProgressMetric("Step width", "cm", setOf("Step width")) { it.frontal?.stepWidthCm },
    ProgressMetric("Trunk sway", "°", setOf("Trunk sway")) { it.frontal?.trunkSwayDeg },
)

/** One analysed clip on a trend: when, at what speed, and its value. */
private class ProgressPoint(val session: Session, val value: Double)

/** How each form measurement has changed across every analysed clip, oldest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf<List<Session>?>(null) }
    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            SessionRepository(context).list().filter { it.metrics != null }.sortedBy { it.createdAt }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val list = sessions
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Each dot is a clip, oldest on the left. The green band is the good range. Form changes with " +
                        "pace, so compare clips at similar speeds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                val strikeClips = list.filter { it.metrics?.heelStrikeRatio != null }
                if (strikeClips.size >= 2) {
                    FootStrikeTrendCard(strikeClips)
                }
            }
            items(PROGRESS_METRICS) { metric ->
                val points = list.mapNotNull { s -> metric.value(s.metrics!!)?.let { ProgressPoint(s, it) } }
                if (points.size >= 2) ProgressCard(metric, points)
            }
        }
    }
}

@Composable
private fun ProgressCard(metric: ProgressMetric, points: List<ProgressPoint>) {
    val latest = points.last()
    val band = Coach.review(latest.session.metrics!!, latest.session.speedKmh)
        .firstOrNull { it.topic in metric.topics }?.scale
    val first = points.first()
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    fun n(v: Double) = v.roundToInt()
    val summary = "${n(first.value)} → ${n(latest.value)} ${metric.unit} over ${points.size} clips"
    val lineColor = MaterialTheme.colorScheme.primary
    val goodColor = Color(0xFF4CAF50)
    val grid = MaterialTheme.colorScheme.outlineVariant
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(metric.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(summary, style = MaterialTheme.typography.labelLarge)
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics {
                        contentDescription = "${metric.title}: $summary, from ${dates.format(Date(first.session.createdAt))}"
                    },
            ) {
                val values = points.map { it.value }
                val lo = minOf(values.min(), band?.goodLow?.takeIf { it > band.min } ?: values.min())
                val hi = maxOf(values.max(), band?.goodHigh?.takeIf { it < band.max } ?: values.max())
                val pad = maxOf((hi - lo) * 0.15, 1.0)
                fun y(v: Double) = (size.height - (v - lo + pad) / (hi - lo + 2 * pad) * size.height).toFloat()
                fun x(i: Int) = if (points.size == 1) size.width / 2 else i * size.width / (points.size - 1)
                band?.let {
                    val top = y(minOf(it.goodHigh, hi + pad)).coerceIn(0f, size.height)
                    val bottom = y(maxOf(it.goodLow, lo - pad)).coerceIn(0f, size.height)
                    drawRect(goodColor.copy(alpha = 0.15f), Offset(0f, top), Size(size.width, bottom - top))
                }
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                points.indices.toList().zipWithNext { a, b ->
                    drawLine(lineColor, Offset(x(a), y(points[a].value)), Offset(x(b), y(points[b].value)), 3.dp.toPx())
                }
                points.forEachIndexed { i, p -> drawCircle(lineColor, 5.dp.toPx(), Offset(x(i), y(p.value))) }
            }
            Row {
                Text(dates.format(Date(first.session.createdAt)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(dates.format(Date(latest.session.createdAt)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FootStrikeTrendCard(clips: List<Session>) {
    val first = clips.first()
    val latest = clips.last()
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val latestSummary = latest.metrics?.footStrikeSummary ?: return

    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foot strike evolution", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(latestSummary, style = MaterialTheme.typography.labelMedium)
            }
            val grid = MaterialTheme.colorScheme.outlineVariant
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .semantics {
                        contentDescription = "Foot strike trend over ${clips.size} clips: latest $latestSummary"
                    },
            ) {
                val n = clips.size
                val barWidth = (size.width / (n * 2f)).coerceIn(8.dp.toPx(), 28.dp.toPx())
                val spacing = size.width / n
                val radius = CornerRadius(4.dp.toPx())

                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())

                clips.forEachIndexed { i, s ->
                    val m = s.metrics ?: return@forEachIndexed
                    val heel = (m.heelStrikeRatio ?: 0.0).toFloat()
                    val midfoot = (m.midfootStrikeRatio ?: 0.0).toFloat()
                    val forefoot = (m.forefootStrikeRatio ?: 0.0).toFloat()
                    val x = i * spacing + (spacing - barWidth) / 2f
                    var currentY = size.height

                    // Background track for the bar
                    drawRoundRect(grid.copy(alpha = 0.25f), Offset(x, 0f), Size(barWidth, size.height), radius)

                    if (heel > 0) {
                        val h = heel * size.height
                        currentY -= h
                        drawRect(Signal.Info, Offset(x, currentY), Size(barWidth, h))
                    }
                    if (midfoot > 0) {
                        val h = midfoot * size.height
                        currentY -= h
                        drawRect(Signal.Good, Offset(x, currentY), Size(barWidth, h))
                    }
                    if (forefoot > 0) {
                        val h = forefoot * size.height
                        currentY -= h
                        drawRect(Signal.Improve, Offset(x, currentY), Size(barWidth, h))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StrikeLegendDot(Signal.Info, "Heel")
                StrikeLegendDot(Signal.Good, "Midfoot")
                StrikeLegendDot(Signal.Improve, "Forefoot")
            }
            Row {
                Text(dates.format(Date(first.createdAt)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(dates.format(Date(latest.createdAt)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StrikeLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
