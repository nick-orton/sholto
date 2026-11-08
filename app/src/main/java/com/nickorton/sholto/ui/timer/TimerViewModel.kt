package com.nickorton.sholto.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.engine.RoundEngine
import com.nickorton.sholto.engine.RoundState
import com.nickorton.sholto.engine.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the timer screens show. Each variant is one screen. */
sealed interface TimerUiState {
    /** Home: no session yet. */
    data class Idle(
        val config: RoundConfig,
    ) : TimerUiState

    /** A session is running or paused. */
    data class Running(
        val phase: Phase,
        val round: Int,
        val totalRounds: Int?,
        val phaseTimeLeftMs: Long,
        val roundProgress: Float,
        val warningLengthMs: Long,
        val isPaused: Boolean,
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
 * While a session runs it polls the engine every [tickMs]. The engine works
 * out the time from the [clock], so a late or missed poll only delays the
 * redraw and never changes the timing.
 */
class TimerViewModel(
    private val config: RoundConfig = RoundConfig(),
    private val clock: Clock = ElapsedRealtimeClock,
    private val tickMs: Long = 100L,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TimerUiState>(TimerUiState.Idle(config))
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var engine: RoundEngine? = null
    private var ticker: Job? = null

    /** Starts a new session from Home or Done. Ignored while one is running. */
    fun start() {
        if (_uiState.value is TimerUiState.Running) return
        engine = RoundEngine(config, clock).also { it.start() }
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
            refresh()
        }
    }

    /** Goes back to Home. Only from Done, so a running session can't be lost. */
    fun home() {
        if (_uiState.value !is TimerUiState.Done) return
        engine = null
        _uiState.value = TimerUiState.Idle(config)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker =
            viewModelScope.launch {
                while (isActive) {
                    delay(tickMs)
                    refresh()
                }
            }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun refresh() {
        val state = engine?.snapshot() ?: return
        _uiState.value = state.toUiState()
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
                    warningLengthMs = config.warningLengthMs,
                    isPaused = status == SessionStatus.Paused,
                )
            }
        }
}
