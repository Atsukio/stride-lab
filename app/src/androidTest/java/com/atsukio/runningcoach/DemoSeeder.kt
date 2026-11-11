package com.atsukio.runningcoach

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.platform.app.InstrumentationRegistry
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.data.CaptureInfo
import com.atsukio.runningcoach.data.SessionBuilder
import com.atsukio.runningcoach.data.SessionRepository
import com.atsukio.runningcoach.share.VideoEncoder
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Not a real test: saves two made-up clips (a stick runner drawn into an MP4, with its poses) so the README
 * demo can be screen-recorded from the real app on the emulator, with no one's footage. It skips itself unless
 * run by hand on the emulator with `am instrument -e demo true -e class com.atsukio.runningcoach.DemoSeeder ...`.
 */
class DemoSeeder {
    @Test
    fun seedDemoClips() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("demo") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = SessionRepository(context)
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        seed(repo, "demo-earlier", now - 7 * day, DemoRunner(cadence = 162.0, landingAheadPx = 95.0, leanDeg = 3.0))
        seed(repo, "demo-latest", now, DemoRunner(cadence = 172.0, landingAheadPx = 50.0, leanDeg = 6.0))
    }

    private fun seed(repo: SessionRepository, id: String, createdAt: Long, runner: DemoRunner) {
        repo.delete(id)
        val track = runner.track(SECONDS)
        repo.savePose(id, track)
        drawVideo(runner, repo.videoFile(id))
        val speed = runner.beltSpeedKmh(HEIGHT_CM)
        repo.saveCaptureInfo(id, CaptureInfo(speedKmh = speed))
        val session = SessionBuilder.build(
            id = id, createdAt = createdAt, track = track, playbackFps = FPS, heightCm = HEIGHT_CM,
            capture = CaptureInfo(speedKmh = speed), extractionNotes = emptyList(), processingSec = null,
            analysedLive = false, scaleEdited = false,
        )
        repo.save(session)
    }

    private fun drawVideo(runner: DemoRunner, out: java.io.File) {
        val scale = VIDEO_W / DemoRunner.WIDTH.toFloat()
        val bitmap = Bitmap.createBitmap(VIDEO_W, VIDEO_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val wall = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, VIDEO_H.toFloat(), Color.rgb(58, 62, 66), Color.rgb(34, 36, 39), Shader.TileMode.CLAMP)
        }
        val deck = Paint().apply { color = Color.rgb(22, 23, 25) }
        val stripe = Paint().apply { color = Color.rgb(48, 50, 54); strokeWidth = 4f }
        val far = limbPaint(Color.rgb(96, 104, 112), 26f * scale)
        val near = limbPaint(Color.rgb(196, 202, 208), 30f * scale)
        val shoe = limbPaint(Color.rgb(230, 120, 60), 22f * scale)
        val label = Paint().apply { color = Color.argb(150, 255, 255, 255); textSize = 22f; isAntiAlias = true }
        val frames = (SECONDS * FPS).toInt()
        VideoEncoder(VIDEO_W, VIDEO_H, FPS, out).use { encoder ->
            for (i in 0 until frames) {
                val t = i / FPS.toDouble()
                val p = runner.points(t)
                canvas.drawRect(0f, 0f, VIDEO_W.toFloat(), VIDEO_H.toFloat(), wall)
                val deckTop = ((DemoRunner.GROUND_Y + 14) * scale).toFloat()
                canvas.drawRect(0f, deckTop, VIDEO_W.toFloat(), VIDEO_H.toFloat(), deck)
                val shift = ((t * runner.beltPxPerSecond() * scale) % 80).toFloat()
                var x = -shift
                while (x < VIDEO_W) {
                    canvas.drawLine(x, deckTop + 18, x + 40, deckTop + 18, stripe)
                    x += 80f
                }
                fun line(a: Int, b: Int, paint: Paint) =
                    canvas.drawLine((p[a].first * scale).toFloat(), (p[a].second * scale).toFloat(), (p[b].first * scale).toFloat(), (p[b].second * scale).toFloat(), paint)
                // The far side (left) first, then the body, then the near side.
                line(Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW, far)
                line(Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST, far)
                line(Landmark.LEFT_HIP, Landmark.LEFT_KNEE, far)
                line(Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, far)
                line(Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX, shoe)
                line(Landmark.RIGHT_HIP, Landmark.RIGHT_SHOULDER, limbPaint(Color.rgb(170, 176, 182), 60f * scale))
                val head = p[Landmark.NOSE]
                canvas.drawCircle(((head.first - 18) * scale).toFloat(), ((head.second - 8) * scale).toFloat(), 58f * scale, near)
                line(Landmark.RIGHT_HIP, Landmark.RIGHT_KNEE, near)
                line(Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, near)
                line(Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX, shoe)
                line(Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW, near)
                line(Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST, near)
                canvas.drawText("Synthetic runner (demo)", 20f, VIDEO_H - 20f, label)
                encoder.add(bitmap, (i * 1_000_000L / FPS).toLong())
            }
            encoder.finish()
        }
    }

    private fun limbPaint(colour: Int, width: Float) = Paint().apply {
        color = colour
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.FILL_AND_STROKE
        isAntiAlias = true
    }

    private companion object {
        const val FPS = 60f
        const val SECONDS = 12.0
        const val HEIGHT_CM = 175.0
        const val VIDEO_W = 1280
        const val VIDEO_H = 720
    }
}

