package com.atsukio.runningcoach.ui

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.atsukio.runningcoach.analysis.Badge
import com.atsukio.runningcoach.analysis.CameraView
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.EarlierClip
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FeedbackStatus
import com.atsukio.runningcoach.analysis.FocusPlan
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.GhostAligner
import com.atsukio.runningcoach.analysis.PersonalBests
import com.atsukio.runningcoach.analysis.PoseCleaner
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.analysis.measurementKey
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.remeasureIfOutdated
import com.atsukio.runningcoach.share.ResultSharer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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


@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(id: String, onBack: () -> Unit, onReanalyse: () -> Unit, onCompare: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var data by remember { mutableStateOf<ResultData?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    // How far the shared video has got, 0..1, while one is being drawn.
    var shareProgress by remember { mutableStateOf<Float?>(null) }
    // -1 until the person steps through landings: then the middle one shows.
    var landingIndex by rememberSaveable { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    // Bumped after the speed or height is changed, to load the new measurements.
    var reloads by remember { mutableIntStateOf(0) }

    LaunchedEffect(id, reloads) {
        val loaded = withContext(Dispatchers.IO) {
            // Clips measured by older code are brought up to date from their saved poses.
            val session = repo.remeasureIfOutdated(repo.get(id) ?: return@withContext null)
            // Newest first, so the first earlier clip is the one just before this. Only clips filmed from the
            // same side are comparable: a rear clip measures different things from a side-on one.
            val view = session.metrics?.view
            val earlier = repo.list().filter { it.createdAt < session.createdAt && it.metrics != null && it.metrics.view == view }
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
                onCompare = onCompare,
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
            canOverlay = video.exists() && shareData.pose != null,
            busy = sharing,
            progress = shareProgress,
            onDismiss = { if (!sharing) shareOpen = false },
            onShare = { withLanding, withVideo, withOverlay, withReport ->
                sharing = true
                shareProgress = null
                scope.launch {
                    val session = shareData.session
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val feedback = Coach.review(session.metrics!!, session.speedKmh, shareData.previous)
                            ResultSharer.prepare(
                                context, session, feedback, shareData.pose, video,
                                ResultSharer.Options(landing.takeIf { withLanding }, withVideo, withOverlay, withReport),
                                onProgress = { shareProgress = it },
                            )
                        }
                    }
                    sharing = false
                    shareProgress = null
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
    canOverlay: Boolean,
    busy: Boolean,
    progress: Float?,
    onDismiss: () -> Unit,
    onShare: (withLanding: Boolean, withVideo: Boolean, withOverlay: Boolean, withReport: Boolean) -> Unit,
) {
    var withLanding by rememberSaveable { mutableStateOf(landingNumber != null) }
    var withVideo by rememberSaveable { mutableStateOf(false) }
    var withOverlay by rememberSaveable { mutableStateOf(false) }
    var withReport by rememberSaveable { mutableStateOf(false) }
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
                ShareOption("Written report (PDF)", "Every measurement and tip, with up to three landings", withReport, enabled = !busy) { withReport = it }
                if (videoMb == null) {
                    ShareOption("The video clip", "Removed to save space", false, enabled = false) {}
                } else {
                    ShareOption("The video clip", "%.0f MB".format(videoMb.coerceAtLeast(1.0)), withVideo, enabled = !busy) { withVideo = it }
                    ShareOption(
                        "The clip with your skeleton drawn on",
                        if (canOverlay) "Made on your phone, which takes about as long as the clip" else "Needs the analysed poses",
                        withOverlay && canOverlay,
                        enabled = !busy && canOverlay,
                    ) { withOverlay = it }
                }
                if (busy) {
                    Spacer(Modifier.height(4.dp))
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Text(
                    "You pick the app it goes to. Stride Lab itself never uploads anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShare(withLanding && landingNumber != null, withVideo, withOverlay && canOverlay, withReport) },
                enabled = !busy,
            ) {
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

private enum class FeedbackFilter { ALL, IMPROVE, GOOD }

@Composable
private fun ResultContent(
    data: ResultData,
    video: File,
    padding: PaddingValues,
    landingIndex: Int,
    onLandingIndex: (Int) -> Unit,
    onRecalculated: () -> Unit,
    onCompare: () -> Unit,
) {
    val session = data.session
    val metrics = session.metrics
    val feedback = remember(session) {
        metrics?.let { Coach.review(it, session.speedKmh, data.previous) }.orEmpty()
    }
    var feedbackFilter by rememberSaveable { mutableStateOf(FeedbackFilter.ALL) }
    val filteredFeedback = remember(feedback, feedbackFilter) {
        when (feedbackFilter) {
            FeedbackFilter.ALL -> feedback
            FeedbackFilter.IMPROVE -> feedback.filter { it.status == FeedbackStatus.IMPROVE }
            FeedbackFilter.GOOD -> feedback.filter { it.status == FeedbackStatus.GOOD }
        }
    }
    val context = LocalContext.current
    val settings = remember { CoachSettings(context) }
    var plan by remember { mutableStateOf(settings.focusPlan) }
    val onStartPlan: (Feedback) -> Unit = { focus ->
        val started = FocusPlan.from(focus, System.currentTimeMillis())
        settings.focusPlan = started
        plan = started
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
        if (hasVideo && metrics != null && data.pose != null && metrics.contactFrames.size >= 3) {
            item {
                FilledTonalButton(onClick = onCompare, modifier = Modifier.fillMaxWidth()) {
                    Text("Compare with another clip")
                }
            }
        }
        item {
            Summary(session, feedback, plan, onStartPlan)
        }
        if (hasVideo && metrics != null && data.pose != null && metrics.frontal != null) {
            item {
                StanceCard(
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
        if (hasVideo && metrics != null && data.pose != null && metrics.view == CameraView.SIDE && metrics.contactFrames.isNotEmpty()) {
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
        // Foot strike can't be judged from in front or behind.
        if (metrics != null && metrics.view == CameraView.SIDE) {
            val strikeFocus = feedback.firstOrNull { it.topic == "Foot strike" } ?: Feedback(
                topic = "Foot strike",
                value = metrics.footStrikeSummary ?: "Foot strike",
                status = FeedbackStatus.INFO,
                message = "Focus on landing lightly under your hips.",
                cue = "Land softly under your hips.",
                drill = "Barefoot strides on grass or quick metronome steps.",
            )
            item { FootStrikeCard(metrics, strikeFocus, plan, onStartPlan) }
        }
        if (metrics != null && data.pose != null) {
            item { ScaleCard(session, metrics.estimatedHeightCm, onRecalculated) }
        }
        if (feedback.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Form breakdown & coaching", icon = LabIcons.Biomechanics)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${feedback.size} metrics",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val improveCount = feedback.count { it.status == FeedbackStatus.IMPROVE }
                    val goodCount = feedback.count { it.status == FeedbackStatus.GOOD }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = feedbackFilter == FeedbackFilter.ALL,
                            onClick = { feedbackFilter = FeedbackFilter.ALL },
                            label = { Text("All (${feedback.size})") },
                        )
                        if (improveCount > 0) {
                            FilterChip(
                                selected = feedbackFilter == FeedbackFilter.IMPROVE,
                                onClick = { feedbackFilter = FeedbackFilter.IMPROVE },
                                leadingIcon = { StatusDot(good = false) },
                                label = { Text("To work on ($improveCount)") },
                            )
                        }
                        if (goodCount > 0) {
                            FilterChip(
                                selected = feedbackFilter == FeedbackFilter.GOOD,
                                onClick = { feedbackFilter = FeedbackFilter.GOOD },
                                leadingIcon = { StatusDot(good = true) },
                                label = { Text("In range ($goodCount)") },
                            )
                        }
                    }
                }
            }
        }
        items(filteredFeedback) { FeedbackCard(it, data.badges[measurementKey(it.topic)]) }
        if (session.warnings.isNotEmpty()) {
            item {
                LabCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("About this recording", icon = Icons.Default.Info)
                        session.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                )
                Text(
                    "These are coaching suggestions based on video, not medical advice. " +
                        "If something hurts, see a physiotherapist or doctor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
