package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates a side-on stick-figure runner with known cadence, landing distance, lean and bounce.
 * Each leg is two 200 px segments, so the analyser should find a 400 px leg.
 */
internal class SyntheticRunner(
    val cadence: Double = 170.0,
    val landingAheadPx: Double = 60.0,
    val leanDeg: Double = 6.0,
    val bouncePx: Double = 40.0,
    val facingRight: Boolean = true,
    val shoulderSpreadPx: Double = 4.0,
    val fps: Float = 60f,
    val heelStrikeOffsetPx: Double = 0.0,
) {
    private val width = 1920
    private val height = 1080
    private val groundY = 1000.0
    private val hipHeight = 360.0
    private val segment = 200.0
    private val pushOffBehindPx = 110.0
    private val liftPx = 150.0
    private val stanceFraction = 0.35
    private val dir = if (facingRight) 1.0 else -1.0

    /** Treadmill speed that matches the stance foot's movement, for a runner of [heightCm]. */
    fun beltSpeedKmh(heightCm: Double): Double {
        val pxPerSecond = (landingAheadPx + pushOffBehindPx) / (stanceFraction * 2 * 60.0 / cadence)
        val cmPerPx = 0.49 * heightCm / (2 * segment)
        return pxPerSecond * cmPerPx / 100 * 3.6
    }

    /** [phaseFrames] moves where the camera's frames fall in the runner's stride, as a different clip start would. */
    fun track(seconds: Double, phaseFrames: Double = 0.0): PoseTrack {
        val frames = (0 until (seconds * fps).toInt()).map { frame((it + phaseFrames) / fps.toDouble()) }
        return PoseTrack(fps, width, height, frames)
    }

    private fun frame(t: Double): FramePose {
        val stride = 2 * 60.0 / cadence
        val step = stride / 2
        val hip = Vec2(960.0, groundY - hipHeight - bouncePx / 2 * cos(2 * PI * t / step))
        val shoulder = hip + Vec2(dir * sin(Math.toRadians(leanDeg)) * 300, -cos(Math.toRadians(leanDeg)) * 300)

        val points = Array(Landmark.COUNT) { Vec2(hip.x, hip.y) }
        fun set(i: Int, p: Vec2) { points[i] = p }

        set(Landmark.LEFT_HIP, hip + Vec2(-1.0, 0.0))
        set(Landmark.RIGHT_HIP, hip + Vec2(1.0, 0.0))
        set(Landmark.LEFT_SHOULDER, shoulder + Vec2(-shoulderSpreadPx / 2, 0.0))
        set(Landmark.RIGHT_SHOULDER, shoulder + Vec2(shoulderSpreadPx / 2, 0.0))
        set(Landmark.NOSE, shoulder + Vec2(dir * 25, -100.0))
        set(Landmark.LEFT_EAR, shoulder + Vec2(-dir * 5, -105.0))
        set(Landmark.RIGHT_EAR, shoulder + Vec2(-dir * 5, -105.0))
        for ((sh, el, wr) in listOf(
            Triple(Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST),
            Triple(Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST),
        )) {
            val elbow = points[sh] + Vec2(0.0, 130.0)
            set(el, elbow)
            set(wr, elbow + Vec2(dir * 110, 0.0))
        }

        for ((phaseOffset, ids) in listOf(
            0.0 to intArrayOf(Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX),
            0.5 to intArrayOf(Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX),
        )) {
            val p = ((t / stride + phaseOffset) % 1.0)
            val ankle = if (p < stanceFraction) {
                val q = p / stanceFraction
                Vec2(hip.x + dir * (landingAheadPx - (landingAheadPx + pushOffBehindPx) * q), groundY)
            } else {
                val q = (p - stanceFraction) / (1 - stanceFraction)
                val x = -pushOffBehindPx + (landingAheadPx + pushOffBehindPx) * (1 - cos(PI * q)) / 2
                Vec2(hip.x + dir * x, groundY - liftPx * sin(PI * q))
            }
            set(ids[0], knee(hip, ankle))
            set(ids[1], ankle)
            set(ids[2], ankle + Vec2(-dir * 12, 10.0 + heelStrikeOffsetPx))
            set(ids[3], ankle + Vec2(dir * 45, 14.0 - heelStrikeOffsetPx))
        }

        return FramePose(
            x = FloatArray(Landmark.COUNT) { (points[it].x / width).toFloat() },
            y = FloatArray(Landmark.COUNT) { (points[it].y / height).toFloat() },
            visibility = FloatArray(Landmark.COUNT) { 1f },
        )
    }

    /** Two-segment inverse kinematics with the knee pointing forwards. */
    private fun knee(hip: Vec2, ankle: Vec2): Vec2 {
        val d = (ankle - hip).length.coerceAtMost(2 * segment * 0.999)
        val u = (ankle - hip) * (1 / (ankle - hip).length)
        val mid = hip + u * (d / 2)
        val h = sqrt(segment * segment - (d / 2) * (d / 2))
        val normal = Vec2(u.y, -u.x).let { if (dir * it.x > 0) it else it * -1.0 }
        return mid + normal * h
    }
}

