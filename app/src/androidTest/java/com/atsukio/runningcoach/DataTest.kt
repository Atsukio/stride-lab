package com.atsukio.runningcoach

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atsukio.runningcoach.data.CrashLog
import com.atsukio.runningcoach.data.SessionRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = SessionRepository(context)
    private val ids = mutableListOf<String>()

    private fun newSession(createdAt: Long): String {
        val id = "test-" + repo.newSessionId()
        ids += id
        repo.save(Fixtures.session(id, createdAt))
        repo.videoFile(id).writeBytes(ByteArray(2_000))
        return id
    }

    @After
    fun tidy() {
        ids.forEach(repo::delete)
    }

    @Test
    fun removingOldVideosKeepsTheirResults() {
        // The cut-off is early 1970, so only clips these tests create are in reach, never a real clip.
        val old = newSession(createdAt = 1_000)
        val recent = newSession(createdAt = 10_000)

        val (count, freed) = repo.deleteVideosBefore(cutoffMs = 5_000)

        assertEquals(1, count)
        assertEquals(2_000L, freed)
        assertFalse(repo.hasVideo(old))
        assertNotNull("the results of the old clip stay", repo.get(old)?.metrics)
        assertTrue(repo.hasVideo(recent))
    }

    @Test
    fun storageUsageCountsVideosSeparatelyFromResults() {
        newSession(createdAt = 1_000)
        val usage = repo.storageUsage()
        assertTrue(usage.videoBytes >= 2_000)
        assertTrue(usage.otherBytes > 0)
    }

    @Test
    fun theCrashLogCanBeReadAndCleared() {
        // Put back whatever log the device already had, so running this on a real phone loses nothing.
        val original = CrashLog.read(context)
        try {
            CrashLog.clear(context)
            assertEquals("", CrashLog.read(context))
            java.io.File(context.filesDir, "crash.log").writeText("--- 2026-11-07, thread main\nboom\n")
            assertTrue(CrashLog.read(context).contains("boom"))
            CrashLog.clear(context)
            assertEquals("", CrashLog.read(context))
        } finally {
            if (original.isNotEmpty()) java.io.File(context.filesDir, "crash.log").writeText(original)
        }
    }
}
