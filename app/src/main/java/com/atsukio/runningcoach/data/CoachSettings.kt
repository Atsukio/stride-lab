package com.atsukio.runningcoach.data

import android.content.Context
import androidx.core.content.edit
import com.atsukio.runningcoach.analysis.FocusPlan
import com.atsukio.runningcoach.ui.Appearance
import kotlinx.serialization.json.Json

class CoachSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Used to turn on-screen distances into centimetres. */
    var runnerHeightCm: Double
        get() = prefs.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT_CM).toDouble()
        set(value) = prefs.edit { putFloat(KEY_HEIGHT, value.toFloat()) }

    /** Pose detection on the GPU as well as the CPU. Off is slower but rules out GPU driver problems. */
    var useGpu: Boolean
        get() = prefs.getBoolean(KEY_GPU, true)
        set(value) = prefs.edit { putBoolean(KEY_GPU, value) }

    /** Find poses while recording, so results are ready almost as soon as the clip ends. */
    var liveAnalysis: Boolean
        get() = prefs.getBoolean(KEY_LIVE, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE, value) }

    /** How long each clip records for, in seconds. */
    var recordSeconds: Int
        get() = prefs.getInt(KEY_RECORD_SECONDS, DEFAULT_RECORD_SECONDS).takeIf { it in RECORD_OPTIONS } ?: DEFAULT_RECORD_SECONDS
        set(value) = prefs.edit { putInt(KEY_RECORD_SECONDS, value) }

    /** Whether the first-run setup guide has been seen. */
    var guideSeen: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_GUIDE_SEEN, value) }

    /** Whether the notification permission prompt has been shown once. */
    var askedNotifications: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, value) }

    /** Speak the focus cue now and then during a whole run, using an offline voice. */
    var spokenCues: Boolean
        get() = prefs.getBoolean(KEY_SPOKEN, false)
        set(value) = prefs.edit { putBoolean(KEY_SPOKEN, value) }

    /** The focus plan being followed, if any. */
    var focusPlan: FocusPlan?
        get() = prefs.getString(KEY_PLAN, null)?.let { runCatching { Json.decodeFromString<FocusPlan>(it) }.getOrNull() }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_PLAN) else putString(KEY_PLAN, Json.encodeToString(value))
        }

    /** Dark, light or the phone's choice, stored by name. */
    var appearance: Appearance
        get() = runCatching { Appearance.valueOf(prefs.getString(KEY_APPEARANCE, null).orEmpty()) }.getOrDefault(Appearance.DARK)
        set(value) = prefs.edit { putString(KEY_APPEARANCE, value.name) }

    /** The metronome's step rate. */
    var metronomeSpm: Int
        get() = prefs.getInt(KEY_METRONOME, DEFAULT_METRONOME_SPM).coerceIn(METRONOME_RANGE)
        set(value) = prefs.edit { putInt(KEY_METRONOME, value.coerceIn(METRONOME_RANGE)) }

    companion object {
        val RECORD_OPTIONS = listOf(10, 15, 20, 30)
        val METRONOME_RANGE = 140..210
        private const val DEFAULT_METRONOME_SPM = 170
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
        private const val KEY_SPOKEN = "spoken_cues"
        private const val KEY_PLAN = "focus_plan"
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_METRONOME = "metronome_spm"
        private const val KEY_GUIDE_SEEN = "guide_seen"
        private const val DEFAULT_RECORD_SECONDS = 15
        private const val KEY_LIVE = "live_analysis"
        private const val KEY_RECORD_SECONDS = "record_seconds"
        private const val KEY_GPU = "use_gpu"
        private const val KEY_HEIGHT = "runner_height_cm"
        private const val DEFAULT_HEIGHT_CM = 175f
    }
}
