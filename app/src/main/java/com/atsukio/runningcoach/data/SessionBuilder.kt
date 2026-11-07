package com.atsukio.runningcoach.data

import com.atsukio.runningcoach.analysis.CrossCheck
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitResult
import com.atsukio.runningcoach.analysis.PoseTrack

/** Turns a pose track and what's known about the run into a [Session]. Shared by analysis and recalculation. */
internal object SessionBuilder {
    fun build(
        id: String,
        createdAt: Long,
        track: PoseTrack,
        playbackFps: Float,
        heightCm: Double,
        capture: CaptureInfo,
        extractionNotes: List<String>,
        processingSec: Double?,
        analysedLive: Boolean,
        scaleEdited: Boolean,
    ): Session {
        val result = GaitAnalyzer.analyze(track, heightCm, beltSpeedKmh = capture.speedKmh)
        val success = result as? GaitResult.Success
        val watchScaleNote = if (success?.metrics?.scaledFromTreadmill == true && capture.speedFromSensor) {
            "Distances were scaled from your watch's pace. Typing the treadmill's own speed is more accurate."
        } else {
            null
        }
        val videoCadence = success?.metrics?.cadenceSpm
        val watchCadence = capture.sensor?.cadenceSpm
        val crossCheck = if (videoCadence != null && watchCadence != null) CrossCheck.cadence(videoCadence, watchCadence) else null
        return Session(
            id = id,
            createdAt = createdAt,
            speedKmh = capture.speedKmh,
            speedFromSensor = capture.speedFromSensor,
            runnerHeightCm = heightCm,
            fps = track.fps,
            playbackFps = playbackFps,
            videoWidth = track.width,
            videoHeight = track.height,
            metrics = success?.metrics,
            warnings = success?.warnings.orEmpty() + extractionNotes + listOfNotNull(crossCheck, watchScaleNote),
            failure = (result as? GaitResult.Failure)?.reason,
            sensor = capture.sensor,
            runId = capture.runId,
            runOffsetSec = capture.runOffsetSec,
            processingSec = processingSec,
            analysedLive = analysedLive,
            scaleEdited = scaleEdited,
            extractionNotes = extractionNotes,
            measureVersion = MEASURE_VERSION,
        )
    }

    /**
     * Raised whenever a change to the measuring code moves the figures, so older clips can be measured again
     * and trends compare like with like. 1: landings measured between frames. 2: the landing foot is the front
     * one, and left/right balance compares medians.
     */
    const val MEASURE_VERSION = 2

    /** The speed and height a person may type for one clip: a speed of 3 to 30 km/h (or none) and a height of 120 to 220 cm. */
    fun validSpeed(kmh: Double?): Boolean = kmh == null || kmh in 3.0..30.0

    fun validHeight(cm: Double): Boolean = cm in 120.0..220.0
}

/**
 * Measures a saved clip again with a speed and height chosen for that clip alone. It reuses the saved poses,
 * so it takes a moment and finds the same landings. Returns null if the clip or its poses are gone.
 */
internal fun SessionRepository.recalculate(id: String, speedKmh: Double?, heightCm: Double): Session? =
    remeasure(id, speedKmh, heightCm, byHand = true)

/** Clips whose figures came from an older version of the measuring code and can be measured again. */
internal fun SessionRepository.outdatedClips(): List<Session> =
    list().filter { it.measureVersion < SessionBuilder.MEASURE_VERSION && hasPose(it.id) }

/**
 * Measures every outdated clip again with its own speed and height, so old and new clips are comparable.
 * Returns how many were updated.
 */
internal fun SessionRepository.remeasureOutdated(): Int =
    outdatedClips().count { remeasure(it.id, it.speedKmh, it.runnerHeightCm, byHand = false) != null }

/** [byHand] is true when the person typed the speed or height for this clip, which marks it as edited. */
private fun SessionRepository.remeasure(id: String, speedKmh: Double?, heightCm: Double, byHand: Boolean): Session? {
    val old = get(id) ?: return null
    val track = loadPose(id) ?: return null
    // Rebuilt from the session, which holds everything the capture file did, in case that file is missing.
    val capture = CaptureInfo(
        speedKmh = speedKmh,
        // A typed speed replaces a watch reading.
        speedFromSensor = if (byHand) false else old.speedFromSensor,
        sensor = old.sensor,
        runId = old.runId,
        runOffsetSec = old.runOffsetSec,
    )
    saveCaptureInfo(id, capture)
    val session = SessionBuilder.build(
        id = id,
        createdAt = old.createdAt,
        track = track,
        playbackFps = old.playbackFps,
        heightCm = heightCm,
        capture = capture,
        // Clips saved before notes were stored still carry the frame-rate note in their warnings.
        extractionNotes = old.extractionNotes.ifEmpty { old.warnings.filter { it.startsWith("The camera's frame rate dropped") } },
        processingSec = old.processingSec,
        analysedLive = old.analysedLive,
        scaleEdited = byHand || old.scaleEdited,
    )
    save(session)
    return session
}
