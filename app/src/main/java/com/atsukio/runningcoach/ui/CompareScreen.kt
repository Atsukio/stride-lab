package com.atsukio.runningcoach.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import com.atsukio.runningcoach.analysis.FootStrikeType
import kotlin.math.abs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.atsukio.runningcoach.analysis.ComparedMetric
import com.atsukio.runningcoach.analysis.GhostAligner
import com.atsukio.runningcoach.analysis.MetricComparison
import com.atsukio.runningcoach.analysis.PoseCleaner
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.analysis.StrideWindow
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** One clip on the compare screen: its results, its cleaned poses and where its video is. */
private class ClipData(val session: Session, val pose: PoseTrack?, val video: File) {
    val hasVideo get() = video.exists()
    val contacts get() = session.metrics?.contactFrames.orEmpty()
}

private fun loadClip(repo: SessionRepository, session: Session) =
    ClipData(session, repo.loadPose(session.id)?.let(PoseCleaner::clean), repo.videoFile(session.id))

/**
 * Two clips side by side. The videos loop the same number of landings and play at matching speeds, so
 * both runners are at the same point of their stride together, with a table of the measurements below.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var first by remember { mutableStateOf<ClipData?>(null) }
    var others by remember { mutableStateOf<List<Session>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var otherId by rememberSaveable { mutableStateOf<String?>(null) }
    var second by remember { mutableStateOf<ClipData?>(null) }

    LaunchedEffect(id) {
        val (clip, list) = withContext(Dispatchers.IO) {
            val session = repo.get(id)
            // Newest first, so the default is the most recent other clip that has a video to show.
            val all = repo.list().filter { it.id != id && it.metrics != null }
            session?.let { loadClip(repo, it) } to all
        }
        first = clip
        others = list
        if (otherId == null) otherId = (list.firstOrNull { repo.hasVideo(it.id) && repo.hasPose(it.id) } ?: list.firstOrNull())?.id
        loaded = true
    }
    LaunchedEffect(otherId) {
        val session = others.firstOrNull { it.id == otherId }
        second = null
        if (session != null) second = withContext(Dispatchers.IO) { loadClip(repo, session) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare clips") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val a = first
        when {
            !loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            a == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This session no longer exists.")
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ClipPicker(others, otherId) { otherId = it }
                }
                val b = second
                if (others.isEmpty()) {
                    item {
                        Text(
                            "There's nothing to compare with yet. Analyse another clip first.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else if (b == null) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    item {
                        val canSync = a.hasVideo && b.hasVideo && a.pose != null && b.pose != null &&
                            StrideWindow.count(a.contacts) >= 1 && StrideWindow.count(b.contacts) >= 1
                        if (canSync) {
                            key(a.session.id, b.session.id) { SyncedPlayers(a, b) }
                        } else {
                            Text(
                                "Side-by-side video needs both videos and enough landings in each. The measurements are below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val metricsA = a.session.metrics
                    val metricsB = b.session.metrics
                    if (metricsA?.footStrikeSummary != null && metricsB?.footStrikeSummary != null) {
                        item { FootStrikeComparisonCard(metricsA, metricsB) }
                    }
                    val rows = MetricComparison.rows(a.session.metrics!!, b.session.metrics!!)
                    item { ComparisonTable(rows) }
                    item {
                        Text(
                            "Distances come from each clip's own scale (treadmill speed or height), so small differences " +
                                "can be the setup rather than your form. These are coaching figures, not medical advice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipPicker(sessions: List<Session>, selected: String?, onSelect: (String) -> Unit) {
    if (sessions.isEmpty()) return
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Compare this clip with")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sessions.forEach { session ->
                val speed = session.speedKmh?.let { " · %.1f km/h".format(it) }.orEmpty()
                FilterChip(
                    selected = session.id == selected,
                    onClick = { onSelect(session.id) },
                    label = { Text(format.format(Date(session.createdAt)) + speed) },
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SyncedPlayers(first: ClipData, second: ClipData) {
    val context = LocalContext.current
    val contactsA = first.contacts
    val contactsB = second.contacts
    val countA = StrideWindow.count(contactsA)
    val countB = StrideWindow.count(contactsB)
    val fpsA = first.session.playbackFps
    val fpsB = second.session.playbackFps

    var stride by rememberSaveable { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }
    var showSkeleton by remember { mutableStateOf(true) }
    val index = stride.coerceIn(0, countA - 1)
    val windowA = StrideWindow.at(contactsA, index) ?: return
    // The other clip cycles through its own strides if it has fewer.
    val windowB = StrideWindow.at(contactsB, index % countB) ?: return
    val startA = frameStartMs(windowA.startFrame, fpsA)
    val endA = frameStartMs(windowA.endFrame, fpsA)
    val startB = frameStartMs(windowB.startFrame, fpsB)
    val endB = frameStartMs(windowB.endFrame, fpsB)
    val speedB = StrideWindow.matchingSpeed(speed, windowA, fpsA, windowB, fpsB)

    fun newPlayer(file: File) = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        repeatMode = Player.REPEAT_MODE_OFF
        volume = 0f
        playWhenReady = false
        prepare()
    }
    val playerA = remember { newPlayer(first.video) }
    val playerB = remember { newPlayer(second.video) }
    DisposableEffect(Unit) {
        onDispose {
            playerA.release()
            playerB.release()
        }
    }
    val isPlaying by rememberUpdatedState(playing)

    // A new stride: both clips jump to its start.
    LaunchedEffect(index) {
        playerA.seekTo(startA)
        playerB.seekTo(startB)
        playerA.playWhenReady = isPlaying
        playerB.playWhenReady = isPlaying
    }
    LaunchedEffect(speed, index) {
        playerA.setPlaybackSpeed(speed)
        playerB.setPlaybackSpeed(speedB.coerceIn(0.05f, 8f))
    }
    LaunchedEffect(playing) {
        playerA.playWhenReady = playing
        playerB.playWhenReady = playing
    }
    var positionA by remember { mutableLongStateOf(startA) }
    var positionB by remember { mutableLongStateOf(startB) }
    LaunchedEffect(index, speed) {
        while (true) {
            withFrameMillis {
                positionA = playerA.currentPosition
                positionB = playerB.currentPosition
                if (isPlaying) {
                    if (positionA >= endA) {
                        // Back to the start together, so the runners never drift apart.
                        playerA.seekTo(startA)
                        playerB.seekTo(startB)
                        playerA.playWhenReady = true
                        playerB.playWhenReady = true
                    } else if (positionB >= endB) {
                        // The other clip finished its stride first: hold it for the end of this one.
                        playerB.playWhenReady = false
                    }
                }
            }
        }
    }

    val frameA = frameAt(positionA, fpsA)
    val frameB = frameAt(positionB, fpsB)
    val contactA = contactsA.minByOrNull { abs(it - frameA) }
    val isContactA = contactA != null && abs(contactA - frameA) <= (fpsA * 0.18f).roundToInt().coerceAtLeast(2)
    val activeStrikeA = if (isContactA && contactA != null) {
        first.session.metrics?.footStrikes?.getOrNull(contactsA.indexOf(contactA))
    } else null

    val contactB = contactsB.minByOrNull { abs(it - frameB) }
    val isContactB = contactB != null && abs(contactB - frameB) <= (fpsB * 0.18f).roundToInt().coerceAtLeast(2)
    val activeStrikeB = if (isContactB && contactB != null) {
        second.session.metrics?.footStrikes?.getOrNull(contactsB.indexOf(contactB))
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComparedVideo("This clip", first, playerA, frameA, showSkeleton, Modifier.weight(1f))
            ComparedVideo("Other clip", second, playerB, frameB, showSkeleton, Modifier.weight(1f))
        }
        val bothPoses = first.pose != null && second.pose != null
        if (bothPoses && GhostAligner.facing(first.pose!!) * GhostAligner.facing(second.pose!!) < 0) {
            Text(
                "The runner faces the other way in one clip. The stride is still lined up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val fraction = if (endA > startA) ((positionA - startA).toFloat() / (endA - startA)).coerceIn(0f, 1f) else 0f
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusDot(activeStrikeA == FootStrikeType.MIDFOOT)
                Text(
                    "This: ${activeStrikeA?.let(::strikeName) ?: "Flight"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = activeStrikeA?.let(::strikeColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (activeStrikeA != null) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Text(
                "Stride ${(fraction * 100).roundToInt()}%",
                style = monoValue(12),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Other: ${activeStrikeB?.let(::strikeName) ?: "Flight"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = activeStrikeB?.let(::strikeColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (activeStrikeB != null) FontWeight.SemiBold else FontWeight.Normal,
                )
                StatusDot(activeStrikeB == FootStrikeType.MIDFOOT)
            }
        }
        Slider(
            value = fraction,
            onValueChange = { f ->
                playing = false
                playerA.seekTo(startA + (f * (endA - startA)).toLong())
                playerB.seekTo(startB + (f * (endB - startB)).toLong())
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
            listOf(1f to "1×", 0.5f to "½×", 0.25f to "¼×").forEach { (value, label) ->
                FilterChip(selected = speed == value, onClick = { speed = value }, label = { Text(label) })
            }
            Box(Modifier.weight(1f))
            Switch(checked = showSkeleton, onCheckedChange = { showSkeleton = it })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { stride = (index - 1).coerceAtLeast(0) }, enabled = index > 0) { Text("‹ Stride") }
            OutlinedButton(onClick = { stride = (index + 1).coerceAtMost(countA - 1) }, enabled = index < countA - 1) { Text("Stride ›") }
            Box(Modifier.weight(1f))
            Text(
                "Stride ${index + 1} of $countA",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ComparedVideo(title: String, clip: ClipData, player: ExoPlayer, frame: Int, showSkeleton: Boolean, modifier: Modifier) {
    val aspect = clip.session.videoWidth.toFloat() / clip.session.videoHeight.coerceAtLeast(1)
    val activeContact = clip.contacts.minByOrNull { abs(it - frame) }
    val isAtContact = activeContact != null && abs(activeContact - frame) <= (clip.session.playbackFps * 0.18f).roundToInt().coerceAtLeast(2)
    val activeStrike = if (isAtContact && activeContact != null) {
        val contactIdx = clip.contacts.indexOf(activeContact)
        clip.session.metrics?.footStrikes?.getOrNull(contactIdx)
    } else null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(title)
            if (activeStrike != null) {
                val (name, col) = when (activeStrike) {
                    FootStrikeType.HEEL -> "Heel" to Signal.Info
                    FootStrikeType.MIDFOOT -> "Midfoot" to Signal.Good
                    FootStrikeType.FOREFOOT -> "Forefoot" to Signal.Improve
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(col.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = col, fontWeight = FontWeight.SemiBold)
                }
            } else {
                clip.session.metrics?.footStrikeSummary?.let { summary ->
                    Text(
                        " · $summary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            Box(Modifier.heightIn(max = 360.dp).aspectRatio(aspect)) {
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
                val pose = clip.pose
                if (showSkeleton && pose != null) {
                    SkeletonOverlay(pose, frame, clip.contacts, clip.session.playbackFps, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ComparisonTable(rows: List<ComparedMetric>) {
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                SectionLabel("Measurement", Modifier.weight(1.6f))
                listOf("This", "Other", "Change").forEach {
                    SectionLabel(it, Modifier.weight(1f), colour = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.6f)) {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium)
                        Text(row.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Cell(row.format(row.first), Modifier.weight(1f))
                    Cell(row.format(row.second), Modifier.weight(1f))
                    Cell(row.formatDifference(), Modifier.weight(1f), muted = true)
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, modifier: Modifier, muted: Boolean = false) {
    Text(
        text,
        modifier,
        style = monoValue(16),
        textAlign = TextAlign.Start,
        fontWeight = if (muted) FontWeight.Normal else FontWeight.Bold,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FootStrikeComparisonCard(a: com.atsukio.runningcoach.analysis.GaitMetrics, b: com.atsukio.runningcoach.analysis.GaitMetrics) {
    val summaryA = a.footStrikeSummary ?: return
    val summaryB = b.footStrikeSummary ?: return
    val heelA = (a.heelStrikeRatio ?: 0.0).toFloat()
    val heelB = (b.heelStrikeRatio ?: 0.0).toFloat()
    val midfootA = (a.midfootStrikeRatio ?: 0.0).toFloat()
    val midfootB = (b.midfootStrikeRatio ?: 0.0).toFloat()
    val heelDiff = ((heelA - heelB) * 100).roundToInt()
    val midfootDiff = ((midfootA - midfootB) * 100).roundToInt()

    val callout = when {
        heelDiff <= -10 -> "Heel striking decreased by ${-heelDiff}% in this clip — shifting towards a midfoot landing."
        heelDiff >= 10 -> "Heel striking increased by $heelDiff% in this clip."
        midfootDiff >= 10 -> "Midfoot striking increased by $midfootDiff% in this clip."
        else -> "Foot strike pattern remained consistent between both clips."
    }

    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Foot strike comparison", icon = LabIcons.FootStrike)
            Text(callout, style = MaterialTheme.typography.bodyMedium)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This clip", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(summaryA, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StrikeRatioBar(a)

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Other clip", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(summaryB, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StrikeRatioBar(b)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(Signal.Info, CircleShape))
                    Text("Heel", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(Signal.Good, CircleShape))
                    Text("Midfoot", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(Signal.Improve, CircleShape))
                    Text("Forefoot", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StrikeRatioBar(m: com.atsukio.runningcoach.analysis.GaitMetrics) {
    val heel = (m.heelStrikeRatio ?: 0.0).toFloat()
    val midfoot = (m.midfootStrikeRatio ?: 0.0).toFloat()
    val forefoot = (m.forefootStrikeRatio ?: 0.0).toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(MaterialTheme.shapes.small),
    ) {
        val w = size.width
        val h = size.height
        var startX = 0f
        if (heel > 0) {
            val segmentW = w * heel
            drawRect(Signal.Info, Offset(startX, 0f), Size(segmentW, h))
            startX += segmentW
        }
        if (midfoot > 0) {
            val segmentW = w * midfoot
            drawRect(Signal.Good, Offset(startX, 0f), Size(segmentW, h))
            startX += segmentW
        }
        if (forefoot > 0) {
            val segmentW = w * forefoot
            drawRect(Signal.Improve, Offset(startX, 0f), Size(segmentW, h))
        }
    }
}

private fun strikeName(type: FootStrikeType): String = when (type) {
    FootStrikeType.HEEL -> "Heel strike"
    FootStrikeType.MIDFOOT -> "Midfoot"
    FootStrikeType.FOREFOOT -> "Forefoot"
}

private fun strikeColor(type: FootStrikeType): Color = when (type) {
    FootStrikeType.HEEL -> Signal.Info
    FootStrikeType.MIDFOOT -> Signal.Good
    FootStrikeType.FOREFOOT -> Signal.Improve
}
