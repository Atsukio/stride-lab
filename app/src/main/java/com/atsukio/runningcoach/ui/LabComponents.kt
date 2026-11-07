package com.atsukio.runningcoach.ui

import androidx.compose.animation.core.LinearEasing
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
fun StatChip(
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    colour: Color = MaterialTheme.colorScheme.onSurface,
    countUp: Boolean = false,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (countUp) CountUpText(value, monoValue(14), colour) else Text(value, style = monoValue(14), color = colour)
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
    // One loop from 0 to 2: the first half sweeps right, the second half sweeps back left. Unlike a reversing
    // animation, this says which way the line is moving, so the trail can stay behind it.
    val loop by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val movingRight = loop <= 1f
    val sweep = if (movingRight) loop else 2f - loop
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
            // Behind the line: to its left when moving right, to its right when moving left.
            val dx = i * 3.dp.toPx() * (if (movingRight) 1f else -1f)
            drawLine(line.copy(alpha = 0.12f * (13 - i) / 12f), Offset(scanX - dx, 0f), Offset(scanX - dx, size.height), strokeWidth = 3.dp.toPx())
        }
        drawLine(line, Offset(scanX, 0f), Offset(scanX, size.height), strokeWidth = 2.dp.toPx())
    }
}

private val LEADING_NUMBER = Regex("^(-?\\d+(?:\\.\\d+)?)(.*)$", RegexOption.DOT_MATCHES_ALL)

/**
 * Text that begins with a figure, counting up to it the first time it appears, e.g. "27 cm ahead of hips" counts
 * from 0 to 27. Monospace digits keep the text from jiggling as it counts. It plays once per screen visit, even
 * when a list scrolls the item away and back, and it jumps straight to the answer when animations are off. Text
 * without a leading figure is shown as it is, and screen readers always get the final text.
 */
@Composable
fun CountUpText(
    text: String,
    style: TextStyle,
    colour: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    durationMs: Int = 700,
) {
    val match = remember(text) { LEADING_NUMBER.matchEntire(text) }
    var played by rememberSaveable(text) { mutableStateOf(false) }
    val progress = remember(text) { Animatable(if (played || match == null) 1f else 0f) }
    LaunchedEffect(text) {
        if (!played && match != null) {
            progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        }
        played = true
    }
    val shown = if (match == null || progress.value >= 1f) {
        text
    } else {
        val target = match.groupValues[1]
        val decimals = target.substringAfter('.', "").length
        "%.${decimals}f".format(Locale.US, target.toDouble() * progress.value) + match.groupValues[2]
    }
    Text(
        shown,
        modifier.semantics { contentDescription = text },
        color = colour,
        style = style,
        textAlign = textAlign,
    )
}
