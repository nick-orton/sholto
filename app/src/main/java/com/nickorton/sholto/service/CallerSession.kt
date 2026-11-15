package com.nickorton.sholto.service

import com.nickorton.sholto.audio.CallAnnouncer
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.data.TapBehavior
import com.nickorton.sholto.engine.CallTiming
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.PunchEngine
import com.nickorton.sholto.ui.caller.CallerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Controls a punch-caller session and exposes its state. Owned by
 * [WorkoutService] and observed by the UI.
 */
interface CallerController {
    val uiState: StateFlow<CallerUiState>

    fun start()

    fun pause()

    fun resume()

    fun end()

    fun home()
}

/**
 * Runs one punch-caller session at a time on [scope] and exposes it as
 * [uiState]. Pure Kotlin and Coroutines so it can be tested on the JVM with a
 * fake [clock] and seeded [randomFactory].
 *
 * Every call time is computed from `startTimeMs + pausedDurationMs` rather
 * than by adding up delays, so pauses shift future calls by the exact pause
 * duration and timing never drifts. The ticker wakes on the exact millisecond
 * each call is due.
 */
class CallerSession(
    private val scope: CoroutineScope,
    settings: Flow<CallerSettings> = flowOf(CallerSettings()),
    private val clock: Clock = ElapsedRealtimeClock,
    private val randomFactory: () -> Random = { Random(clock.now()) },
    private val announcer: CallAnnouncer = CallAnnouncer.None,
    private val cuePlayer: CuePlayer = CuePlayer.None,
    private val hapticPlayer: CuePlayer = CuePlayer.None,
    private val isInCall: () -> Boolean = { false },
    private val tickMs: Long = 50L,
) : CallerController {
    private val _uiState = MutableStateFlow<CallerUiState>(CallerUiState.Loading)
    override val uiState: StateFlow<CallerUiState> = _uiState.asStateFlow()

    private var latest: CallerSettings? = null
    private var session = CallerSettings()
    private var engine: PunchEngine? = null
    private var ticker: Job? = null

    private var startTimeMs = 0L
    private var pausedDurationMs = 0L
    private var pausedAtMs: Long? = null
    private var currentCall: Combo? = null
    private var callCount = 0
    private var lastCallElapsedMs = 0L
    private var nextCallAtElapsedMs = 0L
    private var lastElapsedMs = 0L
    private var wasInCall = false

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settings.collect { s ->
                latest = s
                val state = _uiState.value
                if (state is CallerUiState.Loading || state is CallerUiState.Idle) {
                    _uiState.value = CallerUiState.Idle(s)
                }
            }
        }
    }

    override fun start() {
        if (_uiState.value is CallerUiState.Running) return
        val s = latest ?: return
        session = s
        val newEngine = PunchEngine(s.punch, randomFactory())
        engine = newEngine
        startTimeMs = clock.now()
        pausedDurationMs = 0L
        pausedAtMs = null
        currentCall = null
        callCount = 0
        lastCallElapsedMs = 0L
        lastElapsedMs = 0L
        nextCallAtElapsedMs = newEngine.nextGap()
        wasInCall = isInCall()
        refresh()
        startTicker()
    }

    override fun pause() {
        if (engine == null || pausedAtMs != null) return
        refresh()
        if (engine == null) return
        pausedAtMs = clock.now()
        stopTicker()
        refresh()
    }

    override fun resume() {
        val pausedAt = pausedAtMs ?: return
        if (engine == null) return
        pausedDurationMs += (clock.now() - pausedAt).coerceAtLeast(0L)
        pausedAtMs = null
        wasInCall = isInCall()
        refresh()
        startTicker()
    }

    override fun end() {
        if (engine == null) return
        val effectiveNow = pausedAtMs ?: clock.now()
        val rawElapsed = (effectiveNow - startTimeMs - pausedDurationMs).coerceAtLeast(0L)
        val elapsed = session.sessionLengthMs?.let { rawElapsed.coerceAtMost(it) } ?: rawElapsed
        stopTicker()
        engine = null
        pausedAtMs = null
        _uiState.value = CallerUiState.Done(callsMade = callCount, totalTimeMs = elapsed, finished = false)
    }

    override fun home() {
        if (_uiState.value !is CallerUiState.Done) return
        engine = null
        _uiState.value = CallerUiState.Idle(latest ?: session)
    }

    fun onCallStateChanged(inCall: Boolean) {
        val startedCall = inCall && !wasInCall
        wasInCall = inCall
        if (startedCall) pause()
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

    private fun nextTickDelayMs(): Long {
        val endMs = session.sessionLengthMs ?: Long.MAX_VALUE
        val nextBoundaryMs = minOf(nextCallAtElapsedMs, endMs)
        val untilBoundary = (nextBoundaryMs - lastElapsedMs).coerceAtLeast(1L)
        return minOf(tickMs, untilBoundary)
    }

    private fun refresh() {
        val activeEngine = engine ?: return
        val effectiveNow = pausedAtMs ?: clock.now()
        val rawElapsed = (effectiveNow - startTimeMs - pausedDurationMs).coerceAtLeast(0L)
        val limit = session.sessionLengthMs
        val finished = pausedAtMs == null && limit != null && rawElapsed >= limit
        val elapsed = if (limit != null) rawElapsed.coerceAtMost(limit) else rawElapsed
        lastElapsedMs = elapsed

        var newestCall: Combo? = null
        if (pausedAtMs == null) {
            while (nextCallAtElapsedMs <= elapsed && (limit == null || nextCallAtElapsedMs < limit)) {
                val call = activeEngine.nextCall()
                currentCall = call
                newestCall = call
                callCount++
                lastCallElapsedMs = nextCallAtElapsedMs
                // A combo has to be said and thrown before the wait starts.
                val speech = CallTiming.speechMs(call.spokenText(session.calloutStyle, session.stance), session.speechRate)
                nextCallAtElapsedMs += CallTiming.busyMs(call, speech) + activeEngine.nextGap()
            }
        }

        if (newestCall != null) {
            announcer.announce(newestCall, session.calloutStyle, session.stance, session.speechRate)
        }

        if (finished) {
            stopTicker()
            engine = null
            _uiState.value = CallerUiState.Done(callsMade = callCount, totalTimeMs = elapsed, finished = true)
            if (session.finishSound) cuePlayer.play(Cue.SessionDone)
            if (session.finishVibration) hapticPlayer.play(Cue.SessionDone)
            return
        }

        val age = if (currentCall == null) 0L else (elapsed - lastCallElapsedMs).coerceAtLeast(0L)
        val timeLeft = limit?.let { (it - elapsed).coerceAtLeast(0L) }
        _uiState.value =
            CallerUiState.Running(
                currentCall = currentCall,
                stance = session.stance,
                callCount = callCount,
                callAgeMs = age,
                elapsedMs = elapsed,
                timeLeftMs = timeLeft,
                isPaused = pausedAtMs != null,
                keepScreenOn = session.keepScreenOn,
                tapEnds = session.tapBehavior == TapBehavior.Stop,
            )
    }
}
