package com.atsukio.runningcoach.ui

import android.view.KeyEvent

/**
 * Turns key presses from Bluetooth camera remotes into single shutter presses.
 *
 * Most remotes pair as a keyboard and send volume up; others send volume down, Enter or
 * play/pause, and some send two of these for one press, so presses close together count once.
 */
class ShutterKeys(private val debounceMs: Long = 600) {
    private var lastPressAt: Long? = null

    /** Returns true if [keyCode] is a shutter key (so it should be consumed); runs [onPress] once per press. */
    fun handle(keyCode: Int, isDown: Boolean, repeatCount: Int, timeMs: Long, onPress: () -> Unit): Boolean {
        if (keyCode !in KEYS) return false
        val last = lastPressAt
        if (isDown && repeatCount == 0 && (last == null || timeMs - last >= debounceMs)) {
            lastPressAt = timeMs
            onPress()
        }
        return true
    }

    companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )
    }
}
