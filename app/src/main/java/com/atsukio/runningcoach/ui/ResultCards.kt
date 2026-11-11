package com.atsukio.runningcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atsukio.runningcoach.analysis.Badge
import com.atsukio.runningcoach.analysis.CameraView
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FeedbackStatus
import com.atsukio.runningcoach.analysis.FocusPlan
import com.atsukio.runningcoach.analysis.FootStrikeType
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.analysis.measurementKey
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.data.SessionBuilder
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.recalculate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun Summary(session: Session, feedback: List<Feedback>, plan: FocusPlan?, onStartPlan: (Feedback) -> Unit) {
    val failure = session.failure
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (failure != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text("Couldn't analyse this clip", style = MaterialTheme.typography.titleMedium)
                }
                Text(failure)
                FilmingTips()
                return@Column
            }
            val focus = Coach.mainFocus(feedback)
            if (focus != null) {
                SectionLabel("Main focus", colour = signalColour(false), icon = LabIcons.Target)
                Text(focus.topic, style = MaterialTheme.typography.headlineSmall)
                focus.cue?.let {
                    Text("Think: \"$it\"", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (plan != null && measurementKey(plan.topic) == measurementKey(focus.topic)) {
                    SectionLabel("In your focus plan", colour = signalColour(true), icon = Icons.Default.Star)
                } else {
                    OutlinedButton(onClick = { onStartPlan(focus) }) {
                        Text(if (plan == null) "Start a 3-week plan for this" else "Switch my plan to this")
                    }
                }
            } else {
                SectionLabel("All clear", colour = signalColour(true), icon = Icons.Default.Check)
                Text("Nice running: nothing major to fix", style = MaterialTheme.typography.headlineSmall)
            }
            // Wraps so the fourth chip keeps its figure and unit on one line on a phone-width card.
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.metrics?.let { StatChip("${it.stepCount}", "steps", countUp = true) }
                session.speedKmh?.let { StatChip(formatSpeed(it), if (session.speedFromSensor) "km/h watch" else "km/h") }
                StatChip("${session.fps.roundToInt()}", "fps")
                session.metrics?.takeIf { it.view != CameraView.SIDE }?.let { StatChip(it.view.label.substringBefore(' '), "view") }
                session.metrics?.let { m ->
                    val heel = m.heelStrikeRatio
                    val primary = m.primaryStrikeType
                    if (heel != null) {
                        val h = (heel * 100).roundToInt()
                        StatChip("$h%", "heel strike")
                    } else if (primary != null) {
                        StatChip(primary.name.lowercase().replaceFirstChar { it.uppercase() }, "strike")
                    }
                }
            }
            session.processingSec?.let {
                Text(
                    "Analysed in %.1f s".format(it) + if (session.analysedLive) " while recording" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            session.sensor?.let { sensor ->
                val watch = listOfNotNull(
                    sensor.heartRate?.let { "♥ $it" },
                    sensor.cadenceSpm?.let { "${it.roundToInt()} steps/min" },
                ).joinToString(" · ")
                if (watch.isNotEmpty()) {
                    Text(
                        "Watch during clip: $watch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FootStrikeCard(
    metrics: GaitMetrics,
    focus: Feedback,
    plan: FocusPlan?,
    onStartPlan: (Feedback) -> Unit,
) {
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Foot strike pattern", icon = LabIcons.FootStrike)
                Spacer(Modifier.weight(1f))
                metrics.primaryStrikeType?.let { primary ->
                    val (label, col) = when (primary) {
                        FootStrikeType.HEEL -> "Mainly Heel" to Signal.Info
                        FootStrikeType.MIDFOOT -> "Mainly Midfoot" to Signal.Good
                        FootStrikeType.FOREFOOT -> "Mainly Forefoot" to Signal.Improve
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = col)
                }
            }
            val summary = metrics.footStrikeSummary
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.headlineSmall)
                val heel = (metrics.heelStrikeRatio ?: 0.0).toFloat()
                val midfoot = (metrics.midfootStrikeRatio ?: 0.0).toFloat()
                val forefoot = (metrics.forefootStrikeRatio ?: 0.0).toFloat()
                val totalSteps = metrics.footStrikes.size.takeIf { it > 0 } ?: metrics.stepCount
                val heelSteps = (heel * totalSteps).roundToInt()
                val midfootSteps = (midfoot * totalSteps).roundToInt()
                val forefootSteps = (forefoot * totalSteps).roundToInt()

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
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

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StrikeStatCard(
                        title = "Heel",
                        ratio = heel,
                        steps = heelSteps,
                        color = Signal.Info,
                        modifier = Modifier.weight(1f),
                    )
                    StrikeStatCard(
                        title = "Midfoot",
                        ratio = midfoot,
                        steps = midfootSteps,
                        color = Signal.Good,
                        modifier = Modifier.weight(1f),
                    )
                    StrikeStatCard(
                        title = "Forefoot",
                        ratio = forefoot,
                        steps = forefootSteps,
                        color = Signal.Improve,
                        modifier = Modifier.weight(1f),
                    )
                }

                val note = when (metrics.primaryStrikeType) {
                    FootStrikeType.HEEL -> "Mainly heel striking. Very common for distance runners; keeping your foot landing close under your hips helps absorb shock naturally."
                    FootStrikeType.MIDFOOT -> "Mainly midfoot striking. Distributes ground impact evenly across your foot and lower leg."
                    FootStrikeType.FOREFOOT -> "Mainly forefoot striking. Puts more load on your calves and Achilles; ensure a relaxed contact and allow the heel to settle gently."
                    null -> ""
                }
                if (note.isNotEmpty()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan != null && measurementKey(plan.topic) == measurementKey(focus.topic)) {
                    SectionLabel("In your focus plan", colour = signalColour(true), icon = Icons.Default.Star)
                } else {
                    OutlinedButton(onClick = { onStartPlan(focus) }) {
                        Text(if (plan == null) "Start a 3-week plan for foot strike" else "Switch my plan to foot strike")
                    }
                }
            } else {
                Text(
                    "Foot strike couldn't be classified for this clip. Keep the runner's feet and treadmill belt fully in frame.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StrikeStatCard(
    title: String,
    ratio: Float,
    steps: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val pct = (ratio * 100).roundToInt()
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, if (pct > 0) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (pct > 0) color else color.copy(alpha = 0.4f)))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "$pct%",
            style = monoValue(16),
            color = if (pct > 0) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            "$steps steps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/**
 * The treadmill speed and height used to turn pixels into centimetres for this clip. Both can be changed
 * here for this clip alone; the saved poses are measured again, so the new figures show straight away.
 */
@Composable
internal fun ScaleCard(session: Session, estimatedCm: Double?, onRecalculated: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CoachSettings(context) }
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    var savedCm by remember { mutableStateOf(settings.runnerHeightCm) }
    var speedText by remember(session.speedKmh) { mutableStateOf(session.speedKmh?.let(::formatSpeed).orEmpty()) }
    var heightText by remember(session.runnerHeightCm) { mutableStateOf(session.runnerHeightCm.roundToInt().toString()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val speedBlank = speedText.isBlank()
    val speed = speedText.replace(',', '.').trim().toDoubleOrNull()
    val speedOk = speedBlank || (speed != null && SessionBuilder.validSpeed(speed))
    val newSpeed = if (speedBlank) null else speed
    val height = heightText.toIntOrNull()?.toDouble()
    val heightOk = height != null && SessionBuilder.validHeight(height)
    val speedChanged = (newSpeed == null) != (session.speedKmh == null) ||
        (newSpeed != null && session.speedKmh != null && abs(newSpeed - session.speedKmh) > 0.005)
    val heightChanged = height != null && height.roundToInt() != session.runnerHeightCm.roundToInt()
    val canRecalculate = speedOk && heightOk && (speedChanged || heightChanged) && !busy

    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Scale for this clip", icon = LabIcons.ScaleTune)
            if (estimatedCm != null) {
                val estimate = estimatedCm.roundToInt()
                Text("From the belt speed you look about $estimate cm tall.", style = MaterialTheme.typography.titleMedium)
                val gap = abs(estimatedCm - session.runnerHeightCm) / session.runnerHeightCm
                val typed = session.speedKmh
                if (gap > GaitAnalyzer.SPEED_MISMATCH && typed != null) {
                    // Far off: the speed is the likelier mistake, so offer the speed that fits first.
                    val fitting = GaitAnalyzer.fittingSpeedKmh(typed, session.runnerHeightCm, estimatedCm)
                    Text(
                        "That doesn't fit your ${session.runnerHeightCm.roundToInt()} cm, so the treadmill was probably " +
                            "running at about ${"%.1f".format(fitting)} km/h, not ${formatSpeed(typed)}. Enter what it was set to.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { speedText = "%.1f".format(fitting) }) { Text("Try ${"%.1f".format(fitting)} km/h") }
                        TextButton(onClick = { heightText = estimate.toString() }) { Text("Use $estimate cm") }
                    }
                } else if (abs(estimate - session.runnerHeightCm.roundToInt()) >= 8) {
                    Text(
                        "That is a little way from ${session.runnerHeightCm.roundToInt()} cm, so check the speed you entered.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { heightText = estimate.toString() }) { Text("Use $estimate cm for this clip") }
                }
            } else {
                Text(
                    if (session.speedKmh == null) "No treadmill speed, so distances come from the height below."
                    else "The treadmill speed doesn't match the video, so distances come from the height below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { speedText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5); failed = false },
                    label = { Text("Speed (km/h)") },
                    singleLine = true,
                    isError = !speedOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit).take(3); failed = false },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    isError = !heightOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (session.metrics?.view != CameraView.SIDE && session.metrics != null) {
                    // From in front or behind the belt moves towards or away from the camera, so it can't give a scale.
                    "From this angle distances always come from your height. The speed only sets what counts as a good step rate. " +
                        "Speed 3 to 30 km/h, height 120 to 220 cm."
                } else {
                    "With a speed, distances come from the belt and height is only a check. Leave the speed blank to use height alone. " +
                        "Speed 3 to 30 km/h, height 120 to 220 cm."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.scaleEdited) {
                Text(
                    "Changed by hand for this clip. Analysing it again keeps these.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed) {
                Text("Couldn't recalculate this clip.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { repo.recalculate(session.id, newSpeed, height!!) }.getOrNull()
                            }
                            busy = false
                            if (result == null) failed = true else onRecalculated()
                        }
                    },
                    enabled = canRecalculate,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Recalculate")
                }
                if (heightOk && height!!.roundToInt() != savedCm.roundToInt()) {
                    TextButton(onClick = {
                        settings.runnerHeightCm = height
                        savedCm = height
                    }) { Text("Make ${height.roundToInt()} cm my default") }
                }
            }
        }
    }
}

