package com.atsukio.runningcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import com.atsukio.runningcoach.analysis.PlanTracker
import com.atsukio.runningcoach.analysis.PlanClip
import com.atsukio.runningcoach.analysis.FocusPlan
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atsukio.runningcoach.R
import com.atsukio.runningcoach.analysis.Coach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.AnalysisRunner
import com.atsukio.runningcoach.data.AnalysisState
import com.atsukio.runningcoach.data.AnalysisTask
import com.atsukio.runningcoach.data.RunRecorder
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.WholeRun
import com.atsukio.runningcoach.pose.GpuCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private sealed interface HomeItem {
    val time: Long

    class Clip(val session: Session) : HomeItem {
        override val time get() = session.createdAt
    }

    class Run(val run: WholeRun, val clipCount: Int) : HomeItem {
        override val time get() = run.startedAt
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewAnalysis: () -> Unit,
    onStartRun: () -> Unit,
    onOpenRunSummary: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAnalysis: (id: String, reanalyse: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val settings = remember { CoachSettings(context) }
    val activeRun by remember { RunRecorder.get(context) }.active.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<HomeItem>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf(settings.focusPlan) }
    val runner = remember { AnalysisRunner.get(context) }
    val analyses by runner.tasks.collectAsStateWithLifecycle()
    // Bumped when a background analysis finishes, so the history picks up the new clip.
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        items = withContext(Dispatchers.IO) {
            repo.deleteAbandoned()
            val sessions = repo.list()
            val clipCounts = sessions.groupingBy { it.runId }.eachCount()
            val runs = repo.listRuns().filter { it.endedAt != null || it.id != activeRun?.id }
            (sessions.filter { it.runId == null }.map { HomeItem.Clip(it) } +
                runs.map { HomeItem.Run(it, clipCounts[it.id] ?: 0) })
                .sortedByDescending { it.time }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono, letterSpacing = 3.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            // The hero panel already has the record button; this one keeps it in reach once there's a history.
            if (items?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = onNewAnalysis,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New analysis") },
                )
            }
        },
    ) { padding ->
        val list = items
        when {
            list == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(analyses.values.toList(), key = { "analysis-${it.sessionId}" }) { task ->
                    AnalysisTaskCard(
                        task = task,
                        onOpen = { onOpenAnalysis(task.sessionId, task.reanalyse) },
                        onShowResult = {
                            runner.dismiss(task.sessionId)
                            onOpenSession(task.sessionId)
                        },
                        onDismiss = { runner.cancel(task.sessionId) },
                        onDone = { reload++ },
                    )
                }
                item { RecordHero(onRecord = onNewAnalysis) }
                val analysed = list.filterIsInstance<HomeItem.Clip>().map { it.session }.filter { it.metrics != null }
                plan?.let { active ->
                    item {
                        PlanCard(
                            plan = active,
                            clips = analysed.map { PlanClip(it.createdAt, it.metrics!!, it.speedKmh) },
                            onCheckIn = onNewAnalysis,
                            onEnd = {
                                settings.focusPlan = null
                                plan = null
                            },
                        )
                    }
                }
                item { WholeRunCard(inProgress = activeRun != null, onClick = onStartRun) }
                if (analysed.size >= 2) item { TrendsCard(analysed, onClick = onOpenTrends) }
                if (list.isEmpty()) item { FilmingTips(onOpenGuide = onOpenGuide) } else item { SectionLabel("History", Modifier.padding(top = 8.dp)) }
                items(list, key = { if (it is HomeItem.Run) "run-${it.run.id}" else (it as HomeItem.Clip).session.id }) { item ->
                    when (item) {
                        is HomeItem.Clip -> SessionCard(item.session, onClick = { onOpenSession(item.session.id) })
                        is HomeItem.Run -> RunCard(item.run, item.clipCount, onClick = { onOpenRunSummary(item.run.id) })
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(settings, onDismiss = { showSettings = false })
    }
}

/** A one-line progress summary that opens the trends screen. */
@Composable
private fun TrendsCard(analysed: List<Session>, onClick: () -> Unit) {
    val byDate = analysed.sortedBy { it.createdAt }
    val first = byDate.first().metrics?.cadenceSpm
    val last = byDate.last().metrics?.cadenceSpm
    LabCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Progress")
            Text("Your progress", style = MaterialTheme.typography.titleMedium)
            Text(
                if (first != null && last != null) {
                    "Cadence ${first.roundToInt()} → ${last.roundToInt()} steps/min over ${analysed.size} clips. " +
                        "See every measurement over time."
                } else {
                    "See how each measurement has changed over ${analysed.size} clips."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun FilmingTips(modifier: Modifier = Modifier, onOpenGuide: (() -> Unit)? = null) {
    LabCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How to film", style = MaterialTheme.typography.titleMedium)
            Text("1. Run on a treadmill so you stay in one spot.")
            Text("2. Put the phone side-on to the runner at hip height, about 2–3 m away.")
            Text("3. Keep the whole body, head to feet, in frame.")
            Text("4. Record 10–15 seconds of steady running.")
            Text("Tip: a Bluetooth camera remote can start and stop recording.")
            onOpenGuide?.let { TextButton(onClick = it) { Text("Open the setup guide") } }
            Text(
                "Videos and results stay on this phone. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WholeRunCard(inProgress: Boolean, onClick: () -> Unit) {
    LabCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(if (inProgress) "In progress" else "Whole run", colour = if (inProgress) signalColour(true) else MaterialTheme.colorScheme.secondary)
            Text(
                if (inProgress) "Run in progress: tap to return" else "Start a whole run",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Take a few clips during one run and see how your form changes as you tire, " +
                    "alongside heart rate from your watch.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RunCard(run: WholeRun, clipCount: Int, onClick: () -> Unit) {
    val heartRates = run.samples.mapNotNull { it.heartRate }
    LabCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Whole run", colour = MaterialTheme.colorScheme.secondary)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.startedAt)),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(formatDuration(run.durationSec), null)
                StatChip("$clipCount", if (clipCount == 1) "clip" else "clips")
                heartRates.takeIf { it.isNotEmpty() }?.let { StatChip("${it.average().roundToInt()}", "bpm avg") }
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    val metrics = session.metrics
    val focus = metrics?.let { Coach.mainFocus(Coach.review(it, session.speedKmh)) }
    LabCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(if (metrics == null) false else focus == null)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.createdAt)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (metrics == null) {
                Text("Couldn't analyse this clip", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    focus?.let { "Focus: ${it.topic.lowercase()}" } ?: "Looking good",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.cadenceSpm?.let { StatChip("${it.roundToInt()}", "spm") }
                    session.speedKmh?.let { StatChip(formatSpeed(it), "km/h") }
                    metrics.overstrideCm?.let { StatChip("${it.roundToInt()}", "cm overstride") }
                }
            }
        }
    }
}

/** The focus being worked on, how far through it is, and how the latest check-in clip measured up. */
@Composable
private fun PlanCard(plan: FocusPlan, clips: List<PlanClip>, onCheckIn: () -> Unit, onEnd: () -> Unit) {
    val progress = remember(plan, clips) { PlanTracker.progress(plan, System.currentTimeMillis(), clips) }
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(
                "Focus plan · day ${progress.day} of ${progress.totalDays}",
                colour = if (progress.reachedGoal) signalColour(true) else MaterialTheme.colorScheme.primary,
            )
            Text(plan.topic, style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            plan.cue?.let { Text("Think: \"$it\"", color = MaterialTheme.colorScheme.primary) }
            plan.drill?.let { Text("Drill: $it", style = MaterialTheme.typography.bodyMedium) }
            val latest = progress.latest
            Text(
                when {
                    progress.reachedGoal -> "Your latest check-in is in the good range: " + latest?.value.orEmpty()
                    latest != null -> "Latest check-in: ${latest.value}, still to work on."
                    progress.timeIsUp -> "Time's up. Film a check-in to see how it went."
                    else -> "Practise the drill a few times a week, then film a check-in clip."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!progress.reachedGoal) Button(onClick = onCheckIn) { Text("Film a check-in") }
                OutlinedButton(onClick = onEnd) { Text(if (progress.finished) "Finish plan" else "End plan") }
            }
        }
    }
}

/** A clip being analysed in the background, or one that has just finished or failed. */
@Composable
private fun AnalysisTaskCard(
    task: AnalysisTask,
    onOpen: () -> Unit,
    onShowResult: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val state by task.state.collectAsStateWithLifecycle()
    LaunchedEffect(state is AnalysisState.Done) { if (state is AnalysisState.Done) onDone() }
    val done = state is AnalysisState.Done
    LabCard(onClick = if (done) onShowResult else onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                is AnalysisState.Running -> {
                    SectionLabel("Analysing in the background")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).roundToInt()}%. Tap to watch.", style = MaterialTheme.typography.bodyMedium)
                }
                AnalysisState.Measuring -> {
                    SectionLabel("Analysing in the background")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Measuring your form. Tap to watch.", style = MaterialTheme.typography.bodyMedium)
                }
                is AnalysisState.Done -> {
                    SectionLabel("Ready", colour = signalColour(true))
                    Text("Your analysis is ready. Tap to see it.", style = MaterialTheme.typography.titleMedium)
                }
                is AnalysisState.Error -> {
                    SectionLabel("Couldn't analyse", colour = signalColour(false))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

/** The main call to action: record a clip. */
@Composable
private fun RecordHero(onRecord: () -> Unit) {
    ViewfinderPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Ready")
            Text("Check your running form", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Film a side-on clip from the treadmill. Everything is analysed on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRecord) { Text("Record a clip") }
        }
    }
}

@Composable
private fun SettingsDialog(settings: CoachSettings, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(settings.runnerHeightCm.roundToInt().toString()) }
    var seconds by remember { mutableIntStateOf(settings.recordSeconds) }
    var live by remember { mutableStateOf(settings.liveAnalysis) }
    var gpu by remember { mutableStateOf(settings.useGpu) }
    var appearance by remember { mutableStateOf(settings.appearance) }
    val value = text.toDoubleOrNull()
    val valid = value != null && value in 100.0..230.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Runner's height (cm)") },
                    supportingText = {
                        Text(
                            "Turns what the camera sees into centimetres when there's no treadmill speed. " +
                                "Enter a speed when recording and the app measures your height for you.",
                        )
                    },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Appearance.entries.forEach { option ->
                        FilterChip(selected = appearance == option, onClick = { appearance = option }, label = { Text(option.label) })
                    }
                }
                Text("Clip length", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoachSettings.RECORD_OPTIONS.forEach { option ->
                        FilterChip(selected = seconds == option, onClick = { seconds = option }, label = { Text("$option s") })
                    }
                }
                SettingSwitch(
                    title = "Analyse while recording",
                    detail = "Results are ready almost as soon as the clip ends. Uses more battery and can warm the phone.",
                    checked = live,
                    onChange = { live = it },
                )
                val gpuVerdict = remember { GpuCheck.verdict(context) }
                SettingSwitch(
                    title = "Use the GPU for pose detection",
                    detail = when (gpuVerdict) {
                        GpuCheck.Verdict.DISAGREES ->
                            "Not used on this phone: a check found its GPU results don't match the CPU's, so the CPU is used. " +
                                "It's checked again after system updates."
                        GpuCheck.Verdict.UNAVAILABLE -> "This phone's GPU can't run pose detection, so the CPU is used."
                        else -> "Faster. The app checks the GPU against the CPU on first use and switches it off " +
                            "if they disagree."
                    },
                    checked = gpu,
                    onChange = { gpu = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value?.let { settings.runnerHeightCm = it }
                    settings.recordSeconds = seconds
                    settings.liveAnalysis = live
                    settings.useGpu = gpu
                    settings.appearance = appearance
                    AppearanceState.current = appearance
                    onDismiss()
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun formatSpeed(kmh: Double): String =
    if (kmh % 1.0 == 0.0) kmh.roundToInt().toString() else "%.1f".format(kmh)
