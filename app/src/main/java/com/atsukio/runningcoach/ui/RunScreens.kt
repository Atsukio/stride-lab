package com.atsukio.runningcoach.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atsukio.runningcoach.MainActivity
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.RunClip
import com.atsukio.runningcoach.analysis.RunInsights
import com.atsukio.runningcoach.audio.SpokenCues
import com.atsukio.runningcoach.audio.CueSchedule
import com.atsukio.runningcoach.data.RunRecorder
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.WholeRun
import com.atsukio.runningcoach.sensors.SensorSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val SUGGESTED_CLIP_INTERVAL_SEC = 10 * 60

private val HeartRateColor = Color(0xFFE57373)

fun formatDuration(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A whole run in progress: live watch readings, the clips taken so far, and when to take the next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    onTakeClip: (autoStart: Boolean) -> Unit,
    onOpenSession: (String) -> Unit,
    onFinished: (runId: String) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { RunRecorder.get(context) }
    val repo = remember { SessionRepository(context) }
    val hub = rememberSensorHub()
    val stats = rememberFreshStats(hub)
    val run by recorder.active.collectAsStateWithLifecycle()
    val current = run
    if (current == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedSec = (now - current.startedAt) / 1000.0

    var clips by remember { mutableStateOf<List<Session>>(emptyList()) }
    LaunchedEffect(current.id) {
        clips = withContext(Dispatchers.IO) { clipsOf(repo, current.id) }
    }

    // Spoken cues: the phone's own offline voice says the focus cue after a minute, then every five minutes.
    val settings = remember { CoachSettings(context) }
    var spoken by remember { mutableStateOf(settings.spokenCues) }
    val voice = remember { SpokenCues(context) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    var lastCue by remember { mutableIntStateOf(CueSchedule.dueIndex(elapsedSec)) }
    val due = CueSchedule.dueIndex(elapsedSec)
    LaunchedEffect(due, spoken, voice.offlineVoiceReady) {
        if (!spoken || !voice.offlineVoiceReady || due <= lastCue) return@LaunchedEffect
        lastCue = due
        val latest = clips.lastOrNull { it.metrics != null }
        val feedback = latest?.metrics?.let { Coach.review(it, latest.speedKmh) }.orEmpty()
        CueSchedule.cue(settings.focusPlan, feedback)?.let { voice.speak(it) }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A Bluetooth remote press opens the camera and starts the countdown in one go.
    val activity = LocalActivity.current as? MainActivity
    val takeClip by rememberUpdatedState(onTakeClip)
    DisposableEffect(activity) {
        activity?.onShutterKey = { takeClip(true) }
        onDispose { activity?.onShutterKey = null }
    }

    var confirmFinish by remember { mutableStateOf(false) }
    BackHandler { confirmFinish = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whole run") },
                navigationIcon = {
                    IconButton(onClick = { confirmFinish = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Finish run")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { RunTimerHero(elapsedSec) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Heart rate", stats?.heartRate?.let { "$it" } ?: "–", "bpm", Modifier.weight(1f), HeartRateColor)
                    StatTile("Cadence", stats?.cadenceSpm?.takeIf { it > 0 }?.let { "$it" } ?: "–", "spm", Modifier.weight(1f))
                    StatTile(
                        "Pace",
                        stats?.speedMps?.takeIf { it > 0.3 }?.let { formatPace(it).removeSuffix(" /km") } ?: "–",
                        "/km",
                        Modifier.weight(1f),
                    )
                }
            }
            item { LiveStatsChip() }
            item {
                LabCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetronomeControl()
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Spoken cues", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (voice.offlineVoiceReady) {
                                        "Says your focus cue after a minute, then every 5 minutes, in the phone's own voice."
                                    } else {
                                        "This phone has no offline voice installed, so cues stay silent. Nothing is ever sent online."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = spoken && voice.offlineVoiceReady,
                                onCheckedChange = {
                                    spoken = it
                                    settings.spokenCues = it
                                    // Start from the current cue number so turning it on doesn't speak at once.
                                    lastCue = due
                                },
                                enabled = voice.offlineVoiceReady,
                            )
                        }
                    }
                }
            }
            item {
                val lastOffset = clips.mapNotNull { it.runOffsetSec }.maxOrNull()
                NextClipCard(elapsedSec, lastOffset)
            }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onTakeClip(false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Take a clip") }
                    OutlinedButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth()) { Text("Finish run") }
                }
            }
            if (clips.isNotEmpty()) {
                item {
                    Text("Clips", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                }
            }
            items(clips, key = { it.id }) { ClipRow(it, onClick = { onOpenSession(it.id) }) }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this run?") },
            text = { Text("You've taken ${clips.size} ${if (clips.size == 1) "clip" else "clips"}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    recorder.finish()?.let { onFinished(it.id) }
                }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }
}

