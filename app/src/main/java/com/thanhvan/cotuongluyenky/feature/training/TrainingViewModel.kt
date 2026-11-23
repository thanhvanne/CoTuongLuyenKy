package com.thanhvan.cotuongluyenky.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.data.repository.PuzzleItem
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleSourceType
import com.thanhvan.cotuongluyenky.domain.training.StatisticsService
import com.thanhvan.cotuongluyenky.domain.training.TrainingService
import com.thanhvan.cotuongluyenky.domain.training.TrainingStatistics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TrainingCategory(val label: String) {
    DUE("Cần ôn"),
    MISTAKE_FIX("Sửa sai"),
    TACTICS("Chiến thuật"),
    OPENINGS("Khai cuộc"),
    ENDGAMES("Tàn cuộc"),
    SAVED("Đã lưu"),
    ALL("Tất cả")
}

data class TrainingUiState(
    val selectedCategory: TrainingCategory = TrainingCategory.DUE,
    val puzzles: List<PuzzleItem> = emptyList(),
    val duePuzzleId: Long? = null,
    val mistakePuzzleCount: Int = 0,
    val stats: TrainingStatistics = TrainingStatistics(),
    val isSyncingMistakes: Boolean = false,
    val statusMessage: String? = null
)

class TrainingViewModel(
    private val puzzleRepository: PuzzleRepository,
    private val trainingService: TrainingService,
    private val statisticsService: StatisticsService
) : ViewModel() {

    private val selectedCategory = MutableStateFlow(TrainingCategory.DUE)
    private val isSyncingMistakes = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TrainingUiState> = combine(
        selectedCategory,
        puzzleRepository.observeAllPuzzles(),
        statisticsService.observeStatistics(),
        isSyncingMistakes,
        statusMessage
    ) { category, allPuzzles, stats, syncing, msg ->
        val now = System.currentTimeMillis()
        val dueList = allPuzzles.filter { it.isDueAt(now) }.sortedBy { it.nextReviewAt }
        val filtered = when (category) {
            TrainingCategory.DUE -> dueList
            TrainingCategory.MISTAKE_FIX -> allPuzzles.filter { it.sourceType == PuzzleSourceType.GAME_MISTAKE }
            TrainingCategory.TACTICS -> allPuzzles.filter { it.sourceType == PuzzleSourceType.BUILTIN_TACTIC }
            TrainingCategory.OPENINGS -> allPuzzles.filter { it.sourceType == PuzzleSourceType.BUILTIN_OPENING }
            TrainingCategory.ENDGAMES -> allPuzzles.filter { it.sourceType == PuzzleSourceType.BUILTIN_ENDGAME }
            TrainingCategory.SAVED -> allPuzzles.filter {
                it.sourceType == PuzzleSourceType.MANUAL_SAVE || it.sourceType == PuzzleSourceType.IMPORTED
            }
            TrainingCategory.ALL -> allPuzzles
        }

        TrainingUiState(
            selectedCategory = category,
            puzzles = filtered,
            duePuzzleId = dueList.firstOrNull()?.id ?: allPuzzles.firstOrNull()?.id,
            mistakePuzzleCount = allPuzzles.count { it.sourceType == PuzzleSourceType.GAME_MISTAKE },
            stats = stats,
            isSyncingMistakes = syncing,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrainingUiState()
    )

    init {
        viewModelScope.launch {
            runCatching { trainingService.ensureCatalogSeeded() }
        }
    }

    fun selectCategory(category: TrainingCategory) {
        selectedCategory.value = category
    }

    fun syncMistakePuzzles() {
        if (isSyncingMistakes.value) return
        viewModelScope.launch {
            isSyncingMistakes.value = true
            statusMessage.value = null
            val created = runCatching { trainingService.syncMistakePuzzlesFromAllGames() }.getOrDefault(0)
            isSyncingMistakes.value = false
            statusMessage.value = if (created > 0) {
                "Đã tạo thêm $created bài tập sửa sai mới từ các ván đã phân tích."
            } else {
                "Chưa có sai lầm mới nào từ các ván đã phân tích. Hãy phân tích thêm ván đấu trong Lịch sử."
            }
        }
    }

    fun deletePuzzle(puzzleId: Long) {
        viewModelScope.launch {
            puzzleRepository.deletePuzzle(puzzleId)
        }
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }

    class Factory(
        private val puzzleRepository: PuzzleRepository,
        private val trainingService: TrainingService,
        private val statisticsService: StatisticsService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TrainingViewModel(puzzleRepository, trainingService, statisticsService) as T
        }
    }
}
