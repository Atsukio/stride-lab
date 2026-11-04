package com.atsukio.runningcoach.data

import android.content.Context
import com.atsukio.runningcoach.sensors.SensorHub
import com.atsukio.runningcoach.sensors.SensorSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps a whole run going while the user moves between the run, capture and analysis screens,
 * logging watch readings once a second and saving them every 30 seconds.
 */
class RunRecorder private constructor(context: Context) {
    private val repo = SessionRepository(context)
    private val hub = SensorHub.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val samples = mutableListOf<SensorSample>()
    private var job: Job? = null

    private val _active = MutableStateFlow<WholeRun?>(null)
    val active = _active.asStateFlow()

    fun start(): WholeRun {
        _active.value?.let { return it }
        val run = WholeRun(id = repo.newSessionId(), startedAt = System.currentTimeMillis())
        synchronized(samples) { samples.clear() }
        repo.saveRun(run)
        _active.value = run
        job = scope.launch {
            var sinceSave = 0
            while (isActive) {
                delay(1000)
                val tSec = (System.currentTimeMillis() - run.startedAt) / 1000.0
                hub.freshStats()?.let { synchronized(samples) { samples += it.toSample(tSec) } }
                if (++sinceSave >= 30) {
                    repo.saveRun(snapshot(run))
                    sinceSave = 0
                }
            }
        }
        return run
    }

    fun finish(): WholeRun? {
        val run = _active.value ?: return null
        job?.cancel()
        job = null
        val finished = snapshot(run).copy(endedAt = System.currentTimeMillis())
        repo.saveRun(finished)
        _active.value = null
        return finished
    }

    private fun snapshot(run: WholeRun) = run.copy(samples = synchronized(samples) { samples.toList() })

    companion object {
        @Volatile
        private var instance: RunRecorder? = null

        fun get(context: Context): RunRecorder = instance ?: synchronized(this) {
            instance ?: RunRecorder(context.applicationContext).also { instance = it }
        }
    }
}
