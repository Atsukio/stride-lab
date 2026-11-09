package com.atsukio.runningcoach.analysis

/**
 * A stretch of a clip from one foot landing to a later one. Two clips are compared by looping the same
 * number of landings from each, so both runners are at the same point of their stride at the same moment.
 */
class StrideWindow(val startFrame: Int, val endFrame: Int) {
    val length: Int get() = endFrame - startFrame

    companion object {
        /** Two landings is one left-and-right stride. */
        const val STEPS = 2

        /** How many windows of [steps] landings a clip with these [contacts] has. */
        fun count(contacts: List<Int>, steps: Int = STEPS): Int = (contacts.size - steps).coerceAtLeast(0)

        /** The window starting at landing [index], or null if the clip doesn't have that many landings. */
        fun at(contacts: List<Int>, index: Int, steps: Int = STEPS): StrideWindow? {
            if (steps < 1 || index < 0 || index + steps > contacts.size - 1) return null
            val window = StrideWindow(contacts[index], contacts[index + steps])
            return window.takeIf { it.length > 0 }
        }

        /**
         * The playback speed for the second clip so its window lasts as long as the first clip's does at
         * [speed]. Each window is measured in frames, so each clip's frame rate is needed.
         */
        fun matchingSpeed(speed: Float, first: StrideWindow, firstFps: Float, second: StrideWindow, secondFps: Float): Float {
            val firstSeconds = first.length / firstFps.toDouble()
            val secondSeconds = second.length / secondFps.toDouble()
            return (speed * secondSeconds / firstSeconds).toFloat()
        }
    }
}
