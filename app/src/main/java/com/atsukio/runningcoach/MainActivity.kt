package com.atsukio.runningcoach

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import com.atsukio.runningcoach.audio.Metronome
import com.atsukio.runningcoach.data.CoachSettings
import com.atsukio.runningcoach.data.CrashLog
import com.atsukio.runningcoach.data.RunRecorder
import com.atsukio.runningcoach.sensors.SensorHub
import com.atsukio.runningcoach.ui.AppearanceState
import com.atsukio.runningcoach.ui.CoachApp
import com.atsukio.runningcoach.ui.CoachTheme
import com.atsukio.runningcoach.ui.ShutterKeys
import com.atsukio.runningcoach.ui.isDarkAppearance

class MainActivity : ComponentActivity() {
    private val shutterKeys = ShutterKeys()

    /** Set by the capture screen so Bluetooth camera remotes can start and stop recording. */
    var onShutterKey: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        AppearanceState.current = CoachSettings(this).appearance
        enableEdgeToEdge()
        setContent {
            // Light system-bar icons on the dark theme and the reverse, following the chosen appearance.
            val dark = isDarkAppearance()
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            CoachTheme {
                CoachApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Let go of the watch when the app is closed, unless a whole run is still going.
        if (isFinishing && RunRecorder.get(this).active.value == null) {
            SensorHub.get(this).disconnect(forget = false)
        }
        if (isFinishing) Metronome.stop()
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
