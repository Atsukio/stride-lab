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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
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

private fun goodRangeText(scale: Scale): String {
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
 * lands, the shin angle, the knee bend and the lean. Steps through every landing in the clip.
 */
@Composable
fun LandingCard(video: File, track: PoseTrack, metrics: GaitMetrics, aspectRatio: Float, playbackFps: Float) {
    val contacts = metrics.contactFrames
    if (contacts.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(contacts.size / 2) }
    val frame = contacts[index.coerceIn(contacts.indices)]
    val legCm = metrics.overstrideCm?.let { cm -> metrics.overstrideLegRatio?.takeIf { abs(it) > 1e-6 }?.let { cm / it } }
    val annotation = remember(frame) { LandingAnnotation.at(track, frame, legCm) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frame) {
        image = null
        image = withContext(Dispatchers.IO) { videoFrame(video, frame / playbackFps.toDouble(), aspectRatio) }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("At landing", style = MaterialTheme.typography.titleMedium)
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
                    annotation?.let { LandingOverlay(it, Modifier.fillMaxSize()) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { index-- }, enabled = index > 0) { Text("‹ Previous") }
                Spacer(Modifier.weight(1f))
                Text("Landing ${index + 1} of ${contacts.size}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { index++ }, enabled = index < contacts.lastIndex) { Text("Next ›") }
            }
            annotation?.let { a ->
                a.overstrideCm?.let {
                    Legend(
                        OverstrideColor,
                        if (it > 0) "Foot lands ${it.roundToInt()} cm ahead of your hips" else "Foot lands under your hips",
                    )
                }
                Legend(ShinColor, "Shin ${abs(a.shinDeg).roundToInt()}° from vertical" + if (a.shinDeg < 0) ", foot behind the knee" else "")
                Legend(KneeColor, "Knee bent ${a.kneeFlexionDeg.roundToInt()}°")
                Legend(
                    MaterialTheme.colorScheme.onSurface,
                    "Leaning ${abs(a.leanDeg).roundToInt()}° " + if (a.leanDeg >= 0) "forward" else "back",
                )
            } ?: Text(
                "The runner's leg couldn't be made out on this landing.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
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
private fun LandingOverlay(a: LandingAnnotation, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        fun p(point: com.atsukio.runningcoach.analysis.Point) = Offset(point.x * size.width, point.y * size.height)
        val shoulder = p(a.shoulder)
        val hip = p(a.hip)
        val knee = p(a.knee)
        val ankle = p(a.ankle)
        val thin = 2.dp.toPx()
        val thick = 4.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        val label = TextStyle(color = Color.White, fontSize = 13.sp, background = Color.Black.copy(alpha = 0.65f))

        // Body: trunk and thigh in white, the shin highlighted.
        drawLine(Color.White, shoulder, hip, thin, StrokeCap.Round)
        drawLine(Color.White, hip, knee, thin, StrokeCap.Round)
        drawLine(ShinColor, knee, ankle, thick, StrokeCap.Round)

        // Lean: the trunk against a vertical line through the hips.
        drawLine(Color.White.copy(alpha = 0.6f), hip, Offset(hip.x, shoulder.y), thin, pathEffect = dashes)

        // Overstride: straight down from the hips, then across to where the foot lands.
        a.overstrideCm?.let { cm ->
            val ground = Offset(hip.x, ankle.y)
            drawLine(OverstrideColor, hip, ground, thin, pathEffect = dashes)
            drawLine(OverstrideColor, ground, ankle, thick, StrokeCap.Round)
            val text = if (cm > 0) "${cm.roundToInt()} cm" else "under hips"
            drawText(measurer, text, Offset((ground.x + ankle.x) / 2 - 20.dp.toPx(), ankle.y + 8.dp.toPx()), label)
        }

        // Shin: against a vertical line through the ankle.
        drawLine(ShinColor.copy(alpha = 0.7f), ankle, Offset(ankle.x, knee.y), thin, pathEffect = dashes)
        drawText(measurer, "${abs(a.shinDeg).roundToInt()}°", Offset(knee.x + 10.dp.toPx(), (knee.y + ankle.y) / 2), label)

        // Knee bend.
        drawCircle(KneeColor, radius = 7.dp.toPx(), center = knee, style = Stroke(thin))
        drawText(measurer, "${a.kneeFlexionDeg.roundToInt()}°", Offset(knee.x + 12.dp.toPx(), knee.y - 22.dp.toPx()), label.copy(color = KneeColor))
    }
}

/** The video frame at [seconds], upright and at most 720 px on its long side. */
private fun videoFrame(video: File, seconds: Double, aspectRatio: Float): ImageBitmap? = runCatching {
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
        val scale = 720f / maxOf(frame.width, frame.height)
        if (scale < 1f) frame = frame.scale((frame.width * scale).roundToInt(), (frame.height * scale).roundToInt())
        frame.asImageBitmap()
    } finally {
        retriever.release()
    }
}.getOrNull()
