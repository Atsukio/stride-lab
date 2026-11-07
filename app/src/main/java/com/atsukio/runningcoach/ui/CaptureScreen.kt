package com.atsukio.runningcoach.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Range
import android.util.Size
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.foundation.Canvas
import com.atsukio.runningcoach.analysis.LiveStrikeDetector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import android.os.SystemClock
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atsukio.runningcoach.MainActivity
import com.atsukio.runningcoach.analysis.Framing
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.data.CaptureInfo
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.RunRecorder
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.pose.GpuCheck
import com.atsukio.runningcoach.pose.LivePoseAnalyzer
import com.atsukio.runningcoach.pose.LivePreview
import com.atsukio.runningcoach.pose.LiveResults
import com.atsukio.runningcoach.sensors.SensorSample
import com.atsukio.runningcoach.sensors.SensorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine

private const val COUNTDOWN_SECONDS = 5

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
    val settings = remember { CoachSettings(context) }
    val maxSeconds = remember { settings.recordSeconds }
    var liveOn by remember { mutableStateOf(settings.liveAnalysis) }
    var liveAnalyzer by remember { mutableStateOf<LivePoseAnalyzer?>(null) }
    var liveUnavailable by remember { mutableStateOf(false) }

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

    LaunchedEffect(lensFacing, hasPermission, liveOn) {
        if (!hasPermission) return@LaunchedEffect
        val provider = cameraProvider(context)
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        // 16:9 like the video, so the preview shows exactly what gets recorded and analysed.
        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build(),
            )
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        fun recorder() = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
            .build()
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var analyzer: LivePoseAnalyzer? = null
        // 60 fps catches the moment of landing much better, so it comes before live analysis.
        val attempts = listOfNotNull(
            if (liveOn) (true to true) else null,
            false to true,
            if (liveOn) (true to false) else null,
            false to false,
        )
        try {
            for ((withLive, at60) in attempts) {
                try {
                    provider.unbindAll()
                    val capture = if (at60) {
                        VideoCapture.Builder(recorder()).setTargetFrameRate(Range(60, 60)).build()
                    } else {
                        VideoCapture.withOutput(recorder())
                    }
                    if (withLive) {
                        val analysis = liveAnalysisUseCase()
                        val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                        val camera60Fps = camera.cameraInfo.supportedFrameRateRanges.any { it.upper >= 60 }
                        analyzer = LivePoseAnalyzer(context, GpuCheck.shouldUseGpu(context, settings.useGpu), camera60Fps)
                            .also { analysis.setAnalyzer(analysisExecutor, it) }
                    } else {
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }
                    liveAnalyzer = analyzer
                    liveUnavailable = liveOn && !withLive
                    videoCapture = capture
                    break
                } catch (e: IllegalArgumentException) {
                    continue
                } catch (e: IllegalStateException) {
                    continue
                }
            }
            if (videoCapture == null) error = "This camera isn't available."
            awaitCancellation()
        } finally {
            videoCapture = null
            liveAnalyzer = null
            provider.unbindAll()
            analyzer?.close()
            analysisExecutor.shutdown()
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
            val live = liveAnalyzer
            live?.begin()
            recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event !is VideoRecordEvent.Finalize) return@start
                    recording = null
                    val liveResult = live?.end()
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
                        liveResult?.let { LiveResults.put(id, it) }
                        currentOnVideoReady(id)
                    }
                }
            var nextSampleMs = 0L
            while (true) {
                val elapsedMs = System.currentTimeMillis() - startedAt
                val seconds = (elapsedMs / 1000).toInt()
                if (seconds >= maxSeconds) break
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
        val noPreview = remember { MutableStateFlow<LivePreview?>(null) }
        val livePreview by (liveAnalyzer?.preview ?: noPreview).collectAsStateWithLifecycle()
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            livePreview?.let { LiveSkeleton(it, mirrored = lensFacing == CameraSelector.LENS_FACING_FRONT, Modifier.fillMaxSize()) }
            ViewfinderMarks(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 72.dp, bottom = if (phase == CapturePhase.Idle) 292.dp else 148.dp),
            )
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
                        "REC 0:%02d / 0:%02d".format(p.seconds, maxSeconds) + if (liveAnalyzer != null) " · ANALYSING" else "",
                        dot = Color(0xFFFF5252),
                        pulsing = true,
                    )
                    CapturePhase.Idle -> {
                        val advice = livePreview?.let { Framing.check(it.pose, it.brightness) }
                        OverlayLabel(
                            advice?.message ?: "Side-on · whole body · hip height",
                            dot = when (advice?.good) {
                                true -> Signal.Good
                                false -> Signal.Improve
                                null -> Color.White.copy(alpha = 0.6f)
                            },
                        )
                    }
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
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CountdownRing(it.secondsLeft)
                OverlayLabel("GET RUNNING")
            }
        }
        if (phase == CapturePhase.Saving) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF0B1210).copy(alpha = 0.82f),
        ) {
            Column(
                Modifier.navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { Text(it, color = Color(0xFFFF8A80)) }
                if (liveUnavailable && phase == CapturePhase.Idle) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "This camera can't analyse while recording, so clips are analysed afterwards.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            settings.liveAnalysis = false
                            liveOn = false
                        }) { Text("Turn off") }
                    }
                }
                if (phase == CapturePhase.Idle) {
                    MetronomeControl(contentColor = Color.White)
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
                        progress = (phase as? CapturePhase.Recording)?.let { it.seconds.toFloat() / maxSeconds },
                        onClick = ::onRecordButton,
                    )
                    Box(Modifier.size(width = 96.dp, height = 1.dp))
                }
            }
        }
    }
}

