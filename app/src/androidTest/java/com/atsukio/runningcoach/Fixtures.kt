package com.atsukio.runningcoach

import com.atsukio.runningcoach.analysis.GaitMetrics
import com.atsukio.runningcoach.data.Session

/** A made-up result, so the tests never need a camera or pose detection. */
object Fixtures {
    fun metrics(contactFrames: List<Int> = emptyList()) = GaitMetrics(
        cadenceSpm = 158.0, overstrideCm = 27.0, overstrideLegRatio = 0.33, shinAngleDeg = 9.0,
        kneeFlexionAtContactDeg = 14.0, peakStanceKneeFlexionDeg = 38.0, verticalOscillationCm = 8.2,
        trunkLeanDeg = 5.0, elbowAngleDeg = 88.0, overstrideAsymmetryCm = 3.0, stepCount = 28,
        contactFrames = contactFrames,
    )

    fun session(id: String, createdAt: Long = System.currentTimeMillis()) = Session(
        id = id, createdAt = createdAt, speedKmh = 10.5, runnerHeightCm = 175.0, fps = 60f, playbackFps = 60f,
        videoWidth = 1080, videoHeight = 1920, metrics = metrics(),
    )
}
