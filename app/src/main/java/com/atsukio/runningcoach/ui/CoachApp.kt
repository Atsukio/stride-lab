package com.atsukio.runningcoach.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atsukio.runningcoach.data.RunRecorder

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF7FD8B6))
        else -> lightColorScheme(primary = Color(0xFF1E6B52))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private const val CAPTURE_ROUTE = "capture?runId={runId}&autoStart={autoStart}"

@Composable
fun CoachApp() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val runRecorder = remember { RunRecorder.get(context) }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNewAnalysis = { nav.navigate("capture") },
                onStartRun = {
                    runRecorder.start()
                    nav.navigate("run")
                },
                onOpenRunSummary = { id -> nav.navigate("runsummary/$id") },
                onOpenSession = { id -> nav.navigate("result/$id") },
            )
        }
        composable("run") {
            RunScreen(
                onTakeClip = { autoStart ->
                    val runId = runRecorder.active.value?.id ?: return@RunScreen
                    nav.navigate("capture?runId=$runId&autoStart=$autoStart")
                },
                onOpenSession = { id -> nav.navigate("result/$id") },
                onFinished = { runId -> nav.navigate("runsummary/$runId") { popUpTo("home") } },
                onExit = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable(
            CAPTURE_ROUTE,
            arguments = listOf(
                navArgument("runId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            CaptureScreen(
                runId = entry.arguments?.getString("runId"),
                autoStart = entry.arguments?.getBoolean("autoStart") ?: false,
                onBack = { nav.popBackStack() },
                onVideoReady = { id ->
                    nav.navigate("analyse/$id") { popUpTo(CAPTURE_ROUTE) { inclusive = true } }
                },
            )
        }
        composable(
            "analyse/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            AnalysisScreen(
                onFinished = { id, runId ->
                    if (runId != null && runRecorder.active.value?.id == runId) {
                        // Back to the run screen, ready for the next clip.
                        nav.popBackStack()
                    } else {
                        nav.navigate("result/$id") { popUpTo("home") }
                    }
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable("result/{id}") { entry ->
            ResultScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("runsummary/{id}") { entry ->
            RunSummaryScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenSession = { id -> nav.navigate("result/$id") },
            )
        }
    }
}
