package com.nickorton.sholto.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nickorton.sholto.MainActivity
import com.nickorton.sholto.R
import com.nickorton.sholto.SholtoApplication
import com.nickorton.sholto.audio.Haptics
import com.nickorton.sholto.audio.SoundPlayer
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.ui.timer.TimerUiState
import com.nickorton.sholto.ui.timer.formatCountdown
import com.nickorton.sholto.ui.timer.notificationText
import com.nickorton.sholto.ui.timer.notificationTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * True when [AudioManager.getMode] reports a ringing or active phone or VoIP
 * call. Checking the audio mode needs no phone-state permission.
 */
internal fun isCallAudioMode(mode: Int): Boolean =
    mode == AudioManager.MODE_RINGTONE ||
        mode == AudioManager.MODE_IN_CALL ||
        mode == AudioManager.MODE_IN_COMMUNICATION ||
        mode == AudioManager.MODE_CALL_SCREENING

/**
 * Foreground service that owns the running [TimerSession], [SoundPlayer] and
 * [Haptics] (DESIGN.md §5.4).
 *
 * While a session is running or paused:
 * - The service runs in the foreground (`mediaPlayback`) with an ongoing
 *   notification showing the phase, time left and round, plus Pause/Resume
 *   and Stop actions that work from the shade and lock screen.
 * - While unpaused, a partial wake lock keeps the CPU awake with the screen
 *   off so cues fire on their exact second boundaries.
 * - Swiping the app away from Recents leaves the session running from the
 *   notification; tapping the notification reattaches the UI.
 * - An incoming phone or VoIP call pauses a running session automatically.
 */
