package com.atsukio.runningcoach

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atsukio.runningcoach.ui.CoachApp
import com.atsukio.runningcoach.ui.CoachTheme
import com.atsukio.runningcoach.ui.ShutterKeys

class MainActivity : ComponentActivity() {
    private val shutterKeys = ShutterKeys()

    /** Set by the capture screen so Bluetooth camera remotes can start and stop recording. */
    var onShutterKey: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoachTheme {
                CoachApp()
            }
        }
    }

    // Handled here rather than in Compose because volume keys only reach Compose when something has focus.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleShutter(keyCode, event, isDown = true) || super.onKeyDown(keyCode, event)

    // Consuming the matching key-up stops the volume from changing.
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        handleShutter(keyCode, event, isDown = false) || super.onKeyUp(keyCode, event)

    private fun handleShutter(keyCode: Int, event: KeyEvent, isDown: Boolean): Boolean {
        val handler = onShutterKey ?: return false
        return shutterKeys.handle(keyCode, isDown, event.repeatCount, event.eventTime, handler)
    }
}
