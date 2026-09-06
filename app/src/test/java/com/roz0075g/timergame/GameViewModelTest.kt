package com.roz0075g.timergame

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isNormalAndEmpty() {
        val state = GameViewModel().state.value

        assertEquals(0, state.elapsedSeconds)
        assertEquals(GameMode.NORMAL, state.mode)
        assertEquals("開始前", state.phase)
        assertTrue(state.counts.all { it == 0 })
        assertTrue(state.statuses.all { it == SlotStatus.NONE })
        assertEquals(0, state.successes)
        assertEquals(0, state.failures)
    }

    @Test
    fun modeCanBeChangedBeforeStart() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        assertEquals(GameMode.HARD, vm.state.value.mode)
    }

    @Test
    fun modeCannotBeChangedAfterStart() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        vm.selectMode(GameMode.NORMAL)
        vm.pause()
        assertEquals(GameMode.HARD, vm.state.value.mode)
    }

    @Test
    fun reset_returnsToInitialNormalState() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        vm.pause()
        vm.reset()

        val state = vm.state.value
        assertEquals(GameMode.NORMAL, state.mode)
        assertEquals(0, state.elapsedSeconds)
        assertFalse(state.started)
        assertFalse(state.running)
        assertFalse(state.finished)
        assertTrue(state.counts.all { it == 0 })
        assertTrue(state.statuses.all { it == SlotStatus.NONE })
        assertEquals(0, state.successes)
        assertEquals(0, state.failures)
    }

    @Test
    fun hardMode_startsImmediatelyInExecutionPhase() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        vm.pause()

        val state = vm.state.value
        assertEquals(GameMode.HARD, state.mode)
        assertEquals("実行フェーズ", state.phase)
        assertNotNull(state.lastRoll)
        assertTrue(state.lastRoll in 1..6)
    }

    @Test
    fun hardMode_hasExactlyOnePendingCurrentSlot() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        vm.pause()

        val state = vm.state.value
        val pendingSlots = state.statuses.withIndex()
            .filter { it.value == SlotStatus.PENDING }
            .map { it.index }

        assertEquals(1, pendingSlots.size)
        assertEquals(pendingSlots.single(), state.currentSlot)
        assertEquals(state.lastRoll!! - 1, state.currentSlot)
        assertTrue(state.counts[state.currentSlot] >= 1)
    }

    @Test
    fun hardMode_success_doesNotMoveCurrentPosition() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        val before = vm.state.value
        val current = before.currentSlot
        assertEquals(SlotStatus.PENDING, before.statuses[current])
        vm.markSuccess(current)
        vm.pause()

        val after = vm.state.value
        assertEquals(current, after.currentSlot)
        assertEquals(SlotStatus.SUCCESS, after.statuses[current])
        assertEquals(1, after.successes)
    }

    @Test
    fun hardMode_failure_doesNotMoveCurrentPosition() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        val before = vm.state.value
        val current = before.currentSlot
        vm.markFailure(current)
        vm.pause()

        val after = vm.state.value
        assertEquals(current, after.currentSlot)
        assertEquals(SlotStatus.FAILURE, after.statuses[current])
        assertEquals(1, after.failures)
    }

    @Test
    fun successCanOnlyBeMarkedForPendingSlot() {
        val vm = GameViewModel()
        vm.selectMode(GameMode.HARD)
        vm.startOrResume()
        val current = vm.state.value.currentSlot
        vm.markSuccess(current)
        vm.markSuccess(current)
        vm.pause()

        assertEquals(1, vm.state.value.successes)
        assertNotEquals(SlotStatus.PENDING, vm.state.value.statuses[current])
    }

    @Test
    fun currentPositionText_reflectsCurrentSlot() {
        val state = GameState(
            phase = "実行フェーズ",
            currentSlot = 3,
            running = true,
            started = true
        )
        assertEquals("現在位置: 30–39秒枠", currentPositionText(state))
    }
}