private class RunData(val run: WholeRun, val clips: List<Session>)

private enum class TrendMetric(val label: String, val unit: String, val value: (GaitMetrics) -> Double?) {
    OVERSTRIDE("Overstride", "cm", { it.overstrideCm }),
    CADENCE("Cadence", "steps/min", { it.cadenceSpm }),
    BOUNCE("Bounce", "cm", { it.verticalOscillationCm }),
    LEAN("Lean", "°", { it.trunkLeanDeg }),
    HEEL_STRIKE("Heel strike", "%", { it.heelStrikeRatio?.let { r -> r * 100 } }),
}

/** How form changed over a finished run, alongside heart rate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSummaryScreen(id: String, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var data by remember { mutableStateOf<RunData?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var metric by remember { mutableStateOf(TrendMetric.OVERSTRIDE) }

    LaunchedEffect(id) {
        data = withContext(Dispatchers.IO) { repo.getRun(id)?.let { RunData(it, clipsOf(repo, id)) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete run")
                    }
                },
            )
        },
    ) { padding ->
        val loaded = data
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val analysed = loaded.clips.filter { it.metrics != null }
        val insights = remember(loaded) {
            RunInsights.summarize(
                analysed.map {
                    RunClip((it.runOffsetSec ?: 0.0) / 60, it.metrics!!, it.sensor?.heartRate)
                },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RunOverview(loaded) }
            item {
                LabCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How your form changed", style = MaterialTheme.typography.titleMedium)
                        insights.forEach { Text("• $it") }
                    }
                }
            }
            if (analysed.size >= 2) {
                item {
                    LabCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TrendMetric.entries.forEach {
                                    FilterChip(selected = metric == it, onClick = { metric = it }, label = { Text(it.label) })
                                }
                            }
                            val points = analysed.mapNotNull { s ->
                                metric.value(s.metrics!!)?.let { ((s.runOffsetSec ?: 0.0) / 60) to it }
                            }
                            TrendChart(
                                points = points,
                                heartRate = loaded.run.samples,
                                durationMin = loaded.run.durationSec / 60,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                            )
                            val range = points.map { it.second }
                            if (range.isNotEmpty()) {
                                Text(
                                    "● ${metric.label}: ${range.min().roundToInt()}–${range.max().roundToInt()} ${metric.unit}" +
                                        if (loaded.run.samples.any { it.heartRate != null }) "    — Heart rate" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            items(loaded.clips, key = { it.id }) { ClipRow(it, onClick = { onOpenSession(it.id) }) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this run?") },
            text = { Text("The run and all of its clips will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteRun(id)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RunOverview(data: RunData) {
    val samples = data.run.samples
    val heartRates = samples.mapNotNull { it.heartRate }
    val speeds = samples.mapNotNull { it.speedKmh }.filter { it > 1 }
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(data.run.startedAt)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    formatDuration(data.run.durationSec),
                    "${data.clips.size} ${if (data.clips.size == 1) "clip" else "clips"}",
                    heartRates.takeIf { it.isNotEmpty() }?.let { "avg ♥ ${it.average().roundToInt()}, max ${it.max()}" },
                    speeds.takeIf { it.isNotEmpty() }?.let { "avg ${formatPace(it.average() / 3.6)}" },
                ).joinToString(" · "),
            )
        }
    }
}

/** Form metric per clip as dots and a line, with heart rate for the whole run faintly behind it. */
@Composable
private fun TrendChart(
    points: List<Pair<Double, Double>>,
    heartRate: List<SensorSample>,
    durationMin: Double,
    color: Color,
    modifier: Modifier,
) {
    val hr = heartRate.mapNotNull { s -> s.heartRate?.let { s.tSec / 60 to it.toDouble() } }
    val maxX = maxOf(durationMin, points.maxOfOrNull { it.first } ?: 0.0, 1.0)
    Canvas(modifier) {
        fun x(minute: Double) = (minute / maxX * size.width).toFloat()
        fun yScale(values: List<Double>): (Double) -> Float {
            val lo = values.min()
            val hi = values.max()
            val pad = maxOf((hi - lo) * 0.15, 1.0)
            return { v -> (size.height - (v - lo + pad) / (hi - lo + 2 * pad) * size.height).toFloat() }
        }
        if (hr.size >= 2) {
            val y = yScale(hr.map { it.second })
            val path = Path().apply {
                moveTo(x(hr[0].first), y(hr[0].second))
                hr.drop(1).forEach { lineTo(x(it.first), y(it.second)) }
            }
            drawPath(path, HeartRateColor.copy(alpha = 0.5f), style = Stroke(2.dp.toPx()))
        }
        if (points.isNotEmpty()) {
            val sorted = points.sortedBy { it.first }
            val y = yScale(sorted.map { it.second })
            sorted.zipWithNext { a, b ->
                drawLine(color, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), 3.dp.toPx())
            }
            sorted.forEach { drawCircle(color, 5.dp.toPx(), Offset(x(it.first), y(it.second))) }
        }
    }
}

