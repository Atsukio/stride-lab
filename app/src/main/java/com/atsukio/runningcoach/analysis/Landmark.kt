package com.atsukio.runningcoach.analysis

/** MediaPipe Pose landmark indices. */
object Landmark {
    const val COUNT = 33

    const val NOSE = 0
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    val LEFT_CONNECTIONS = listOf(
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST, LEFT_SHOULDER to LEFT_HIP,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE,
        LEFT_ANKLE to LEFT_HEEL, LEFT_HEEL to LEFT_FOOT_INDEX, LEFT_ANKLE to LEFT_FOOT_INDEX,
    )
    val RIGHT_CONNECTIONS = listOf(
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST, RIGHT_SHOULDER to RIGHT_HIP,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE,
        RIGHT_ANKLE to RIGHT_HEEL, RIGHT_HEEL to RIGHT_FOOT_INDEX, RIGHT_ANKLE to RIGHT_FOOT_INDEX,
    )
    val CENTER_CONNECTIONS = listOf(LEFT_SHOULDER to RIGHT_SHOULDER, LEFT_HIP to RIGHT_HIP)
}
