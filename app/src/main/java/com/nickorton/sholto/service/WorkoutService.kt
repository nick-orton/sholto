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
import com.nickorton.sholto.audio.TtsAnnouncer
import com.nickorton.sholto.ui.caller.CallerUiState
import com.nickorton.sholto.ui.timer.TimerUiState
import com.nickorton.sholto.ui.timer.callerNotificationText
import com.nickorton.sholto.ui.timer.callerNotificationTitle
import com.nickorton.sholto.ui.timer.notificationText
import com.nickorton.sholto.ui.timer.notificationTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * Foreground service that owns the running [TimerSession], [CallerSession],
 * [SoundPlayer], [TtsAnnouncer] and [Haptics] (DESIGN.md §5.4).
 *
 * While a timer or punch-caller session is running or paused:
 * - The service runs in the foreground (`mediaPlayback`) with an ongoing
 *   notification showing the current state and time, plus Pause/Resume and
 *   Stop actions that work from the shade and lock screen.
 * - While unpaused, a partial wake lock keeps the CPU awake with the screen
 *   off so bells and spoken callouts fire on time.
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
    private lateinit var announcer: TtsAnnouncer
    private lateinit var session: TimerSession
    private lateinit var callerSession: CallerSession
    private lateinit var notificationManager: NotificationManager
    private lateinit var audioManager: AudioManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var finalBellJob: Job? = null
    private var bound = false
    private var startedService = false
    private var inForeground = false
    private var lastNotification: NotificationContent? = null

    override val uiState: StateFlow<TimerUiState>
        get() = session.uiState

    val callerController: CallerController =
        object : CallerController {
            override val uiState: StateFlow<CallerUiState>
                get() = callerSession.uiState

            override fun start() {
                if (session.uiState.value is TimerUiState.Running) return
                finalBellJob?.cancel()
                finalBellJob = null
                callerSession.start()
            }

            override fun pause() = callerSession.pause()

            override fun resume() = callerSession.resume()

            override fun end() = callerSession.end()

            override fun home() = callerSession.home()
        }

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
        announcer = TtsAnnouncer(this)
        val inCallCheck = { isCallAudioMode(audioManager.mode) }
        session =
            TimerSession(
                scope = serviceScope,
                settings = app.settingsRepository.settings,
                cuePlayer = soundPlayer,
                hapticPlayer = haptics,
                isInCall = inCallCheck,
            )
        callerSession =
            CallerSession(
                scope = serviceScope,
                settings = app.settingsRepository.callerSettings,
                announcer = announcer,
                cuePlayer = soundPlayer,
                hapticPlayer = haptics,
                isInCall = inCallCheck,
            )
        registerCallModeListener()

        serviceScope.launch {
            combine(session.uiState, callerSession.uiState) { timerState, callerState ->
                timerState to callerState
            }.collect { (timerState, callerState) ->
                onStatesChanged(timerState, callerState)
            }
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
                val current = activeNotificationContent()
                // Every startForegroundService call must be answered by
                // startForeground, even if the session ended before this
                // intent arrived.
                if (!inForeground) {
                    promoteToForeground(current ?: FALLBACK_NOTIFICATION)
                }
                if (current == null) {
                    exitForeground()
                    stopIfIdle()
                }
            }

            ACTION_PAUSE -> {
                session.pause()
                callerSession.pause()
            }

            ACTION_RESUME -> {
                session.resume()
                callerSession.resume()
            }

            ACTION_STOP -> {
                session.end()
                callerSession.end()
                if (!bound) {
                    session.home()
                    callerSession.home()
                }
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
        announcer.release()
        super.onDestroy()
    }

    override fun start() {
        if (callerSession.uiState.value is CallerUiState.Running) return
        finalBellJob?.cancel()
        finalBellJob = null
        session.start()
    }

    override fun pause() = session.pause()

    override fun resume() = session.resume()

    override fun end() = session.end()

    override fun home() = session.home()

    private fun activeNotificationContent(): NotificationContent? {
        val timer = session.uiState.value
        if (timer is TimerUiState.Running) return timer.toNotificationContent()
        val caller = callerSession.uiState.value
        if (caller is CallerUiState.Running) return caller.toNotificationContent()
        return null
    }

    private fun onStatesChanged(
        timerState: TimerUiState,
        callerState: CallerUiState,
    ) {
        val active =
            when {
                timerState is TimerUiState.Running -> timerState.toNotificationContent()
                callerState is CallerUiState.Running -> callerState.toNotificationContent()
                else -> null
            }
        if (active != null) {
            finalBellJob?.cancel()
            finalBellJob = null
            if (active.isPaused) releaseWakeLock() else acquireWakeLock()
            updateNotificationIfNeeded(active)
            return
        }

        exitForeground()
        val naturalFinish =
            (timerState is TimerUiState.Done && timerState.finished) ||
                (callerState is CallerUiState.Done && callerState.finished)
        if (naturalFinish) {
            if (finalBellJob?.isActive != true) {
                acquireWakeLock()
                finalBellJob =
                    serviceScope.launch {
                        delay(FINAL_BELL_HOLD_MS)
                        releaseWakeLock()
                        if (!bound) {
                            session.home()
                            callerSession.home()
                            startedService = false
                            stopSelf()
                        }
                    }
            }
        } else if (finalBellJob?.isActive != true) {
            releaseWakeLock()
            stopIfIdle()
        }
    }

    private fun promoteToForeground(content: NotificationContent) {
        if (!startedService) {
            startedService = true
            ContextCompat.startForegroundService(
                this,
                Intent(this, WorkoutService::class.java).setAction(ACTION_START),
            )
        }
        val notification = buildNotification(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        inForeground = true
        lastNotification = content
    }

    private fun updateNotificationIfNeeded(content: NotificationContent) {
        if (!inForeground) {
            promoteToForeground(content)
            return
        }
        if (content == lastNotification) return
        lastNotification = content
        if (canPostNotifications()) {
            runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification(content)) }
        }
    }

    private fun exitForeground() {
        if (!inForeground) return
        inForeground = false
        lastNotification = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopIfIdle() {
        if (!bound && activeNotificationContent() == null && finalBellJob?.isActive != true) {
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

    private fun buildNotification(content: NotificationContent): Notification {
        val openAppIntent =
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN_APP,
                Intent(this, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val toggleAction =
            if (content.isPaused) {
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
            .setContentTitle(content.title)
            .setContentText(content.text)
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
                    val inCall = isCallAudioMode(mode)
                    session.onCallStateChanged(inCall)
                    callerSession.onCallStateChanged(inCall)
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
     * this changes keeps updates to once a second instead of every tick.
     */
    private data class NotificationContent(
        val title: String,
        val text: String,
        val isPaused: Boolean,
    )

    private fun TimerUiState.Running.toNotificationContent() =
        NotificationContent(
            title = notificationTitle(this),
            text = notificationText(this),
            isPaused = isPaused,
        )

    private fun CallerUiState.Running.toNotificationContent() =
        NotificationContent(
            title = callerNotificationTitle(this),
            text = callerNotificationText(this),
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

        private val FALLBACK_NOTIFICATION =
            NotificationContent(
                title = "GET READY · 0:00",
                text = "ROUND 1",
                isPaused = false,
            )
    }
}
