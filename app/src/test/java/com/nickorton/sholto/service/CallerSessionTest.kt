package com.nickorton.sholto.service

import com.nickorton.sholto.audio.CallAnnouncer
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.data.CallerSettings
import com.nickorton.sholto.engine.CalloutStyle
import com.nickorton.sholto.engine.GapDistribution
import com.nickorton.sholto.engine.Punch
import com.nickorton.sholto.engine.PunchConfig
import com.nickorton.sholto.engine.Stance
import com.nickorton.sholto.ui.caller.CallerUiState
import com.nickorton.sholto.ui.caller.CallerViewModel
import com.nickorton.sholto.ui.caller.callAlpha
import com.nickorton.sholto.ui.caller.flashAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class CallerSessionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calls punches on fixed gap boundaries and ends when fixed duration elapses`() =
        runTest(dispatcher) {
            val spoken = mutableListOf<Pair<Punch, Float>>()
            val announcer =
                CallAnnouncer { punch, _, _, speechRate ->
                    spoken += punch to speechRate
                }
            val playedCues = mutableListOf<Cue>()
            val buzzedCues = mutableListOf<Cue>()

            val settings =
                CallerSettings(
                    punch =
                        PunchConfig(
                            meanGapMs = 1_000L,
                            minGapMs = 1_000L,
                            maxGapMs = 1_000L,
                            distribution = GapDistribution.Fixed,
                            enabled = setOf(Punch.One, Punch.Two),
                            avoidRepeats = true,
                        ),
                    speechRate = 1.4f,
                    sessionLengthMs = 2_500L,
                )
            val session =
                CallerSession(
                    scope = backgroundScope,
                    settings = MutableStateFlow(settings),
                    announcer = announcer,
                    cuePlayer = CuePlayer { playedCues += it },
                    hapticPlayer = CuePlayer { buzzedCues += it },
                    clock = { testScheduler.currentTime },
                    randomFactory = { Random(42) },
                )
            val vm = CallerViewModel(MutableStateFlow(session))
            runCurrent()
            assertTrue(vm.uiState.value is CallerUiState.Idle)

            vm.start()
            val atStart = vm.uiState.value as CallerUiState.Running
            assertEquals(null, atStart.currentCall)
            assertEquals(0, atStart.callCount)
            assertEquals(2_500L, atStart.timeLeftMs)

            // First call arrives at t = 1_000 ms.
            advanceTimeBy(1_000L)
            runCurrent()
            val afterFirst = vm.uiState.value as CallerUiState.Running
            assertEquals(1, afterFirst.callCount)
            assertEquals(1, spoken.size)
            assertEquals(1.4f, spoken.single().second, 1e-4f)

            // Second call arrives at t = 2_000 ms and avoids repeating the first.
            advanceTimeBy(1_000L)
            runCurrent()
            val afterSecond = vm.uiState.value as CallerUiState.Running
            assertEquals(2, afterSecond.callCount)
            assertEquals(2, spoken.size)
            assertTrue(spoken[0].first != spoken[1].first)

            // Session ends at t = 2_500 ms before the third call at 3_000 ms.
            advanceTimeBy(500L)
            runCurrent()
            val done = vm.uiState.value as CallerUiState.Done
            assertEquals(2, done.callsMade)
            assertEquals(2_500L, done.totalTimeMs)
            assertTrue(done.finished)
            assertEquals(listOf(Cue.SessionDone), playedCues)
            assertEquals(listOf(Cue.SessionDone), buzzedCues)
        }

    @Test
    fun `pause, resume and phone call shift call boundaries without drift`() =
        runTest(dispatcher) {
            val spoken = mutableListOf<Punch>()
            val settings =
                CallerSettings(
                    punch =
                        PunchConfig(
                            meanGapMs = 1_000L,
                            minGapMs = 1_000L,
                            maxGapMs = 1_000L,
                            distribution = GapDistribution.Fixed,
                        ),
                    sessionLengthMs = null,
                )
            val session =
                CallerSession(
                    scope = backgroundScope,
                    settings = MutableStateFlow(settings),
                    announcer = CallAnnouncer { punch, _, _, _ -> spoken += punch },
                    clock = { testScheduler.currentTime },
                    randomFactory = { Random(7) },
                )

            session.start()
            advanceTimeBy(400L)
            runCurrent()

            // Pause for 5_000 ms; no call should fire while paused.
            session.pause()
            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(0, spoken.size)

            // Resume: 600 ms left until the first call.
            session.resume()
            advanceTimeBy(599L)
            runCurrent()
            assertEquals(0, spoken.size)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(1, spoken.size)

            // Phone call pauses automatically and stays paused until the user resumes.
            session.onCallStateChanged(inCall = true)
            assertTrue((session.uiState.value as CallerUiState.Running).isPaused)
            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(1, spoken.size)

            session.end()
            val done = session.uiState.value as CallerUiState.Done
            assertFalse(done.finished)
            assertEquals(1, done.callsMade)
            assertEquals(1_000L, done.totalTimeMs)
        }

    @Test
    fun `callAlpha and flashAlpha fade over the first second`() {
        assertEquals(1f, callAlpha(0L), 1e-4f)
        assertEquals(0.625f, callAlpha(500L), 1e-4f)
        assertEquals(0.25f, callAlpha(1_000L), 1e-4f)
        assertEquals(0.25f, callAlpha(5_000L), 1e-4f)

        assertEquals(0.35f, flashAlpha(0L), 1e-4f)
        assertEquals(0.175f, flashAlpha(75L), 1e-4f)
        assertEquals(0f, flashAlpha(150L), 1e-4f)
    }
}
