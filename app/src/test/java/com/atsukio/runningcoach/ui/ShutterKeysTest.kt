package com.atsukio.runningcoach.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutterKeysTest {
    private var presses = 0
    private val keys = ShutterKeys(debounceMs = 600)

    private fun press(keyCode: Int, timeMs: Long, isDown: Boolean = true, repeat: Int = 0) =
        keys.handle(keyCode, isDown, repeat, timeMs) { presses++ }

    @Test
    fun volumeUpTriggersOncePerPress() {
        assertTrue(press(KeyEvent.KEYCODE_VOLUME_UP, 1000))
        assertTrue(press(KeyEvent.KEYCODE_VOLUME_UP, 1100, isDown = false))
        assertEquals(1, presses)
    }

    @Test
    fun heldKeyDoesNotRepeat() {
        press(KeyEvent.KEYCODE_VOLUME_UP, 1000)
        press(KeyEvent.KEYCODE_VOLUME_UP, 1500, repeat = 1)
        press(KeyEvent.KEYCODE_VOLUME_UP, 2000, repeat = 2)
        assertEquals(1, presses)
    }

    @Test
    fun remotesSendingTwoKeysCountOnce() {
        press(KeyEvent.KEYCODE_VOLUME_UP, 1000)
        press(KeyEvent.KEYCODE_ENTER, 1020)
        assertEquals(1, presses)
    }

    @Test
    fun separatePressesBothCount() {
        press(KeyEvent.KEYCODE_VOLUME_UP, 1000)
        press(KeyEvent.KEYCODE_VOLUME_UP, 2000)
        assertEquals(2, presses)
    }

    @Test
    fun otherKeysAreLeftAlone() {
        assertFalse(press(KeyEvent.KEYCODE_BACK, 1000))
        assertFalse(press(KeyEvent.KEYCODE_A, 1000))
        assertEquals(0, presses)
    }
}
