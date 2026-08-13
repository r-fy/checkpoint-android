package org.rfisolns.checkpoint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Screen is on/unlocked, or the user manually started a session -> prompts fire
// every interval. Screen off for longer than SCREEN_OFF_GRACE_MILLIS with no
// manual override -> session goes quiet. Manual override ignores screen state
// entirely, same as explicitly starting a stopwatch.
class CheckpointSessionService : LifecycleService() {

    companion object {
        const val ACTION_START_MANUAL = "org.rfisolns.checkpoint.action.START_MANUAL"
        const val ACTION_STOP_MANUAL = "org.rfisolns.checkpoint.action.STOP_MANUAL"
        private const val LISTENER_CHANNEL = "session_listener"
        private const val PROMPT_CHANNEL = "checkin_prompts"
        private const val LISTENER_NOTIFICATION_ID = 1
        private const val PROMPT_NOTIFICATION_ID = 2
        private const val SCREEN_OFF_GRACE_MILLIS = 2 * 60 * 1000L
    }

    private var screenOn = false
    private var manualOverride = false
    private var promptLoopJob: Job? = null
    private var screenOffTimeoutJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOn = true
                    screenOffTimeoutJob?.cancel()
                    updateSessionState()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    scheduleScreenOffTimeout()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(LISTENER_NOTIFICATION_ID, buildListenerNotification())
        screenOn = getSystemService<PowerManager>()?.isInteractive == true
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        updateSessionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_MANUAL -> {
                manualOverride = true
                screenOffTimeoutJob?.cancel()
                updateSessionState()
            }
            ACTION_STOP_MANUAL -> {
                manualOverride = false
                updateSessionState()
            }
        }
        return START_STICKY
    }

    private fun scheduleScreenOffTimeout() {
        screenOffTimeoutJob?.cancel()
        if (manualOverride) return
        screenOffTimeoutJob = lifecycleScope.launch {
            delay(SCREEN_OFF_GRACE_MILLIS)
            updateSessionState()
        }
    }

    private fun updateSessionState() {
        val active = (screenOn || manualOverride) && !Settings.isPaused(this)
        CheckpointSessionState.setSessionActive(active)
        CheckpointSessionState.setManualOverride(manualOverride)
        if (active) startPromptLoop() else stopPromptLoop()
    }

    private fun startPromptLoop() {
        if (promptLoopJob?.isActive == true) return
        promptLoopJob = lifecycleScope.launch {
            while (isActive) {
                val intervalMinutes = Settings.getIntervalMinutes(this@CheckpointSessionService)
                val intervalMillis = intervalMinutes * 60 * 1000L
                Settings.setNextPromptEpochMillis(
                    this@CheckpointSessionService,
                    System.currentTimeMillis() + intervalMillis,
                )
                delay(intervalMillis)
                if (!Settings.isPaused(this@CheckpointSessionService)) {
                    showPromptNotification(intervalMinutes)
                }
            }
        }
    }

    private fun stopPromptLoop() {
        promptLoopJob?.cancel()
        promptLoopJob = null
        Settings.clearNextPromptEpochMillis(this)
    }

    private fun showPromptNotification(intervalMinutes: Int) {
        val openIntent = Intent(this, QuickEntryActivity::class.java).apply {
            putExtra(QuickEntryActivity.EXTRA_DURATION_MINUTES, intervalMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PROMPT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("What have you been doing?")
            .setContentText("Last $intervalMinutes minutes — tap to log it")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(PROMPT_NOTIFICATION_ID, notification)
    }

    private fun buildListenerNotification(): Notification =
        NotificationCompat.Builder(this, LISTENER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Checkpoint is listening")
            .setContentText("Watching for screen unlock to start a check-in session")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(LISTENER_CHANNEL, "Session listener", NotificationManager.IMPORTANCE_MIN),
        )
        manager.createNotificationChannel(
            NotificationChannel(PROMPT_CHANNEL, "Check-in prompts", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        promptLoopJob?.cancel()
        screenOffTimeoutJob?.cancel()
        CheckpointSessionState.setSessionActive(false)
    }
}
