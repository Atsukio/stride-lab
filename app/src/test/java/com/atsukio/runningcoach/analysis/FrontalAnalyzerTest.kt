package com.atsukio.runningcoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * A stick-figure runner seen from behind (or from the front) with known hip drop, knee drift, step width and
 * trunk sway. Hips are 90 px apart and shoulders 220 px, over a 300 px trunk.
 */
internal class SyntheticRearRunner(
    val cadence: Double = 170.0,
    val dropLeftDeg: Double = 5.0,
    val dropRightDeg: Double = 5.0,
    val kneeInLeftDeg: Double = 4.0,
    val kneeInRightDeg: Double = 4.0,
    val stepWidthPx: Double = 30.0,
    val swayDeg: Double = 3.0,
    val fromFront: Boolean = false,
    val fps: Float = 60f,
) {
    private val width = 1920
    private val height = 1080
    private val groundY = 1000.0
    private val hipHeight = 380.0
    private val bouncePx = 40.0
    private val liftPx = 150.0
    private val stanceFraction = 0.35

    fun track(seconds: Double): PoseTrack =
        PoseTrack(fps, width, height, (0 until (seconds * fps).toInt()).map { frame(it / fps.toDouble()) })

    private fun frame(t: Double): FramePose {
        val stride = 2 * 60.0 / cadence
        val centre = Vec2(960.0, groundY - hipHeight - bouncePx / 2 * cos(2 * PI * t / (stride / 2)))
        // How far through its stance each leg is (0..1), or null while that foot is in the air.
        fun stance(offset: Double): Double? = ((t / stride + offset) % 1.0).let { if (it < stanceFraction) it / stanceFraction else null }
        fun swing(offset: Double): Double = (((t / stride + offset) % 1.0) - stanceFraction) / (1 - stanceFraction)
        val leftStance = stance(0.0)
        val rightStance = stance(0.5)

        // Seen from behind, the runner's left is on the left of the image, so x here is "towards their right".
        val roll = Math.toRadians(
            (leftStance?.let { dropLeftDeg * sin(PI * it) } ?: 0.0) - (rightStance?.let { dropRightDeg * sin(PI * it) } ?: 0.0),
        )
        val points = Array(Landmark.COUNT) { centre }
        // A positive roll lowers the right hip, as when standing on the left leg.
        val leftHip = centre + Vec2(-45 * cos(roll), -45 * sin(roll))
        val rightHip = centre + Vec2(45 * cos(roll), 45 * sin(roll))
        points[Landmark.LEFT_HIP] = leftHip
        points[Landmark.RIGHT_HIP] = rightHip

        val sway = Math.toRadians(swayDeg * sin(2 * PI * t / stride))
        val shoulders = centre + Vec2(300 * sin(sway), -300 * cos(sway))
        points[Landmark.LEFT_SHOULDER] = shoulders + Vec2(-110.0, 0.0)
        points[Landmark.RIGHT_SHOULDER] = shoulders + Vec2(110.0, 0.0)
        points[Landmark.NOSE] = shoulders + Vec2(0.0, -100.0)
        points[Landmark.LEFT_EAR] = shoulders + Vec2(-20.0, -105.0)
        points[Landmark.RIGHT_EAR] = shoulders + Vec2(20.0, -105.0)
        points[Landmark.LEFT_ELBOW] = points[Landmark.LEFT_SHOULDER] + Vec2(-20.0, 130.0)
        points[Landmark.RIGHT_ELBOW] = points[Landmark.RIGHT_SHOULDER] + Vec2(20.0, 130.0)
        points[Landmark.LEFT_WRIST] = points[Landmark.LEFT_ELBOW] + Vec2(10.0, 60.0)
        points[Landmark.RIGHT_WRIST] = points[Landmark.RIGHT_ELBOW] + Vec2(-10.0, 60.0)

        for (left in listOf(true, false)) {
            val out = if (left) -1.0 else 1.0
            val hip = if (left) leftHip else rightHip
            val q = if (left) leftStance else rightStance
            val lift = if (q != null) 0.0 else liftPx * sin(PI * swing(if (left) 0.0 else 0.5))
            val ankle = Vec2(960.0 + out * stepWidthPx / 2, groundY - lift)
            val kneeIn = if (left) kneeInLeftDeg else kneeInRightDeg
            // Pushing the knee off the hip-to-ankle line by d bends the leg through 2·atan(d / half the line).
            val half = (ankle - hip).length / 2
            val push = if (q == null) 0.0 else half * tan(Math.toRadians(kneeIn * sin(PI * q)) / 2)
            val knee = midpoint(hip, ankle) + Vec2(-out * push, 0.0)
            val ids = if (left) {
                intArrayOf(Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX)
            } else {
                intArrayOf(Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX)
            }
            points[ids[0]] = knee
            points[ids[1]] = ankle
            points[ids[2]] = ankle + Vec2(0.0, 10.0)
            points[ids[3]] = ankle + Vec2(out * 6, 14.0)
        }

        // From the front everything is mirrored and the face is in view.
        val mirror = { p: Vec2 -> if (fromFront) Vec2(2 * 960.0 - p.x, p.y) else p }
        val visibility = FloatArray(Landmark.COUNT) { 1f }
        if (!fromFront) visibility[Landmark.NOSE] = 0.1f
        return FramePose(
            x = FloatArray(Landmark.COUNT) { (mirror(points[it]).x / width).toFloat() },
            y = FloatArray(Landmark.COUNT) { (points[it].y / height).toFloat() },
            visibility = visibility,
        )
    }
}

