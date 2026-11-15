package com.nickorton.sholto.service

import com.nickorton.sholto.audio.CallAnnouncer
import com.nickorton.sholto.audio.CuePlanner
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.CombinedSettings
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.CallScheduler
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.Combo
import com.nickorton.sholto.engine.ElapsedRealtimeClock
import com.nickorton.sholto.engine.PunchEngine
import com.nickorton.sholto.engine.RoundEngine
import com.nickorton.sholto.engine.RoundState
import com.nickorton.sholto.engine.ScheduledCall
import com.nickorton.sholto.engine.SessionStatus
import com.nickorton.sholto.ui.combined.CombinedUiState
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
import kotlin.random.Random

/**
 * Controls a combined session and exposes its state. Owned by
 * [WorkoutService] and observed by the UI.
 */
interface CombinedController {
    val uiState: StateFlow<CombinedUiState>

    fun start()

    fun pause()

    fun resume()

    fun end()

    fun home()
}

/**
 * Runs the punch caller inside the round timer (DESIGN.md §4), one session at
 * a time on [scope]. Pure Kotlin and Coroutines so it can be tested on the
 * JVM with a fake [clock] and seeded [randomFactory].
 *
 * There is one clock for both halves: the [RoundEngine]'s active session
 * time. [CallScheduler] places every call in that time, so:
 * - calls happen only in Work and Warning, and stop for Rest;
 * - pausing freezes the timer and the calls together, and resuming shifts
 *   both by exactly the pause;
 * - no call is spoken within [CallScheduler.BELL_GUARD_MS] of a phase
 *   change, so a call never talks over a bell or the warning clacks.
 *
 * The ticker wakes at every whole second of the countdown (for bells and
 * beeps) and at the exact time of the next call. A call found more than
 * [lateCallToleranceMs] late, for example after the CPU slept, is skipped
 * rather than spoken at the wrong moment, possibly over a bell.
 *
 * Each session uses the settings as they were when it started.
 *
 * @param cuePlayer Plays the timer sounds, for the cues switched on in settings.
 * @param hapticPlayer Vibrates on phase changes, when vibration is on.
 */
