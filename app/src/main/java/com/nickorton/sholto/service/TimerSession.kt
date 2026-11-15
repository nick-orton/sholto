package com.nickorton.sholto.service

import com.nickorton.sholto.audio.CuePlanner
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.TapBehavior
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.RoundEngine
import com.nickorton.sholto.engine.RoundState
import com.nickorton.sholto.engine.SessionStatus
import com.nickorton.sholto.ui.timer.TimerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controls a round-timer session and exposes its state. Owned by
 * [WorkoutService] and observed by the UI.
 */
interface WorkoutController {
    val uiState: StateFlow<TimerUiState>

    fun start()

    fun pause()

    fun resume()

    fun end()

    fun home()
}

/**
 * Runs one round-timer session at a time on [scope] and exposes it as
 * [uiState]. Pure Kotlin and Coroutines so it can be tested on the JVM.
 *
 * Each session uses the [settings] as they were when it started, so a change
 * takes effect from the next session.
 *
 * While a session runs it polls the engine every [tickMs], and also wakes on
 * every whole second of the countdown. Phase changes and countdown beeps all
 * fall on whole seconds, so cues go out on time rather than up to a tick
 * late. The engine works out the time from the [clock], so a late or missed
 * poll only delays the redraw and never changes the timing.
 *
 * When a phone or VoIP call comes in ([isInCall] turns true, or
 * [onCallStateChanged] is called with true), a running session pauses
 * automatically and stays paused until [resume] is called.
 *
 * @param cuePlayer Plays the sounds, for the cues switched on in settings.
 * @param hapticPlayer Vibrates, when vibration is on in settings.
 */
class TimerSession(
    private val scope: CoroutineScope,
    settings: Flow<TimerSettings> = flowOf(TimerSettings()),
    private val clock: Clock = ElapsedRealtimeClock,
    private val cuePlayer: CuePlayer = CuePlayer.None,
    private val hapticPlayer: CuePlayer = CuePlayer.None,
    private val isInCall: () -> Boolean = { false },
    private val tickMs: Long = 100L,
) : WorkoutController {
    private val _uiState = MutableStateFlow<TimerUiState>(TimerUiState.Loading)
    override val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    /** The latest saved settings, or null until they've been read. */
    private var latest: TimerSettings? = null

    /** The settings of the current or last session. */
    private var session = TimerSettings()
    private var sessionPlayer = CuePlayer.None
    private var engine: RoundEngine? = null
    private var planner = CuePlanner()
    private var ticker: Job? = null
    private var phaseTimeLeftMs = 0L
    private var wasInCall = false

    init {
        scope.launch {
            settings.collect { s ->
                latest = s
                val state = _uiState.value
                if (state is TimerUiState.Loading || state is TimerUiState.Idle) {
                    _uiState.value = TimerUiState.Idle(s.round)
                }
            }
        }
    }

    /**
     * Starts a new session from Home or Done. Ignored while one is running,
     * or before the settings have been read.
     */
    override fun start() {
        if (_uiState.value is TimerUiState.Running) return
        val s = latest ?: return
        session = s
        sessionPlayer = playerFor(s)
        wasInCall = isInCall()
        engine = RoundEngine(s.round, clock).also { it.start() }
        planner = CuePlanner()
        refresh()
        startTicker()
    }

    override fun pause() {
        if (engine?.pause() == true) {
            stopTicker()
            refresh()
        }
    }

    override fun resume() {
        if (engine?.resume() == true) {
            wasInCall = isInCall()
            refresh()
            startTicker()
        }
    }

    /** Ends the session early and shows the summary. */
    override fun end() {
        if (engine?.stop() == true) {
            stopTicker()
            // No bell for ending early, even if a boundary had just passed.
            refresh(playCues = false)
        }
    }

    /** Goes back to Home. Only from Done, so a running session can't be lost. */
    override fun home() {
        if (_uiState.value !is TimerUiState.Done) return
        engine = null
        _uiState.value = TimerUiState.Idle((latest ?: session).round)
    }

    /**
     * Pauses the session when a call begins. Ending the call leaves the
     * session paused until you resume it yourself.
     */
    fun onCallStateChanged(inCall: Boolean) {
        val startedCall = inCall && !wasInCall
        wasInCall = inCall
        if (startedCall) pause()
    }

    /** Sounds for the cues switched on, and vibration if it's on. */
    private fun playerFor(s: TimerSettings) =
        CuePlayer { cue ->
            if (cue in s.sounds) cuePlayer.play(cue)
            if (s.vibration) hapticPlayer.play(cue)
        }

    private fun startTicker() {
        ticker?.cancel()
        ticker =
            scope.launch {
                while (isActive) {
                    delay(nextTickDelayMs())
                    onCallStateChanged(isInCall())
                    if (isActive) refresh()
                }
            }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    /** Up to [tickMs], but never past the next whole second of the countdown. */
    private fun nextTickDelayMs(): Long {
        val left = phaseTimeLeftMs
        if (left <= 0L) return tickMs
        return minOf(tickMs, (left - 1L) % 1000L + 1L)
    }

    private fun refresh(playCues: Boolean = true) {
        val engine = engine ?: return
        val changes = engine.pollEvents()
        val state = engine.snapshot()
        phaseTimeLeftMs = state.phaseTimeLeftMs
        _uiState.value = state.toUiState()
        if (playCues) planner.cuesFor(changes, state).forEach(sessionPlayer::play)
        if (state.status == SessionStatus.Finished || state.status == SessionStatus.Stopped) stopTicker()
    }

    private fun RoundState.toUiState(): TimerUiState =
        when (status) {
            SessionStatus.Finished, SessionStatus.Stopped -> {
                TimerUiState.Done(
                    roundsCompleted = roundsCompleted,
                    totalTimeMs = elapsedMs,
                    finished = status == SessionStatus.Finished,
                )
            }

            SessionStatus.Idle, SessionStatus.Running, SessionStatus.Paused -> {
                toRunningUiState(session)
            }
        }
}

/** The running timer screen's state for this reading, with [settings]' display options. */
internal fun RoundState.toRunningUiState(settings: TimerSettings): TimerUiState.Running =
    TimerUiState.Running(
        phase = phase,
        round = round,
        totalRounds = totalRounds,
        phaseTimeLeftMs = phaseTimeLeftMs,
        roundProgress = roundProgress,
        warningLengthMs = settings.round.warningLengthMs,
        isPaused = status == SessionStatus.Paused,
        keepScreenOn = settings.keepScreenOn,
        tapEnds = settings.tapBehavior == TapBehavior.Stop,
    )
