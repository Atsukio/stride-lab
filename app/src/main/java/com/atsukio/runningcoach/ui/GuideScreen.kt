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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

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
            SetupDiagram(Modifier.fillMaxWidth().height(262.dp))
            Text(
                "✓ Beside the treadmill   ✗ Not in front of it or behind it",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Step(1, "Beside the treadmill, side-on", "Point the phone at your side, at right angles to the way you run, not at your front or back.")
            Step(
                2,
                "Hip height, 2–3 m away",
                "Stand the phone upright on a tripod, shelf or chair. Use the back camera for the best quality, " +
                    "or the front camera if you want to watch the framing check on screen.",
            )
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

/**
 * Where the phone goes, seen from above: beside the treadmill, square-on to the runner's side, with what
 * the camera should see in an inset. A side elevation can't show "beside", which made the phone look like
 * it was in front of the runner. The layout was checked by first-time readers on a rendered mock.
 */
@Composable
private fun SetupDiagram(modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val belt = MaterialTheme.colorScheme.surfaceVariant
    val background = MaterialTheme.colorScheme.surface
    val shoulders = lerp(belt, ink, 0.55f)
    val measurer = rememberTextMeasurer()
    // Fixed-size labels: they're part of the drawing, and everything they say is also in the steps below.
    val density = LocalDensity.current
    val label = TextStyle(color = ink, fontSize = with(density) { 12.dp.toSp() })
    val small = TextStyle(color = ink, fontSize = with(density) { 11.dp.toSp() })
    Canvas(
        modifier.semantics {
            contentDescription = "Diagram seen from above: the phone stands upright 2 to 3 metres beside the treadmill " +
                "at hip height, aimed at the runner's side, at right angles to the running direction. An inset shows " +
                "the camera's view: the runner side-on, head to feet, running towards the treadmill's console."
        },
    ) {
        val w = size.width
        val h = size.height
        fun d(v: Float) = v.dp.toPx()
        val cone = PathEffect.dashPathEffect(floatArrayOf(d(6f), d(4f)))
        fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            close()
        }

        // ---- Plan view, seen from above: the left two thirds ----
        val planW = w * 0.66f
        val tl = planW * 0.06f
        val tr = planW * 0.94f
        val tt = h * 0.14f
        val tb = h * 0.40f
        drawRoundRect(ink, Offset(tl, tt), Size(tr - tl, tb - tt), CornerRadius(d(8f)), style = Stroke(d(2.5f)))
        drawRoundRect(belt, Offset(tl + d(8f), tt + d(8f)), Size(tr - tl - d(34f), tb - tt - d(16f)), CornerRadius(d(4f)))
        drawRoundRect(ink, Offset(tr - d(20f), tt + d(4f)), Size(d(14f), tb - tt - d(8f)), CornerRadius(d(3f)), style = Stroke(d(2f)))

        // Running direction.
        val arrowY = h * 0.07f
        val arrowEnd = planW * 0.62f
        drawLine(ink, Offset(planW * 0.30f, arrowY), Offset(arrowEnd, arrowY), d(2f))
        drawPath(triangle(Offset(arrowEnd + d(8f), arrowY), Offset(arrowEnd - d(2f), arrowY - d(5f)), Offset(arrowEnd - d(2f), arrowY + d(5f))), ink)
        drawText(measurer, "running direction", Offset(planW * 0.30f, arrowY - d(17f)), small)

        // The runner from above, facing the console: staggered feet, arms, shoulders across the belt, head and nose.
        val rx = planW * 0.45f
        val ry = (tt + tb) / 2
        drawOval(ink, Offset(rx + d(12f), ry + d(3f)), Size(d(14f), d(7f)))
        drawOval(ink, Offset(rx - d(26f), ry - d(10f)), Size(d(14f), d(7f)))
        drawLine(ink, Offset(rx, ry - d(15f)), Offset(rx + d(11f), ry - d(13f)), d(3f), StrokeCap.Round)
        drawLine(ink, Offset(rx, ry + d(15f)), Offset(rx - d(11f), ry + d(13f)), d(3f), StrokeCap.Round)
        drawRoundRect(shoulders, Offset(rx - d(6f), ry - d(17f)), Size(d(12f), d(34f)), CornerRadius(d(6f)))
        drawOval(ink, Offset(rx - d(5f), ry - d(7f)), Size(d(14f), d(14f)))
        drawPath(triangle(Offset(rx + d(19f), ry), Offset(rx + d(9f), ry - d(5f)), Offset(rx + d(9f), ry + d(5f))), ink)
        drawText(measurer, "you", Offset(rx + d(30f), ry - d(8f)), small)

        // The phone standing upright beside the treadmill, seen from above: a thin bar, lens towards the runner.
        val px = rx
        val py = h * 0.80f
        drawRoundRect(accent, Offset(px - d(13f), py - d(3f)), Size(d(26f), d(6f)), CornerRadius(d(1.5f)))
        drawCircle(accent, d(3f), Offset(px, py - d(4f)))
        for ((lx, ly) in listOf(-16f to 10f, 16f to 10f, 0f to 16f)) {
            drawLine(accent, Offset(px, py), Offset(px + d(lx), py + d(ly)), d(2f), StrokeCap.Round)
        }
        val lens = Offset(px, py - d(7f))
        val edge = tb + d(2f)
        drawLine(accent, lens, Offset(planW * 0.20f, edge), d(2f), pathEffect = cone)
        drawLine(accent, lens, Offset(planW * 0.70f, edge), d(2f), pathEffect = cone)
        drawLine(accent, lens, Offset(px, edge), d(2f), pathEffect = cone)
        // Square-on to the treadmill.
        drawLine(accent, Offset(px - d(12f), edge), Offset(px - d(12f), edge + d(12f)), d(2f))
        drawLine(accent, Offset(px - d(12f), edge + d(12f)), Offset(px, edge + d(12f)), d(2f))
        drawText(measurer, "aim at your side", Offset(px + d(6f), edge + d(6f)), small.copy(color = accent, background = background))
        // Distance, along the camera's line from the runner to the phone.
        val dx = px - d(44f)
        drawLine(ink, Offset(dx, ry), Offset(dx, py), d(1.5f))
        drawLine(ink, Offset(dx - d(5f), ry), Offset(dx + d(5f), ry), d(1.5f))
        drawLine(ink, Offset(dx - d(5f), py), Offset(dx + d(5f), py), d(1.5f))
        drawText(measurer, "2–3 m", Offset(dx - d(40f), (edge + py) / 2), label)
        drawText(measurer, "phone upright, hip height", Offset(px - d(52f), py + d(18f)), small.copy(color = accent))

        // ---- Inset: what the camera should see ----
        val ix0 = w * 0.72f
        val ix1 = w * 0.97f
        val iy0 = h * 0.10f
        val iy1 = h * 0.90f
        drawRoundRect(ink, Offset(ix0, iy0), Size(ix1 - ix0, iy1 - iy0), CornerRadius(d(10f)), style = Stroke(d(2f)))
        drawText(measurer, "camera's view", Offset(ix0 + d(2f), iy1 + d(4f)), small)
        val fl = ix0 + d(6f)
        val fr = ix1 - d(6f)
        val ft = iy0 + d(14f)
        val fb = iy1 - d(14f)
        fun p(fx: Float, fy: Float) = Offset(fl + (fr - fl) * fx, ft + (fb - ft) * fy)
        val limb = d(2.6f)
        fun bone(a: Offset, b: Offset) = drawLine(ink, a, b, limb, StrokeCap.Round)
        // The treadmill in profile: a long low deck, with the upright and console at the same end as above.
        val deckTop = 0.86f
        drawRoundRect(belt, p(0.04f, deckTop), Size((fr - fl) * 0.76f, (fb - ft) * 0.06f), CornerRadius(d(2f)))
        drawRoundRect(ink, p(0.04f, deckTop), Size((fr - fl) * 0.76f, (fb - ft) * 0.06f), CornerRadius(d(2f)), style = Stroke(d(1.5f)))
        drawLine(ink, p(0.78f, deckTop), p(0.90f, 0.50f), d(2.5f), StrokeCap.Round)
        drawLine(ink, p(0.90f, 0.50f), p(0.74f, 0.50f), d(2.5f), StrokeCap.Round)
        // The runner in profile facing right, leaning forward, mid-stride, head joined to the body.
        val head = p(0.51f, 0.165f)
        val r = d(6.5f)
        drawCircle(ink, r, head)
        drawPath(triangle(Offset(head.x - r * 0.3f, head.y - r), Offset(head.x + r * 2.3f, head.y - r * 0.45f), Offset(head.x + r * 0.4f, head.y - r * 0.05f)), ink)
        val hip = p(0.38f, 0.50f)
        bone(p(0.49f, 0.22f), hip)
        // Arms bent at the elbow: front hand forward and up, back elbow driven behind.
        bone(p(0.47f, 0.27f), p(0.57f, 0.37f)); bone(p(0.57f, 0.37f), p(0.66f, 0.31f))
        bone(p(0.47f, 0.27f), p(0.38f, 0.39f)); bone(p(0.38f, 0.39f), p(0.32f, 0.46f))
        // Legs: front foot landing near under the hips, back heel kicked up behind.
        bone(hip, p(0.50f, 0.66f)); bone(p(0.50f, 0.66f), p(0.47f, deckTop - 0.01f)); bone(p(0.47f, deckTop - 0.01f), p(0.55f, deckTop - 0.01f))
        bone(hip, p(0.32f, 0.67f)); bone(p(0.32f, 0.67f), p(0.16f, 0.61f))
    }
}