class CombinedSession(
    private val scope: CoroutineScope,
    settings: Flow<CombinedSettings> = flowOf(CombinedSettings()),
    private val clock: Clock = ElapsedRealtimeClock,
    private val randomFactory: () -> Random = { Random(clock.now()) },
    private val announcer: CallAnnouncer = CallAnnouncer.None,
    private val cuePlayer: CuePlayer = CuePlayer.None,
    private val hapticPlayer: CuePlayer = CuePlayer.None,
    private val isInCall: () -> Boolean = { false },
    private val tickMs: Long = 100L,
    private val lateCallToleranceMs: Long = 250L,
) : CombinedController {
    private val _uiState = MutableStateFlow<CombinedUiState>(CombinedUiState.Loading)
    override val uiState: StateFlow<CombinedUiState> = _uiState.asStateFlow()

    /** The latest saved settings, or null until they've been read. */
    private var latest: CombinedSettings? = null

    /** The settings of the current or last session. */
    private var session = CombinedSettings()
    private var sessionPlayer = CuePlayer.None
    private var engine: RoundEngine? = null
    private var planner = CuePlanner()
    private var scheduler: CallScheduler? = null
    private var ticker: Job? = null
    private var wasInCall = false

    /** The next call, due in active session time, or null for none. */
    private var nextCall: ScheduledCall? = null
    private var currentCall: Combo? = null
    private var lastCallAtMs = 0L
    private var callCount = 0
    private var phaseTimeLeftMs = 0L
    private var elapsedMs = 0L

    init {
        scope.launch {
            settings.collect { s ->
                latest = s
                val state = _uiState.value
                if (state is CombinedUiState.Loading || state is CombinedUiState.Idle) {
                    _uiState.value = CombinedUiState.Idle(s)
                }
            }
        }
    }

    /**
     * Starts a new session from Home or Done. Ignored while one is running,
     * or before the settings have been read.
     */
    override fun start() {
        if (_uiState.value is CombinedUiState.Running) return
        val s = latest ?: return
        session = s
        sessionPlayer = playerFor(s.timer)
        wasInCall = isInCall()
        val c = s.caller
        val newScheduler =
            CallScheduler(
                config = s.timer.round,
                punches = PunchEngine(c.punch, randomFactory()),
                finalPushFactor = s.finalPush.meanFactor,
                speechMs = { announcer.speechMs(it, c.callout) },
            )
        scheduler = newScheduler
        nextCall = newScheduler.firstCall()
        currentCall = null
        lastCallAtMs = 0L
        callCount = 0
        planner = CuePlanner()
        engine = RoundEngine(s.timer.round, clock).also { it.start() }
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

    /** Ends the session early and shows the summary. No bell, no last call. */
    override fun end() {
        if (engine?.stop() == true) {
            stopTicker()
            refresh(playSounds = false)
        }
    }

    /** Goes back to Home. Only from Done, so a running session can't be lost. */
    override fun home() {
        if (_uiState.value !is CombinedUiState.Done) return
        engine = null
        _uiState.value = CombinedUiState.Idle(latest ?: session)
    }

    /**
     * Pauses the session when a phone call begins. Ending the call leaves the
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

    /**
     * Up to [tickMs], but never past the next whole second of the countdown
     * or the next call.
     */
    private fun nextTickDelayMs(): Long {
        val left = phaseTimeLeftMs
        val untilSecond = if (left <= 0L) tickMs else (left - 1L) % 1000L + 1L
        val untilCall = nextCall?.let { (it.atMs - elapsedMs).coerceAtLeast(1L) } ?: Long.MAX_VALUE
        return minOf(tickMs, untilSecond, untilCall)
    }

    private fun refresh(playSounds: Boolean = true) {
        val engine = engine ?: return
        val changes = engine.pollEvents()
        val state = engine.snapshot()
        phaseTimeLeftMs = state.phaseTimeLeftMs
        elapsedMs = state.elapsedMs

        // A new phase starts with a clean screen: last round's call is gone.
        if (changes.isNotEmpty()) currentCall = null
        val call = if (state.status == SessionStatus.Running) takeDueCall(state.elapsedMs) else null

        _uiState.value = state.toUiState()
        if (playSounds) {
            planner.cuesFor(changes, state).forEach(sessionPlayer::play)
            if (call != null) {
                announcer.announce(call, session.caller.callout)
            }
        }
        if (state.status == SessionStatus.Finished || state.status == SessionStatus.Stopped) stopTicker()
    }

    /**
     * Makes every call due by [elapsed] and returns the one to speak, if any.
     * Calls more than [lateCallToleranceMs] late are skipped.
     */
    private fun takeDueCall(elapsed: Long): Combo? {
        val scheduler = scheduler ?: return null
        var toSpeak: Combo? = null
        while (true) {
            val due = nextCall ?: break
            if (due.atMs > elapsed) break
            if (elapsed - due.atMs <= lateCallToleranceMs) {
                currentCall = due.combo
                lastCallAtMs = due.atMs
                callCount++
                toSpeak = due.combo
            }
            nextCall = scheduler.nextCallAfter(due)
        }
        return toSpeak
    }

    private fun RoundState.toUiState(): CombinedUiState =
        when (status) {
            SessionStatus.Finished, SessionStatus.Stopped -> {
                CombinedUiState.Done(
                    roundsCompleted = roundsCompleted,
                    callsMade = callCount,
                    totalTimeMs = elapsedMs,
                    finished = status == SessionStatus.Finished,
                )
            }

            SessionStatus.Idle, SessionStatus.Running, SessionStatus.Paused -> {
                val call = currentCall
                CombinedUiState.Running(
                    timer = toRunningUiState(session.timer),
                    currentCall = call,
                    stance = session.caller.stance,
                    callCount = callCount,
                    callAgeMs = if (call == null) 0L else (elapsedMs - lastCallAtMs).coerceAtLeast(0L),
                )
            }
        }
}
