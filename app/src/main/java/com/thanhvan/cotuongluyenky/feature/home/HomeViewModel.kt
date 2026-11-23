package com.thanhvan.cotuongluyenky.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.GameSummary
import com.thanhvan.cotuongluyenky.data.repository.PlayerStatsSnapshot
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val settings: UserSettings = UserSettings(),
    val unfinishedGame: GameSummary? = null,
    val stats: PlayerStatsSnapshot = PlayerStatsSnapshot(),
    val duePuzzlesCount: Int = 0,
    val solvedPuzzlesCount: Int = 0,
    val nextDuePuzzleId: Long? = null,
    val engineInfo: EngineInfo = EngineInfo("Luyện Kỳ Core", "1.0.0", false)
)

class HomeViewModel(
    gameRepository: GameRepository,
    puzzleRepository: PuzzleRepository,
    settingsStore: UserSettingsStore,
    engineManager: EngineManager
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        settingsStore.settingsFlow,
        gameRepository.observeLatestUnfinishedGame(),
        gameRepository.observePlayerStats(),
        puzzleRepository.observeAllPuzzles()
    ) { settings, unfinished, stats, puzzles ->
        val now = System.currentTimeMillis()
        val due = puzzles.filter { it.isDueAt(now) }.sortedBy { it.nextReviewAt }
        HomeUiState(
            settings = settings,
            unfinishedGame = unfinished,
            stats = stats,
            duePuzzlesCount = due.size,
            solvedPuzzlesCount = puzzles.count { it.isSolved },
            nextDuePuzzleId = due.firstOrNull()?.id ?: puzzles.firstOrNull()?.id,
            engineInfo = engineManager.activeEngineInfo
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(engineInfo = engineManager.activeEngineInfo)
    )

    class Factory(
        private val gameRepository: GameRepository,
        private val puzzleRepository: PuzzleRepository,
        private val settingsStore: UserSettingsStore,
        private val engineManager: EngineManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(gameRepository, puzzleRepository, settingsStore, engineManager) as T
        }
    }
}
