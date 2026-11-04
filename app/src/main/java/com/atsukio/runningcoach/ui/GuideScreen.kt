package com.atsukio.runningcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run setup: where to put the phone and how to film, since framing matters more than anything. */
@Composable
fun GuideScreen(onDone: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Set up your camera", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Stride Lab measures your form from the side, so where the phone goes matters more than anything else.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SetupDiagram(Modifier.fillMaxWidth().height(200.dp))
            Step(1, "Side-on", "Put the phone level with the treadmill, facing your side, not your front or back.")
            Step(2, "Hip height, 2–3 m away", "A tripod, shelf or chair works. Lean the phone on something steady.")
            Step(3, "Whole body in frame", "Head to feet, with a little space around you. The camera screen tells you if anything's cut off.")
            Step(4, "Good light", "Bright light keeps the camera at 60 fps, which catches each foot landing.")
            Step(5, "Run steadily", "Get up to your normal pace first, then record 10–30 seconds.")
            Text(
                "Everything stays on this phone. Nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Got it") }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A treadmill and runner seen from above the floor, with the phone side-on at hip height. */
@Composable
private fun SetupDiagram(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = ink, fontSize = 12.sp)
    Canvas(
        modifier.semantics {
            contentDescription = "Diagram: the phone stands 2 to 3 metres to the side of the treadmill at hip height, " +
                "with the runner's whole body in view."
        },
    ) {
        val w = size.width
        val h = size.height
        val floor = h * 0.88f
        val stroke = 3.dp.toPx()

        // Treadmill: belt, upright and handlebar.
        val beltLeft = w * 0.08f
        val beltRight = w * 0.5f
        drawRoundRect(ink, Offset(beltLeft, floor - 10.dp.toPx()), Size(beltRight - beltLeft, 10.dp.toPx()), CornerRadius(5.dp.toPx()), style = Stroke(stroke))
        drawLine(ink, Offset(beltRight - 6.dp.toPx(), floor - 10.dp.toPx()), Offset(beltRight + 6.dp.toPx(), h * 0.42f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(beltRight + 6.dp.toPx(), h * 0.42f), Offset(beltRight - 22.dp.toPx(), h * 0.42f), stroke, StrokeCap.Round)

        // Runner mid-stride.
        val hip = Offset(w * 0.29f, h * 0.5f)
        val head = Offset(w * 0.31f, h * 0.14f)
        drawCircle(ink, 9.dp.toPx(), head, style = Stroke(stroke))
        drawLine(ink, Offset(head.x - 1.dp.toPx(), head.y + 10.dp.toPx()), hip, stroke, StrokeCap.Round)
        drawLine(ink, hip, Offset(w * 0.34f, h * 0.68f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(w * 0.34f, h * 0.68f), Offset(w * 0.33f, floor - 12.dp.toPx()), stroke, StrokeCap.Round)
        drawLine(ink, hip, Offset(w * 0.23f, h * 0.66f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(w * 0.23f, h * 0.66f), Offset(w * 0.16f, h * 0.7f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(w * 0.305f, h * 0.27f), Offset(w * 0.35f, h * 0.38f), stroke, StrokeCap.Round)
        drawLine(ink, Offset(w * 0.35f, h * 0.38f), Offset(w * 0.39f, h * 0.31f), stroke, StrokeCap.Round)

        // Phone on a stand at hip height, to the side.
        val phone = Offset(w * 0.88f, hip.y)
        drawRoundRect(accent, Offset(phone.x - 6.dp.toPx(), phone.y - 14.dp.toPx()), Size(12.dp.toPx(), 28.dp.toPx()), CornerRadius(3.dp.toPx()))
        drawLine(accent, Offset(phone.x, phone.y + 14.dp.toPx()), Offset(phone.x, floor), stroke)
        drawLine(accent, Offset(phone.x - 14.dp.toPx(), floor), Offset(phone.x + 14.dp.toPx(), floor), stroke)

        // Field of view: wide enough for head to feet.
        val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        drawLine(accent.copy(alpha = 0.7f), Offset(phone.x - 6.dp.toPx(), phone.y), Offset(w * 0.1f, h * 0.04f), 2.dp.toPx(), pathEffect = dashes)
        drawLine(accent.copy(alpha = 0.7f), Offset(phone.x - 6.dp.toPx(), phone.y), Offset(w * 0.1f, floor + 6.dp.toPx()), 2.dp.toPx(), pathEffect = dashes)

        // Distance and height labels.
        drawLine(ink.copy(alpha = 0.6f), Offset(beltRight + 16.dp.toPx(), floor + 8.dp.toPx()), Offset(phone.x, floor + 8.dp.toPx()), 1.5f.dp.toPx())
        drawText(measurer, "2–3 m", Offset((beltRight + phone.x) / 2 - 16.dp.toPx(), floor - 18.dp.toPx()), label)
        drawText(measurer, "hip height", Offset(phone.x - 58.dp.toPx(), phone.y - 34.dp.toPx()), label.copy(color = accent))
        drawLine(ink, Offset(0f, floor), Offset(w, floor), 1.5f.dp.toPx())
    }
}
