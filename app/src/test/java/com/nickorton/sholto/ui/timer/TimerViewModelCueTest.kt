package com.nickorton.sholto.ui.timer

import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.audio.Cue.Countdown
import com.nickorton.sholto.audio.Cue.RoundEnd
import com.nickorton.sholto.audio.Cue.RoundStart
import com.nickorton.sholto.audio.Cue.SessionDone
import com.nickorton.sholto.audio.Cue.Warning
import com.nickorton.sholto.audio.CuePlayer
import com.nickorton.sholto.engine.Clock
import com.nickorton.sholto.engine.RoundConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Cues must land exactly on their boundaries, not up to a tick late. The
 * clock is the scheduler's virtual time, so each recorded time is when the
 * ticker actually woke up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelCueTest {
    private val scheduler = TestCoroutineScheduler()
    private val clock = Clock { scheduler.currentTime }
    private val played = mutableListOf<Pair<Long, Cue>>()
    private val recorder = CuePlayer { played += scheduler.currentTime to it }

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

    private fun viewModel(c: RoundConfig = config) = TimerViewModel(config = c, clock = clock, cuePlayer = recorder)

    private fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    @Test
    fun `a full session plays every cue at its exact time`() {
        val vm = viewModel()
        vm.start()
        advance(100_000L)
        assertEquals(
            listOf(
                2_000L to Countdown,
                3_000L to Countdown,
                4_000L to Countdown,
                5_000L to RoundStart,
                20_000L to Warning,
                25_000L to RoundEnd,
                32_000L to Countdown,
                33_000L to Countdown,
                34_000L to Countdown,
                35_000L to RoundStart,
                50_000L to Warning,
                55_000L to RoundEnd,
                62_000L to Countdown,
                63_000L to Countdown,
                64_000L to Countdown,
                65_000L to RoundStart,
                80_000L to Warning,
                85_000L to SessionDone,
            ),
            played,
        )
    }

    @Test
    fun `pausing between beeps shifts the rest and repeats none`() {
        val vm = viewModel()
        vm.start()
        advance(33_500L) // Rest, 1.5 s left, beeps at 32 s and 33 s done.
        vm.pause()
        advance(10_000L)
        vm.resume()
        advance(2_000L)
        assertEquals(
            listOf(32_000L to Countdown, 33_000L to Countdown, 44_000L to Countdown, 45_000L to RoundStart),
            played.filter { it.first > 30_000L },
        )
    }

    @Test
    fun `pausing before the countdown shifts its beeps by the pause`() {
        val vm = viewModel()
        vm.start()
        advance(31_500L) // Rest, 3.5 s left.
        vm.pause()
        advance(2_000L)
        vm.resume()
        advance(3_500L)
        assertEquals(
            listOf(34_000L to Countdown, 35_000L to Countdown, 36_000L to Countdown, 37_000L to RoundStart),
            played.filter { it.first > 30_000L },
        )
    }

    @Test
    fun `ending early plays nothing`() {
        val vm = viewModel()
        vm.start()
        advance(10_000L)
        played.clear()
        vm.end()
        advance(60_000L)
        assertEquals(emptyList<Pair<Long, Cue>>(), played)
    }

    @Test
    fun `no get ready means the first round starts with a bell at once`() {
        val vm = viewModel(config.copy(getReadyLengthMs = 0L))
        vm.start()
        assertEquals(listOf(0L to RoundStart), played)
    }

    @Test
    fun `again plays the countdown again`() {
        val vm = viewModel()
        vm.start()
        advance(10_000L)
        vm.end()
        played.clear()
        vm.start()
        advance(5_000L)
        assertEquals(
            listOf(12_000L to Countdown, 13_000L to Countdown, 14_000L to Countdown, 15_000L to RoundStart),
            played,
        )
    }
}