class FrontalAnalyzerTest {
    private val heightCm = 175.0

    private fun analyse(runner: SyntheticRearRunner, seconds: Double = 12.0): Pair<GaitMetrics, List<String>> {
        val result = GaitAnalyzer.analyze(runner.track(seconds), heightCm)
        assertTrue("Expected success but got $result", result is GaitResult.Success)
        result as GaitResult.Success
        return result.metrics to result.warnings
    }

    @Test
    fun measuresAKnownRunnerFromBehind() {
        val (m, warnings) = analyse(SyntheticRearRunner())
        val f = m.frontal!!
        assertEquals(CameraView.REAR, m.view)
        assertEquals(170.0, m.cadenceSpm!!, 2.0)
        assertEquals(5.0, f.pelvicDropDeg!!, 1.0)
        assertEquals(4.0, f.kneeInwardDeg!!, 1.5)
        assertEquals(3.0, f.trunkSwayDeg!!, 0.7)
        assertTrue("step width was ${f.stepWidthCm}", f.stepWidthCm!! in 5.0..8.5)
        assertTrue(m.verticalOscillationCm!! in 6.0..11.0)
        assertTrue(m.stepCount >= 30)
        assertEquals(m.contactFrames.size, f.leftContacts.size)
        assertTrue("landings should alternate", f.leftContacts.zipWithNext().all { (a, b) -> a != b })
        assertTrue(warnings.any { it.contains("new") })
        assertTrue("no doubt about the view: $warnings", warnings.none { it.contains("left and right") })
    }

    @Test
    fun sideOnFiguresAreLeftOut() {
        val m = analyse(SyntheticRearRunner()).first
        assertNull(m.overstrideCm)
        assertNull(m.shinAngleDeg)
        assertNull(m.trunkLeanDeg)
        assertNull(m.footStrikeSummary)
    }

    @Test
    fun theFrontViewGivesTheSameFiguresWithTheSameLegs() {
        val runner = SyntheticRearRunner(dropLeftDeg = 3.0, dropRightDeg = 9.0, kneeInLeftDeg = 12.0, kneeInRightDeg = 2.0)
        val rear = analyse(runner).first
        val front = analyse(
            SyntheticRearRunner(dropLeftDeg = 3.0, dropRightDeg = 9.0, kneeInLeftDeg = 12.0, kneeInRightDeg = 2.0, fromFront = true),
        ).first
        assertEquals(CameraView.FRONT, front.view)
        assertEquals(rear.frontal!!.pelvicDropLeftDeg!!, front.frontal!!.pelvicDropLeftDeg!!, 0.3)
        assertEquals(rear.frontal.pelvicDropRightDeg!!, front.frontal.pelvicDropRightDeg!!, 0.3)
        assertEquals(rear.frontal.kneeInwardLeftDeg!!, front.frontal.kneeInwardLeftDeg!!, 0.3)
        assertEquals(rear.frontal.stepWidthCm!!, front.frontal.stepWidthCm!!, 0.3)
    }

