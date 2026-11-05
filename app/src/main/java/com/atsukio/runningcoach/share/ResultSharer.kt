package com.atsukio.runningcoach.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.atsukio.runningcoach.analysis.Feedback
import com.atsukio.runningcoach.analysis.PoseTrack
import com.atsukio.runningcoach.data.Session
import com.atsukio.runningcoach.ui.landingAnnotation
import com.atsukio.runningcoach.ui.videoFrameBitmap
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shares a result through Android's share sheet. The app has no internet access: the person picks the
 * app it goes to, and only that app gets read access to the files, through a [FileProvider] link.
 */
object ResultSharer {

    /** What to send besides the summary image: the landing at [landingIndex], and the clip itself. */
    class Options(val landingIndex: Int?, val includeVideo: Boolean)

    /** Renders the image, copies the files to share and returns the share-sheet intent. Slow: call off the main thread. */
    fun prepare(
        context: Context,
        session: Session,
        feedback: List<Feedback>,
        pose: PoseTrack?,
        video: File,
        options: Options,
    ): Intent {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.createdAt))
        val metrics = session.metrics
        val landing = if (metrics == null) null else options.landingIndex?.let { index ->
            val frame = metrics.contactFrames.getOrNull(index) ?: return@let null
            val aspect = session.videoWidth.toFloat() / session.videoHeight.coerceAtLeast(1)
            videoFrameBitmap(video, frame / session.playbackFps.toDouble(), aspect, maxSide = 1080)?.let {
                LandingStill(it, pose?.let { track -> landingAnnotation(track, metrics, frame) }, index + 1, metrics.contactFrames.size)
            }
        }
        val image = ShareCard.render(context, session, feedback, date, landing)

        // Only the latest share is kept; anything older is cleared first.
        val dir = File(context.cacheDir, SHARE_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        val name = "stride-lab-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(session.createdAt))
        val png = File(dir, "$name.png")
        png.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val files = mutableListOf(png)
        if (options.includeVideo && video.exists()) files += video.copyTo(File(dir, "$name.mp4"))

        val authority = "${context.packageName}.share"
        val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
            }
        }
        send.putExtra(Intent.EXTRA_TEXT, ShareSummary.text(session, feedback, date))
        send.putExtra(Intent.EXTRA_SUBJECT, "My running form, $date")
        send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share your results")
    }

    private const val SHARE_DIR = "shared"
}
