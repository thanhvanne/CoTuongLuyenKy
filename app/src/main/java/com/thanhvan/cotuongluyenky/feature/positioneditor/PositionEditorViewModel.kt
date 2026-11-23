package com.thanhvan.cotuongluyenky.feature.positioneditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.ClockOption
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleTheme
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.rules.ZobristHash
import com.thanhvan.cotuongluyenky.domain.training.TrainingService
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.EngineVariation
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.manager.EngineAnalysisOutcome
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.feature.game.components.BoardArrow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tool selected in the visual piece-placement palette (Section 18.10).
 */
sealed interface EditorTool {
    data object Erase : EditorTool
    data class Place(val piece: Piece) : EditorTool
}

data class PositionAnalysisPreview(
    val scoreRed: EngineScore? = null,
    val bestMove: Move? = null,
    val variations: List<EngineVariation> = emptyList(),
    val depth: Int = 0
)

data class PositionEditorUiState(
    val board: BoardState = RulesEngine.initialBoard(),
    val fenInput: String = FenCodec.INITIAL_FEN,
    val isFlipped: Boolean = false,
    val activeTool: EditorTool = EditorTool.Place(Piece(Side.RED, PieceType.CHARIOT)),
    val validationError: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisPreview: PositionAnalysisPreview? = null,
    val feedbackBanner: String? = null
) {
    val isBoardValid: Boolean get() = validationError == null

    val arrows: List<BoardArrow>
        get() {
            val best = analysisPreview?.bestMove ?: return emptyList()
            return if (isBoardValid && RulesEngine.isMoveLegal(board, best)) {
                listOf(BoardArrow(best.from, best.to, isOpponentReply = false))
            } else {
                emptyList()
            }
        }
}

