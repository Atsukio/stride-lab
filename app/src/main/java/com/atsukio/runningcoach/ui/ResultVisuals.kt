package com.atsukio.runningcoach.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.atsukio.runningcoach.analysis.FootStrikeType
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.LandingAnnotation
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.analysis.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val OverstrideColor = Color(0xFFFFEB3B)
private val ShinColor = Color(0xFFFFB74D)
private val KneeColor = Color(0xFF4FC3F7)
private val TargetMintColor = Color(0xFF8BE9C1)

/** Visual mode for landing inspection: actual tracked form, ideal target form, or both overlaid. */
enum class LandingVisualMode(val label: String) {
    TRACKED("Tracked"),
    TARGET("Target"),
    OVERLAY("Overlay"),
}

/** A bar showing where a measurement sits against its good range. */
@Composable
fun RangeBar(scale: Scale, markerColor: Color, goodColor: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.surface
    val range = goodRangeText(scale)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .semantics { contentDescription = "${scale.value.roundToInt()} ${scale.unit}. $range" },
        ) {
            val span = scale.max - scale.min
            fun x(v: Double) = (((v - scale.min) / span).coerceIn(0.0, 1.0) * size.width).toFloat()
            val barHeight = 6.dp.toPx()
            val top = (size.height - barHeight) / 2
            val radius = CornerRadius(barHeight / 2)
            drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
            drawRoundRect(
                goodColor.copy(alpha = 0.55f),
                Offset(x(scale.goodLow), top),
                Size(x(scale.goodHigh) - x(scale.goodLow), barHeight),
                radius,
            )
            val centre = Offset(x(scale.value), size.height / 2)
            drawCircle(outline, radius = 8.dp.toPx(), center = centre)
            drawCircle(markerColor, radius = 6.dp.toPx(), center = centre)
        }
        Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun goodRangeText(scale: Scale): String {
    fun n(v: Double) = v.roundToInt().toString()
    val unit = if (scale.unit.startsWith("°")) scale.unit else " ${scale.unit}"
    return when {
        scale.goodHigh >= scale.max -> "Good: ${n(scale.goodLow)}$unit or more"
        scale.goodLow <= scale.min -> "Good: up to ${n(scale.goodHigh)}$unit"
        else -> "Good: ${n(scale.goodLow)}–${n(scale.goodHigh)}$unit"
    }
}

/**
 * A still of one foot landing with the measurements drawn on it: how far ahead of the hips the foot
 * lands, the shin angle, the knee bend and the lean. Steps through every landing in the clip;
 * [index] is the one showing, held by the caller so sharing can use the same landing.
 */