class GaitAnalyzerTest {
    private val heightCm = 175.0
    private val cmPerPx = 0.49 * heightCm / 400

    private fun analyze(runner: SyntheticRunner, seconds: Double = 12.0): GaitResult =
        GaitAnalyzer.analyze(runner.track(seconds), heightCm)

    private fun metricsOf(result: GaitResult): GaitMetrics {
        assertTrue("Expected success but got $result", result is GaitResult.Success)
        return (result as GaitResult.Success).metrics
    }

    @Test
    fun landingFiguresDoNotJumpWithTheFramePhase() {
        // A guard that where the frames fall in the stride doesn't change the figures. This synthetic foot is
        // nearly still when it crosses the landing line, so it passes without interpolation too: the real check
        // is GpuDiagnosticTest on a real clip.
        val runner = SyntheticRunner(fps = 30f)
        val results = listOf(0.0, 0.25, 0.5, 0.75).map {
            metricsOf(GaitAnalyzer.analyze(runner.track(12.0, it), heightCm))
        }
        val overstride = results.map { it.overstrideCm!! }
        val shin = results.map { it.shinAngleDeg!! }
        assertTrue("overstride spread was $overstride", overstride.max() - overstride.min() < 1.5)
        assertTrue("shin spread was $shin", shin.max() - shin.min() < 2.0)
    }

    @Test
    fun aWrongTreadmillSpeedGetsTheSpeedThatFits() {
        val runner = SyntheticRunner()
        val actual = runner.beltSpeedKmh(heightCm)
        // Typing 20% too fast, close to the 6.2 km/h entry for a treadmill really at 5 (24%), but keeping the
        // implied height (about 210 cm) inside the range where the belt is still used.
        val result = GaitAnalyzer.analyze(runner.track(12.0), heightCm, beltSpeedKmh = actual * 1.2) as GaitResult.Success
        val warning = result.warnings.single { "doesn't fit your height" in it }
        val suggested = Regex("""about ([\d.,]+) km/h""").find(warning)!!.groupValues[1].replace(',', '.').toDouble()
        assertEquals(actual, suggested, 0.15)
        assertEquals(heightCm * 1.2, result.metrics.estimatedHeightCm!!, 5.0)
    }

    @Test
    fun aCloseSpeedGetsNoSpeedWarning() {
        val runner = SyntheticRunner()
        val result = GaitAnalyzer.analyze(runner.track(12.0), heightCm, beltSpeedKmh = runner.beltSpeedKmh(heightCm) * 1.05) as GaitResult.Success
        assertTrue(result.warnings.none { "doesn't fit your height" in it })
    }

    @Test
    fun fittingSpeedScalesWithTheHeightGap() {
        // The Pixel clip: 6.2 km/h typed, 216 cm implied, runner 165 cm.
        assertEquals(4.74, GaitAnalyzer.fittingSpeedKmh(6.2, 165.0, 216.0), 0.01)
    }

    @Test
    fun landingTimeIsInterpolatedBetweenFrames() {
        // Heel crosses the level of 100 a quarter of the way from frame 1 (y 80) to frame 2 (y 160)
        val heel = doubleArrayOf(0.0, 80.0, 160.0)
        assertEquals(1.25, GaitAnalyzer.landingTime(heel, 2, 100.0), 1e-9)
        // No crossing between the frames: stays on the detected frame
        assertEquals(2.0, GaitAnalyzer.landingTime(heel, 2, 70.0), 0.0)
        assertEquals(0.0, GaitAnalyzer.landingTime(heel, 0, 100.0), 0.0)
    }

