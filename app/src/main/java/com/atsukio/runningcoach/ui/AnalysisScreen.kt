package com.atsukio.runningcoach.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitResult
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
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
    data object Done : AnalysisState
    data class Error(val message: String) : AnalysisState
}

class AnalysisViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    val sessionId: String = checkNotNull(handle["id"])
    private val speedKmh = handle.get<String>("speed")?.toDoubleOrNull()
    private val repo = SessionRepository(app)
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Running(0f))
    val state = _state.asStateFlow()

    private val job = viewModelScope.launch { run() }

    private suspend fun run() {
        try {
            var info: VideoInfo? = null
            var lastPercent = -1
            val track = PoseExtractor(getApplication()).extract(
                repo.videoFile(sessionId),
                onInfo = { info = it },
            ) { progress ->
                val percent = (progress * 100).roundToInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    _state.value = AnalysisState.Running(progress)
                }
            }
            _state.value = AnalysisState.Measuring
            val heightCm = CoachSettings(getApplication()).runnerHeightCm
            withContext(Dispatchers.IO) {
                repo.savePose(sessionId, track)
                val result = GaitAnalyzer.analyze(track, heightCm)
                repo.save(
                    Session(
                        id = sessionId,
                        createdAt = System.currentTimeMillis(),
                        speedKmh = speedKmh,
                        runnerHeightCm = heightCm,
                        fps = track.fps,
                        playbackFps = info?.playbackFps ?: track.fps,
                        videoWidth = track.width,
                        videoHeight = track.height,
                        metrics = (result as? GaitResult.Success)?.metrics,
                        warnings = (result as? GaitResult.Success)?.warnings.orEmpty(),
                        failure = (result as? GaitResult.Failure)?.reason,
                    ),
                )
            }
            _state.value = AnalysisState.Done
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
    onFinished: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: AnalysisViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancel = {
        viewModel.cancel()
        onCancel()
    }
    BackHandler(onBack = cancel)

    LaunchedEffect(state) {
        if (state == AnalysisState.Done) onFinished(viewModel.sessionId)
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
                AnalysisState.Measuring, AnalysisState.Done -> {
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