@Composable
fun LandingCard(
    video: File,
    track: PoseTrack,
    metrics: GaitMetrics,
    aspectRatio: Float,
    playbackFps: Float,
    index: Int,
    onIndex: (Int) -> Unit,
) {
    val contacts = metrics.contactFrames
    if (contacts.isEmpty()) return
    val frame = contacts[index.coerceIn(contacts.indices)]
    val annotation = remember(frame) { landingAnnotation(track, metrics, frame) }
    val legCm = remember(metrics) {
        metrics.overstrideCm?.let { cm -> metrics.overstrideLegRatio?.takeIf { abs(it) > 1e-6 }?.let { cm / it } }
    }
    var mode by rememberSaveable { mutableStateOf(LandingVisualMode.TRACKED) }
    var targetStrike by rememberSaveable { mutableStateOf(FootStrikeType.MIDFOOT) }
    val idealAnnotation = remember(annotation, legCm, targetStrike) {
        annotation?.ideal(legCm = legCm, targetStrike = targetStrike)
    }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frame) {
        image = null
        image = withContext(Dispatchers.IO) { videoFrame(video, frame / playbackFps.toDouble(), aspectRatio) }
    }

    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The label sits above the chips: on one row, a phone-width card squeezed "Overlay" onto two lines.
            SectionLabel("At landing", icon = LabIcons.Landing)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LandingVisualMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    )
                }
            }
            if (mode != LandingVisualMode.TRACKED) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, TargetMintColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(LabIcons.FootStrike, contentDescription = null, tint = TargetMintColor, modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TARGET STRIKE OVERLAY", style = MaterialTheme.typography.labelSmall, color = TargetMintColor)
                        Text(
                            if (targetStrike == FootStrikeType.MIDFOOT) "Projected midfoot landing under hips" else "Projected forefoot landing under hips",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    listOf(FootStrikeType.MIDFOOT to "Midfoot", FootStrikeType.FOREFOOT to "Forefoot").forEach { (type, label) ->
                        FilterChip(
                            selected = targetStrike == type,
                            onClick = { targetStrike = type },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .heightIn(max = 420.dp)
                        .aspectRatio(aspectRatio)
                        .semantics { contentDescription = annotation?.let(::describe) ?: "Landing frame" },
                ) {
                    val still = image
                    if (still != null) {
                        Image(still, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    if (annotation != null && idealAnnotation != null) {
                        LandingOverlay(annotation, idealAnnotation, mode, Modifier.fillMaxSize())
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIndex(index - 1) }, enabled = index > 0) { Text("‹ Previous") }
                Spacer(Modifier.weight(1f))
                Text("Landing ${index + 1} of ${contacts.size}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onIndex(index + 1) }, enabled = index < contacts.lastIndex) { Text("Next ›") }
            }
            val showing = if (mode == LandingVisualMode.TARGET) idealAnnotation else annotation
            showing?.let { a ->
                if (mode == LandingVisualMode.TARGET) {
                    Legend(TargetMintColor, "Target: ${targetStrike.name.lowercase()} landing close under hips")
                } else if (mode == LandingVisualMode.OVERLAY) {
                    Legend(TargetMintColor, "Dashed mint: target ${targetStrike.name.lowercase()} landing under hips")
                }
                a.overstrideCm?.let {
                    Legend(
                        if (mode == LandingVisualMode.TARGET) TargetMintColor else OverstrideColor,
                        if (it > 0) "Foot lands ${it.roundToInt()} cm ahead of your hips" else "Foot lands under your hips",
                    )
                }
                Legend(ShinColor, "Shin ${abs(a.shinDeg).roundToInt()}° from vertical" + if (a.shinDeg < 0) ", foot behind the knee" else "")
                Legend(KneeColor, "Knee bent ${a.kneeFlexionDeg.roundToInt()}°")
                Legend(
                    MaterialTheme.colorScheme.onSurface,
                    "Leaning ${abs(a.leanDeg).roundToInt()}° " + if (a.leanDeg >= 0) "forward" else "back",
                )
                a.strike?.let { strike ->
                    val (label, col) = when (strike) {
                        FootStrikeType.HEEL -> "Heel strike" to Signal.Info
                        FootStrikeType.MIDFOOT -> "Midfoot strike" to Signal.Good
                        FootStrikeType.FOREFOOT -> "Forefoot strike" to Signal.Improve
                    }
                    Legend(col, "Landing strike: $label")
                }
                if (annotation?.strike == FootStrikeType.HEEL && (mode == LandingVisualMode.TARGET || mode == LandingVisualMode.OVERLAY)) {
                    Text(
                        "Shifting from heel to midfoot landing absorbs shock naturally through your arch and knee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                metrics.footStrikeSummary?.let { summary ->
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            LabIcons.FootStrike,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "Clip overall: $summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } ?: Text(
                "The runner's leg couldn't be made out on this landing.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The annotation for landing [frame], with distances in cm when the leg length is known. */
internal fun landingAnnotation(track: PoseTrack, metrics: GaitMetrics, frame: Int): LandingAnnotation? {
    val legCm = metrics.overstrideCm?.let { cm -> metrics.overstrideLegRatio?.takeIf { abs(it) > 1e-6 }?.let { cm / it } }
    val time = metrics.contactFrames.indexOf(frame).takeIf { it >= 0 }?.let { metrics.contactTimes.getOrNull(it) } ?: frame.toDouble()
    return LandingAnnotation.at(track, frame, legCm, time)
}

private fun describe(a: LandingAnnotation) = listOfNotNull(
    a.overstrideCm?.let { "Foot ${it.roundToInt()} centimetres ahead of hips" },
    "shin ${abs(a.shinDeg).roundToInt()} degrees",
    "knee bent ${a.kneeFlexionDeg.roundToInt()} degrees",
    "leaning ${abs(a.leanDeg).roundToInt()} degrees",
).joinToString(", ")

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LandingOverlay(
    actual: LandingAnnotation,
    ideal: LandingAnnotation,
    mode: LandingVisualMode,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        when (mode) {
            LandingVisualMode.TRACKED -> drawLanding(actual, measurer, Offset.Zero, size)
            LandingVisualMode.TARGET -> drawLanding(ideal, measurer, Offset.Zero, size, isIdeal = true)
            LandingVisualMode.OVERLAY -> {
                drawLanding(actual, measurer, Offset.Zero, size)
                drawLandingGhost(ideal, Offset.Zero, size)
            }
        }
    }
}

/** Draws the target proper form leg as a dashed mint ghost with target foot strike. */
internal fun DrawScope.drawLandingGhost(ideal: LandingAnnotation, origin: Offset, frameSize: Size) {
    fun p(point: com.atsukio.runningcoach.analysis.Point) =
        Offset(origin.x + point.x * frameSize.width, origin.y + point.y * frameSize.height)
    val hip = p(ideal.hip)
    val knee = p(ideal.knee)
    val ankle = p(ideal.ankle)
    val thick = 4.dp.toPx()
    val thin = 2.dp.toPx()
    val dashes = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))

    drawLine(TargetMintColor, hip, knee, thick, StrokeCap.Round, pathEffect = dashes)
    drawLine(TargetMintColor, knee, ankle, thick, StrokeCap.Round, pathEffect = dashes)

    if (ideal.heel != null && ideal.toe != null) {
        val h = p(ideal.heel)
        val t = p(ideal.toe)
        drawLine(TargetMintColor.copy(alpha = 0.8f), ankle, h, thin, StrokeCap.Round, pathEffect = dashes)
        drawLine(TargetMintColor, h, t, thick, StrokeCap.Round, pathEffect = dashes)

        val strikeSpot = when (ideal.strike) {
            FootStrikeType.HEEL -> h
            FootStrikeType.FOREFOOT -> t
            FootStrikeType.MIDFOOT, null -> Offset((h.x + t.x) / 2f, (h.y + t.y) / 2f)
        }
        drawCircle(TargetMintColor.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = strikeSpot)
        drawCircle(TargetMintColor, radius = 5.dp.toPx(), center = strikeSpot)
        drawCircle(Color.White, radius = 2.dp.toPx(), center = strikeSpot)
    } else {
        drawCircle(TargetMintColor, radius = 6.dp.toPx(), center = ankle)
    }
}

/**
 * Draws [text] at [topLeft] at its natural size. Compose's own drawText fits text into the space left
 * between [topLeft] and the canvas edge, and throws when that space is negative, so a label placed
 * just off the edge (a foot near the bottom of the frame) would crash instead of being clipped.
 */
internal fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, topLeft: Offset, style: TextStyle) =
    drawText(measurer.measure(text, style), topLeft = topLeft)

/** The landing measurements drawn over a video frame that fills [frameSize] at [origin]. */
internal fun DrawScope.drawLanding(
    a: LandingAnnotation,
    measurer: TextMeasurer,
    origin: Offset,
    frameSize: Size,
    isIdeal: Boolean = false,
) {
    run {
        fun p(point: com.atsukio.runningcoach.analysis.Point) =
            Offset(origin.x + point.x * frameSize.width, origin.y + point.y * frameSize.height)
        val shoulder = p(a.shoulder)
        val hip = p(a.hip)
        val knee = p(a.knee)
        val ankle = p(a.ankle)
        val thin = 2.dp.toPx()
        val thick = 4.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        val label = TextStyle(color = Color.White, fontSize = 13.sp, background = Color.Black.copy(alpha = 0.65f))
        val shinCol = if (isIdeal) TargetMintColor else ShinColor
        val overstrideCol = if (isIdeal) TargetMintColor else OverstrideColor

        // Body: trunk and thigh in white, the shin highlighted.
        drawLine(Color.White, shoulder, hip, thin, StrokeCap.Round)
        drawLine(Color.White, hip, knee, thin, StrokeCap.Round)
        drawLine(shinCol, knee, ankle, thick, StrokeCap.Round)

        // Lean: the trunk against a vertical line through the hips.
        drawLine(Color.White.copy(alpha = 0.6f), hip, Offset(hip.x, shoulder.y), thin, pathEffect = dashes)

        // Overstride: straight down from the hips, then across to where the foot lands.
        a.overstrideCm?.let { cm ->
            val ground = Offset(hip.x, ankle.y)
            drawLine(overstrideCol, hip, ground, thin, pathEffect = dashes)
            drawLine(overstrideCol, ground, ankle, thick, StrokeCap.Round)
            val text = if (cm > 0) "${cm.roundToInt()} cm" else "under hips"
            drawLabel(measurer, text, Offset((ground.x + ankle.x) / 2 - 20.dp.toPx(), ankle.y + 8.dp.toPx()), label)
        }

        // Shin: against a vertical line through the ankle.
        drawLine(shinCol.copy(alpha = 0.7f), ankle, Offset(ankle.x, knee.y), thin, pathEffect = dashes)
        drawLabel(measurer, "${abs(a.shinDeg).roundToInt()}°", Offset(knee.x + 10.dp.toPx(), (knee.y + ankle.y) / 2), label)

        // Knee bend.
        drawCircle(KneeColor, radius = 7.dp.toPx(), center = knee, style = Stroke(thin))
        drawLabel(measurer, "${a.kneeFlexionDeg.roundToInt()}°", Offset(knee.x + 12.dp.toPx(), knee.y - 22.dp.toPx()), label.copy(color = KneeColor))

        // Foot strike zone: draw heel-to-toe foot segment and strike point circle
        if (a.heel != null && a.toe != null) {
            val h = p(a.heel)
            val t = p(a.toe)
            drawLine(Color.White.copy(alpha = 0.8f), ankle, h, thin, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.8f), h, t, thick, StrokeCap.Round)
            val strikeSpot = when (a.strike) {
                FootStrikeType.HEEL -> h
                FootStrikeType.FOREFOOT -> t
                FootStrikeType.MIDFOOT -> Offset((h.x + t.x) / 2f, (h.y + t.y) / 2f)
                null -> when {
                    a.heel.y - a.toe.y > 0.015f -> h
                    a.toe.y - a.heel.y > 0.015f -> t
                    else -> Offset((h.x + t.x) / 2f, (h.y + t.y) / 2f)
                }
            }
            val spotCol = when (a.strike) {
                FootStrikeType.HEEL -> Signal.Info
                FootStrikeType.MIDFOOT -> Signal.Good
                FootStrikeType.FOREFOOT -> Signal.Improve
                null -> shinCol
            }
            drawCircle(spotCol, radius = 5.dp.toPx(), center = strikeSpot)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = strikeSpot)
        }
    }
}

private fun videoFrame(video: File, seconds: Double, aspectRatio: Float): ImageBitmap? =
    videoFrameBitmap(video, seconds, aspectRatio)?.asImageBitmap()

/** The video frame at [seconds], upright and at most [maxSide] px on its long side. */
internal fun videoFrameBitmap(video: File, seconds: Double, aspectRatio: Float, maxSide: Int = 720): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(video.path)
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        var frame = retriever.getFrameAtTime((seconds * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST)
            ?: return@runCatching null
        // Some devices return the frame as stored rather than upright.
        val looksUpright = (frame.width >= frame.height) == (aspectRatio >= 1f)
        if (!looksUpright && rotation != 0) {
            frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val scale = maxSide.toFloat() / maxOf(frame.width, frame.height)
        if (scale < 1f) frame = frame.scale((frame.width * scale).roundToInt(), (frame.height * scale).roundToInt())
        frame
    } finally {
        retriever.release()
    }
}.getOrNull()
