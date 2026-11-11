package com.atsukio.runningcoach.data

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.atsukio.runningcoach.AnalysisService
import com.atsukio.runningcoach.pose.GpuCheck
import com.atsukio.runningcoach.pose.LiveResults
import com.atsukio.runningcoach.pose.PoseExtractor
import com.atsukio.runningcoach.pose.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface AnalysisState {
    data class Running(val progress: Float) : AnalysisState
    data object Measuring : AnalysisState
    data class Done(val runId: String?, val liveProblems: List<String> = emptyList()) : AnalysisState
    data class Error(val message: String) : AnalysisState

    val inProgress: Boolean get() = this is Running || this is Measuring
}

/** One clip being analysed: its live [state], when it started, and the job to cancel. */
class AnalysisTask(
    val sessionId: String,
    val reanalyse: Boolean,
    /** On the [SystemClock.elapsedRealtime] clock, for the elapsed-time display. */
    val startedAtMs: Long,
) {
    internal val mutableState = MutableStateFlow<AnalysisState>(AnalysisState.Running(0f))
    val state: StateFlow<AnalysisState> = mutableState.asStateFlow()
    internal var job: Job? = null
}

/**
 * Runs analyses for the whole app, not for one screen, so a clip keeps being analysed when the person
 * leaves the screen or the app. While anything is running, [AnalysisService] keeps the process alive and
 * shows a notification.
 */
class AnalysisRunner private constructor(private val app: Application) {
    private val repo = SessionRepository(app)

    // Main, like a view model's scope: pose detection was written and tested on that dispatcher.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _tasks = MutableStateFlow<Map<String, AnalysisTask>>(emptyMap())
    val tasks: StateFlow<Map<String, AnalysisTask>> = _tasks.asStateFlow()

    /** Starts analysing [sessionId], or returns the task already running or finished for it. */
    fun start(sessionId: String, reanalyse: Boolean): AnalysisTask {
        _tasks.value[sessionId]?.let { return it }
        val task = AnalysisTask(sessionId, reanalyse, SystemClock.elapsedRealtime())
        _tasks.update { it + (sessionId to task) }
        ContextCompat.startForegroundService(app, AnalysisService.intent(app))
        task.job = scope.launch { run(task) }
        return task
    }

    /** Stops the analysis and, for a new clip, deletes it. Re-analysing keeps the clip. */
    fun cancel(sessionId: String) {
        val task = _tasks.value[sessionId] ?: return
        task.job?.cancel()
        if (!task.reanalyse) repo.delete(sessionId)
        dismiss(sessionId)
    }

    /**
     * Forgets a task that has finished, before the clip is analysed again. One left behind (its result was
     * opened from the notification or the history list) would make [start] hand back the old result at once.
     */
    fun forgetFinished(sessionId: String) {
        if (_tasks.value[sessionId]?.state?.value?.inProgress == false) dismiss(sessionId)
    }

    /** Forgets a finished or failed task once the person has seen it. */
    fun dismiss(sessionId: String) {
        _tasks.update { it - sessionId }
    }

    private suspend fun run(task: AnalysisTask) {
        val sessionId = task.sessionId
        try {
            var info: VideoInfo? = null
            var lastPercent = -1
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
                    task.mutableState.value = AnalysisState.Running(progress)
                }
            }
            val track = extraction.track
            task.mutableState.value = AnalysisState.Measuring
            val existing = if (task.reanalyse) withContext(Dispatchers.IO) { repo.get(sessionId) } else null
            // A height chosen for this clip beats the Settings one, so analysing again keeps it.
            val heightCm = existing?.takeIf { it.scaleEdited }?.runnerHeightCm ?: CoachSettings(app).runnerHeightCm
            val capture = withContext(Dispatchers.IO) {
                repo.savePose(sessionId, track)
                val capture = repo.loadCaptureInfo(sessionId)
                repo.save(
                    SessionBuilder.build(
                        id = sessionId,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        track = track,
                        playbackFps = info?.playbackFps ?: track.fps,
                        heightCm = heightCm,
                        capture = capture,
                        extractionNotes = extraction.notes,
                        processingSec = (SystemClock.elapsedRealtime() - task.startedAtMs) / 1000.0,
                        analysedLive = extraction.usedLive,
                        scaleEdited = existing?.scaleEdited ?: false,
                    ),
                )
                capture
            }
            task.mutableState.value = AnalysisState.Done(capture.runId, extraction.liveProblems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkageError) {
            // The pose library has no build for this phone's processor (it needs an ARM phone).
            task.mutableState.value = AnalysisState.Error("This phone's processor can't run pose detection, so clips can't be analysed on it.")
        } catch (e: Exception) {
            task.mutableState.value = AnalysisState.Error(e.message ?: "Something went wrong while analysing the video.")
        }
    }

    companion object {
        @Volatile
        private var instance: AnalysisRunner? = null

        fun get(context: Context): AnalysisRunner = instance ?: synchronized(this) {
            instance ?: AnalysisRunner(context.applicationContext as Application).also { instance = it }
        }
    }
}
