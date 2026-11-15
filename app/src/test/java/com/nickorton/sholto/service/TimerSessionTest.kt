package com.nickorton.sholto.service

import android.media.AudioManager
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.Presets
import com.nickorton.sholto.data.TimerSettings
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import com.nickorton.sholto.ui.timer.TimerUiState
import com.nickorton.sholto.ui.timer.TimerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerSessionTest {
    private val scheduler = TestCoroutineScheduler()
    private val clock = Clock { scheduler.currentTime }
    private lateinit var serviceScope: CoroutineScope

    private val config =
        RoundConfig(
            roundLengthMs = 20_000L,
            warningLengthMs = 5_000L,
            restLengthMs = 10_000L,
            rounds = 3,
            getReadyLengthMs = 5_000L,
        )

    @Before
    fun setUp() {
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @After
    fun tearDown() {
        serviceScope.cancel()
        Dispatchers.resetMain()
    }

    private fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    @Test
    fun `incoming phone call pauses a running session and leaves it paused after the call ends`() {
        var inCall = false
        val session =
            TimerSession(
                scope = serviceScope,
                settings = flowOf(TimerSettings(round = config)),
                clock = clock,
                isInCall = { inCall },
            )
        scheduler.runCurrent()

        session.start()
        advance(10_000L) // 5 s into Round 1 Work (10 s left in Work).
        assertFalse((session.uiState.value as TimerUiState.Running).isPaused)

        // Incoming call detected on the next tick.
        inCall = true
        advance(100L)
        val paused = session.uiState.value as TimerUiState.Running
        assertTrue(paused.isPaused)
        assertEquals(9_900L, paused.phaseTimeLeftMs)

        // Call lasts 30 seconds and ends: session stays paused at the same mark.
        advance(30_000L)
        inCall = false
        session.onCallStateChanged(false)
        assertEquals(paused, session.uiState.value)

        // User resumes manually when ready.
        session.resume()
        assertFalse((session.uiState.value as TimerUiState.Running).isPaused)
        advance(9_900L)
        assertEquals(Phase.Warning, (session.uiState.value as TimerUiState.Running).phase)
    }

    @Test
    fun `resuming during an active call does not re-pause until a new call arrives`() {
        var inCall = false
        val session =
            TimerSession(
                scope = serviceScope,
                settings = flowOf(TimerSettings(round = config)),
                clock = clock,
                isInCall = { inCall },
            )
        scheduler.runCurrent()

        session.start()
        advance(6_000L)

        // Call arrives via immediate listener callback.
        inCall = true
        session.onCallStateChanged(true)
        assertTrue((session.uiState.value as TimerUiState.Running).isPaused)

        // User chooses to resume while still on speakerphone.
        session.resume()
        advance(2_000L)
        val running = session.uiState.value as TimerUiState.Running
        assertFalse(running.isPaused)
        assertEquals(12_000L, running.phaseTimeLeftMs)

        // Call ends, then a second call comes in later -> pauses again.
        inCall = false
        session.onCallStateChanged(false)
        advance(1_000L)
        inCall = true
        session.onCallStateChanged(true)
        assertTrue((session.uiState.value as TimerUiState.Running).isPaused)
    }

    @Test
    fun `session survives UI disconnect and reattaches to a new ViewModel`() {
        val session =
            TimerSession(
                scope = serviceScope,
                settings = flowOf(TimerSettings(round = config)),
                clock = clock,
            )
        scheduler.runCurrent()

        val controllerFlow1 = MutableStateFlow<WorkoutController?>(session)
        var unbound1 = false
        val vm1 = TimerViewModel(controllers = controllerFlow1, onClose = { unbound1 = true })
        scheduler.runCurrent()

        vm1.start()
        advance(8_000L) // Round 1 Work, 12 s left.
        assertEquals(12_000L, (vm1.uiState.value as TimerUiState.Running).phaseTimeLeftMs)

        // Simulate swiping the app away: ViewModel disconnects, service keeps running.
        controllerFlow1.value = null
        scheduler.runCurrent()
        assertEquals(TimerUiState.Loading, vm1.uiState.value)

        // 27 seconds pass with no UI attached (into Round 2 Work).
        advance(27_000L)

        // User taps the ongoing notification: new ViewModel binds to the same session.
        val vm2 = TimerViewModel(controller = session, onClose = { unbound1 = true })
        scheduler.runCurrent()
        val reattached = vm2.uiState.value as TimerUiState.Running
        assertEquals(Phase.Work, reattached.phase)
        assertEquals(2, reattached.round)
        assertEquals(15_000L, reattached.phaseTimeLeftMs)
        assertFalse(unbound1)
    }

    @Test
    fun `12 x 3_00 pro session fires every bell on time with zero drift`() {
        val pro = Presets.builtIn.first { it.id == "pro" }.applyTo(RoundConfig())
        val bells = mutableListOf<Pair<Long, Cue>>()
        val session =
            TimerSession(
                scope = serviceScope,
                settings = flowOf(TimerSettings(round = pro)),
                clock = clock,
                cuePlayer = CuePlayer { cue -> if (cue != Cue.Countdown) bells += scheduler.currentTime to cue },
            )
        scheduler.runCurrent()

        session.start()
        val totalMs = pro.totalLengthMs!!
        advance(totalMs)

        assertEquals(TimerUiState.Done(roundsCompleted = 12, totalTimeMs = totalMs, finished = true), session.uiState.value)
        // 12 rounds: each has RoundStart, Warning, and RoundEnd (except round 12 ends with SessionDone).
        assertEquals(36, bells.size)
        for (r in 0 until 12) {
            val roundStartMs = 10_000L + r * 240_000L
            assertEquals(roundStartMs to Cue.RoundStart, bells[r * 3])
            assertEquals((roundStartMs + 150_000L) to Cue.Warning, bells[r * 3 + 1])
            val expectedEndCue = if (r == 11) Cue.SessionDone else Cue.RoundEnd
            assertEquals((roundStartMs + 180_000L) to expectedEndCue, bells[r * 3 + 2])
        }
    }

    @Test
    fun `isCallAudioMode identifies ringing and active calls`() {
        assertFalse(isCallAudioMode(AudioManager.MODE_NORMAL))
        assertTrue(isCallAudioMode(AudioManager.MODE_RINGTONE))
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_CALL))
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_COMMUNICATION))
        assertTrue(isCallAudioMode(AudioManager.MODE_CALL_SCREENING))
    }
}
