package com.atsukio.runningcoach.data

import android.content.Context
import com.atsukio.runningcoach.analysis.FramePose
import com.atsukio.runningcoach.analysis.Landmark
import com.atsukio.runningcoach.analysis.PoseTrack
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

/** Bytes kept on the phone for sessions: the videos, and the results and pose data. */
data class StorageUsage(val videoBytes: Long, val otherBytes: Long)

/**
 * Stores each session in app-private storage: `sessions/<id>/` holds the video,
 * the pose track and the results, and `runs/<id>.json` holds whole runs.
 * Nothing is written anywhere else.
 */
class SessionRepository(context: Context) {
    private val root = File(context.filesDir, "sessions")
    private val runsRoot = File(context.filesDir, "runs")
    private val json = Json { ignoreUnknownKeys = true }

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun videoFile(id: String): File = File(dir(id), "video.mp4")

    fun get(id: String): Session? {
        val file = File(File(root, id), SESSION_FILE)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Session>(file.readText()) }.getOrNull()
    }

    fun list(): List<Session> =
        root.listFiles().orEmpty().mapNotNull { get(it.name) }.sortedByDescending { it.createdAt }

    fun save(session: Session) {
        File(dir(session.id), SESSION_FILE).writeText(json.encodeToString(session))
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    /** Removes clips that were recorded or imported but never finished analysing. */
    fun deleteAbandoned(olderThanMs: Long = 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        root.listFiles().orEmpty()
            .filter { !File(it, SESSION_FILE).exists() && it.lastModified() < cutoff }
            .forEach { it.deleteRecursively() }
    }

    /** Bytes used by the saved videos, and by everything else kept for each session. */
    fun storageUsage(): StorageUsage {
        var video = 0L
        var other = 0L
        root.listFiles().orEmpty().forEach { dir ->
            dir.listFiles().orEmpty().forEach { file ->
                if (file.name == "video.mp4") video += file.length() else other += file.length()
            }
        }
        return StorageUsage(videoBytes = video, otherBytes = other)
    }

    /**
     * Deletes the video of every finished session recorded before [cutoffMs], keeping its results and
     * pose data. Returns how many videos were removed and the bytes freed.
     */
    fun deleteVideosBefore(cutoffMs: Long): Pair<Int, Long> {
        var count = 0
        var freed = 0L
        list().filter { it.createdAt < cutoffMs }.forEach { session ->
            val video = videoFile(session.id)
            if (video.exists()) {
                freed += video.length()
                if (video.delete()) count++
            }
        }
        return count to freed
    }

    /** Whether this session still has its video. Results remain after the video is removed. */
    fun hasVideo(id: String): Boolean = videoFile(id).exists()

    fun saveCaptureInfo(id: String, info: CaptureInfo) {
        File(dir(id), CAPTURE_FILE).writeText(json.encodeToString(info))
    }

    fun loadCaptureInfo(id: String): CaptureInfo {
        val file = File(File(root, id), CAPTURE_FILE)
        if (!file.exists()) return CaptureInfo()
        return runCatching { json.decodeFromString<CaptureInfo>(file.readText()) }.getOrDefault(CaptureInfo())
    }

    fun saveRun(run: WholeRun) {
        runsRoot.mkdirs()
        // Written to a temporary file first so a run in progress is never left half-written.
        val temp = File(runsRoot, "${run.id}.tmp")
        temp.writeText(json.encodeToString(run))
        temp.renameTo(File(runsRoot, "${run.id}.json"))
    }

    fun getRun(id: String): WholeRun? {
        val file = File(runsRoot, "$id.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<WholeRun>(file.readText()) }.getOrNull()
    }

    fun listRuns(): List<WholeRun> = runsRoot.listFiles().orEmpty()
        .filter { it.extension == "json" }
        .mapNotNull { getRun(it.nameWithoutExtension) }
        .sortedByDescending { it.startedAt }

    /** Deletes the run and every clip taken during it. */
    fun deleteRun(id: String) {
        list().filter { it.runId == id }.forEach { delete(it.id) }
        File(runsRoot, "$id.json").delete()
    }

    fun savePose(id: String, track: PoseTrack) {
        DataOutputStream(File(dir(id), POSE_FILE).outputStream().buffered()).use { out ->
            out.writeInt(POSE_FORMAT)
            out.writeFloat(track.fps)
            out.writeInt(track.width)
            out.writeInt(track.height)
            out.writeInt(track.frames.size)
            for (frame in track.frames) {
                out.writeBoolean(frame != null)
                if (frame == null) continue
                for (i in 0 until Landmark.COUNT) {
                    out.writeFloat(frame.x[i])
                    out.writeFloat(frame.y[i])
                    out.writeFloat(frame.visibility[i])
                }
            }
        }
    }

    fun loadPose(id: String): PoseTrack? {
        val file = File(File(root, id), POSE_FILE)
        if (!file.exists()) return null
        return DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != POSE_FORMAT) return null
            val fps = input.readFloat()
            val width = input.readInt()
            val height = input.readInt()
            val frames = List(input.readInt()) {
                if (!input.readBoolean()) return@List null
                val x = FloatArray(Landmark.COUNT)
                val y = FloatArray(Landmark.COUNT)
                val visibility = FloatArray(Landmark.COUNT)
                for (i in 0 until Landmark.COUNT) {
                    x[i] = input.readFloat()
                    y[i] = input.readFloat()
                    visibility[i] = input.readFloat()
                }
                FramePose(x, y, visibility)
            }
            PoseTrack(fps, width, height, frames)
        }
    }

    private fun dir(id: String) = File(root, id).apply { mkdirs() }

    private companion object {
        const val SESSION_FILE = "session.json"
        const val CAPTURE_FILE = "capture.json"
        const val POSE_FILE = "pose.bin"
        const val POSE_FORMAT = 1
    }
}
