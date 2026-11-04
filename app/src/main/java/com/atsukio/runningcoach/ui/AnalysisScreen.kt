package com.atsukio.runningcoach.ui

import android.app.Application
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atsukio.runningcoach.analysis.CrossCheck
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitResult
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.pose.GpuCheck
import com.atsukio.runningcoach.pose.LiveResults
import com.atsukio.runningcoach.pose.PoseExtractor
import com.atsukio.runningcoach.pose.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface AnalysisState {
    data class Running(val progress: Float) : AnalysisState
    data object Measuring : AnalysisState
    data class Done(val runId: String?, val liveProblems: List<String> = emptyList()) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

class AnalysisViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val sessionId: String = checkNotNull(handle["id"])
    private val repo = SessionRepository(app)
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Running(0f))
    val state = _state.asStateFlow()

    private val job = viewModelScope.launch { run() }

    private suspend fun run() {
        try {
            var info: VideoInfo? = null
            var lastPercent = -1
            val app = getApplication<Application>()
            val video = repo.videoFile(sessionId)
            GpuCheck.ensureChecked(app, video)
            val useGpu = GpuCheck.shouldUseGpu(app, CoachSettings(app).useGpu)
            // Live poses found on a GPU that has since failed the check aren't trusted.
            val live = LiveResults.take(sessionId)?.takeIf { useGpu || !it.usedGpu }
            val extraction = PoseExtractor(app, useGpu = useGpu).extract(
                video,
                live = live,
                onInfo = { info = it },
            ) { progress ->
                val percent = (progress * 100).roundToInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    _state.value = AnalysisState.Running(progress)
                }
            }
            val track = extraction.track
            _state.value = AnalysisState.Measuring
            val heightCm = CoachSettings(getApplication()).runnerHeightCm
            val capture = withContext(Dispatchers.IO) {
                repo.savePose(sessionId, track)
                val capture = repo.loadCaptureInfo(sessionId)
                val result = GaitAnalyzer.analyze(track, heightCm, beltSpeedKmh = capture.speedKmh)
                val success = result as? GaitResult.Success
                val watchScaleNote = if (success?.metrics?.scaledFromTreadmill == true && capture.speedFromSensor) {
                    "Distances were scaled from your watch's pace. Typing the treadmill's own speed is more accurate."
                } else {
                    null
                }
                val videoCadence = success?.metrics?.cadenceSpm
                val watchCadence = capture.sensor?.cadenceSpm
                val crossCheck = if (videoCadence != null && watchCadence != null) {
                    CrossCheck.cadence(videoCadence, watchCadence)
                } else {
                    null
                }
                repo.save(
                    Session(
                        id = sessionId,
                        createdAt = System.currentTimeMillis(),
                        speedKmh = capture.speedKmh,
                        speedFromSensor = capture.speedFromSensor,
                        runnerHeightCm = heightCm,
                        fps = track.fps,
                        playbackFps = info?.playbackFps ?: track.fps,
                        videoWidth = track.width,
                        videoHeight = track.height,
                        metrics = success?.metrics,
                        warnings = success?.warnings.orEmpty() + listOfNotNull(crossCheck, watchScaleNote),
                        failure = (result as? GaitResult.Failure)?.reason,
                        sensor = capture.sensor,
                        runId = capture.runId,
                        runOffsetSec = capture.runOffsetSec,
                    ),
                )
                capture
            }
            _state.value = AnalysisState.Done(capture.runId, extraction.liveProblems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = AnalysisState.Error(e.message ?: "Something went wrong while analysing the video.")
        }
    }

    fun cancel() {
        job.cancel()
        repo.delete(sessionId)
    }
}

@Composable
fun AnalysisScreen(
    onFinished: (id: String, runId: String?) -> Unit,
    onCancel: () -> Unit,
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
        if (done != null && !askAboutLive) onFinished(viewModel.sessionId, done.runId)
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
                    Text("Finding the runner in each frame…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).roundToInt()}%")
                    Text(
                        "This runs on your phone and can take a minute.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = cancel) { Text("Cancel") }
                }
                AnalysisState.Measuring, is AnalysisState.Done -> {
                    Text("Measuring your form…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
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
