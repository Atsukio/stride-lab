package com.atsukio.runningcoach.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Range
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.atsukio.runningcoach.MainActivity
import com.atsukio.runningcoach.data.CaptureInfo
import com.atsukio.runningcoach.data.RunRecorder
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.sensors.SensorSample
import com.atsukio.runningcoach.sensors.SensorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine

private const val COUNTDOWN_SECONDS = 5
private const val MAX_RECORDING_SECONDS = 15

private sealed interface CapturePhase {
    data object Idle : CapturePhase
    data class Countdown(val secondsLeft: Int) : CapturePhase
    data class Recording(val seconds: Int) : CapturePhase
    data object Saving : CapturePhase
}

/**
 * Records a clip with the camera, or imports one from the gallery, into app-private storage.
 * During a whole run ([runId] set) the clip is linked to the run, and [autoStart] begins the
 * countdown straight away so a Bluetooth remote press on the run screen is all it takes.
 */
@Composable
fun CaptureScreen(
    runId: String?,
    autoStart: Boolean,
    onBack: () -> Unit,
    onVideoReady: (id: String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repo = remember { SessionRepository(context) }
    val hub = rememberSensorHub()
    val liveStats = rememberFreshStats(hub)
    val runRecorder = remember { RunRecorder.get(context) }
    val scope = rememberCoroutineScope()
    val currentOnVideoReady by rememberUpdatedState(onVideoReady)

    var hasPermission by remember { mutableStateOf(hasCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var speedText by rememberSaveable { mutableStateOf("") }
    val speedKmh = speedText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 3.0..30.0 }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var phase by remember { mutableStateOf<CapturePhase>(CapturePhase.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var captureJob by remember { mutableStateOf<Job?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        phase = CapturePhase.Saving
        scope.launch {
            val id = repo.newSessionId()
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        repo.videoFile(id).outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
            }
            if (copied) {
                withContext(Dispatchers.IO) { repo.saveCaptureInfo(id, CaptureInfo(speedKmh = speedKmh)) }
                currentOnVideoReady(id)
            } else {
                repo.delete(id)
                phase = CapturePhase.Idle
                error = "Couldn't open that video."
            }
        }
    }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }

    LaunchedEffect(lensFacing, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val provider = cameraProvider(context)
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        fun recorder() = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
            .build()
        try {
            provider.unbindAll()
            // 60 fps catches the moment of landing much better; fall back to the default rate if unsupported.
            videoCapture = try {
                VideoCapture.Builder(recorder()).setTargetFrameRate(Range(60, 60)).build()
                    .also { provider.bindToLifecycle(lifecycleOwner, selector, preview, it) }
            } catch (e: Exception) {
                provider.unbindAll()
                VideoCapture.withOutput(recorder())
                    .also { provider.bindToLifecycle(lifecycleOwner, selector, preview, it) }
            }
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            error = "This camera isn't available."
        } finally {
            videoCapture = null
            provider.unbindAll()
        }
    }

    // The phone is usually propped up for a while, so stop the screen from sleeping.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun startCapture() {
        val capture = videoCapture ?: return
        error = null
        captureJob = scope.launch {
            for (s in COUNTDOWN_SECONDS downTo 1) {
                phase = CapturePhase.Countdown(s)
                delay(1000)
            }
            val id = repo.newSessionId()
            val file = repo.videoFile(id)
            val typedSpeed = speedKmh
            val run = runRecorder.active.value?.takeIf { it.id == runId }
            val samples = mutableListOf<SensorSample>()
            val startedAt = System.currentTimeMillis()
            recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event !is VideoRecordEvent.Finalize) return@start
                    recording = null
                    if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
                        repo.delete(id)
                        phase = CapturePhase.Idle
                        error = "Recording failed. Please try again."
                    } else {
                        val sensor = SensorSummary.of(samples)
                        val info = CaptureInfo(
                            speedKmh = typedSpeed ?: sensor?.speedKmh,
                            speedFromSensor = typedSpeed == null && sensor?.speedKmh != null,
                            sensor = sensor,
                            runId = run?.id,
                            runOffsetSec = run?.let { (startedAt - it.startedAt) / 1000.0 },
                        )
                        repo.saveCaptureInfo(id, info)
                        currentOnVideoReady(id)
                    }
                }
            var nextSampleMs = 0L
            while (true) {
                val elapsedMs = System.currentTimeMillis() - startedAt
                val seconds = (elapsedMs / 1000).toInt()
                if (seconds >= MAX_RECORDING_SECONDS) break
                phase = CapturePhase.Recording(seconds)
                if (elapsedMs >= nextSampleMs) {
                    hub.freshStats()?.let { samples += it.toSample(elapsedMs / 1000.0) }
                    nextSampleMs += 1000
                }
                delay(200)
            }
            phase = CapturePhase.Saving
            recording?.stop()
        }
    }

    fun onRecordButton() {
        when (phase) {
            CapturePhase.Idle -> startCapture()
            is CapturePhase.Countdown -> {
                captureJob?.cancel()
                phase = CapturePhase.Idle
            }
            is CapturePhase.Recording -> {
                captureJob?.cancel()
                phase = CapturePhase.Saving
                recording?.stop()
            }
            CapturePhase.Saving -> Unit
        }
    }

    val activity = LocalActivity.current as? MainActivity
    val shutter by rememberUpdatedState<() -> Unit> { onRecordButton() }
    DisposableEffect(activity) {
        activity?.onShutterKey = { shutter() }
        onDispose { activity?.onShutterKey = null }
    }

    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(videoCapture) {
        if (autoStart && !autoStarted && videoCapture != null) {
            autoStarted = true
            startCapture()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Camera access is needed to record. You can also import a video instead.",
                    color = Color.White,
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Allow camera") }
            }
        }

        LiveStatsChip(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp))

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, colors = overlayIconColors()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (val p = phase) {
                    is CapturePhase.Recording -> OverlayLabel(
                        "● 0:%02d / 0:%02d".format(p.seconds, MAX_RECORDING_SECONDS),
                        color = Color(0xFFFF5252),
                    )
                    CapturePhase.Idle -> OverlayLabel("Side-on · whole body in frame · hip height")
                    else -> Unit
                }
            }
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                enabled = phase == CapturePhase.Idle,
                colors = overlayIconColors(),
            ) { Icon(Icons.Default.Refresh, contentDescription = "Switch camera") }
        }

        (phase as? CapturePhase.Countdown)?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${it.secondsLeft}", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                OverlayLabel("Get running…")
            }
        }
        if (phase == CapturePhase.Saving) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { Text(it, color = Color(0xFFFF8A80)) }
                if (phase == CapturePhase.Idle) {
                    OutlinedTextField(
                        value = speedText,
                        onValueChange = { speedText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(4) },
                        label = { Text("Treadmill speed, km/h (optional)") },
                        supportingText = liveStats?.speedKmh?.takeIf { it > 1 }?.let { watchSpeed ->
                            { Text("Leave empty to use your watch: ${formatSpeed(watchSpeed)} km/h", color = Color.White) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (runId == null) {
                        OutlinedButton(
                            onClick = {
                                importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            enabled = phase == CapturePhase.Idle,
                        ) { Text("Import", color = Color.White) }
                    } else {
                        Box(Modifier.size(width = 96.dp, height = 1.dp))
                    }
                    RecordButton(
                        phase = phase,
                        enabled = videoCapture != null && phase != CapturePhase.Saving,
                        onClick = ::onRecordButton,
                    )
                    Box(Modifier.size(width = 96.dp, height = 1.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordButton(phase: CapturePhase, enabled: Boolean, onClick: () -> Unit) {
    val recording = phase is CapturePhase.Recording
    val label = when (phase) {
        CapturePhase.Idle -> "Start recording"
        is CapturePhase.Countdown -> "Cancel"
        is CapturePhase.Recording -> "Stop recording"
        CapturePhase.Saving -> "Saving"
    }
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = label },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xFFE53935),
                disabledContainerColor = Color.Gray,
            ),
        ) {
            if (recording) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
            }
        }
    }
}

@Composable
private fun OverlayLabel(text: String, color: Color = Color.White) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun overlayIconColors() = IconButtonDefaults.iconButtonColors(
    containerColor = Color.Black.copy(alpha = 0.4f),
    contentColor = Color.White,
)

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
        { cont.resumeWith(runCatching { future.get() }) },
        ContextCompat.getMainExecutor(context),
    )
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
