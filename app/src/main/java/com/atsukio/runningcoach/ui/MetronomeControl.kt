package com.atsukio.runningcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atsukio.runningcoach.audio.Metronome
import com.atsukio.runningcoach.data.CoachSettings

/** Start/stop and step-rate controls for the [Metronome]. The rate is remembered in Settings. */
@Composable
fun MetronomeControl(modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    val context = LocalContext.current
    val settings = remember { CoachSettings(context) }
    val playing by Metronome.playingSpm.collectAsStateWithLifecycle()
    var spm by remember { mutableIntStateOf(playing ?: settings.metronomeSpm) }

    fun change(by: Int) {
        spm = (spm + by).coerceIn(CoachSettings.METRONOME_RANGE)
        settings.metronomeSpm = spm
        if (playing != null) Metronome.start(spm)
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Metronome", color = contentColor, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { change(-2) }, modifier = Modifier.semantics { contentDescription = "Slower" }) {
            Text("−", color = contentColor)
        }
        Text(
            "$spm",
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp).semantics { contentDescription = "$spm steps per minute" },
        )
        OutlinedButton(onClick = { change(2) }, modifier = Modifier.semantics { contentDescription = "Faster" }) {
            Text("+", color = contentColor)
        }
        FilledTonalButton(onClick = { if (playing != null) Metronome.stop() else Metronome.start(spm) }) {
            Text(if (playing != null) "Stop" else "Play")
        }
    }
}
