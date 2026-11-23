package com.thanhvan.cotuongluyenky.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameStatus
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.GameSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter(val label: String) {
    ALL("Tất cả"),
    IN_PROGRESS("Đang dở"),
    WIN("Thắng"),
    LOSS("Thua"),
    DRAW("Hòa"),
    HAS_BLUNDERS("Có sai lầm nghiêm trọng"),
    NOT_ANALYZED("Chưa phân tích")
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val games: List<GameSummary> = emptyList()
)

class HistoryViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        selectedFilter,
        gameRepository.observeAllGames()
    ) { filter, allGames ->
        val filtered = when (filter) {
            HistoryFilter.ALL -> allGames
            HistoryFilter.IN_PROGRESS -> allGames.filter { it.status == GameStatus.IN_PROGRESS }
            HistoryFilter.WIN -> allGames.filter { it.status == GameStatus.FINISHED && it.playerWon }
            HistoryFilter.LOSS -> allGames.filter { it.status == GameStatus.FINISHED && it.playerLost }
            HistoryFilter.DRAW -> allGames.filter { it.status == GameStatus.FINISHED && it.result == GameResult.DRAW }
            HistoryFilter.HAS_BLUNDERS -> allGames.filter { it.blunderCount > 0 }
            HistoryFilter.NOT_ANALYZED -> allGames.filter {
                it.status == GameStatus.FINISHED && it.analysisStatus == AnalysisStatus.NOT_ANALYZED
            }
        }
        HistoryUiState(filter = filter, games = filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun setFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            gameRepository.deleteGame(gameId)
        }
    }

    class Factory(
        private val gameRepository: GameRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(gameRepository) as T
        }
    }
}
