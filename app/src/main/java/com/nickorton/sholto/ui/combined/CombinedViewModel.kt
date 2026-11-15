package com.nickorton.sholto.ui.combined

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.sholto.data.CombinedSettings
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.service.CombinedController
import com.nickorton.sholto.ui.timer.TimerUiState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** What the combined-mode screens show. Each variant is one screen. */
sealed interface CombinedUiState {
    /** The saved settings or service connection haven't settled yet. */
    data object Loading : CombinedUiState

    /** Home: no session yet. Starting one uses [settings]. */
    data class Idle(
        val settings: CombinedSettings,
    ) : CombinedUiState

    /**
     * A combined session is running or paused.
     *
     * @property timer The round timer's part: phase, countdown, progress.
     * @property currentCall The latest call this phase, or null before the
     *   first one and outside Work and Warning.
     * @property callAgeMs Time since [currentCall], for the flash and fade.
     */
    data class Running(
        val timer: TimerUiState.Running,
        val currentCall: Combo?,
        val stance: Stance,
        val callCount: Int,
        val callAgeMs: Long,
    ) : CombinedUiState {
        val isPaused: Boolean get() = timer.isPaused
    }

    /**
     * The session is over.
     *
     * @property finished True if every round ran, false if it was ended early.
     * @property totalTimeMs Active time, not counting pauses.
     */
    data class Done(
        val roundsCompleted: Int,
        val callsMade: Int,
        val totalTimeMs: Long,
        val finished: Boolean,
    ) : CombinedUiState
}

/** Connects the combined-mode screens to the [CombinedController] owned by `WorkoutService`. */
class CombinedViewModel(
    controllers: Flow<CombinedController?>,
    private val onClose: () -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow<CombinedUiState>(CombinedUiState.Loading)
    val uiState: StateFlow<CombinedUiState> = _uiState.asStateFlow()

    private var controller: CombinedController? = null

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            controllers.collectLatest { c ->
                controller = c
                if (c == null) {
                    _uiState.value = CombinedUiState.Loading
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

    private inline fun runCommand(block: CombinedController.() -> Unit) {
        val c = controller ?: return
        c.block()
        _uiState.value = c.uiState.value
    }

    override fun onCleared() {
        onClose()
        super.onCleared()
    }
}
