package com.roz0075g.timergame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val GAME_SECONDS = 30 * 60
private const val SLOT_COUNT = 6

enum class SlotStatus { NONE, PENDING, SUCCESS, FAILURE }
enum class GameMode { NORMAL, HARD }

data class GameState(
    val elapsedSeconds: Int = 0,
    val running: Boolean = false,
    val started: Boolean = false,
    val finished: Boolean = false,
    val counts: List<Int> = List(SLOT_COUNT) { 0 },
    val statuses: List<SlotStatus> = List(SLOT_COUNT) { SlotStatus.NONE },
    val currentSlot: Int = 0,
    val lastRoll: Int? = null,
    val successes: Int = 0,
    val failures: Int = 0,
    val phase: String = "開始前",
    val mode: GameMode = GameMode.NORMAL
)

class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    private var previousMinute = -1
    private var previousEvenSlot = -1

    fun selectMode(mode: GameMode) {
        val s = _state.value
        if (s.started || s.running) return
        _state.value = s.copy(mode = mode)
    }

    fun startOrResume() {
        if (_state.value.finished || _state.value.running) return
        if (!_state.value.started) {
            previousMinute = -1
            previousEvenSlot = -1
        }
        _state.value = _state.value.copy(started = true, running = true)
        tick()
        viewModelScope.launch {
            while (isActive && _state.value.running && !_state.value.finished) {
                delay(1_000)
                tick()
            }
        }
    }

    fun pause() { _state.value = _state.value.copy(running = false) }

    fun reset() {
        previousMinute = -1
        previousEvenSlot = -1
        _state.value = GameState()
    }

    fun markSuccess(slot: Int) {
        val s = _state.value
        if (!s.running || s.finished || s.statuses[slot] != SlotStatus.PENDING) return
        val statuses = s.statuses.toMutableList()
        statuses[slot] = SlotStatus.SUCCESS
        _state.value = s.copy(statuses = statuses, successes = s.successes + 1, currentSlot = slot)
    }

    fun markFailure(slot: Int) {
        val s = _state.value
        if (!s.running || s.finished || s.statuses[slot] != SlotStatus.PENDING) return
        val statuses = s.statuses.toMutableList()
        statuses[slot] = SlotStatus.FAILURE
        _state.value = s.copy(statuses = statuses, failures = s.failures + 1, currentSlot = slot)
    }

    private fun tick() {
        val s = _state.value
        if (!s.started || !s.running || s.finished) return
        val nextElapsed = (s.elapsedSeconds + 1).coerceAtMost(GAME_SECONDS)
        val minute = (nextElapsed - 1) / 60
        val hardMode = s.mode == GameMode.HARD
        val oddPhase = minute % 2 == 0
        val executionPhase = hardMode || !oddPhase
        var next = s.copy(
            elapsedSeconds = nextElapsed,
            phase = if (executionPhase) "実行フェーズ" else "抽選フェーズ"
        )

        if (minute != previousMinute) {
            if (executionPhase) {
                if (!hardMode && previousMinute >= 0) next = failPending(next)
                previousEvenSlot = -1

                if (hardMode) {
                    val roll = Random.nextInt(1, 7)
                    val slot = roll - 1
                    val counts = next.counts.toMutableList()
                    counts[slot] += 1
                    next = next.copy(
                        counts = counts,
                        statuses = List(SLOT_COUNT) { index ->
                            if (index == slot) SlotStatus.PENDING else SlotStatus.NONE
                        },
                        currentSlot = slot,
                        lastRoll = roll
                    )
                } else {
                    next = next.copy(
                        statuses = next.counts.map { if (it > 0) SlotStatus.PENDING else SlotStatus.NONE },
                        currentSlot = 0
                    )
                }
            } else {
                if (previousMinute >= 0) next = failPending(next)
                val roll = Random.nextInt(1, 7)
                val slot = roll - 1
                val counts = next.counts.toMutableList()
                counts[slot] += 1
                next = next.copy(
                    counts = counts,
                    statuses = List(SLOT_COUNT) { SlotStatus.NONE },
                    lastRoll = roll
                )
            }
            previousMinute = minute
        }

        if (executionPhase) {
            val slot = if (hardMode) {
                next.currentSlot
            } else {
                ((nextElapsed % 60) / 10).coerceIn(0, 5)
            }
            if (!hardMode && previousEvenSlot >= 0 && slot > previousEvenSlot) {
                next = failSlotsBefore(next, slot)
            }
            previousEvenSlot = slot
            next = next.copy(currentSlot = slot)
        }

        if (nextElapsed >= GAME_SECONDS) {
            next = failPending(next).copy(running = false, finished = true, phase = "ゲーム終了")
        }
        _state.value = next
    }

    private fun failSlotsBefore(state: GameState, slot: Int): GameState {
        var result = state
        for (i in 0 until slot) result = failSlotIfPending(result, i)
        return result
    }

    private fun failPending(state: GameState): GameState {
        var result = state
        for (i in 0 until SLOT_COUNT) result = failSlotIfPending(result, i)
        return result
    }

    private fun failSlotIfPending(state: GameState, slot: Int): GameState {
        if (state.statuses[slot] != SlotStatus.PENDING) return state
        val statuses = state.statuses.toMutableList()
        statuses[slot] = SlotStatus.FAILURE
        return state.copy(statuses = statuses, failures = state.failures + 1)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TimerGameApp() }
    }
}

