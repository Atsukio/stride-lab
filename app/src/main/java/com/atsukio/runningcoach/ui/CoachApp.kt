package com.atsukio.runningcoach.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

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

@Composable
fun CoachApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNewAnalysis = { nav.navigate("capture") },
                onOpenSession = { id -> nav.navigate("result/$id") },
            )
        }
        composable("capture") {
            CaptureScreen(
                onBack = { nav.popBackStack() },
                onVideoReady = { id, speedKmh ->
                    nav.navigate("analyse/$id?speed=${speedKmh ?: ""}") { popUpTo("home") }
                },
            )
        }
        composable(
            "analyse/{id}?speed={speed}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("speed") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            AnalysisScreen(
                onFinished = { id -> nav.navigate("result/$id") { popUpTo("home") } },
                onCancel = { nav.popBackStack() },
            )
        }
        composable("result/{id}") { entry ->
            ResultScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