/**
 * The live pose drawn over the camera preview, which shows the frame fitted to the screen. Points are
 * mirrored for the front camera, whose preview is a mirror image while the analysis frames aren't. A ring
 * pulses at the foot each time it lands, so you can see the app following your stride.
 */
@Composable
private fun LiveSkeleton(preview: LivePreview, mirrored: Boolean, modifier: Modifier) {
    val detector = remember { LiveStrikeDetector() }
    val pulse = remember { Animatable(1f) }
    var strike by remember { mutableStateOf<LiveStrikeDetector.Strike?>(null) }
    var strikes by remember { mutableIntStateOf(0) }
    // Detecting is quick and never suspends, so each new frame can't cut a running pulse short;
    // the pulse itself is animated by the effect below, keyed on the landing count.
    LaunchedEffect(preview) {
        detector.update(preview.pose, SystemClock.elapsedRealtime())?.let {
            strike = it
            strikes++
        }
    }
    LaunchedEffect(strikes) {
        if (strikes > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(400, easing = LinearOutSlowInEasing))
        }
    }

    val pose = preview.pose ?: return
    Canvas(modifier) {
        val aspect = preview.frameWidth.toFloat() / preview.frameHeight.coerceAtLeast(1)
        val fitWidth = if (size.width / size.height > aspect) size.height * aspect else size.width
        val fitHeight = fitWidth / aspect
        val left = (size.width - fitWidth) / 2
        val top = (size.height - fitHeight) / 2
        fun at(nx: Float, ny: Float): Offset {
            val x = if (mirrored) 1 - nx else nx
            return Offset(left + x * fitWidth, top + ny * fitHeight)
        }
        fun point(i: Int) = at(pose.x[i], pose.y[i])
        val glow = Signal.Good.copy(alpha = 0.22f)
        val bone = Color(0xFFD9FFF0).copy(alpha = 0.9f)
        val limbs = (Landmark.LEFT_CONNECTIONS + Landmark.RIGHT_CONNECTIONS + Landmark.CENTER_CONNECTIONS)
            .filter { (a, b) -> pose.visibility[a] >= 0.5f && pose.visibility[b] >= 0.5f }
        limbs.forEach { (a, b) -> drawLine(glow, point(a), point(b), 9.dp.toPx(), StrokeCap.Round) }
        limbs.forEach { (a, b) -> drawLine(bone, point(a), point(b), 2.5.dp.toPx(), StrokeCap.Round) }
        // Joints as small dots.
        listOf(
            Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER, Landmark.LEFT_ELBOW, Landmark.RIGHT_ELBOW,
            Landmark.LEFT_HIP, Landmark.RIGHT_HIP, Landmark.LEFT_KNEE, Landmark.RIGHT_KNEE,
            Landmark.LEFT_ANKLE, Landmark.RIGHT_ANKLE,
        ).filter { pose.visibility[it] >= 0.5f }.forEach { drawCircle(Signal.Good, 3.5.dp.toPx(), point(it)) }

        // The landing pulse: a ring that grows and fades.
        val p = pulse.value
        val landed = strike
        if (landed != null && p < 1f) {
            val centre = at(landed.x, landed.y)
            val radius = (10 + 34 * p).dp.toPx()
            drawCircle(Signal.Good.copy(alpha = 0.25f * (1 - p)), radius, centre)
            drawCircle(Signal.Good.copy(alpha = 0.95f * (1 - p)), radius, centre, style = Stroke(3.dp.toPx()))
        }
    }
}