class WorkoutService :
    Service(),
    WorkoutController {
    inner class LocalBinder : Binder() {
        val service: WorkoutService get() = this@WorkoutService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var soundPlayer: SoundPlayer
    private lateinit var haptics: Haptics
    private lateinit var session: TimerSession
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var finalBellJob: Job? = null
    private var bound = false
    private var startedService = false
    private var inForeground = false
    private var lastNotification: NotificationKey? = null

    override val uiState: StateFlow<TimerUiState>
        get() = session.uiState

    override fun onCreate() {
        super.onCreate()
        val app = application as SholtoApplication
        notificationManager = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply { setReferenceCounted(false) }

        createNotificationChannel()
        soundPlayer = SoundPlayer(this)
        haptics = Haptics(this)
        session =
            TimerSession(
                scope = serviceScope,
                settings = app.settingsRepository.settings,
                cuePlayer = soundPlayer,
                hapticPlayer = haptics,
                isInCall = { isCallAudioMode(audioManager.mode) },
            )
        registerCallModeListener()

        serviceScope.launch {
            session.uiState.collect(::onSessionStateChanged)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                startedService = true
                val current = session.uiState.value as? TimerUiState.Running
                // Every startForegroundService call must be answered by
                // startForeground, even if the session ended before this
                // intent arrived.
                if (!inForeground) {
                    promoteToForeground(current ?: FALLBACK_RUNNING_STATE)
                }
                if (current == null) {
                    exitForeground()
                    stopIfIdle()
                }
            }

            ACTION_PAUSE -> {
                pause()
            }

            ACTION_RESUME -> {
                resume()
            }

            ACTION_STOP -> {
                end()
                if (!bound) home()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        bound = true
        return binder
    }

    override fun onRebind(intent: Intent?) {
        bound = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        stopIfIdle()
        return true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running in the foreground when a session is active or paused;
        // otherwise clean up.
        stopIfIdle()
    }

    override fun onDestroy() {
        unregisterCallModeListener()
        releaseWakeLock()
        serviceScope.cancel()
        soundPlayer.release()
        super.onDestroy()
    }

    override fun start() {
        finalBellJob?.cancel()
        finalBellJob = null
        session.start()
    }

    override fun pause() = session.pause()

    override fun resume() = session.resume()

    override fun end() = session.end()

    override fun home() = session.home()

    private fun onSessionStateChanged(state: TimerUiState) {
        when (state) {
            is TimerUiState.Running -> {
                finalBellJob?.cancel()
                finalBellJob = null
                if (state.isPaused) releaseWakeLock() else acquireWakeLock()
                updateNotificationIfNeeded(state)
            }

            is TimerUiState.Done -> {
                exitForeground()
                if (state.finished) {
                    // Hold the wake lock until the 4-second final bell has
                    // finished ringing and released audio focus.
                    acquireWakeLock()
                    finalBellJob?.cancel()
                    finalBellJob =
                        serviceScope.launch {
                            delay(FINAL_BELL_HOLD_MS)
                            releaseWakeLock()
                            if (!bound) {
                                session.home()
                                startedService = false
                                stopSelf()
                            }
                        }
                } else {
                    releaseWakeLock()
                    stopIfIdle()
                }
            }

            TimerUiState.Loading, is TimerUiState.Idle -> {
                exitForeground()
                if (finalBellJob?.isActive != true) {
                    releaseWakeLock()
                    stopIfIdle()
                }
            }
        }
    }

    private fun promoteToForeground(state: TimerUiState.Running) {
        if (!startedService) {
            startedService = true
            ContextCompat.startForegroundService(
                this,
                Intent(this, WorkoutService::class.java).setAction(ACTION_START),
            )
        }
        val key = state.toNotificationKey()
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
        lastNotification = key
    }

    private fun updateNotificationIfNeeded(state: TimerUiState.Running) {
        val key = state.toNotificationKey()
        if (!inForeground) {
            promoteToForeground(state)
            return
        }
        if (key == lastNotification) return
        lastNotification = key
        if (canPostNotifications()) {
            runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(state)) }
        }
    }

    private fun exitForeground() {
        if (!inForeground) return
        inForeground = false
        lastNotification = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopIfIdle() {
        if (!bound && session.uiState.value !is TimerUiState.Running && finalBellJob?.isActive != true) {
            startedService = false
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private fun canPostNotifications(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: TimerUiState.Running): Notification {
        val openAppIntent =
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN_APP,
                Intent(this, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleAction =
            if (state.isPaused) {
                NotificationCompat.Action
                    .Builder(
                        0,
                        getString(R.string.notification_resume),
                        serviceActionIntent(ACTION_RESUME, REQUEST_RESUME),
                    ).build()
            } else {
                NotificationCompat.Action
                    .Builder(
                        0,
                        getString(R.string.pause),
                        serviceActionIntent(ACTION_PAUSE, REQUEST_PAUSE),
                    ).build()
            }
        val stopAction =
            NotificationCompat.Action
                .Builder(
                    0,
                    getString(R.string.notification_stop),
                    serviceActionIntent(ACTION_STOP, REQUEST_STOP),
                ).build()

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(notificationTitle(state))
            .setContentText(notificationText(state))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggleAction)
            .addAction(stopAction)
            .build()
    }

    private fun serviceActionIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, WorkoutService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun registerCallModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener =
                AudioManager.OnModeChangedListener { mode ->
                    session.onCallStateChanged(isCallAudioMode(mode))
                }
            modeListener = listener
            audioManager.addOnModeChangedListener(mainExecutor, listener)
        }
    }

    private fun unregisterCallModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener?.let(audioManager::removeOnModeChangedListener)
            modeListener = null
        }
    }

    /**
     * Everything visible in the notification. Rebuilding and posting only when
     * this changes keeps updates to once a second instead of every 100 ms tick.
     */
    private data class NotificationKey(
        val phase: Phase,
        val round: Int,
        val totalRounds: Int?,
        val warningLengthMs: Long,
        val countdown: String,
        val isPaused: Boolean,
    )

    private fun TimerUiState.Running.toNotificationKey() =
        NotificationKey(
            phase = phase,
            round = round,
            totalRounds = totalRounds,
            warningLengthMs = warningLengthMs,
            countdown = formatCountdown(phaseTimeLeftMs),
            isPaused = isPaused,
        )

    companion object {
        const val ACTION_START = "com.nickorton.sholto.action.START"
        const val ACTION_PAUSE = "com.nickorton.sholto.action.PAUSE"
        const val ACTION_RESUME = "com.nickorton.sholto.action.RESUME"
        const val ACTION_STOP = "com.nickorton.sholto.action.STOP"

        private const val CHANNEL_ID = "workout_session"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "Sholto:WorkoutSession"

        // LongBell is 4,000 ms plus 250 ms of audio focus tail.
        private const val FINAL_BELL_HOLD_MS = 4_250L

        // Upper ceiling for an unpaused session; re-applied on resume.
        private const val MAX_WAKE_LOCK_MS = 6 * 60 * 60_000L

        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_PAUSE = 2
        private const val REQUEST_RESUME = 3
        private const val REQUEST_STOP = 4

        private val FALLBACK_RUNNING_STATE =
            TimerUiState.Running(
                phase = Phase.GetReady,
                round = 1,
                totalRounds = null,
                phaseTimeLeftMs = 0L,
                roundProgress = 0f,
                warningLengthMs = 0L,
                isPaused = false,
            )
    }
}
