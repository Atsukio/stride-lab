package com.atsukio.runningcoach.ui

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atsukio.runningcoach.sensors.LiveStats
import com.atsukio.runningcoach.sensors.SensorHub
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun rememberSensorHub(): SensorHub {
    val context = LocalContext.current
    return remember { SensorHub.get(context) }
}

/** The latest sensor readings, or null once they are more than a few seconds old. */
@Composable
fun rememberFreshStats(hub: SensorHub): LiveStats? {
    val stats by hub.stats.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    return stats?.takeIf { now - it.updatedAtMs <= SensorHub.STALE_AFTER_MS }
}

fun formatPace(speedMps: Double): String {
    val secondsPerKm = (1000 / speedMps).roundToInt()
    return "%d:%02d /km".format(secondsPerKm / 60, secondsPerKm % 60)
}

fun formatStats(stats: LiveStats): String = listOfNotNull(
    stats.heartRate?.let { "♥ $it" },
    stats.cadenceSpm?.takeIf { it > 0 }?.let { "$it spm" },
    stats.speedMps?.takeIf { it > 0.3 }?.let { formatPace(it) },
).joinToString(" · ")

/** A small pill showing the watch connection and live readings; tap to connect or manage it. */
@Composable
fun LiveStatsChip(modifier: Modifier = Modifier) {
    val hub = rememberSensorHub()
    val status by hub.status.collectAsStateWithLifecycle()
    val stats = rememberFreshStats(hub)
    var showDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hub.reconnectSaved() }

    val text = when (val s = status) {
        is SensorHub.Status.Connected -> stats?.let(::formatStats)?.ifEmpty { null } ?: "${s.name}: waiting for data"
        is SensorHub.Status.Connecting -> "Connecting to ${s.name}…"
        SensorHub.Status.BluetoothOff -> "Bluetooth is off"
        SensorHub.Status.Unavailable -> "No Bluetooth"
        else -> "Connect watch"
    }
    Surface(
        onClick = { showDialog = true },
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
    if (showDialog) SensorDialog(onDismiss = { showDialog = false })
}

@Composable
fun SensorDialog(onDismiss: () -> Unit) {
    val hub = rememberSensorHub()
    val status by hub.status.collectAsStateWithLifecycle()
    val found by hub.found.collectAsStateWithLifecycle()
    val stats = rememberFreshStats(hub)
    var permitted by remember { mutableStateOf(hub.hasPermissions()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = it.values.all { granted -> granted }
    }
    LaunchedEffect(permitted) {
        val busy = status is SensorHub.Status.Connected || status is SensorHub.Status.Connecting
        if (permitted && !busy) hub.startScan()
    }
    DisposableEffect(Unit) { onDispose { hub.stopScan() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watch and sensors") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = status) {
                    is SensorHub.Status.Connected -> {
                        Text("Connected to ${s.name}", style = MaterialTheme.typography.titleSmall)
                        val receiving = listOfNotNull(
                            "heart rate".takeIf { s.heartRate },
                            "pace and cadence".takeIf { s.runningData },
                        )
                        Text(
                            if (receiving.isEmpty()) {
                                "This device isn't sending heart rate or running data."
                            } else {
                                "Receiving ${receiving.joinToString(" and ")}."
                            },
                        )
                        stats?.let { Text(formatStats(it)) }
                        Row {
                            TextButton(onClick = { hub.disconnect(forget = false) }) { Text("Disconnect") }
                            TextButton(onClick = { hub.disconnect(forget = true) }) { Text("Forget") }
                        }
                    }
                    is SensorHub.Status.Connecting -> {
                        Text("Connecting to ${s.name}…")
                        Text("On a Garmin watch, make sure the Virtual Run activity is open.")
                        TextButton(onClick = { hub.disconnect(forget = true) }) { Text("Cancel") }
                    }
                    SensorHub.Status.Unavailable -> Text("This phone doesn't support Bluetooth sensors.")
                    SensorHub.Status.BluetoothOff -> {
                        Text("Turn on Bluetooth to connect a watch or sensor.")
                        TextButton(onClick = hub::startScan) { Text("Try again") }
                    }
                    else -> {
                        Text(
                            "Garmin Forerunner and similar: start the Virtual Run activity on the watch. " +
                                "Other watches: turn on heart rate broadcast. Chest straps and foot pods work too.",
                        )
                        if (!permitted) {
                            Button(onClick = { permissionLauncher.launch(SensorHub.requiredPermissions()) }) {
                                Text("Allow Bluetooth")
                            }
                        } else {
                            if (status == SensorHub.Status.Scanning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("  Searching…")
                                }
                            } else {
                                TextButton(onClick = hub::startScan) { Text("Search again") }
                            }
                            found.forEach { device ->
                                Card(onClick = { hub.connect(device) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(device.name, Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
                Text(
                    "Readings stay on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
