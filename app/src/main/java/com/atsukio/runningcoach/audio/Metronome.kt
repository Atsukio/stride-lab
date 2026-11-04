package com.atsukio.runningcoach.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A click at a set step rate, for practising a quicker cadence. One beat is rendered once and looped by
 * the audio hardware, so the timing is sample-accurate and doesn't drift. It plays over music without
 * pausing it, and keeps going between the capture and run screens.
 */
object Metronome {
    private const val SAMPLE_RATE = 44_100
    private val _playingSpm = MutableStateFlow<Int?>(null)

    /** The step rate being played, or null when stopped. */
    val playingSpm: StateFlow<Int?> = _playingSpm.asStateFlow()

    private var track: AudioTrack? = null

    @Synchronized
    fun start(stepsPerMinute: Int) {
        stop()
        val beat = beat(stepsPerMinute)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(beat.size * 2)
            .build()
        audio.write(beat, 0, beat.size)
        audio.setLoopPoints(0, beat.size, -1)
        audio.play()
        track = audio
        _playingSpm.value = stepsPerMinute
    }

    @Synchronized
    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        _playingSpm.value = null
    }

    /** One beat: a short, bright click followed by silence, lasting exactly 60 / [stepsPerMinute] seconds. */
    internal fun beat(stepsPerMinute: Int): ShortArray {
        val length = (SAMPLE_RATE * 60.0 / stepsPerMinute).roundToInt()
        val click = (SAMPLE_RATE * 0.03).roundToInt()
        return ShortArray(length) { i ->
            if (i >= click) return@ShortArray 0
            val t = i.toDouble() / SAMPLE_RATE
            (sin(2 * PI * 1_500 * t) * exp(-t * 120) * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
    }
}
