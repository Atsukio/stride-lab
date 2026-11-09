package com.atsukio.runningcoach.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.FocusPlan
import java.util.Locale

/** When a whole run speaks its cue: once after a minute, then every five minutes. */
object CueSchedule {
    const val FIRST_CUE_SEC = 60.0
    const val EVERY_SEC = 5 * 60.0

    /** The number of the cue that is due at [elapsedSec] (0 for the first), or -1 before the first. */
    fun dueIndex(elapsedSec: Double): Int =
        if (elapsedSec < FIRST_CUE_SEC) -1 else ((elapsedSec - FIRST_CUE_SEC) / EVERY_SEC).toInt()

    /** What to say: the focus plan's cue if there is one, otherwise the main focus or foot strike cue of the latest clip. */
    fun cue(plan: FocusPlan?, latestFeedback: List<Feedback>): String? =
        plan?.cue ?: Coach.mainFocus(latestFeedback)?.cue ?: latestFeedback.firstOrNull { it.topic == "Foot strike" && it.cue != null }?.cue
}

/**
 * Speaks short cues through the phone's own text-to-speech. Only voices that work without a network are
 * used, so nothing about a run is sent anywhere; if the phone has none, [offlineVoiceReady] stays false
 * and the cues stay silent.
 */
class SpokenCues(context: Context) {
    /** True once the engine is up and has an offline voice for the phone's language. */
    var offlineVoiceReady by mutableStateOf(false)
        private set

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val language = Locale.getDefault().language
            val voice: Voice? = runCatching {
                tts.voices.orEmpty()
                    .filter { !it.isNetworkConnectionRequired && it.locale.language == language }
                    .minByOrNull { it.quality.let { q -> -q } }
            }.getOrNull()
            if (voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) offlineVoiceReady = true
        }
    }

    fun speak(text: String) {
        if (offlineVoiceReady) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        offlineVoiceReady = false
    }
}