class PositionEditorViewModel(
    private val gameRepository: GameRepository,
    private val trainingService: TrainingService,
    private val engineManager: EngineManager,
    private val settingsStore: UserSettingsStore
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    private val _uiState = MutableStateFlow(PositionEditorUiState())
    val uiState: StateFlow<PositionEditorUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun onFenInputChanged(newText: String) {
        _uiState.update { it.copy(fenInput = newText, feedbackBanner = null) }
    }

    fun applyFenInput() {
        val raw = _uiState.value.fenInput.trim()
        analysisJob?.cancel()
        when (val parsed = FenCodec.parse(raw)) {
            is FenParseResult.Success -> {
                _uiState.update {
                    it.copy(
                        board = parsed.board,
                        fenInput = FenCodec.toFen(parsed.board),
                        validationError = null,
                        analysisPreview = null,
                        isAnalyzing = false,
                        feedbackBanner = "FEN hợp lệ! Đã cập nhật bàn cờ."
                    )
                }
            }
            is FenParseResult.Error -> {
                _uiState.update {
                    it.copy(
                        validationError = parsed.message,
                        analysisPreview = null,
                        isAnalyzing = false,
                        feedbackBanner = null
                    )
                }
            }
        }
    }

    fun selectTool(tool: EditorTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun flipBoard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun setSideToMove(side: Side) {
        val current = _uiState.value.board
        if (current.sideToMove == side) return
        val copy = current.copyArray()
        val updated = BoardState.create(
            squares = copy,
            sideToMove = side,
            halfMoveClock = 0,
            fullMoveNumber = 1,
            positionHash = ZobristHash.computeHash(copy, side)
        )
        applyEditedBoard(updated)
    }

    fun resetToInitialPosition() {
        applyEditedBoard(RulesEngine.initialBoard(), banner = "Đã đặt lại thế cờ ban đầu.")
    }

    fun clearBoardKeepGenerals() {
        val squares = arrayOfNulls<Piece>(Square.BOARD_SQUARES)
        squares[Square(3, 0).index] = Piece(Side.RED, PieceType.GENERAL)
        squares[Square(4, 9).index] = Piece(Side.BLACK, PieceType.GENERAL)
        val side = _uiState.value.board.sideToMove
        val board = BoardState.create(
            squares = squares,
            sideToMove = side,
            halfMoveClock = 0,
            fullMoveNumber = 1,
            positionHash = ZobristHash.computeHash(squares, side)
        )
        applyEditedBoard(board, banner = "Đã xóa bàn cờ (giữ lại 2 Tướng hợp lệ).")
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        val copy = state.board.copyArray()
        when (val tool = state.activeTool) {
            EditorTool.Erase -> {
                copy[square.index] = null
            }
            is EditorTool.Place -> {
                val existing = copy[square.index]
                if (existing == tool.piece) {
                    copy[square.index] = null
                } else {
                    if (tool.piece.type == PieceType.GENERAL) {
                        for (idx in copy.indices) {
                            if (copy[idx] == tool.piece) copy[idx] = null
                        }
                    }
                    copy[square.index] = tool.piece
                }
            }
        }
        val side = state.board.sideToMove
        val updated = BoardState.create(
            squares = copy,
            sideToMove = side,
            halfMoveClock = 0,
            fullMoveNumber = 1,
            positionHash = ZobristHash.computeHash(copy, side)
        )
        applyEditedBoard(updated)
    }

    private fun applyEditedBoard(board: BoardState, banner: String? = null) {
        analysisJob?.cancel()
        val encoded = FenCodec.toFen(board)
        val error = when (val checked = FenCodec.parse(encoded)) {
            is FenParseResult.Success -> null
            is FenParseResult.Error -> checked.message
        }
        _uiState.update {
            it.copy(
                board = board,
                fenInput = encoded,
                validationError = error,
                isAnalyzing = false,
                analysisPreview = null,
                feedbackBanner = banner
            )
        }
    }

    fun analyzeCurrentPosition() {
        val state = _uiState.value
        if (!state.isBoardValid) return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, feedbackBanner = null) }
            val fen = FenCodec.toFen(state.board)
            val outcome = engineManager.analyzePosition(
                position = PositionWithHistory(fen, state.board, emptyList()),
                limit = SearchLimit(moveTimeMs = 900L, maxDepth = 5),
                variations = 3,
                resourceMode = settings.value.engineResourceMode,
                preemptActive = true
            )
            when (outcome) {
                is EngineAnalysisOutcome.Success -> {
                    val top = outcome.analysis.variations.firstOrNull()
                    val scoreRed = top?.score?.toRedPerspective(state.board.sideToMove)
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            analysisPreview = PositionAnalysisPreview(
                                scoreRed = scoreRed,
                                bestMove = top?.moves?.firstOrNull(),
                                variations = outcome.analysis.variations,
                                depth = outcome.analysis.depth
                            )
                        )
                    }
                }
                is EngineAnalysisOutcome.Failure -> {
                    _uiState.update {
                        it.copy(isAnalyzing = false, feedbackBanner = outcome.message)
                    }
                }
                is EngineAnalysisOutcome.Cancelled -> {
                    _uiState.update { it.copy(isAnalyzing = false) }
                }
            }
        }
    }

    fun playAgainstEngine(onGameCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (!state.isBoardValid) return
        viewModelScope.launch {
            val s = settings.value
            val fen = FenCodec.toFen(state.board)
            val id = gameRepository.createNewGame(
                playerSide = state.board.sideToMove,
                engineInfo = engineManager.activeEngineInfo,
                engineLevel = s.defaultEngineLevel,
                mode = GameMode.PRACTICE,
                clockOption = ClockOption.UNLIMITED,
                initialFen = fen
            )
            onGameCreated(id)
        }
    }

    fun saveAsPuzzle() {
        val state = _uiState.value
        if (!state.isBoardValid) return
        viewModelScope.launch {
            val preview = state.analysisPreview
            val accepted = listOfNotNull(preview?.bestMove)
            val variations = preview?.variations?.map { it.moves } ?: emptyList()
            val id = trainingService.saveManualPuzzle(
                board = state.board,
                title = "Thế cờ đã lưu (${state.board.sideToMove.vietnameseName} đi)",
                theme = PuzzleTheme.MISTAKE_FIX,
                acceptedMoves = accepted,
                variations = variations,
                commentary = "Thế cờ tự nhập từ Trình đặt quân / FEN."
            )
            _uiState.update {
                it.copy(feedbackBanner = "Đã lưu thế cờ vào mục Bài tập đã lưu (#$id).")
            }
        }
    }

    fun notifyFenCopied() {
        _uiState.update { it.copy(feedbackBanner = "Đã sao chép chuỗi FEN vào bộ nhớ tạm.") }
    }

    class Factory(
        private val gameRepository: GameRepository,
        private val trainingService: TrainingService,
        private val engineManager: EngineManager,
        private val settingsStore: UserSettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PositionEditorViewModel(
                gameRepository = gameRepository,
                trainingService = trainingService,
                engineManager = engineManager,
                settingsStore = settingsStore
            ) as T
        }
    }
}
