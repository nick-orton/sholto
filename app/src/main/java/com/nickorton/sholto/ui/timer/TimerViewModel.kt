package com.nickorton.sholto.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.sholto.audio.CuePlanner
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.TapBehavior
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.engine.RoundEngine
import com.nickorton.sholto.engine.RoundState
import com.nickorton.sholto.engine.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the timer screens show. Each variant is one screen. */
sealed interface TimerUiState {
    /** The saved settings haven't been read yet. Takes a few milliseconds. */
    data object Loading : TimerUiState

    /** Home: no session yet. Starting one uses [config]. */
    data class Idle(
        val config: RoundConfig,
    ) : TimerUiState

    /**
     * A session is running or paused.
     *
     * @property keepScreenOn Keep the display on while this is shown.
     * @property tapEnds A tap ends the session instead of pausing it.
     */
    data class Running(
        val phase: Phase,
        val round: Int,
        val totalRounds: Int?,
        val phaseTimeLeftMs: Long,
        val roundProgress: Float,
        val warningLengthMs: Long,
        val isPaused: Boolean,
        val keepScreenOn: Boolean = true,
        val tapEnds: Boolean = false,
    ) : TimerUiState

    /**
     * The session is over.
     *
     * @property finished True if every round ran, false if it was ended early.
     * @property totalTimeMs Active time, not counting pauses.
     */
    data class Done(
        val roundsCompleted: Int,
        val totalTimeMs: Long,
        val finished: Boolean,
    ) : TimerUiState
}

/**
 * Runs one round-timer session at a time and exposes it as [uiState].
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
 * @param cuePlayer Plays the sounds, for the cues switched on in settings.
 * @param hapticPlayer Vibrates, when vibration is on in settings.
 */
class TimerViewModel(
    settings: Flow<TimerSettings> = flowOf(TimerSettings()),
    private val clock: Clock = ElapsedRealtimeClock,
    private val cuePlayer: CuePlayer = CuePlayer.None,
    private val hapticPlayer: CuePlayer = CuePlayer.None,
    private val tickMs: Long = 100L,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TimerUiState>(TimerUiState.Loading)
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    /** The latest saved settings, or null until they've been read. */
    private var latest: TimerSettings? = null

    /** The settings of the current or last session. */
    private var session = TimerSettings()
    private var sessionPlayer = CuePlayer.None
    private var engine: RoundEngine? = null
    private var planner = CuePlanner()
    private var ticker: Job? = null
    private var phaseTimeLeftMs = 0L

    init {
        viewModelScope.launch {
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
    fun start() {
        if (_uiState.value is TimerUiState.Running) return
        val s = latest ?: return
        session = s
        sessionPlayer = playerFor(s)
        engine = RoundEngine(s.round, clock).also { it.start() }
        planner = CuePlanner()
        refresh()
        startTicker()
    }

    fun pause() {
        if (engine?.pause() == true) {
            stopTicker()
            refresh()
        }
    }

    fun resume() {
        if (engine?.resume() == true) {
            refresh()
            startTicker()
        }
    }

    /** Ends the session early and shows the summary. */
    fun end() {
        if (engine?.stop() == true) {
            stopTicker()
            // No bell for ending early, even if a boundary had just passed.
            refresh(playCues = false)
        }
    }

    /** Goes back to Home. Only from Done, so a running session can't be lost. */
    fun home() {
        if (_uiState.value !is TimerUiState.Done) return
        engine = null
        _uiState.value = TimerUiState.Idle((latest ?: session).round)
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
            viewModelScope.launch {
                while (isActive) {
                    delay(nextTickDelayMs())
                    refresh()
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
                TimerUiState.Running(
                    phase = phase,
                    round = round,
                    totalRounds = totalRounds,
                    phaseTimeLeftMs = phaseTimeLeftMs,
                    roundProgress = roundProgress,
                    warningLengthMs = session.round.warningLengthMs,
                    isPaused = status == SessionStatus.Paused,
                    keepScreenOn = session.keepScreenOn,
                    tapEnds = session.tapBehavior == TapBehavior.Stop,
                )
            }
        }
}
