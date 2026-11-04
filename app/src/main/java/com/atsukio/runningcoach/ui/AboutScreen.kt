package com.atsukio.runningcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.atsukio.runningcoach.R
import com.atsukio.runningcoach.data.CoachSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App version, privacy summary, and the open-source licences the app has to ship. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    val settings = remember { CoachSettings(context) }
    var useGpu by remember { mutableStateOf(settings.useGpu) }
    var licences by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        licences = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.open_source_licences).bufferedReader().use { it.readText() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            version?.let { Text("Version $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Everything happens on this phone. The app has no internet access, and your videos, " +
                    "results and watch readings are never uploaded or backed up.",
            )
            Text(
                "Released under the MIT License. Copyright (c) 2026 Atsukio. " +
                    "Source code and privacy policy: github.com/Atsukio/stride-lab",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use the GPU for pose detection", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Faster. Turn off if the skeleton doesn't line up with the runner; " +
                            "some phones' graphics drivers give less accurate results.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = useGpu, onCheckedChange = {
                    useGpu = it
                    settings.useGpu = it
                })
            }
            HorizontalDivider()
            Text("Open-source licences", style = MaterialTheme.typography.titleMedium)
            Text(licences, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
