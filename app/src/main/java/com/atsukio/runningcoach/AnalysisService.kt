package com.atsukio.runningcoach

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.atsukio.runningcoach.data.AnalysisRunner
import com.atsukio.runningcoach.data.AnalysisState
import com.atsukio.runningcoach.data.AnalysisTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the app's process alive while clips are analysed, with a notification showing progress, so the
 * person can leave the app. It does nothing else: the work itself is done by [AnalysisRunner]. Nothing
 * is uploaded; the notification only shows a percentage.
 */
class AnalysisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching = false
    private val announced = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Analysis", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progress while a clip is analysed on this phone"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go to the foreground at once; the real text follows from the watcher.
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing(null), foregroundType())
        if (!watching) {
            watching = true
            scope.launch { watch() }
        }
        return START_NOT_STICKY
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watch() {
        AnalysisRunner.get(this).tasks
            .flatMapLatest { tasks ->
                if (tasks.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(tasks.values.map { task -> task.state.map { task to it } }) { it.toList() }
                }
            }
            .collect { pairs ->
                pairs.filter { (task, state) -> state is AnalysisState.Done && announced.add(task.sessionId) }
                    .forEach { (task, _) -> announceDone(task) }
                val running = pairs.filter { it.second.inProgress }
                if (running.isEmpty()) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val state = running.first().second
                    val notification = ongoing(state)
                    NotificationManagerCompat.from(this).apply {
                        if (areNotificationsEnabled()) notifyIfAllowed(ONGOING_ID, notification)
                    }
                }
            }
    }

    private fun NotificationManagerCompat.notifyIfAllowed(id: Int, notification: android.app.Notification) {
        @Suppress("MissingPermission") // Checked by areNotificationsEnabled() just before.
        notify(id, notification)
    }

    private fun ongoing(state: AnalysisState?): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_analysis)
            .setContentTitle("Analysing your clip")
            .setContentIntent(openApp(null))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        when (state) {
            is AnalysisState.Running -> {
                val percent = (state.progress * 100).toInt()
                builder.setContentText("$percent%").setProgress(100, percent, false)
            }
            else -> builder.setContentText("Measuring your form…").setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun announceDone(task: AnalysisTask) {
        // Only worth a notification when the person has left the app.
        if (MainActivity.visible) return
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_analysis)
            .setContentTitle("Your analysis is ready")
            .setContentText("Tap to see your running form.")
            .setContentIntent(openApp(task.sessionId))
            .setAutoCancel(true)
            .build()
        manager.notifyIfAllowed(DONE_ID_BASE + task.sessionId.hashCode(), notification)
    }

    private fun openApp(sessionId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            sessionId?.let { putExtra(MainActivity.EXTRA_OPEN_SESSION, it) }
        }
        return PendingIntent.getActivity(
            this, sessionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun foregroundType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "analysis"
        private const val ONGOING_ID = 1
        private const val DONE_ID_BASE = 1000

        fun intent(context: Context) = Intent(context, AnalysisService::class.java)
    }
}