/** The run's clock, large, in a viewfinder panel. */
@Composable
private fun RunTimerHero(elapsedSec: Double) {
    ViewfinderPanel {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionLabel("Run in progress")
            Text(formatDuration(elapsedSec), style = monoValue(64), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** One live reading: a label, a large monospace figure and its unit. */
@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier, colour: Color = Color.Unspecified) {
    LabCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(label, colour = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = monoValue(28), color = if (colour == Color.Unspecified) MaterialTheme.colorScheme.onSurface else colour)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** When to take the next clip: how far through the warm-up, or through the gap since the last clip. */
@Composable
private fun NextClipCard(elapsedSec: Double, lastOffsetSec: Double?) {
    val warmUpSec = 5 * 60.0
    val (progress, message) = if (lastOffsetSec == null) {
        (elapsedSec / warmUpSec) to
            if (elapsedSec >= warmUpSec) "You're warmed up. Take your first clip whenever you're ready." else "Warm up first: take your first clip at about 5 minutes."
    } else {
        ((elapsedSec - lastOffsetSec) / SUGGESTED_CLIP_INTERVAL_SEC) to
            "Next clip suggested at ${formatDuration(lastOffsetSec + SUGGESTED_CLIP_INTERVAL_SEC)}."
    }
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Next clip")
            LinearProgressIndicator(progress = { progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "A Bluetooth camera remote starts a clip from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClipRow(session: Session, onClick: () -> Unit) {
    val metrics = session.metrics
    val focus = metrics?.let { Coach.mainFocus(Coach.review(it, session.speedKmh)) }
    LabCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(if (metrics == null) false else focus == null)
                Text("At ${formatDuration(session.runOffsetSec ?: 0.0)}", style = MaterialTheme.typography.titleSmall)
            }
            if (metrics == null) {
                Text("Couldn't analyse this clip", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(focus?.let { "Focus: ${it.topic.lowercase()}" } ?: "Looking good", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.cadenceSpm?.let { StatChip("${it.roundToInt()}", "spm") }
                    metrics.overstrideCm?.let { StatChip("${it.roundToInt()}", "cm overstride") }
                    session.sensor?.heartRate?.let { StatChip("$it", "bpm") }
                }
            }
        }
    }
}

private fun clipsOf(repo: SessionRepository, runId: String): List<Session> =
    repo.list().filter { it.runId == runId }.sortedBy { it.runOffsetSec ?: 0.0 }