    @Test
    fun measuresKnownRunner() {
        val m = metricsOf(analyze(SyntheticRunner()))
        assertEquals(170.0, m.cadenceSpm!!, 2.0)
        assertEquals(60 * cmPerPx, m.overstrideCm!!, 2.0)
        assertEquals(6.0, m.trunkLeanDeg!!, 1.0)
        assertEquals(40 * cmPerPx, m.verticalOscillationCm!!, 1.5)
        assertEquals(90.0, m.elbowAngleDeg!!, 3.0)
        assertEquals(0.0, m.overstrideAsymmetryCm!!, 1.0)
        assertTrue(m.stepCount >= 30)
    }

    @Test
    fun facingLeftGivesTheSameResult() {
        val right = metricsOf(analyze(SyntheticRunner(facingRight = true)))
        val left = metricsOf(analyze(SyntheticRunner(facingRight = false)))
        assertEquals(right.overstrideCm!!, left.overstrideCm!!, 0.5)
        assertEquals(right.trunkLeanDeg!!, left.trunkLeanDeg!!, 0.5)
        assertEquals(right.shinAngleDeg!!, left.shinAngleDeg!!, 0.5)
    }

    @Test
    fun cadenceFollowsTheRunner() {
        val m = metricsOf(analyze(SyntheticRunner(cadence = 150.0)))
        assertEquals(150.0, m.cadenceSpm!!, 2.0)
    }

    @Test
    fun worksAtThirtyFramesPerSecondWithAWarning() {
        val result = analyze(SyntheticRunner(fps = 30f))
        val m = metricsOf(result)
        assertEquals(170.0, m.cadenceSpm!!, 3.0)
        assertTrue((result as GaitResult.Success).warnings.any { "fps" in it })
    }

    @Test
    fun treadmillSpeedGivesHeightAndScale() {
        val runner = SyntheticRunner()
        val trueHeight = 185.0
        val result = GaitAnalyzer.analyze(runner.track(12.0), runnerHeightCm = 165.0, beltSpeedKmh = runner.beltSpeedKmh(trueHeight))
        val m = metricsOf(result)
        assertEquals(trueHeight, m.estimatedHeightCm!!, 5.0)
        assertTrue(m.scaledFromTreadmill)
        // Distances follow the true height, not the wrong 165 cm setting.
        assertEquals(60 * 0.49 * trueHeight / 400, m.overstrideCm!!, 2.0)
        // 12% off: a gentle check, not the wrong-speed warning.
        val warnings = (result as GaitResult.Success).warnings
        assertTrue(warnings.any { "you look about" in it })
        assertTrue(warnings.none { "doesn't fit your height" in it })
    }

    @Test
    fun matchingHeightGivesNoWarning() {
        val runner = SyntheticRunner()
        val result = GaitAnalyzer.analyze(runner.track(12.0), runnerHeightCm = 175.0, beltSpeedKmh = runner.beltSpeedKmh(175.0))
        assertEquals(175.0, metricsOf(result).estimatedHeightCm!!, 5.0)
        assertTrue((result as GaitResult.Success).warnings.isEmpty())
    }

    @Test
    fun treadmillCalibrationWorksFacingLeftAndAtThirtyFps() {
        val runner = SyntheticRunner(facingRight = false, fps = 30f)
        val m = metricsOf(GaitAnalyzer.analyze(runner.track(12.0), heightCm, runner.beltSpeedKmh(180.0)))
        assertEquals(180.0, m.estimatedHeightCm!!, 7.0)
    }

    @Test
    fun implausibleSpeedFallsBackToHeightSetting() {
        val runner = SyntheticRunner()
        val result = GaitAnalyzer.analyze(runner.track(12.0), heightCm, beltSpeedKmh = runner.beltSpeedKmh(175.0) * 3)
        val m = metricsOf(result)
        assertEquals(null, m.estimatedHeightCm)
        assertTrue(!m.scaledFromTreadmill)
        assertEquals(60 * cmPerPx, m.overstrideCm!!, 2.0)
        assertTrue((result as GaitResult.Success).warnings.any { "doesn't fit your height" in it && "based on your height" in it })
    }

    @Test
    fun noSpeedMeansNoEstimate() {
        val m = metricsOf(analyze(SyntheticRunner()))
        assertEquals(null, m.estimatedHeightCm)
        assertTrue(!m.scaledFromTreadmill)
    }

