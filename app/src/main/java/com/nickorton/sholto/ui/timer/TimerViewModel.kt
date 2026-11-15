package com.nickorton.sholto.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.service.TimerSession
import com.nickorton.sholto.service.WorkoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** What the timer screens show. Each variant is one screen. */
sealed interface TimerUiState {
    /** The saved settings or service connection haven't settled yet. */
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
 * Connects the timer screens to the running [WorkoutController] (owned by
 * `WorkoutService` in the app, or a direct [TimerSession] in unit tests).
 */
class TimerViewModel private constructor(
    controllers: Flow<WorkoutController?>?,
    sessionFactory: ((CoroutineScope) -> WorkoutController)?,
    private val onClose: () -> Unit,
) : ViewModel() {
    /** Connects to a service-backed flow of [WorkoutController]s. */
    constructor(
        controllers: Flow<WorkoutController?>,
        onClose: () -> Unit = {},
    ) : this(controllers = controllers, sessionFactory = null, onClose = onClose)

    /** Wraps a single [WorkoutController]. */
    constructor(
        controller: WorkoutController,
        onClose: () -> Unit = {},
    ) : this(controllers = flowOf(controller), sessionFactory = null, onClose = onClose)

    /** Runs an in-memory [TimerSession] on [viewModelScope] for JVM tests. */
    constructor(
        settings: Flow<TimerSettings> = flowOf(TimerSettings()),
        clock: Clock = ElapsedRealtimeClock,
        cuePlayer: CuePlayer = CuePlayer.None,
        hapticPlayer: CuePlayer = CuePlayer.None,
        tickMs: Long = 100L,
    ) : this(
        controllers = null,
        sessionFactory = { scope ->
            TimerSession(
                scope = scope,
                settings = settings,
                clock = clock,
                cuePlayer = cuePlayer,
                hapticPlayer = hapticPlayer,
                tickMs = tickMs,
            )
        },
        onClose = {},
    )

    private val _uiState = MutableStateFlow<TimerUiState>(TimerUiState.Loading)
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var controller: WorkoutController? = null

    init {
        val source = controllers ?: flowOf(sessionFactory!!.invoke(viewModelScope))
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.collectLatest { c ->
                controller = c
                if (c == null) {
                    _uiState.value = TimerUiState.Loading
                } else {
                    _uiState.value = c.uiState.value
                    c.uiState.collect { _uiState.value = it }
                }
            }
        }
    }

    fun start() = runCommand { start() }

    fun pause() = runCommand { pause() }

    fun resume() = runCommand { resume() }

    fun end() = runCommand { end() }

    fun home() = runCommand { home() }

    private inline fun runCommand(block: WorkoutController.() -> Unit) {
        val c = controller ?: return
        c.block()
        _uiState.value = c.uiState.value
    }

    override fun onCleared() {
        onClose()
        super.onCleared()
    }
}
