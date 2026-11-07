package com.atsukio.runningcoach.ui

import android.app.Application
import com.atsukio.runningcoach.data.AnalysisState
import com.atsukio.runningcoach.data.AnalysisRunner
import kotlinx.coroutines.flow.StateFlow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atsukio.runningcoach.data.CoachSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A thin view model: the analysis itself runs in [AnalysisRunner], so it carries on without this screen. */
class AnalysisViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val sessionId: String = checkNotNull(handle["id"])

    /** Re-running an existing clip: it keeps its date and isn't deleted if cancelled. */
    private val reanalyse: Boolean = handle["reanalyse"] ?: false
    private val runner = AnalysisRunner.get(app)
    private val task = runner.start(sessionId, reanalyse)

    val state: StateFlow<AnalysisState> = task.state

    /** When analysis started, on the [SystemClock.elapsedRealtime] clock, for the elapsed-time display. */
    val startedAtMs = task.startedAtMs

    fun cancel() = runner.cancel(sessionId)

    /** The screen has been shown the outcome, so the runner can forget it. */
    fun finish() = runner.dismiss(sessionId)
}

@Composable
fun AnalysisScreen(
    onFinished: (id: String, runId: String?) -> Unit,
    onCancel: () -> Unit,
    onBackground: () -> Unit,
    viewModel: AnalysisViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancel = {
        viewModel.cancel()
        onCancel()
    }
    BackHandler(onBack = cancel)

    val context = LocalContext.current
    val settings = remember { CoachSettings(context) }
    var askedAboutLive by remember { mutableStateOf(false) }
    val done = state as? AnalysisState.Done
    val askAboutLive = done != null && done.liveProblems.isNotEmpty() && settings.liveAnalysis && !askedAboutLive
    LaunchedEffect(done, askAboutLive) {
        if (done != null && !askAboutLive) {
            viewModel.finish()
            onFinished(viewModel.sessionId, done.runId)
        }
    }

    // Android 13 and later ask before showing the progress notification. Asked once; analysis works either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !settings.askedNotifications) {
            settings.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (askAboutLive) {
        AlertDialog(
            onDismissRequest = { askedAboutLive = true },
            title = { Text("Live analysis is struggling on this phone") },
            text = {
                Text(
                    done!!.liveProblems.joinToString("\n") +
                        "\n\nTurn it off to analyse clips after recording instead. You can change this in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.liveAnalysis = false
                    askedAboutLive = true
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { askedAboutLive = true }) { Text("Keep on") } },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is AnalysisState.Running -> {
                    SectionLabel("Analysing")
                    ScanPanel()
                    Text("${(s.progress * 100).roundToInt()}%", style = MaterialTheme.typography.displayMedium)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Finding the runner in each frame…", style = MaterialTheme.typography.titleMedium)
                    ElapsedTime(viewModel.startedAtMs)
                    Text(
                        "This runs on your phone and can take a minute.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "You can leave this screen or the app. It keeps going and tells you when it's ready.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBackground) { Text("Keep going in the background") }
                    OutlinedButton(onClick = cancel) { Text("Cancel") }
                }
                AnalysisState.Measuring, is AnalysisState.Done -> {
                    SectionLabel("Measuring")
                    ScanPanel()
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Measuring your form…", style = MaterialTheme.typography.titleMedium)
                    ElapsedTime(viewModel.startedAtMs)
                    Button(onClick = onBackground) { Text("Keep going in the background") }
                }
                is AnalysisState.Error -> {
                    Text("Couldn't analyse this video", style = MaterialTheme.typography.titleMedium)
                    Text(s.message, textAlign = TextAlign.Center)
                    Button(onClick = cancel) { Text("Back") }
                }
            }
        }
    }
}

/** Seconds since analysis started, ticking, so speed changes can be compared clip to clip. */
@Composable
private fun ElapsedTime(startedAtMs: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    Text(
        "%.1f s".format((now - startedAtMs) / 1000.0),
        style = monoValue(18),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
