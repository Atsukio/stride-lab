package com.atsukio.runningcoach.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares a plain-text file through the share sheet, using the same single-share folder as results. */
object TextSharer {
    fun prepare(context: Context, name: String, text: String, title: String): Intent {
        val dir = File(context.cacheDir, "shared").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, name).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }
}
