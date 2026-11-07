package com.atsukio.runningcoach.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atsukio.runningcoach.data.CrashLog
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.data.outdatedClips
import com.atsukio.runningcoach.data.remeasureOutdated
import androidx.compose.runtime.LaunchedEffect
import com.atsukio.runningcoach.share.TextSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Where the phone's space goes, with ways to free it: videos can go while results are kept. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf(repo.storageUsage()) }
    var crashLog by remember { mutableStateOf(CrashLog.read(context)) }
    var pendingDelete by remember { mutableStateOf<Pair<String, Long?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var outdated by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { outdated = withContext(Dispatchers.IO) { repo.outdatedClips().size } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Videos use ${megabytes(usage.videoBytes)}.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Results, poses and watch readings use ${megabytes(usage.otherBytes)}. Deleting a video keeps " +
                    "its results, charts and skeleton view, so the session still shows what it found.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Text("Remove videos", style = MaterialTheme.typography.titleSmall)
            listOf(
                "Older than a week" to 7L,
                "Older than a month" to 30L,
                "All videos" to null,
            ).forEach { (label, days) ->
                OutlinedButton(
                    onClick = { pendingDelete = label to days },
                    enabled = !busy && usage.videoBytes > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label) }
            }

            HorizontalDivider()
            Text("Older results", style = MaterialTheme.typography.titleSmall)
            val count = outdated
            when {
                count == null -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                count == 0 -> Text("Every clip is measured the current way.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Text(
                        "$count clip(s) were measured by an older version, so their figures can differ a little " +
                            "from new clips. Measuring them again uses their saved poses and each clip's own speed " +
                            "and height, and takes a few seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                val updated = withContext(Dispatchers.IO) { repo.remeasureOutdated() }
                                outdated = withContext(Dispatchers.IO) { repo.outdatedClips().size }
                                busy = false
                                Toast.makeText(context, "Measured $updated clip(s) again.", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Measure them again") }
                }
            }

            HorizontalDivider()
            Text("Crash log", style = MaterialTheme.typography.titleSmall)
            if (crashLog.isEmpty()) {
                Text("No crashes recorded.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "${crashLog.lines().count { it.startsWith("--- ") }} crash(es) recorded. " +
                        "The log stays on this phone unless you share it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = {
                    context.startActivity(TextSharer.prepare(context, "stride-lab-crash-log.txt", crashLog, "Stride Lab crash log"))
                }) { Text("Share crash log") }
                TextButton(onClick = {
                    CrashLog.clear(context)
                    crashLog = ""
                }) { Text("Clear crash log") }
            }
        }
    }

    pendingDelete?.let { (label, days) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove videos?") },
            text = { Text("$label will be removed from this phone. Their results are kept. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    busy = true
                    scope.launch {
                        val cutoff = if (days == null) Long.MAX_VALUE else System.currentTimeMillis() - days * DAY_MS
                        val (count, freed) = withContext(Dispatchers.IO) { repo.deleteVideosBefore(cutoff) }
                        usage = repo.storageUsage()
                        busy = false
                        Toast.makeText(
                            context,
                            "Removed $count video(s), freed ${megabytes(freed)}.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

internal fun megabytes(bytes: Long): String =
    if (bytes < 1_000_000) "%.0f KB".format(Locale.US, bytes / 1_000.0)
    else "%.1f MB".format(Locale.US, bytes / 1_000_000.0)
