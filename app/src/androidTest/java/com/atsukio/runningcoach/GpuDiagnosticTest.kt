package com.atsukio.runningcoach

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.atsukio.runningcoach.analysis.GaitAnalyzer
import com.atsukio.runningcoach.analysis.GaitResult
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.pose.PoseExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Not a real test: runs one clip through GPU and CPU pose detection and writes the metrics side by side,
 * to set the thresholds in GpuCheck. It skips itself unless `cache/diag/diag.mp4` exists, and it never
 * touches saved sessions. Run it by hand with `am instrument -e class ...GpuDiagnosticTest`.
 */
class GpuDiagnosticTest {
    @Test
    fun compareGpuAndCpu(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "diag")
        val video = File(dir, "diag.mp4")
        assumeTrue(video.exists())
        val out = StringBuilder()
        val tracks = HashMap<String, PoseTrack>()
        for (gpu in listOf(false, true)) {
            val name = if (gpu) "GPU" else "CPU"
            val started = System.currentTimeMillis()
            val track = PoseExtractor(context, useGpu = gpu).extract(video) { }.track
            tracks[name] = track
            out.appendLine("$name: ${track.frames.size} frames at ${track.fps} fps, ${(System.currentTimeMillis() - started) / 1000}s")
            when (val r = GaitAnalyzer.analyze(track, runnerHeightCm = 170.0, beltSpeedKmh = 6.2)) {
                is GaitResult.Success -> {
                    val m = r.metrics
                    out.appendLine("  cadence=${m.cadenceSpm} steps=${m.stepCount} overstride=${m.overstrideCm} shin=${m.shinAngleDeg}")
                    out.appendLine("  kneeContact=${m.kneeFlexionAtContactDeg} kneePeak=${m.peakStanceKneeFlexionDeg} bounce=${m.verticalOscillationCm}")
                    out.appendLine("  trunk=${m.trunkLeanDeg} elbow=${m.elbowAngleDeg} asym=${m.overstrideAsymmetryCm} scaled=${m.scaledFromTreadmill}")
                    out.appendLine("  warnings=${r.warnings}")
                }
                is GaitResult.Failure -> out.appendLine("  failed: ${r.reason}")
            }
        }
        val gpu = tracks.getValue("GPU")
        val cpu = tracks.getValue("CPU")
        val groups = mapOf(
            "legs" to (Landmark.LEFT_HIP..Landmark.RIGHT_FOOT_INDEX).toList(),
            "upper" to listOf(Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER, Landmark.LEFT_ELBOW, Landmark.RIGHT_ELBOW, Landmark.LEFT_WRIST, Landmark.RIGHT_WRIST),
        )
        val aspect = 9.0 / 16.0
        for ((label, ids) in groups) {
            val diffs = gpu.frames.zip(cpu.frames).mapNotNull { (g, c) ->
                if (g == null || c == null) null
                else ids.map { i -> hypot((g.x[i] - c.x[i]) * aspect, (g.y[i] - c.y[i]).toDouble()) }.average()
            }.sorted()
            if (diffs.isNotEmpty()) {
                out.appendLine(
                    "$label landmark gap (frame heights): median=%.4f p90=%.4f max=%.4f over %d frames; >0.03 in %d".format(
                        diffs[diffs.size / 2], diffs[diffs.size * 9 / 10], diffs.last(), diffs.size, diffs.count { it > 0.03 },
                    ),
                )
            }
        }
        out.appendLine("missed: gpu=${gpu.frames.count { it == null }} cpu=${cpu.frames.count { it == null }}")
        File(dir, "result.txt").writeText(out.toString())
        Log.i("GpuDiagnostic", out.toString())
        Unit
    }
}
