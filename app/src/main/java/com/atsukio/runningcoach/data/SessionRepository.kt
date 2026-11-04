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

/**
 * Stores each session in app-private storage: `sessions/<id>/` holds the video,
 * the pose track and the results. Nothing is written anywhere else.
 */
class SessionRepository(context: Context) {
    private val root = File(context.filesDir, "sessions")
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
        const val POSE_FILE = "pose.bin"
        const val POSE_FORMAT = 1
    }
}
