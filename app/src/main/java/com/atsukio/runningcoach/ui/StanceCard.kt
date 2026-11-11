package com.atsukio.runningcoach.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.analysis.StanceAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val HipLineColor = Color(0xFFFFEB3B)
private val StanceLegColor = Color(0xFFFFB74D)
private val StanceKneeColor = Color(0xFF4FC3F7)

/**
 * The front and rear view's version of the landing card: a still from the middle of one stance with the hip
 * line and the standing leg drawn on it. Steps through every landing; [index] is held by the caller.
 */
@Composable
fun StanceCard(
    video: File,
    track: PoseTrack,
    metrics: GaitMetrics,
    aspectRatio: Float,
    playbackFps: Float,
    index: Int,
    onIndex: (Int) -> Unit,
) {
    val contacts = metrics.contactFrames
    val frontal = metrics.frontal ?: return
    if (contacts.isEmpty()) return
    val shown = index.coerceIn(contacts.indices)
    val frame = StanceAnnotation.frameFor(metrics, shown) ?: return
    val left = frontal.leftContacts.getOrNull(shown) ?: return
    val annotation = remember(frame, left) { StanceAnnotation.at(track, frame, left) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frame) {
        image = null
        image = withContext(Dispatchers.IO) { videoFrame(video, frame / playbackFps.toDouble(), aspectRatio) }
    }
    val leg = if (left) "left" else "right"

    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("On one leg", icon = LabIcons.HipDrop)
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .heightIn(max = 420.dp)
                        .aspectRatio(aspectRatio)
                        .semantics {
                            contentDescription = annotation?.let {
                                "Standing on the $leg leg: hip line ${abs(it.dropDeg).roundToInt()} degrees from level, " +
                                    "knee ${abs(it.kneeInwardDeg).roundToInt()} degrees from the hip to ankle line"
                            } ?: "Stance frame"
                        },
                ) {
                    val still = image
                    if (still != null) {
                        Image(still, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    if (annotation != null) StanceOverlay(annotation, Modifier.fillMaxSize())
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onIndex(shown - 1) }, enabled = shown > 0) { Text("‹ Previous") }
                Spacer(Modifier.weight(1f))
                Text("Landing ${shown + 1} of ${contacts.size}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onIndex(shown + 1) }, enabled = shown < contacts.lastIndex) { Text("Next ›") }
            }
            if (annotation == null) {
                Text("The runner's hips and leg couldn't be made out on this landing.", style = MaterialTheme.typography.bodySmall)
            } else {
                Legend(MaterialTheme.colorScheme.onSurface, "Standing on your $leg leg, foot under the body")
                Legend(
                    HipLineColor,
                    when {
                        annotation.dropDeg >= 0.5 -> "Lifted side's hip sits ${annotation.dropDeg.roundToInt()}° below level"
                        annotation.dropDeg <= -0.5 -> "Lifted side's hip sits ${abs(annotation.dropDeg).roundToInt()}° above level"
                        else -> "Hips level"
                    },
                )
                Legend(
                    StanceKneeColor,
                    when {
                        annotation.kneeInwardDeg >= 0.5 -> "Knee ${annotation.kneeInwardDeg.roundToInt()}° inside the hip-to-ankle line"
                        annotation.kneeInwardDeg <= -0.5 -> "Knee ${abs(annotation.kneeInwardDeg).roundToInt()}° outside the hip-to-ankle line"
                        else -> "Knee on the hip-to-ankle line"
                    },
                )
                Text(
                    "One frame from the middle of this step. The figures in the breakdown below are the most each reached, across every step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StanceOverlay(a: StanceAnnotation, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        fun p(point: com.atsukio.runningcoach.analysis.Point) = Offset(point.x * size.width, point.y * size.height)
        val standing = p(a.standingHip)
        val lifted = p(a.liftedHip)
        val knee = p(a.knee)
        val ankle = p(a.ankle)
        val shoulders = p(a.shoulders)
        val hipMid = Offset((standing.x + lifted.x) / 2, (standing.y + lifted.y) / 2)
        val thin = 2.dp.toPx()
        val thick = 4.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        val label = TextStyle(color = Color.White, fontSize = 13.sp, background = Color.Black.copy(alpha = 0.65f))

        // Trunk, against a vertical line up from the middle of the hips.
        drawLine(Color.White, hipMid, shoulders, thin, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.6f), hipMid, Offset(hipMid.x, shoulders.y), thin, pathEffect = dashes)

        // Hip line, against level through the standing hip. Extended a little so the tilt is easy to see.
        val reach = (lifted - standing) * 0.35f
        drawLine(HipLineColor.copy(alpha = 0.7f), Offset(standing.x - reach.x, standing.y), Offset(lifted.x + reach.x, standing.y), thin, pathEffect = dashes)
        drawLine(HipLineColor, standing - reach, lifted + reach, thick, StrokeCap.Round)
        drawLabel(measurer, "${abs(a.dropDeg).roundToInt()}°", Offset(lifted.x + reach.x + 6.dp.toPx(), lifted.y - 9.dp.toPx()), label.copy(color = HipLineColor))

        // Standing leg, against the straight line from hip to ankle.
        drawLine(StanceLegColor.copy(alpha = 0.7f), standing, ankle, thin, pathEffect = dashes)
        drawLine(StanceLegColor, standing, knee, thick, StrokeCap.Round)
        drawLine(StanceLegColor, knee, ankle, thick, StrokeCap.Round)
        drawCircle(StanceKneeColor, radius = 7.dp.toPx(), center = knee, style = Stroke(thin))
        val away = if (knee.x >= hipMid.x) 12.dp.toPx() else -40.dp.toPx()
        drawLabel(measurer, "${abs(a.kneeInwardDeg).roundToInt()}°", Offset(knee.x + away, knee.y - 9.dp.toPx()), label.copy(color = StanceKneeColor))
    }
}
