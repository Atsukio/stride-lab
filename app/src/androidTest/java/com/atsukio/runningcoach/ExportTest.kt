package com.atsukio.runningcoach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.share.LandingStill
import com.atsukio.runningcoach.share.OverlayVideoExporter
import com.atsukio.runningcoach.share.ReportPdf
import com.atsukio.runningcoach.share.ResultSharer
import com.atsukio.runningcoach.share.VideoEncoder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The written report and the clip with the skeleton drawn on. Neither needs a camera or pose detection. */
@RunWith(AndroidJUnit4::class)
class ExportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = File(context.cacheDir, "export-test").apply { deleteRecursively(); mkdirs() }

    @After
    fun tidy() {
        dir.deleteRecursively()
    }

    @Test
    fun theReportIsAReadablePdf() {
        val session = Fixtures.session("report-test")
        val feedback = Coach.review(session.metrics!!, session.speedKmh)
        val frame = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val stills = listOf(LandingStill(frame, null, 1, 5), LandingStill(frame, null, 3, 5))
        val pdf = File(dir, "report.pdf")
        ReportPdf.write(pdf, session, feedback, "8 Nov 2026", stills)

        assertTrue(pdf.length() > 2_000)
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> assertTrue("at least one page", renderer.pageCount >= 1) }
        }
    }

    @Test
    fun theReportCanBeSharedWithoutTheVideo() {
        val session = Fixtures.session("report-share-test")
        val feedback = Coach.review(session.metrics!!, session.speedKmh)
        runBlocking {
            ResultSharer.prepare(
                context, session, feedback, null, File(dir, "missing.mp4"),
                ResultSharer.Options(landingIndex = null, includeVideo = false, report = true),
            )
        }
        val names = File(context.cacheDir, "shared").listFiles().orEmpty().map { it.name }
        assertTrue("a PDF was made: $names", names.any { it.endsWith("-report.pdf") })
    }

    @Test
    fun theSkeletonVideoKeepsTheClipsSizeAndLength() {
        val source = File(dir, "source.mp4")
        val width = 360
        val height = 640
        val frames = 48
        VideoEncoder(width, height, 30f, source).use { encoder ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (i in 0 until frames) {
                bitmap.eraseColor(Color.rgb(20 + i * 3, 80, 140))
                encoder.add(bitmap, i * 1_000_000L / 30)
            }
            encoder.finish()
        }
        assertTrue("the test clip was written", source.length() > 0)

        val track = PoseTrack(30f, width, height, List(frames) { i -> pose(i) })
        val metrics = Fixtures.metrics(contactFrames = listOf(5, 15, 25, 35))
        val out = File(dir, "skeleton.mp4")
        val progress = mutableListOf<Float>()
        runBlocking { OverlayVideoExporter.export(source, track, metrics, 30f, out) { progress += it } }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(out.path)
            assertEquals(width, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt())
            assertEquals(height, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt())
            val seconds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() / 1000.0
            assertEquals("about the length of the source", frames / 30.0, seconds, 0.3)
            val frame = retriever.getFrameAtTime(15L * 1_000_000 / 30, MediaMetadataRetriever.OPTION_CLOSEST)
            assertTrue("a frame can be read back", frame != null)
        } finally {
            retriever.release()
        }
        assertTrue("progress was reported", progress.isNotEmpty() && progress.last() <= 1.0001f)
    }

    /** A made-up standing figure, drifting right so every frame differs. */
    private fun pose(frame: Int): FramePose {
        val x = FloatArray(Landmark.COUNT) { 0.5f + 0.001f * frame }
        val y = FloatArray(Landmark.COUNT) { 0.5f }
        fun at(i: Int, px: Float, py: Float) {
            x[i] += px
            y[i] = py
        }
        at(Landmark.NOSE, 0.03f, 0.2f)
        at(Landmark.LEFT_EAR, 0f, 0.2f)
        at(Landmark.RIGHT_EAR, 0f, 0.2f)
        at(Landmark.LEFT_SHOULDER, 0f, 0.3f)
        at(Landmark.RIGHT_SHOULDER, 0f, 0.3f)
        at(Landmark.LEFT_HIP, 0f, 0.5f)
        at(Landmark.RIGHT_HIP, 0f, 0.5f)
        at(Landmark.LEFT_KNEE, 0.05f, 0.65f)
        at(Landmark.RIGHT_KNEE, -0.03f, 0.65f)
        at(Landmark.LEFT_ANKLE, 0.08f, 0.8f)
        at(Landmark.RIGHT_ANKLE, -0.08f, 0.78f)
        at(Landmark.LEFT_HEEL, 0.08f, 0.82f)
        at(Landmark.RIGHT_HEEL, -0.08f, 0.8f)
        return FramePose(x, y, FloatArray(Landmark.COUNT) { 1f })
    }
}
