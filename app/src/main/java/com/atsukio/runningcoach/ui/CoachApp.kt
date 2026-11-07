package com.atsukio.runningcoach.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.RunRecorder

private const val CAPTURE_ROUTE = "capture?runId={runId}&autoStart={autoStart}"

@Composable
fun CoachApp() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val runRecorder = remember { RunRecorder.get(context) }
    val settings = remember { CoachSettings(context) }
    // First launch opens the setup guide; after that it's a tap away on the home screen.
    NavHost(navController = nav, startDestination = if (settings.guideSeen) "home" else "guide") {
        composable("guide") {
            GuideScreen(onDone = {
                settings.guideSeen = true
                if (!nav.popBackStack()) nav.navigate("home") { popUpTo("guide") { inclusive = true } }
            })
        }
        composable("trends") { TrendsScreen(onBack = { nav.popBackStack() }) }
        composable("home") {
            HomeScreen(
                onNewAnalysis = { nav.navigate("capture") },
                onStartRun = {
                    runRecorder.start()
                    nav.navigate("run")
                },
                onOpenRunSummary = { id -> nav.navigate("runsummary/$id") },
                onOpenSession = { id -> nav.navigate("result/$id") },
                onOpenAbout = { nav.navigate("about") },
                onOpenTrends = { nav.navigate("trends") },
                onOpenGuide = { nav.navigate("guide") },
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
            "analyse/{id}?reanalyse={reanalyse}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("reanalyse") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val reanalyse = entry.arguments?.getBoolean("reanalyse") ?: false
            AnalysisScreen(
                onFinished = { id, runId ->
                    if (reanalyse) {
                        // Replace the old results screen with a fresh one.
                        nav.navigate("result/$id") { popUpTo("result/{id}") { inclusive = true } }
                    } else if (runId != null && runRecorder.active.value?.id == runId) {
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
            val id = entry.arguments?.getString("id").orEmpty()
            ResultScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onReanalyse = { nav.navigate("analyse/$id?reanalyse=true") },
            )
        }
        composable("about") {
            AboutScreen(onBack = { nav.popBackStack() }, onOpenStorage = { nav.navigate("storage") })
        }
        composable("storage") { StorageScreen(onBack = { nav.popBackStack() }) }
        composable("runsummary/{id}") { entry ->
            RunSummaryScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenSession = { id -> nav.navigate("result/$id") },
            )
        }
    }
}
