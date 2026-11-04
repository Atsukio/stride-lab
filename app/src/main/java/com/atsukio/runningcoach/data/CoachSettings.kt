package com.atsukio.runningcoach.data

import android.content.Context
import androidx.core.content.edit

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

    private companion object {
        const val KEY_GPU = "use_gpu"
        const val KEY_HEIGHT = "runner_height_cm"
        const val DEFAULT_HEIGHT_CM = 175f
    }
}