    @Test
    fun aSquareOnClipIsNotMeasuredAsSideOn() {
        // Wide shoulders mean a front or rear view, which FrontalAnalyzer measures (see FrontalAnalyzerTest).
        val result = analyze(SyntheticRunner(shoulderSpreadPx = 170.0))
        assertTrue(result is GaitResult.Failure || (result as GaitResult.Success).metrics.view != CameraView.SIDE)
    }

    @Test
    fun rejectsShortClip() {
        assertTrue(analyze(SyntheticRunner(), seconds = 2.0) is GaitResult.Failure)
    }

    @Test
    fun rejectsClipWithoutRunner() {
        val empty = PoseTrack(60f, 1920, 1080, List(600) { null })
        assertTrue(GaitAnalyzer.analyze(empty, heightCm) is GaitResult.Failure)
    }

    @Test
    fun identifiesFootStrikeTypesAndRatios() {
        val midfoot = metricsOf(analyze(SyntheticRunner(heelStrikeOffsetPx = 0.0)))
        assertEquals(1.0, midfoot.midfootStrikeRatio!!, 0.05)
        assertEquals(0.0, midfoot.heelStrikeRatio!!, 0.05)
        assertEquals(0.0, midfoot.forefootStrikeRatio!!, 0.05)
        assertEquals(FootStrikeType.MIDFOOT, midfoot.primaryStrikeType)
        assertTrue(midfoot.footStrikeSummary!!.contains("Midfoot"))

        val heel = metricsOf(analyze(SyntheticRunner(heelStrikeOffsetPx = 15.0)))
        assertEquals(1.0, heel.heelStrikeRatio!!, 0.05)
        assertEquals(0.0, heel.midfootStrikeRatio!!, 0.05)
        assertEquals(0.0, heel.forefootStrikeRatio!!, 0.05)
        assertEquals(FootStrikeType.HEEL, heel.primaryStrikeType)
        assertTrue(heel.footStrikeSummary!!.contains("Heel"))

        val forefoot = metricsOf(analyze(SyntheticRunner(heelStrikeOffsetPx = -15.0)))
        assertEquals(1.0, forefoot.forefootStrikeRatio!!, 0.05)
        assertEquals(0.0, forefoot.midfootStrikeRatio!!, 0.05)
        assertEquals(0.0, forefoot.heelStrikeRatio!!, 0.05)
        assertEquals(FootStrikeType.FOREFOOT, forefoot.primaryStrikeType)
        assertTrue(forefoot.footStrikeSummary!!.contains("Forefoot"))
    }
}

class CoachTest {
    private fun metrics(
        cadence: Double = 172.0,
        overstrideRatio: Double = 0.1,
        bounce: Double = 7.0,
    ) = GaitMetrics(
        cadenceSpm = cadence,
        overstrideCm = overstrideRatio * 86,
        overstrideLegRatio = overstrideRatio,
        shinAngleDeg = 4.0,
        kneeFlexionAtContactDeg = 18.0,
        peakStanceKneeFlexionDeg = 42.0,
        verticalOscillationCm = bounce,
        trunkLeanDeg = 6.0,
        elbowAngleDeg = 90.0,
        overstrideAsymmetryCm = 2.0,
        stepCount = 40,
        contactFrames = emptyList(),
    )

    @Test
    fun goodFormHasNothingToFix() {
        val feedback = Coach.review(metrics(), speedKmh = 10.0)
        assertEquals(null, Coach.mainFocus(feedback))
        assertTrue(feedback.all { it.status == FeedbackStatus.GOOD })
    }

    @Test
    fun problemsAreListedFirst() {
        val feedback = Coach.review(metrics(cadence = 150.0, overstrideRatio = 0.35), speedKmh = 10.0)
        assertEquals(FeedbackStatus.IMPROVE, feedback[0].status)
        assertEquals(FeedbackStatus.IMPROVE, feedback[1].status)
        assertTrue(feedback.take(2).map { it.topic }.containsAll(listOf("Cadence", "Overstriding")))
    }

    @Test
    fun fasterRunningAllowsALongerReach() {
        val m = metrics(overstrideRatio = 0.20)
        assertEquals("Overstriding", Coach.review(m, speedKmh = 10.0).first().topic)
        assertEquals(null, Coach.mainFocus(Coach.review(m, speedKmh = 15.0)))
    }

    @Test
    fun mentionsLastSession() {
        val feedback = Coach.review(metrics(), speedKmh = 10.0, previous = metrics(cadence = 160.0))
        assertTrue(feedback.first { it.topic == "Cadence" }.message.contains("Last session: 160"))
    }
}