/** Corner brackets framing where the whole runner should be, like a camera's viewfinder. */
@Composable
private fun ViewfinderMarks(modifier: Modifier) {
    val colour = Signal.Good.copy(alpha = 0.7f)
    Canvas(modifier) {
        val arm = 22.dp.toPx()
        val width = 2.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(colour, Offset(x, y), Offset(x + dx * arm, y), width, StrokeCap.Round)
            drawLine(colour, Offset(x, y), Offset(x, y + dy * arm), width, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f)
        corner(size.width, 0f, -1f, 1f)
        corner(0f, size.height, 1f, -1f)
        corner(size.width, size.height, -1f, -1f)
    }
}

/** The pre-recording countdown: a big figure inside a ring that empties over each second. */
@Composable
private fun CountdownRing(secondsLeft: Int) {
    val sweep = remember(secondsLeft) { Animatable(1f) }
    LaunchedEffect(secondsLeft) { sweep.animateTo(0f, tween(1000, easing = LinearEasing)) }
    Box(Modifier.size(180.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val ringSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, inset, ringSize, style = Stroke(stroke))
            drawArc(Signal.Good, -90f, 360f * sweep.value, false, inset, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$secondsLeft", style = monoValue(88), color = Color.White)
    }
}

@Composable
private fun RecordButton(phase: CapturePhase, enabled: Boolean, progress: Float?, onClick: () -> Unit) {
    val recording = phase is CapturePhase.Recording
    val label = when (phase) {
        CapturePhase.Idle -> "Start recording"
        is CapturePhase.Countdown -> "Cancel"
        is CapturePhase.Recording -> "Stop recording"
        CapturePhase.Saving -> "Saving"
    }
    // The ring fills as the clip records. Seconds arrive one at a time, so it glides between them.
    val shown by animateFloatAsState(progress ?: 0f, tween(1000, easing = LinearEasing), label = "recordProgress")
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        if (progress != null) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val inset = Offset(stroke / 2, stroke / 2)
                val ringSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, inset, ringSize, style = Stroke(stroke))
                drawArc(Color(0xFFFF5252), -90f, 360f * shown.coerceIn(0f, 1f), false, inset, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier
                .size(76.dp)
                .border(4.dp, if (progress != null) Color.White.copy(alpha = 0.35f) else Color.White, CircleShape)
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
}

/** A dark pill over the camera: a status dot (pulsing while recording) and a short monospace message. */
@Composable
private fun OverlayLabel(text: String, dot: Color? = null, pulsing: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF0B1210).copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it.copy(alpha = it.alpha * pulse))) }
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.3.sp),
            maxLines = 2,
        )
    }
}

@Composable
private fun overlayIconColors() = IconButtonDefaults.iconButtonColors(
    containerColor = Color(0xFF0B1210).copy(alpha = 0.6f),
    contentColor = Color.White,
)

/**
 * A 720p, 16:9 stream for live pose detection. The same aspect ratio as the video keeps the framing identical,
 * so live poses line up with the recording. Frames are dropped, never queued, when analysis falls behind.
 */
private fun liveAnalysisUseCase() = ImageAnalysis.Builder()
    .setResolutionSelector(
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
            )
            .build(),
    )
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .build()

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener(
        { cont.resumeWith(runCatching { future.get() }) },
        ContextCompat.getMainExecutor(context),
    )
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
