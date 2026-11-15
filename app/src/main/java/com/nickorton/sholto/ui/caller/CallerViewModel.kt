package com.nickorton.sholto.ui.caller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.sholto.audio.CallAnnouncer
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.service.CallerController
import com.nickorton.sholto.service.CallerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.random.Random

/** What the punch-caller screens show. Each variant is one screen. */
sealed interface CallerUiState {
    /** The saved settings or service connection haven't settled yet. */
    data object Loading : CallerUiState

    /** Home: no caller session yet. */
    data class Idle(
        val settings: CallerSettings,
    ) : CallerUiState

    /**
     * A punch-caller session is running or paused.
     *
     * @property currentCall The latest punch called, or null before the first call.
     * @property callCount How many calls have been made so far.
     * @property callAgeMs Time since [currentCall] was called, for the 1 s fade.
     * @property elapsedMs Active time elapsed, not counting pauses.
     * @property timeLeftMs Time left when the session has a fixed length, or null.
     */
    data class Running(
        val currentCall: Combo?,
        val stance: Stance,
        val callCount: Int,
        val callAgeMs: Long,
        val elapsedMs: Long,
        val timeLeftMs: Long?,
        val isPaused: Boolean,
        val keepScreenOn: Boolean = true,
        val tapEnds: Boolean = false,
    ) : CallerUiState

    /**
     * The session is over.
     *
     * @property finished True if a fixed-length session ran to completion,
     *   false if it was ended early or had no fixed length.
     */
    data class Done(
        val callsMade: Int,
        val totalTimeMs: Long,
        val finished: Boolean,
    ) : CallerUiState
}

/**
 * Connects the punch-caller screens to the running [CallerController] (owned
 * by `WorkoutService` in the app, or a direct [CallerSession] in unit tests).
 */
class CallerViewModel private constructor(
    controllers: Flow<CallerController?>?,
    sessionFactory: ((CoroutineScope) -> CallerController)?,
    private val onClose: () -> Unit,
) : ViewModel() {
    constructor(
        controllers: Flow<CallerController?>,
        onClose: () -> Unit = {},
    ) : this(controllers = controllers, sessionFactory = null, onClose = onClose)

    constructor(
        controller: CallerController,
        onClose: () -> Unit = {},
    ) : this(controllers = flowOf(controller), sessionFactory = null, onClose = onClose)

    constructor(
        settings: Flow<CallerSettings> = flowOf(CallerSettings()),
        clock: Clock = ElapsedRealtimeClock,
        randomFactory: () -> Random = { Random(clock.now()) },
        announcer: CallAnnouncer = CallAnnouncer.None,
        cuePlayer: CuePlayer = CuePlayer.None,
        hapticPlayer: CuePlayer = CuePlayer.None,
        tickMs: Long = 50L,
    ) : this(
        controllers = null,
        sessionFactory = { scope ->
            CallerSession(
                scope = scope,
                settings = settings,
                clock = clock,
                randomFactory = randomFactory,
                announcer = announcer,
                cuePlayer = cuePlayer,
                hapticPlayer = hapticPlayer,
                tickMs = tickMs,
            )
        },
        onClose = {},
    )

    private val _uiState = MutableStateFlow<CallerUiState>(CallerUiState.Loading)
    val uiState: StateFlow<CallerUiState> = _uiState.asStateFlow()

    private var controller: CallerController? = null

    init {
        val source = controllers ?: flowOf(sessionFactory!!.invoke(viewModelScope))
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.collectLatest { c ->
                controller = c
                if (c == null) {
                    _uiState.value = CallerUiState.Loading
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

    private inline fun runCommand(block: CallerController.() -> Unit) {
        val c = controller ?: return
        c.block()
        _uiState.value = c.uiState.value
    }

    override fun onCleared() {
        onClose()
        super.onCleared()
    }
}