@Composable
internal fun FeedbackCard(item: Feedback, badge: Badge?) {
    val good = signalColour(true)
    val (statusText, tint) = when (item.status) {
        FeedbackStatus.GOOD -> "In range" to good
        FeedbackStatus.IMPROVE -> "To work on" to signalColour(false)
        FeedbackStatus.INFO -> "Note" to signalColour(null)
    }
    LabCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LabIcons.forTopic(item.topic), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.topic, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(item.status == FeedbackStatus.GOOD)
                        Text(statusText.uppercase(), style = MaterialTheme.typography.labelSmall, color = tint)
                        badge?.let { BadgePill(it) }
                    }
                }
                CountUpText(
                    item.value,
                    style = monoValue(if (item.value.length <= 8) 18 else 14),
                    colour = tint,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
            item.scale?.let { RangeBar(it, markerColor = tint, goodColor = good) }
            Text(item.message, style = MaterialTheme.typography.bodyMedium)
            item.cue?.let { cue ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(LabIcons.CoachingCue, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Column {
                        Text("COACHING CUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("\"$cue\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item.drill?.let { drill ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(LabIcons.Drill, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Column {
                        Text("PRACTICE DRILL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(drill, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item.metronomeSpm?.let { spm ->
                val context = LocalContext.current
                var set by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        CoachSettings(context).metronomeSpm = spm
                        set = true
                    },
                    enabled = !set,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(LabIcons.Cadence, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (set) "Metronome set to $spm spm" else "Set metronome to $spm spm")
                }
            }
        }
    }
}

/** A small milestone tag next to a measurement, e.g. "First time in range". */
@Composable
private fun BadgePill(badge: Badge) {
    val colour = signalColour(true)
    Text(
        "\u2605 " + badge.kind.label,
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        modifier = Modifier
            .border(1.dp, colour.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