    @Test
    fun eachLegIsMeasuredOnItsOwn() {
        val f = analyse(SyntheticRearRunner(dropLeftDeg = 3.0, dropRightDeg = 9.0, kneeInLeftDeg = 12.0, kneeInRightDeg = 2.0)).first.frontal!!
        assertEquals(3.0, f.pelvicDropLeftDeg!!, 1.0)
        assertEquals(9.0, f.pelvicDropRightDeg!!, 1.0)
        assertEquals(6.0, f.pelvicDropDeg!!, 1.0)
        assertEquals(12.0, f.kneeInwardLeftDeg!!, 1.5)
        assertEquals(2.0, f.kneeInwardRightDeg!!, 1.5)
        // The headline is the leg that drifts more.
        assertEquals(f.kneeInwardLeftDeg, f.kneeInwardDeg!!, 1e-9)
    }

    @Test
    fun feetCrossingOverGiveANegativeStepWidth() {
        val f = analyse(SyntheticRearRunner(stepWidthPx = -24.0)).first.frontal!!
        assertTrue("step width was ${f.stepWidthCm}", f.stepWidthCm!! in -7.0..-3.5)
    }

    @Test
    fun aKneeOutsideTheLineReadsNegative() {
        val f = analyse(SyntheticRearRunner(kneeInLeftDeg = -8.0, kneeInRightDeg = -8.0)).first.frontal!!
        assertTrue("knee was ${f.kneeInwardDeg}", f.kneeInwardDeg!! < 1.0)
    }

    @Test
    fun aHiddenFaceWithFrontOrderedShouldersIsFlagged() {
        // The synthetic front runner's face is visible; hide it, as if the detector mixed up left and right.
        val track = SyntheticRearRunner(fromFront = true).track(12.0)
        val hidden = PoseTrack(
            track.fps, track.width, track.height,
            track.frames.map { p -> p?.let { FramePose(it.x, it.y, it.visibility.copyOf().also { v -> v[Landmark.NOSE] = 0.1f }) } },
        )
        val result = GaitAnalyzer.analyze(hidden, heightCm) as GaitResult.Success
        assertTrue(result.warnings.any { it.contains("left and right") })
    }

    @Test
    fun coachingFlagsWhatIsOutOfRange() {
        val m = analyse(SyntheticRearRunner(dropLeftDeg = 11.0, dropRightDeg = 11.0, kneeInLeftDeg = 16.0, stepWidthPx = -24.0, swayDeg = 8.0)).first
        val feedback = Coach.review(m, speedKmh = 10.0).associateBy { it.topic }
        for (topic in listOf("Hip drop", "Knee alignment", "Step width", "Trunk sway")) {
            assertEquals(topic, FeedbackStatus.IMPROVE, feedback.getValue(topic).status)
        }
        assertTrue(feedback.getValue("Knee alignment").message.startsWith("Your left knee"))
        assertTrue("side-on topics are absent", feedback.keys.none { it in setOf("Overstriding", "Foot landing", "Posture", "Arms") })
    }

    @Test
    fun coachingBarsAgreeWithTheVerdict() {
        for (runner in listOf(SyntheticRearRunner(), SyntheticRearRunner(dropLeftDeg = 9.0, dropRightDeg = 8.0, kneeInRightDeg = 11.0, swayDeg = 7.0, stepWidthPx = 8.0))) {
            for (feedback in Coach.review(analyse(runner).first, speedKmh = null)) {
                val scale = feedback.scale ?: continue
                val inGoodRange = scale.value in scale.goodLow..scale.goodHigh
                if (feedback.status == FeedbackStatus.IMPROVE) assertTrue(feedback.topic, !inGoodRange)
                if (feedback.status == FeedbackStatus.GOOD) assertTrue(feedback.topic, inGoodRange)
            }
        }
    }

    @Test
    fun anUnevenHipDropMentionsThePhone() {
        val m = analyse(SyntheticRearRunner(dropLeftDeg = 2.0, dropRightDeg = 9.0)).first
        val hip = Coach.review(m, speedKmh = null).first { it.topic == "Hip drop" }
        assertTrue(hip.message, hip.message.contains("right leg") && hip.message.contains("level"))
    }
}
