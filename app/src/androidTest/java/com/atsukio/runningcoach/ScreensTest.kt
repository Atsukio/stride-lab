package com.atsukio.runningcoach

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atsukio.runningcoach.ui.CoachTheme
import com.atsukio.runningcoach.ui.HomeScreen
import com.atsukio.runningcoach.ui.StorageScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeShowsTheRecordButtonAndItWorks() {
        var recorded = false
        compose.setContent {
            CoachTheme {
                HomeScreen(
                    onNewAnalysis = { recorded = true },
                    onStartRun = {},
                    onOpenRunSummary = {},
                    onOpenSession = {},
                    onOpenAbout = {},
                    onOpenTrends = {},
                    onOpenGuide = {},
                    onOpenAnalysis = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Check your running form").assertIsDisplayed()
        compose.onNodeWithText("Record a clip").performClick()
        assertTrue(recorded)
    }

    @Test
    fun storageExplainsWhatDeletingAVideoKeeps() {
        compose.setContent { CoachTheme { StorageScreen(onBack = {}) } }
        compose.onNodeWithText("Storage").assertIsDisplayed()
        compose.onNodeWithText("All videos").assertIsDisplayed()
        compose.onNodeWithText("Crash log").assertIsDisplayed()
    }
}
