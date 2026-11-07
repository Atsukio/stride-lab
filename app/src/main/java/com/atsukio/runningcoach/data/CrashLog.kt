package com.atsukio.runningcoach.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the stack trace of the last crashes in app-private storage. Nothing is sent anywhere: the person
 * chooses whether to share the file, through the share sheet, from Storage.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val MAX_BYTES = 100_000L

    /** Records uncaught crashes, then lets the usual handler finish the crash. Call once, at start-up. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String = runCatching { file(context).takeIf { it.exists() }?.readText().orEmpty() }
        .getOrDefault("")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun append(context: Context, thread: Thread, error: Throwable) {
        val file = file(context)
        // Keep the newest part once the file grows past its limit.
        if (file.exists() && file.length() > MAX_BYTES) {
            val tail = file.readText().takeLast((MAX_BYTES / 2).toInt())
            file.writeText(tail)
        }
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        file.appendText("--- $stamp, thread ${thread.name}, Android ${android.os.Build.VERSION.RELEASE}\n$trace\n")
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
