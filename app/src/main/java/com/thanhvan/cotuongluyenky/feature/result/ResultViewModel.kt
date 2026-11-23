package com.thanhvan.cotuongluyenky.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PersistedGame
import com.thanhvan.cotuongluyenky.domain.analysis.AnalysisService
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResultAnalysisSummary(
    val status: AnalysisStatus,
    val blunders: Int = 0,
    val mistakes: Int = 0,
    val inaccuracies: Int = 0,
    val turningPointCount: Int = 0
)

data class ResultUiState(
    val isLoading: Boolean = true,
    val game: PersistedGame? = null,
    val analysisSummary: ResultAnalysisSummary? = null
)

class ResultViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val analysisService: AnalysisService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = gameRepository.loadGame(gameId)
            val summary = if (loaded != null) {
                val status = runCatching { AnalysisStatus.valueOf(loaded.entity.analysisStatus) }
                    .getOrDefault(AnalysisStatus.NOT_ANALYZED)
                val analyses = analysisService.loadGameAnalysisModels(gameId)
                val playerMoves = loaded.moves.filter { it.side == loaded.playerSide }
                ResultAnalysisSummary(
                    status = status,
                    blunders = playerMoves.count { analyses[it.plyIndex]?.classification == MoveClassification.BLUNDER },
                    mistakes = playerMoves.count { analyses[it.plyIndex]?.classification == MoveClassification.MISTAKE },
                    inaccuracies = playerMoves.count { analyses[it.plyIndex]?.classification == MoveClassification.INACCURACY },
                    turningPointCount = analyses.values.count { it.isTurningPoint }
                )
            } else null
            _uiState.value = ResultUiState(isLoading = false, game = loaded, analysisSummary = summary)
        }
    }

    class Factory(
        private val gameId: Long,
        private val gameRepository: GameRepository,
        private val analysisService: AnalysisService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ResultViewModel(gameId, gameRepository, analysisService) as T
        }
    }
}
