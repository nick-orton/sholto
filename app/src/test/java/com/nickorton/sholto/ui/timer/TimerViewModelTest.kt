package com.nickorton.sholto.ui.timer

import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.Phase
import com.nickorton.sholto.engine.RoundConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * The engine's clock is the test scheduler's virtual time, so advancing the
 * scheduler moves both the clock and the 100 ms ticker together.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val clock = Clock { scheduler.currentTime }

    // 3 rounds of 0:20 (0:15 work + 0:05 warning), 0:10 rest, 0:05 get ready.
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
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(c: RoundConfig = config) = TimerViewModel(config = c, clock = clock)

    private fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun TimerViewModel.running() = uiState.value as TimerUiState.Running

    @Test
    fun `starts on Home with the config`() {
        assertEquals(TimerUiState.Idle(config), viewModel().uiState.value)
    }

    @Test
    fun `start shows get ready with its full length`() {
        val vm = viewModel()
        vm.start()
        val s = vm.running()
        assertEquals(Phase.GetReady, s.phase)
        assertEquals(5_000L, s.phaseTimeLeftMs)
        assertEquals(1, s.round)
        assertEquals(3, s.totalRounds)
        assertFalse(s.isPaused)
    }

    @Test
    fun `ticker moves through the phases on time`() {
        val vm = viewModel()
        vm.start()

        advance(5_000L)
        assertEquals(Phase.Work, vm.running().phase)
        assertEquals(15_000L, vm.running().phaseTimeLeftMs)

        advance(15_000L)
        assertEquals(Phase.Warning, vm.running().phase)

        advance(5_000L)
        assertEquals(Phase.Rest, vm.running().phase)
        assertEquals(1, vm.running().round)

        advance(10_000L)
        assertEquals(Phase.Work, vm.running().phase)
        assertEquals(2, vm.running().round)
    }

    @Test
    fun `ticker updates about every 100 ms`() {
        val vm = viewModel()
        vm.start()
        advance(100L)
        assertEquals(4_900L, vm.running().phaseTimeLeftMs)
        advance(250L)
        assertEquals(4_700L, vm.running().phaseTimeLeftMs)
    }

    @Test
    fun `pause freezes the countdown and resume continues it`() {
        val vm = viewModel()
        vm.start()
        advance(7_000L)
        vm.pause()
        val paused = vm.running()
        assertTrue(paused.isPaused)
        assertEquals(13_000L, paused.phaseTimeLeftMs)

        advance(60_000L)
        assertEquals(paused, vm.running())

        vm.resume()
        assertFalse(vm.running().isPaused)
        advance(3_000L)
        assertEquals(10_000L, vm.running().phaseTimeLeftMs)
    }

    @Test
    fun `a full session ends on Done with every round`() {
        val vm = viewModel()
        vm.start()
        // 5 + 3 × 20 + 2 × 10 = 85 s.
        advance(85_000L)
        assertEquals(TimerUiState.Done(roundsCompleted = 3, totalTimeMs = 85_000L, finished = true), vm.uiState.value)
    }

    @Test
    fun `pauses don't count toward total time`() {
        val vm = viewModel()
        vm.start()
        advance(30_000L)
        vm.pause()
        advance(45_000L)
        vm.resume()
        advance(55_000L)
        assertEquals(TimerUiState.Done(3, 85_000L, finished = true), vm.uiState.value)
    }

    @Test
    fun `ending early shows the summary so far`() {
        val vm = viewModel()
        vm.start()
        advance(40_000L) // Round 2, Work.
        vm.end()
        assertEquals(TimerUiState.Done(roundsCompleted = 1, totalTimeMs = 40_000L, finished = false), vm.uiState.value)

        // The ticker has stopped: nothing changes later.
        advance(60_000L)
        assertEquals(TimerUiState.Done(1, 40_000L, finished = false), vm.uiState.value)
    }

    @Test
    fun `ending while paused works`() {
        val vm = viewModel()
        vm.start()
        advance(2_000L)
        vm.pause()
        vm.end()
        assertEquals(TimerUiState.Done(0, 2_000L, finished = false), vm.uiState.value)
    }

    @Test
    fun `again starts a fresh session`() {
        val vm = viewModel()
        vm.start()
        advance(40_000L)
        vm.end()
        vm.start()
        val s = vm.running()
        assertEquals(Phase.GetReady, s.phase)
        assertEquals(5_000L, s.phaseTimeLeftMs)
    }

    @Test
    fun `home returns to Idle from Done`() {
        val vm = viewModel()
        vm.start()
        vm.end()
        vm.home()
        assertEquals(TimerUiState.Idle(config), vm.uiState.value)
    }

    @Test
    fun `home and start are ignored while a session runs`() {
        val vm = viewModel()
        vm.start()
        advance(7_000L)
        vm.home()
        vm.start()
        assertEquals(Phase.Work, vm.running().phase)
        assertEquals(13_000L, vm.running().phaseTimeLeftMs)
    }

    @Test
    fun `unlimited rounds keep going until ended`() {
        val vm = viewModel(config.copy(rounds = null))
        vm.start()
        advance(5_000L + 12 * 30_000L)
        val s = vm.running()
        assertEquals(Phase.Work, s.phase)
        assertEquals(13, s.round)
        assertEquals(null, s.totalRounds)
    }
}
