package com.atsukio.runningcoach.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import com.atsukio.runningcoach.share.ResultSharer
import kotlinx.coroutines.launch
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import com.atsukio.runningcoach.analysis.measurementKey
import com.atsukio.runningcoach.analysis.FocusPlan
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.PersonalBests
import com.atsukio.runningcoach.analysis.GhostAligner
import com.atsukio.runningcoach.analysis.EarlierClip
import com.atsukio.runningcoach.analysis.Badge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
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
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseCleaner
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionBuilder
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.recalculate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** [pose] is cleaned for display and analysis; [rawPose] is exactly what the detector found. */
private class ResultData(
    val session: Session,
    val pose: PoseTrack?,
    val rawPose: PoseTrack?,
    val previous: GaitMetrics?,
    /** Milestones against every earlier clip, keyed by coaching topic. */
    val badges: Map<String, Badge> = emptyMap(),
    /** The previous clip's skeleton lined up with this one, when both have enough landings. */
    val ghost: GhostAligner? = null,
)

private val LeftColor = Color(0xFF4FC3F7)
private val RightColor = Color(0xFFFFB74D)
private val LandingColor = Color(0xFFFFEB3B)
private val GhostColor = Color(0xFF8BE9C1)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(id: String, onBack: () -> Unit, onReanalyse: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var data by remember { mutableStateOf<ResultData?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    // -1 until the person steps through landings: then the middle one shows.
    var landingIndex by rememberSaveable { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    // Bumped after the speed or height is changed, to load the new measurements.
    var reloads by remember { mutableIntStateOf(0) }

    LaunchedEffect(id, reloads) {
        val loaded = withContext(Dispatchers.IO) {
            val session = repo.get(id) ?: return@withContext null
            // Newest first, so the first earlier clip is the one just before this.
            val earlier = repo.list().filter { it.createdAt < session.createdAt && it.metrics != null }
            val previous = earlier.firstOrNull()
            val raw = repo.loadPose(id)
            val cleaned = raw?.let(PoseCleaner::clean)
            val badges = session.metrics?.let { metrics ->
                PersonalBests.badges(metrics, session.speedKmh, earlier.map { EarlierClip(it.metrics!!, it.speedKmh) })
            }.orEmpty().associateBy { it.topic }
            val ghost = previous?.let { prev ->
                val previousPose = repo.loadPose(prev.id)?.let(PoseCleaner::clean)
                val here = session.metrics?.contactFrames
                val there = prev.metrics?.contactFrames
                if (cleaned != null && previousPose != null && here != null && there != null) {
                    GhostAligner.create(cleaned, here, previousPose, there)
                } else {
                    null
                }
            }
            ResultData(session, cleaned, raw, previous?.metrics, badges, ghost)
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
                    if (data?.session?.metrics != null) {
                        IconButton(onClick = { shareOpen = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share results")
                        }
                    }
                    if (data != null) {
                        // Runs the clip again with the current version and settings, keeping its date.
                        IconButton(onClick = onReanalyse) {
                            Icon(Icons.Default.Refresh, contentDescription = "Analyse again")
                        }
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
            result != null -> ResultContent(
                result,
                repo.videoFile(id),
                PaddingValues(top = padding.calculateTopPadding()),
                landingIndex = shownLanding(result, landingIndex),
                onLandingIndex = { landingIndex = it },
                onRecalculated = { reloads++ },
            )
            notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This session no longer exists.")
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    val shareData = data
    if (shareOpen && shareData != null) {
        val video = repo.videoFile(id)
        val landing = shownLanding(shareData, landingIndex)
        ShareDialog(
            landingNumber = landing.takeIf {
                video.exists() && shareData.session.metrics?.contactFrames?.isNotEmpty() == true
            }?.plus(1),
            // Null when the video has been removed to save space.
            videoMb = video.takeIf { it.exists() }?.let { it.length() / 1_048_576.0 },
            busy = sharing,
            onDismiss = { if (!sharing) shareOpen = false },
            onShare = { withLanding, withVideo ->
                sharing = true
                scope.launch {
                    val session = shareData.session
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val feedback = Coach.review(session.metrics!!, session.speedKmh, shareData.previous)
                            ResultSharer.prepare(
                                context, session, feedback, shareData.pose, video,
                                ResultSharer.Options(landing.takeIf { withLanding }, withVideo),
                            )
                        }
                    }
                    sharing = false
                    shareOpen = false
                    result
                        .onSuccess { context.startActivity(it) }
                        .onFailure { Toast.makeText(context, "Couldn't prepare the share: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            },
        )
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

/** The landing showing on the landing card: the one picked, or the middle one. */
private fun shownLanding(data: ResultData, picked: Int): Int {
    val count = data.session.metrics?.contactFrames?.size ?: 0
    return if (picked in 0 until count) picked else count / 2
}

/** Choose what to send with the summary image, then open the share sheet. */
@Composable
private fun ShareDialog(
    landingNumber: Int?,
    videoMb: Double?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onShare: (withLanding: Boolean, withVideo: Boolean) -> Unit,
) {
    var withLanding by rememberSaveable { mutableStateOf(landingNumber != null) }
    var withVideo by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share your results") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sends a summary image of your measurements, with a text version for apps that show one.")
                if (landingNumber != null) {
                    ShareOption(
                        "A landing from the video",
                        "Landing $landingNumber, the one on the landing card, with its measurements drawn on",
                        withLanding,
                        enabled = !busy,
                    ) { withLanding = it }
                }
                if (videoMb == null) {
                    ShareOption("The video clip", "Removed to save space", false, enabled = false) {}
                } else {
                    ShareOption("The video clip", "%.0f MB".format(videoMb.coerceAtLeast(1.0)), withVideo, enabled = !busy) { withVideo = it }
                }
                Text(
                    "You pick the app it goes to. Stride Lab itself never uploads anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(withLanding && landingNumber != null, withVideo) }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Share")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun ShareOption(title: String, detail: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultContent(
    data: ResultData,
    video: File,
    padding: PaddingValues,
    landingIndex: Int,
    onLandingIndex: (Int) -> Unit,
    onRecalculated: () -> Unit,
) {
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
        val hasVideo = video.exists()
        if (hasVideo) {
            item {
                VideoReplay(
                    video = video,
                    aspectRatio = session.videoWidth.toFloat() / session.videoHeight.coerceAtLeast(1),
                    playbackFps = session.playbackFps,
                    pose = data.pose,
                    rawPose = data.rawPose,
                    contactFrames = metrics?.contactFrames.orEmpty(),
                    ghost = data.ghost,
                )
            }
        } else {
            item {
                LabCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        "The video was removed to save space. The results are kept.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            val context = LocalContext.current
            val settings = remember { CoachSettings(context) }
            var plan by remember { mutableStateOf(settings.focusPlan) }
            Summary(session, feedback, plan) { focus ->
                val started = FocusPlan.from(focus, System.currentTimeMillis())
                settings.focusPlan = started
                plan = started
            }
        }
        if (hasVideo && metrics != null && data.pose != null && metrics.contactFrames.isNotEmpty()) {
            item {
                LandingCard(
                    video = video,
                    track = data.pose,
                    metrics = metrics,
                    aspectRatio = session.videoWidth.toFloat() / session.videoHeight.coerceAtLeast(1),
                    playbackFps = session.playbackFps,
                    index = landingIndex,
                    onIndex = onLandingIndex,
                )
            }
        }
        if (metrics != null && data.pose != null) {
            item { ScaleCard(session, metrics.estimatedHeightCm, onRecalculated) }
        }
        items(feedback) { FeedbackCard(it, data.badges[measurementKey(it.topic)]) }
        if (session.warnings.isNotEmpty()) {
            item {
                LabCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
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
private fun Summary(session: Session, feedback: List<Feedback>, plan: FocusPlan?, onStartPlan: (Feedback) -> Unit) {
    val failure = session.failure
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (failure != null) {
                Text("Couldn't analyse this clip", style = MaterialTheme.typography.titleMedium)
                Text(failure)
                FilmingTips()
                return@Column
            }
            val focus = Coach.mainFocus(feedback)
            if (focus != null) {
                SectionLabel("Main focus", colour = signalColour(false))
                Text(focus.topic, style = MaterialTheme.typography.headlineSmall)
                focus.cue?.let {
                    Text("Think: \"$it\"", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (plan != null && measurementKey(plan.topic) == measurementKey(focus.topic)) {
                    SectionLabel("In your focus plan", colour = signalColour(true))
                } else {
                    OutlinedButton(onClick = { onStartPlan(focus) }) {
                        Text(if (plan == null) "Start a 3-week plan for this" else "Switch my plan to this")
                    }
                }
            } else {
                SectionLabel("All clear", colour = signalColour(true))
                Text("Nice running: nothing major to fix", style = MaterialTheme.typography.headlineSmall)
            }
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.metrics?.let { StatChip("${it.stepCount}", "steps", countUp = true) }
                session.speedKmh?.let { StatChip(formatSpeed(it), if (session.speedFromSensor) "km/h watch" else "km/h") }
                StatChip("${session.fps.roundToInt()}", "fps")
            }
            session.processingSec?.let {
                Text(
                    "Analysed in %.1f s".format(it) + if (session.analysedLive) " while recording" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/**
 * The treadmill speed and height used to turn pixels into centimetres for this clip. Both can be changed
 * here for this clip alone; the saved poses are measured again, so the new figures show straight away.
 */
@Composable
private fun ScaleCard(session: Session, estimatedCm: Double?, onRecalculated: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CoachSettings(context) }
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    var savedCm by remember { mutableStateOf(settings.runnerHeightCm) }
    var speedText by remember(session.speedKmh) { mutableStateOf(session.speedKmh?.let(::formatSpeed).orEmpty()) }
    var heightText by remember(session.runnerHeightCm) { mutableStateOf(session.runnerHeightCm.roundToInt().toString()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val speedBlank = speedText.isBlank()
    val speed = speedText.replace(',', '.').trim().toDoubleOrNull()
    val speedOk = speedBlank || (speed != null && SessionBuilder.validSpeed(speed))
    val newSpeed = if (speedBlank) null else speed
    val height = heightText.toIntOrNull()?.toDouble()
    val heightOk = height != null && SessionBuilder.validHeight(height)
    val speedChanged = (newSpeed == null) != (session.speedKmh == null) ||
        (newSpeed != null && session.speedKmh != null && abs(newSpeed - session.speedKmh) > 0.005)
    val heightChanged = height != null && height.roundToInt() != session.runnerHeightCm.roundToInt()
    val canRecalculate = speedOk && heightOk && (speedChanged || heightChanged) && !busy

    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Scale for this clip")
            if (estimatedCm != null) {
                val estimate = estimatedCm.roundToInt()
                Text("From the belt speed you look about $estimate cm tall.", style = MaterialTheme.typography.titleMedium)
                val gap = abs(estimatedCm - session.runnerHeightCm) / session.runnerHeightCm
                val typed = session.speedKmh
                if (gap > GaitAnalyzer.SPEED_MISMATCH && typed != null) {
                    // Far off: the speed is the likelier mistake, so offer the speed that fits first.
                    val fitting = GaitAnalyzer.fittingSpeedKmh(typed, session.runnerHeightCm, estimatedCm)
                    Text(
                        "That doesn't fit your ${session.runnerHeightCm.roundToInt()} cm, so the treadmill was probably " +
                            "running at about ${"%.1f".format(fitting)} km/h, not ${formatSpeed(typed)}. Enter what it was set to.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { speedText = "%.1f".format(fitting) }) { Text("Try ${"%.1f".format(fitting)} km/h") }
                        TextButton(onClick = { heightText = estimate.toString() }) { Text("Use $estimate cm") }
                    }
                } else if (abs(estimate - session.runnerHeightCm.roundToInt()) >= 8) {
                    Text(
                        "That is a little way from ${session.runnerHeightCm.roundToInt()} cm, so check the speed you entered.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { heightText = estimate.toString() }) { Text("Use $estimate cm for this clip") }
                }
            } else {
                Text(
                    if (session.speedKmh == null) "No treadmill speed, so distances come from the height below."
                    else "The treadmill speed doesn't match the video, so distances come from the height below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { speedText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5); failed = false },
                    label = { Text("Speed (km/h)") },
                    singleLine = true,
                    isError = !speedOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit).take(3); failed = false },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    isError = !heightOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "With a speed, distances come from the belt and height is only a check. Leave the speed blank to use height alone. " +
                    "Speed 3 to 30 km/h, height 120 to 220 cm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.scaleEdited) {
                Text(
                    "Changed by hand for this clip. Analysing it again keeps these.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed) {
                Text("Couldn't recalculate this clip.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { repo.recalculate(session.id, newSpeed, height!!) }.getOrNull()
                            }
                            busy = false
                            if (result == null) failed = true else onRecalculated()
                        }
                    },
                    enabled = canRecalculate,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Recalculate")
                }
                if (heightOk && height!!.roundToInt() != savedCm.roundToInt()) {
                    TextButton(onClick = {
                        settings.runnerHeightCm = height
                        savedCm = height
                    }) { Text("Make ${height.roundToInt()} cm my default") }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(item: Feedback, badge: Badge?) {
    val good = signalColour(true)
    val (statusText, tint) = when (item.status) {
        FeedbackStatus.GOOD -> "In range" to good
        FeedbackStatus.IMPROVE -> "To work on" to signalColour(false)
        FeedbackStatus.INFO -> "Note" to signalColour(null)
    }
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.topic, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(statusText, colour = tint)
                        badge?.let { BadgePill(it) }
                    }
                }
                // Long values ("14° at landing, 38° max") are set smaller and wrap, so they don't squeeze the title.
                CountUpText(
                    item.value,
                    style = monoValue(if (item.value.length <= 8) 18 else 14),
                    colour = tint,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 190.dp),
                )
            }
            Text(item.message, style = MaterialTheme.typography.bodyMedium)
            item.cue?.let { Text("Cue: \"$it\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
            item.drill?.let { Text("Drill: $it", style = MaterialTheme.typography.bodyMedium) }
            item.scale?.let { RangeBar(it, markerColor = tint, goodColor = good) }
            item.metronomeSpm?.let { spm ->
                val context = LocalContext.current
                var set by remember { mutableStateOf(false) }
                TextButton(onClick = {
                    CoachSettings(context).metronomeSpm = spm
                    set = true
                }, enabled = !set) {
                    Text(if (set) "Metronome set to $spm: find it on the camera and run screens" else "Set the metronome to $spm")
                }
            }
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
    ghost: GhostAligner?,
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
    var showGhost by remember { mutableStateOf(false) }
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
                    SkeletonOverlay(
                        if (showRaw && rawPose != null) rawPose else pose,
                        frame,
                        contactFrames,
                        playbackFps,
                        Modifier.fillMaxSize(),
                        ghost = if (showGhost) ghost?.frameAt(frame) else null,
                    )
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
            if (ghost != null) {
                // The previous clip's skeleton in the same step of the stride, so differences in form show up.
                FilterChip(selected = showGhost, onClick = { showGhost = !showGhost }, label = { Text("Last time") })
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
    ghost: FramePose? = null,
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
        ghost?.let { earlier ->
            // Dashed, in mint, behind the current skeleton.
            val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
            fun ghostPoint(i: Int) = Offset(earlier.x[i] * size.width, earlier.y[i] * size.height)
            val links = Landmark.LEFT_CONNECTIONS + Landmark.RIGHT_CONNECTIONS + Landmark.CENTER_CONNECTIONS
            links.forEach { (a, b) ->
                if (!earlier.x[a].isNaN() && !earlier.x[b].isNaN()) {
                    drawLine(GhostColor, ghostPoint(a), ghostPoint(b), stroke, StrokeCap.Round, pathEffect = dash)
                }
            }
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

/** A small milestone tag next to a measurement, e.g. "First time in range". */
@Composable
private fun BadgePill(badge: Badge) {
    val colour = signalColour(true)
    Text(
        "\u2605 " + badge.kind.label,
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        modifier = Modifier
            .border(1.dp, colour.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * The pose frame showing at [positionMs]. The player reports whole milliseconds, rounded down, so a
 * frame starting at 16.7 ms shows as 16 ms; the extra millisecond keeps that on the right frame.
 */
private fun frameAt(positionMs: Long, fps: Float) = ((positionMs + 1) * fps / 1000.0).toInt()

/** A position just inside frame [frame], for stepping one frame at a time. */
private fun frameStartMs(frame: Int, fps: Float) = kotlin.math.ceil(frame * 1000.0 / fps).toLong()
