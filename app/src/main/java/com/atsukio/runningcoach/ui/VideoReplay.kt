package com.atsukio.runningcoach.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.GhostAligner
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val LeftColor = Color(0xFF4FC3F7)
private val RightColor = Color(0xFFFFB74D)
private val LandingColor = Color(0xFFFFEB3B)
private val GhostColor = Color(0xFF8BE9C1)

@OptIn(UnstableApi::class)
@Composable
internal fun VideoReplay(
    video: File,
    aspectRatio: Float,
    playbackFps: Float,
    pose: PoseTrack?,
    rawPose: PoseTrack?,
    contactFrames: List<Int>,
    ghost: GhostAligner?,
) {
    val context = LocalContext.current
    val firstContactMs = remember(contactFrames, playbackFps) {
        val f = contactFrames.firstOrNull() ?: 0
        frameStartMs(f, playbackFps)
    }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            if (firstContactMs > 0) seekTo(firstContactMs)
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var speed by remember { mutableFloatStateOf(0.5f) }
    var playing by remember { mutableStateOf(true) }
    // Stops the looping decoder while the app is in the background, and picks up again on return.
    val resumePlaying by androidx.compose.runtime.rememberUpdatedState(playing)
    LifecycleStartEffect(player) {
        player.playWhenReady = resumePlaying
        onStopOrDispose { player.playWhenReady = false }
    }
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
        val frame = frameAt(positionMs, playbackFps)
        // Wraps rather than squeezing: with both Raw and Last time showing, one row is too narrow on a phone,
        // and the frame label below used to be crushed to one letter per line.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
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
        }
        Text(
            "Frame $frame · %.2f s".format(positionMs / 1000.0),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
internal fun SkeletonOverlay(
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

        // Ring the landing foot for a moment at each detected foot strike with an impact pulse.
        val closestContact = contactFrames.minByOrNull { abs(it - frame) }
        if (closestContact != null) {
            val dist = abs(closestContact - frame)
            val window = (playbackFps * 0.18f).roundToInt().coerceAtLeast(3)
            if (dist <= window) {
                val frac = dist.toFloat() / window // 0.0 right at contact, up to 1.0
                // Side-on: determine which foot is landing using the front foot / lower foot
                val lAnkleX = pose.x[Landmark.LEFT_ANKLE]
                val rAnkleX = pose.x[Landmark.RIGHT_ANKLE]
                val noseAhead = pose.x[Landmark.NOSE] - (pose.x[Landmark.LEFT_EAR] + pose.x[Landmark.RIGHT_EAR]) / 2f
                val leanAhead = (pose.x[Landmark.LEFT_SHOULDER] + pose.x[Landmark.RIGHT_SHOULDER]) / 2f -
                    (pose.x[Landmark.LEFT_HIP] + pose.x[Landmark.RIGHT_HIP]) / 2f
                val facing = if (!noseAhead.isNaN() && abs(noseAhead) > 0.001f) kotlin.math.sign(noseAhead) else if (leanAhead < 0) -1f else 1f
                val isLeft = if (!lAnkleX.isNaN() && !rAnkleX.isNaN()) {
                    facing * lAnkleX >= facing * rAnkleX
                } else {
                    pose.y[Landmark.LEFT_HEEL] >= pose.y[Landmark.RIGHT_HEEL]
                }
                val heel = if (isLeft) Landmark.LEFT_HEEL else Landmark.RIGHT_HEEL
                val toe = if (isLeft) Landmark.LEFT_FOOT_INDEX else Landmark.RIGHT_FOOT_INDEX
                val ankle = if (isLeft) Landmark.LEFT_ANKLE else Landmark.RIGHT_ANKLE

                // Determine precise contact strike zone: heel strike, forefoot/toe strike, or midfoot
                val contactPoint = when {
                    !pose.y[heel].isNaN() && !pose.y[toe].isNaN() -> {
                        val heelY = pose.y[heel]
                        val toeY = pose.y[toe]
                        // Positive y is down towards the treadmill belt
                        if (heelY - toeY > 0.015f) {
                            // Heel strikes first
                            point(heel)
                        } else if (toeY - heelY > 0.015f) {
                            // Forefoot / toe strikes first
                            point(toe)
                        } else {
                            // Midfoot strike (halfway between heel and toe)
                            Offset((point(heel).x + point(toe).x) / 2f, (point(heel).y + point(toe).y) / 2f)
                        }
                    }
                    !pose.x[heel].isNaN() -> point(heel)
                    !pose.x[toe].isNaN() -> point(toe)
                    !pose.x[ankle].isNaN() -> point(ankle)
                    else -> null
                }

                if (contactPoint != null) {
                    val centre = contactPoint
                    val innerRadius = 10.dp.toPx()
                    val outerRadius = (16 + 28 * frac).dp.toPx()
                    val alpha = (1f - frac).coerceIn(0f, 1f)
                    // High-visibility ground impact flash & expanding ripple ring
                    drawCircle(LandingColor.copy(alpha = 0.45f * alpha), radius = innerRadius, center = centre)
                    drawCircle(LandingColor.copy(alpha = 1f * alpha), radius = innerRadius, center = centre, style = Stroke(stroke * 1.5f))
                    drawCircle(LandingColor.copy(alpha = 0.85f * alpha), radius = outerRadius, center = centre, style = Stroke(stroke))
                }
            }
        }
    }
}

private const val SURE_VISIBILITY = 0.5f

/**
 * The pose frame showing at [positionMs]. The player reports whole milliseconds, rounded down, so a
 * frame starting at 16.7 ms shows as 16 ms; the extra millisecond keeps that on the right frame.
 */
internal fun frameAt(positionMs: Long, fps: Float) = ((positionMs + 1) * fps / 1000.0).toInt()

/** A position just inside frame [frame], for stepping one frame at a time. */
internal fun frameStartMs(frame: Int, fps: Float) = kotlin.math.ceil(frame * 1000.0 / fps).toLong()
