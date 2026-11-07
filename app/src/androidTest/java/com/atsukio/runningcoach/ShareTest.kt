package com.atsukio.runningcoach

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atsukio.runningcoach.analysis.Coach
import com.atsukio.runningcoach.share.LandingStill
import com.atsukio.runningcoach.share.ResultSharer
import com.atsukio.runningcoach.share.ShareCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val session = Fixtures.session("share-test")
    private val feedback = Coach.review(session.metrics!!, session.speedKmh)

    @Test
    fun theSummaryCardIsAPortraitImageThatGrowsWithItsContent() {
        val plain = ShareCard.render(context, session, feedback, "7 Nov 2026", landing = null)
        assertEquals(1080, plain.width)
        assertTrue("tall enough for every measurement", plain.height > 1200)

        val frame = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val withStill = ShareCard.render(context, session, feedback, "7 Nov 2026", LandingStill(frame, null, 2, 5))
        assertTrue("the landing still adds height", withStill.height > plain.height)
    }

    @Test
    fun theShareIntentCarriesAReadableImageAndATextSummary() {
        val chooser = ResultSharer.prepare(
            context, session, feedback, pose = null, video = File(context.cacheDir, "missing.mp4"),
            options = ResultSharer.Options(landingIndex = null, includeVideo = false),
        )
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("My running form"))

        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    @Test
    fun onlyTheLatestShareIsKept() {
        val options = ResultSharer.Options(landingIndex = null, includeVideo = false)
        val video = File(context.cacheDir, "missing.mp4")
        ResultSharer.prepare(context, session, feedback, null, video, options)
        ResultSharer.prepare(context, Fixtures.session("share-test-2", createdAt = 1_000), feedback, null, video, options)
        val files = File(context.cacheDir, "shared").listFiles().orEmpty()
        assertEquals(1, files.size)
    }
}
