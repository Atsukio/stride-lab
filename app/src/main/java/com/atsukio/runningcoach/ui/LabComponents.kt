package com.atsukio.runningcoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A small tag above a section, in monospace capitals: "MAIN FOCUS", "AT LANDING". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, colour: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = colour)
}

/** A status dot: green in range, amber to work on, blue for a note. */
@Composable
fun StatusDot(good: Boolean?, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(signalColour(good)))
}

/** A compact readout: a figure in monospace with a quiet unit, e.g. "172" and "spm". */
@Composable
fun StatChip(value: String, unit: String?, modifier: Modifier = Modifier, colour: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = monoValue(14), color = colour)
        if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Panel with viewfinder corner marks and a faint grid behind its content, for the main call to action. */
@Composable
fun ViewfinderPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val mark = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    LabCard(modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                val step = 24.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(grid.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(grid.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                val inset = 10.dp.toPx()
                val arm = 18.dp.toPx()
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
                    drawLine(mark, Offset(cx, cy), Offset(cx + dx * arm, cy), stroke.width, StrokeCap.Round)
                    drawLine(mark, Offset(cx, cy), Offset(cx, cy + dy * arm), stroke.width, StrokeCap.Round)
                }
                corner(inset, inset, 1f, 1f)
                corner(size.width - inset, inset, -1f, 1f)
                corner(inset, size.height - inset, 1f, -1f)
                corner(size.width - inset, size.height - inset, -1f, -1f)
            }
            Column(Modifier.padding(20.dp)) { content() }
        }
    }
}

/** A faint grid with a scan line sweeping across it, shown while a clip is being analysed. */
@Composable
fun ScanPanel(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
    ) {
        val step = 20.dp.toPx()
        var x = step
        while (x < size.width) {
            drawLine(grid.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(grid.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        val scanX = sweep * size.width
        // A soft trail behind the line.
        for (i in 1..12) {
            val dx = i * 3.dp.toPx()
            drawLine(line.copy(alpha = 0.12f * (13 - i) / 12f), Offset(scanX - dx, 0f), Offset(scanX - dx, size.height), strokeWidth = 3.dp.toPx())
        }
        drawLine(line, Offset(scanX, 0f), Offset(scanX, size.height), strokeWidth = 2.dp.toPx())
    }
}