/**
 * The unit tests' SyntheticRunner (GaitAnalyzerTest.kt), copied because the two test source sets can't share code,
 * with the arms swinging against the legs so it looks like running on screen.
 */
private class DemoRunner(val cadence: Double, val landingAheadPx: Double, val leanDeg: Double) {
    private val bouncePx = 40.0
    // Higher than the unit tests' 360, so the leg is nearly straight at landing as in real running.
    private val hipHeight = 372.0
    private val segment = 200.0
    private val pushOffBehindPx = 110.0
    private val liftPx = 150.0
    private val stanceFraction = 0.35

    fun beltPxPerSecond() = (landingAheadPx + pushOffBehindPx) / (stanceFraction * 2 * 60.0 / cadence)

    fun beltSpeedKmh(heightCm: Double): Double {
        val cmPerPx = 0.49 * heightCm / (2 * segment)
        return beltPxPerSecond() * cmPerPx / 100 * 3.6
    }

    fun track(seconds: Double): PoseTrack {
        val frames = (0 until (seconds * 60).toInt()).map { i ->
            val p = points(i / 60.0)
            FramePose(
                x = FloatArray(Landmark.COUNT) { (p[it].first / WIDTH).toFloat() },
                y = FloatArray(Landmark.COUNT) { (p[it].second / HEIGHT).toFloat() },
                visibility = FloatArray(Landmark.COUNT) { 1f },
            )
        }
        return PoseTrack(60f, WIDTH, HEIGHT, frames)
    }

    /** Every landmark at time [t], in the runner's 1920 × 1080 frame. */
    fun points(t: Double): Array<Pair<Double, Double>> {
        val stride = 2 * 60.0 / cadence
        val hip = 960.0 to GROUND_Y - hipHeight - bouncePx / 2 * cos(2 * PI * t / (stride / 2))
        val lean = Math.toRadians(leanDeg)
        val shoulder = hip.first + sin(lean) * 300 to hip.second - cos(lean) * 300
        val points = Array(Landmark.COUNT) { hip }
        points[Landmark.LEFT_HIP] = hip.first - 1 to hip.second
        points[Landmark.RIGHT_HIP] = hip.first + 1 to hip.second
        points[Landmark.LEFT_SHOULDER] = shoulder.first - 2 to shoulder.second
        points[Landmark.RIGHT_SHOULDER] = shoulder.first + 2 to shoulder.second
        points[Landmark.NOSE] = shoulder.first + 25 to shoulder.second - 100
        points[Landmark.LEFT_EAR] = shoulder.first - 5 to shoulder.second - 105
        points[Landmark.RIGHT_EAR] = shoulder.first - 5 to shoulder.second - 105

        val centre = (landingAheadPx - pushOffBehindPx) / 2
        val half = (landingAheadPx + pushOffBehindPx) / 2
        for ((phaseOffset, ids) in listOf(
            0.0 to intArrayOf(Landmark.LEFT_KNEE, Landmark.LEFT_ANKLE, Landmark.LEFT_HEEL, Landmark.LEFT_FOOT_INDEX, Landmark.LEFT_SHOULDER, Landmark.LEFT_ELBOW, Landmark.LEFT_WRIST),
            0.5 to intArrayOf(Landmark.RIGHT_KNEE, Landmark.RIGHT_ANKLE, Landmark.RIGHT_HEEL, Landmark.RIGHT_FOOT_INDEX, Landmark.RIGHT_SHOULDER, Landmark.RIGHT_ELBOW, Landmark.RIGHT_WRIST),
        )) {
            val p = (t / stride + phaseOffset) % 1.0
            val ankle = if (p < stanceFraction) {
                val q = p / stanceFraction
                hip.first + landingAheadPx - (landingAheadPx + pushOffBehindPx) * q to GROUND_Y
            } else {
                val q = (p - stanceFraction) / (1 - stanceFraction)
                val x = -pushOffBehindPx + (landingAheadPx + pushOffBehindPx) * (1 - cos(PI * q)) / 2
                hip.first + x to GROUND_Y - liftPx * sin(PI * q)
            }
            points[ids[0]] = knee(hip, ankle)
            points[ids[1]] = ankle
            points[ids[2]] = ankle.first - 12 to ankle.second + 10
            points[ids[3]] = ankle.first + 45 to ankle.second + 14
            // The arm on the same side swings back as this leg reaches forward.
            val swing = -0.5 * (ankle.first - hip.first - centre) / half
            val sh = points[ids[4]]
            val elbow = sh.first + 130 * sin(swing) to sh.second + 130 * cos(swing)
            points[ids[5]] = elbow
            points[ids[6]] = elbow.first + 110 * cos(swing - 0.35) to elbow.second - 110 * sin(swing - 0.35)
        }
        return points
    }

    private fun knee(hip: Pair<Double, Double>, ankle: Pair<Double, Double>): Pair<Double, Double> {
        val dx = ankle.first - hip.first
        val dy = ankle.second - hip.second
        val length = sqrt(dx * dx + dy * dy)
        val d = length.coerceAtMost(2 * segment * 0.999)
        val ux = dx / length
        val uy = dy / length
        val h = sqrt(segment * segment - (d / 2) * (d / 2))
        var nx = uy
        var ny = -ux
        if (nx < 0) { nx = -nx; ny = -ny }
        return hip.first + ux * d / 2 + nx * h to hip.second + uy * d / 2 + ny * h
    }

    companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val GROUND_Y = 1000.0
    }
}
