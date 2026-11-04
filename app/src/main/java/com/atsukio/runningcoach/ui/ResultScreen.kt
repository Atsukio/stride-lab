package com.atsukio.runningcoach.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FeedbackStatus
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseCleaner
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** [pose] is cleaned for display and analysis; [rawPose] is exactly what the detector found. */
private class ResultData(val session: Session, val pose: PoseTrack?, val rawPose: PoseTrack?, val previous: GaitMetrics?)

private val GoodColor = Color(0xFF2E7D32)
private val ImproveColor = Color(0xFFEF6C00)
private val LeftColor = Color(0xFF4FC3F7)
private val RightColor = Color(0xFFFFB74D)
private val LandingColor = Color(0xFFFFEB3B)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var data by remember { mutableStateOf<ResultData?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        val loaded = withContext(Dispatchers.IO) {
            val session = repo.get(id) ?: return@withContext null
            val previous = repo.list().firstOrNull { it.createdAt < session.createdAt && it.metrics != null }
            val raw = repo.loadPose(id)
            ResultData(session, raw?.let(PoseCleaner::clean), raw, previous?.metrics)
        }
        data = loaded
        notFound = loaded == null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your form") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (data != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete session")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val result = data
        when {
            result != null -> ResultContent(result, repo.videoFile(id), PaddingValues(top = padding.calculateTopPadding()))
            notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This session no longer exists.")
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("The video and results will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(id)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResultContent(data: ResultData, video: File, padding: PaddingValues) {
    val session = data.session
    val metrics = session.metrics
    val feedback = remember(session) {
        metrics?.let { Coach.review(it, session.speedKmh, data.previous) }.orEmpty()
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            VideoReplay(
                video = video,
                aspectRatio = session.videoWidth.toFloat() / session.videoHeight.coerceAtLeast(1),
                playbackFps = session.playbackFps,
                pose = data.pose,
                rawPose = data.rawPose,
                contactFrames = metrics?.contactFrames.orEmpty(),
            )
        }
        item { Summary(session, feedback) }
        metrics?.estimatedHeightCm?.let { item { HeightCard(it, fromWatch = session.speedFromSensor) } }
        items(feedback) { FeedbackCard(it) }
        if (session.warnings.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("About this recording", style = MaterialTheme.typography.titleSmall)
                        session.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        item {
            Text(
                "These are coaching suggestions based on video, not medical advice. " +
                    "If something hurts, see a physiotherapist or doctor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Summary(session: Session, feedback: List<Feedback>) {
    val failure = session.failure
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (failure != null) {
                Text("Couldn't analyse this clip", style = MaterialTheme.typography.titleMedium)
                Text(failure)
                FilmingTips()
                return@Column
            }
            val focus = Coach.mainFocus(feedback)
            if (focus != null) {
                Text("Main focus: ${focus.topic.lowercase()}", style = MaterialTheme.typography.titleMedium)
                focus.cue?.let { Text("Think: \"$it\"") }
            } else {
                Text("Nice running: nothing major to fix", style = MaterialTheme.typography.titleMedium)
            }
            val details = listOfNotNull(
                session.metrics?.let { "${it.stepCount} steps analysed" },
                session.speedKmh?.let { "${formatSpeed(it)} km/h" + if (session.speedFromSensor) " (from watch)" else "" },
                "${session.fps.roundToInt()} fps",
                session.processingSec?.let {
                    "analysed in %.1f s".format(it) + if (session.analysedLive) " (live)" else ""
                },
            )
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.sensor?.let { sensor ->
                val watch = listOfNotNull(
                    sensor.heartRate?.let { "♥ $it" },
                    sensor.cadenceSpm?.let { "${it.roundToInt()} steps/min" },
                ).joinToString(" · ")
                if (watch.isNotEmpty()) {
                    Text(
                        "Watch during clip: $watch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The height measured from the treadmill belt, with an offer to save it when it differs from Settings. */
@Composable
private fun HeightCard(estimatedCm: Double, fromWatch: Boolean) {
    val context = LocalContext.current
    val settings = remember { CoachSettings(context) }
    var savedCm by remember { mutableStateOf(settings.runnerHeightCm) }
    val estimate = estimatedCm.roundToInt()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estimated height: $estimate cm", style = MaterialTheme.typography.titleMedium)
            Text(
                "Measured from how fast your planted foot moves with the treadmill belt" +
                    if (fromWatch) ", using your watch's pace." else ".",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (abs(estimate - savedCm.roundToInt()) >= 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Settings: ${savedCm.roundToInt()} cm",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        settings.runnerHeightCm = estimate.toDouble()
                        savedCm = estimate.toDouble()
                    }) { Text("Use $estimate cm") }
                }
            } else {
                Text(
                    "Matches your height in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard(item: Feedback) {
    val (icon, tint) = when (item.status) {
        FeedbackStatus.GOOD -> Icons.Default.CheckCircle to GoodColor
        FeedbackStatus.IMPROVE -> Icons.Default.Warning to ImproveColor
        FeedbackStatus.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = item.status.name.lowercase(), tint = tint)
                Spacer(Modifier.width(8.dp))
                Text(item.topic, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(item.value, style = MaterialTheme.typography.labelLarge)
            }
            Text(item.message, style = MaterialTheme.typography.bodyMedium)
            item.cue?.let { Text("Cue: \"$it\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            item.drill?.let { Text("Drill: $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoReplay(
    video: File,
    aspectRatio: Float,
    playbackFps: Float,
    pose: PoseTrack?,
    rawPose: PoseTrack?,
    contactFrames: List<Int>,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var speed by remember { mutableFloatStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }
    var showSkeleton by remember { mutableStateOf(true) }
    var showRaw by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    LaunchedEffect(player) {
        while (true) withFrameMillis { positionMs = player.currentPosition }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            Box(Modifier.heightIn(max = 460.dp).aspectRatio(aspectRatio)) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (showSkeleton && pose != null) {
                    val frame = frameAt(positionMs, playbackFps)
                    SkeletonOverlay(if (showRaw && rawPose != null) rawPose else pose, frame, contactFrames, playbackFps, Modifier.fillMaxSize())
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            listOf(1f to "1×", 0.5f to "½×", 0.25f to "¼×").forEach { (value, label) ->
                FilterChip(selected = speed == value, onClick = { speed = value }, label = { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            Switch(checked = showSkeleton, onCheckedChange = { showSkeleton = it })
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val frame = frameAt(positionMs, playbackFps)
            fun step(by: Int) {
                playing = false
                player.playWhenReady = false
                player.seekTo(frameStartMs((frame + by).coerceAtLeast(0), playbackFps))
            }
            OutlinedButton(onClick = { step(-1) }) { Text("‹ Frame") }
            OutlinedButton(onClick = { step(1) }) { Text("Frame ›") }
            if (rawPose != null) {
                // Shows exactly what the detector found, before swaps, jumps and jitter are cleaned up.
                FilterChip(selected = showRaw, onClick = { showRaw = !showRaw }, label = { Text("Raw") })
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Frame $frame · %.2f s".format(positionMs / 1000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkeletonOverlay(
    track: PoseTrack,
    frame: Int,
    contactFrames: List<Int>,
    playbackFps: Float,
    modifier: Modifier,
) {
    val landingWindow = (playbackFps * 0.05f).roundToInt().coerceAtLeast(1)
    Canvas(modifier) {
        val pose = track.frames.getOrNull(frame) ?: return@Canvas
        fun point(i: Int) = Offset(pose.x[i] * size.width, pose.y[i] * size.height)
        val stroke = 3.dp.toPx()
        fun limb(color: Color, a: Int, b: Int) {
            if (pose.x[a].isNaN() || pose.x[b].isNaN()) return
            // Points the detector wasn't sure of (often the far leg, hidden behind the near one) are drawn faintly.
            val sure = pose.visibility[a] >= SURE_VISIBILITY && pose.visibility[b] >= SURE_VISIBILITY
            drawLine(if (sure) color else color.copy(alpha = 0.35f), point(a), point(b), stroke, StrokeCap.Round)
        }
        Landmark.LEFT_CONNECTIONS.forEach { (a, b) -> limb(LeftColor, a, b) }
        Landmark.RIGHT_CONNECTIONS.forEach { (a, b) -> limb(RightColor, a, b) }
        Landmark.CENTER_CONNECTIONS.forEach { (a, b) -> limb(Color.White, a, b) }
        if (!pose.x[Landmark.NOSE].isNaN()) drawCircle(Color.White, radius = 5.dp.toPx(), center = point(Landmark.NOSE))

        // Ring the landing foot for a moment at each detected foot strike.
        if (contactFrames.any { abs(it - frame) <= landingWindow }) {
            val heel = if (pose.y[Landmark.LEFT_HEEL] >= pose.y[Landmark.RIGHT_HEEL]) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE
            if (!pose.x[heel].isNaN()) drawCircle(LandingColor, radius = 14.dp.toPx(), center = point(heel), style = Stroke(stroke))
        }
    }
}

private const val SURE_VISIBILITY = 0.5f

/**
 * The pose frame showing at [positionMs]. The player reports whole milliseconds, rounded down, so a
 * frame starting at 16.7 ms shows as 16 ms; the extra millisecond keeps that on the right frame.
 */
private fun frameAt(positionMs: Long, fps: Float) = ((positionMs + 1) * fps / 1000.0).toInt()

/** A position just inside frame [frame], for stepping one frame at a time. */
private fun frameStartMs(frame: Int, fps: Float) = kotlin.math.ceil(frame * 1000.0 / fps).toLong()