@Composable
private fun TimerGameApp(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("30分 累積タイマーゲーム", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("通常: 抽選→実行 / ハード: 毎分すぐ実行")
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("モード", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.selectMode(GameMode.NORMAL) }, enabled = !state.started && state.mode != GameMode.NORMAL) { Text("通常") }
                                Button(onClick = { vm.selectMode(GameMode.HARD) }, enabled = !state.started && state.mode != GameMode.HARD) { Text("ハード") }
                            }
                            Text("選択中: ${if (state.mode == GameMode.HARD) "ハード" else "通常"}")
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatTime(state.elapsedSeconds), style = MaterialTheme.typography.displayMedium)
                            Text(state.phase, style = MaterialTheme.typography.titleMedium)
                            if (state.phase == "実行フェーズ") {
                                Text(
                                    currentPositionText(state),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            state.lastRoll?.let { Text("直近の出目: $it  →  ${it * 10 - 10}秒枠") }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = vm::startOrResume, enabled = !state.running && !state.finished) {
                                    Text(if (state.started) "再開" else "スタート")
                                }
                                OutlinedButton(onClick = vm::pause, enabled = state.running) { Text("一時停止") }
                                OutlinedButton(onClick = vm::reset) { Text("リセット") }
                            }
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("達成 ${state.successes}")
                        Text("失敗 ${state.failures}")
                    }
                }
                itemsIndexed(state.counts) { index, count ->
                    val quota = count * 10
                    val status = state.statuses[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "${index * 10}–${index * 10 + 9}秒  ${if (index == state.currentSlot && state.phase == "実行フェーズ") "◀ 現在" else ""}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("累積: ${count}回 / 今回のノルマ: ${if (quota == 0) "なし" else quota}")
                            Text("状態: ${statusLabel(status)}")
                            if (status == SlotStatus.PENDING && quota > 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { vm.markSuccess(index) }) { Text("達成") }
                                    OutlinedButton(onClick = { vm.markFailure(index) }) { Text("失敗") }
                                }
                            }
                        }
                    }
                }
                if (state.finished) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("ゲーム終了", style = MaterialTheme.typography.titleLarge)
                                Text("達成: ${state.successes} / 失敗: ${state.failures}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun currentPositionText(state: GameState): String {
    val start = state.currentSlot * 10
    val end = start + 9
    return "現在位置: $start–$end秒枠"
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun statusLabel(status: SlotStatus): String = when (status) {
    SlotStatus.NONE -> "なし"
    SlotStatus.PENDING -> "処理待ち"
    SlotStatus.SUCCESS -> "達成"
    SlotStatus.FAILURE -> "失敗"
}
