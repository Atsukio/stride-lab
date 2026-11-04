package com.atsukio.runningcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.atsukio.runningcoach.R
import com.atsukio.runningcoach.analysis.Coach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.RunRecorder
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.WholeRun
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
) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val settings = remember { CoachSettings(context) }
    val activeRun by remember { RunRecorder.get(context) }.active.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<HomeItem>?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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
                title = { Text(stringResource(R.string.app_name)) },
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
            ExtendedFloatingActionButton(
                onClick = onNewAnalysis,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New analysis") },
            )
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
                item { WholeRunCard(inProgress = activeRun != null, onClick = onStartRun) }
                if (list.isEmpty()) item { FilmingTips() }
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
        HeightDialog(
            initialCm = settings.runnerHeightCm,
            onDismiss = { showSettings = false },
            onSave = {
                settings.runnerHeightCm = it
                showSettings = false
            },
        )
    }
}

@Composable
fun FilmingTips(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How to film", style = MaterialTheme.typography.titleMedium)
            Text("1. Run on a treadmill so you stay in one spot.")
            Text("2. Put the phone side-on to the runner at hip height, about 2–3 m away.")
            Text("3. Keep the whole body, head to feet, in frame.")
            Text("4. Record 10–15 seconds of steady running.")
            Text("Tip: a Bluetooth camera remote can start and stop recording.")
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Whole run · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(run.startedAt)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    formatDuration(run.durationSec),
                    "$clipCount ${if (clipCount == 1) "clip" else "clips"}",
                    heartRates.takeIf { it.isNotEmpty() }?.let { "avg ♥ ${it.average().roundToInt()}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    val metrics = session.metrics
    val summary = when {
        metrics == null -> "Couldn't analyse this clip"
        else -> {
            val focus = Coach.mainFocus(Coach.review(metrics, session.speedKmh))
            listOfNotNull(
                metrics.cadenceSpm?.let { "${it.roundToInt()} steps/min" },
                focus?.let { "Focus: ${it.topic.lowercase()}" } ?: "Looking good",
            ).joinToString(" · ")
        }
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.createdAt)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            session.speedKmh?.let {
                Text(
                    "${formatSpeed(it)} km/h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeightDialog(initialCm: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(initialCm.roundToInt().toString()) }
    val value = text.toDoubleOrNull()
    val valid = value != null && value in 100.0..230.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Runner's height") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Used to convert what the camera sees into centimetres when there's no treadmill speed. " +
                        "Enter a treadmill speed when recording and the app measures your height for you.",
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatSpeed(kmh: Double): String =
    if (kmh % 1.0 == 0.0) kmh.roundToInt().toString() else "%.1f".format(kmh)
