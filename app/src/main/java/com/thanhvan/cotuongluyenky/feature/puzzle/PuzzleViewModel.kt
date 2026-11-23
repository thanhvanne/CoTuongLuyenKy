package com.thanhvan.cotuongluyenky.feature.puzzle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.ClockOption
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleItem
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.training.PuzzleAttemptStatus
import com.thanhvan.cotuongluyenky.domain.training.TrainingService
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.feature.game.components.BoardArrow
import com.thanhvan.cotuongluyenky.feature.game.components.MoveFeedbackVisual
import com.thanhvan.cotuongluyenky.service.haptic.HapticFeedbackManager
import com.thanhvan.cotuongluyenky.service.sound.GameSoundEffect
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PuzzleSolveState(val label: String) {
    UNSOLVED("Chưa giải"),
    CHECKING("Đang kiểm tra..."),
    CORRECT("Đúng"),
    EQUIVALENT_GOOD("Nước khác nhưng cũng tốt"),
    INCORRECT("Chưa đúng"),
    VIEWED_SOLUTION("Đã xem lời giải")
}

data class PuzzleUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val puzzle: PuzzleItem? = null,
    val baseBoard: BoardState = RulesEngine.initialBoard(),
    val displayBoard: BoardState = RulesEngine.initialBoard(),
    val isFlipped: Boolean = false,
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val lastMove: Move? = null,
    val solveState: PuzzleSolveState = PuzzleSolveState.UNSOLVED,
    val feedbackMessage: String? = null,
    /** 0 = no hint, 1 = idea, 2 = highlight piece, 3 = move + arrow, 4 = full solution & variation */
    val hintLevel: Int = 0,
    val ideaHintText: String? = null,
    val isPreviewingSolution: Boolean = false,
    val solutionVariationIndex: Int = 0,
    val solutionStepIndex: Int = 0,
    val nextPuzzleId: Long? = null
) {
    val activeVariationMoves: List<Move>
        get() = puzzle?.solutionVariations?.getOrNull(solutionVariationIndex) ?: emptyList()

    val primaryBestMove: Move?
        get() = puzzle?.acceptedMoves?.firstOrNull()

    val hintPieceSquare: Square?
        get() = if (hintLevel >= 2 && !isPreviewingSolution) primaryBestMove?.from else null

    val displayArrows: List<BoardArrow>
        get() = when {
            isPreviewingSolution -> {
                val next = activeVariationMoves.getOrNull(solutionStepIndex)
                if (next != null) {
                    listOf(
                        BoardArrow(
                            from = next.from,
                            to = next.to,
                            isOpponentReply = (solutionStepIndex % 2 == 1),
                            stepNumber = solutionStepIndex + 1
                        )
                    )
                } else emptyList()
            }
            hintLevel >= 3 -> {
                val best = primaryBestMove
                if (best != null) listOf(BoardArrow(best.from, best.to, isOpponentReply = false))
                else emptyList()
            }
            else -> emptyList()
        }

    val moveFeedbackVisual: MoveFeedbackVisual?
        get() {
            val mv = lastMove ?: return null
            return when (solveState) {
                PuzzleSolveState.CORRECT, PuzzleSolveState.EQUIVALENT_GOOD ->
                    MoveFeedbackVisual(mv.to, MoveClassification.GOOD)
                PuzzleSolveState.INCORRECT ->
                    MoveFeedbackVisual(mv.to, MoveClassification.MISTAKE)
                else -> null
            }
        }

    val checkedSide: Side?
        get() = when {
            RulesEngine.isInCheck(displayBoard, Side.RED) -> Side.RED
            RulesEngine.isInCheck(displayBoard, Side.BLACK) -> Side.BLACK
            else -> null
        }
}

class PuzzleViewModel(
    private val initialPuzzleId: Long,
    private val puzzleRepository: PuzzleRepository,
    private val gameRepository: GameRepository,
    private val trainingService: TrainingService,
    private val engineManager: EngineManager,
    private val settingsStore: UserSettingsStore,
    private val soundEffectManager: SoundEffectManager,
    private val hapticFeedbackManager: HapticFeedbackManager
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    private val _uiState = MutableStateFlow(PuzzleUiState())
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    init {
        loadPuzzle(initialPuzzleId)
    }

    fun loadPuzzle(puzzleId: Long) {
        viewModelScope.launch {
            _uiState.value = PuzzleUiState(isLoading = true)
            val item = puzzleRepository.getPuzzleById(puzzleId)
            if (item == null) {
                _uiState.value = PuzzleUiState(isLoading = false, loadError = "Không tìm thấy bài tập này.")
                return@launch
            }
            val parsed = FenCodec.parse(item.initialFen)
            if (parsed !is FenParseResult.Success) {
                _uiState.value = PuzzleUiState(isLoading = false, loadError = "Thế cờ bài tập không hợp lệ.")
                return@launch
            }

            val all = puzzleRepository.getAllPuzzles()
            val now = System.currentTimeMillis()
            val nextId = all.firstOrNull { it.id != item.id && it.isDueAt(now) }?.id
                ?: all.firstOrNull { it.id != item.id }?.id

            _uiState.value = PuzzleUiState(
                isLoading = false,
                puzzle = item,
                baseBoard = parsed.board,
                displayBoard = parsed.board,
                isFlipped = item.sideToMove == Side.BLACK,
                solveState = PuzzleSolveState.UNSOLVED,
                nextPuzzleId = nextId
            )
        }
    }

    fun flipBoard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        val puzzle = state.puzzle ?: return
        if (state.isPreviewingSolution) return
        if (state.solveState == PuzzleSolveState.CHECKING) return
        if (state.solveState == PuzzleSolveState.CORRECT || state.solveState == PuzzleSolveState.EQUIVALENT_GOOD) {
            return
        }

        // If previous attempt was INCORRECT, reset back to baseBoard first
        val board = if (state.solveState == PuzzleSolveState.INCORRECT) {
            state.baseBoard
        } else {
            state.displayBoard
        }

        val currentSelection = state.selectedSquare
        if (currentSelection != null && square in state.legalTargets) {
            val attemptMove = Move(currentSelection, square)
            submitAttempt(puzzle, board, attemptMove)
            return
        }

        val piece = board.pieceAt(square)
        if (piece != null && piece.side == board.sideToMove) {
            val targets = RulesEngine.generateLegalMovesFrom(board, square).map { it.to }.toSet()
            _uiState.update {
                it.copy(
                    displayBoard = board,
                    selectedSquare = square,
                    legalTargets = targets,
                    lastMove = if (it.solveState == PuzzleSolveState.INCORRECT) null else it.lastMove,
                    solveState = if (it.solveState == PuzzleSolveState.INCORRECT) PuzzleSolveState.UNSOLVED else it.solveState
                )
            }
        } else {
            _uiState.update { it.copy(selectedSquare = null, legalTargets = emptySet()) }
        }
    }

    private fun submitAttempt(puzzle: PuzzleItem, boardBefore: BoardState, move: Move) {
        val boardAfter = RulesEngine.applyMoveUnchecked(boardBefore, move)
        _uiState.update {
            it.copy(
                displayBoard = boardAfter,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMove = move,
                solveState = PuzzleSolveState.CHECKING,
                feedbackMessage = "Đang kiểm tra nước đi..."
            )
        }

        viewModelScope.launch {
            val s = settings.value
            val result = trainingService.verifyAttempt(
                puzzle = puzzle,
                board = boardBefore,
                playedMove = move,
                notationStyle = s.notationStyle
            )
            val newSolveState = when (result.status) {
                PuzzleAttemptStatus.CORRECT -> PuzzleSolveState.CORRECT
                PuzzleAttemptStatus.EQUIVALENT_GOOD -> PuzzleSolveState.EQUIVALENT_GOOD
                PuzzleAttemptStatus.INCORRECT, PuzzleAttemptStatus.ILLEGAL -> PuzzleSolveState.INCORRECT
            }

            val solved = newSolveState == PuzzleSolveState.CORRECT || newSolveState == PuzzleSolveState.EQUIVALENT_GOOD
            trainingService.recordAttemptOutcome(
                puzzleId = puzzle.id,
                solvedCorrectly = solved,
                hintLevelUsed = _uiState.value.hintLevel
            )

            if (solved) {
                soundEffectManager.play(GameSoundEffect.PUZZLE_CORRECT, s.soundEnabled, s.soundVolume)
            } else {
                soundEffectManager.play(GameSoundEffect.PUZZLE_WRONG, s.soundEnabled, s.soundVolume)
                hapticFeedbackManager.blunderBuzz(s.vibrateOnBlunder)
            }

            val refreshed = puzzleRepository.getPuzzleById(puzzle.id) ?: puzzle
            _uiState.update {
                it.copy(
                    puzzle = refreshed,
                    solveState = newSolveState,
                    feedbackMessage = result.message
                )
            }
        }
    }

    fun retryPuzzle() {
        _uiState.update {
            it.copy(
                displayBoard = it.baseBoard,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMove = null,
                solveState = PuzzleSolveState.UNSOLVED,
                feedbackMessage = null,
                isPreviewingSolution = false,
                solutionStepIndex = 0
            )
        }
    }

    fun requestNextHint() {
        val state = _uiState.value
        val puzzle = state.puzzle ?: return
        val bestMove = puzzle.acceptedMoves.firstOrNull() ?: return
        val nextLevel = (state.hintLevel + 1).coerceAtMost(4)

        if (nextLevel == 4) {
            viewModelScope.launch {
                trainingService.recordAttemptOutcome(
                    puzzleId = puzzle.id,
                    solvedCorrectly = false,
                    hintLevelUsed = 4
                )
            }
            _uiState.update {
                it.copy(
                    hintLevel = 4,
                    solveState = if (it.solveState == PuzzleSolveState.CORRECT || it.solveState == PuzzleSolveState.EQUIVALENT_GOOD) {
                        it.solveState
                    } else {
                        PuzzleSolveState.VIEWED_SOLUTION
                    },
                    isPreviewingSolution = true,
                    solutionVariationIndex = 0,
                    solutionStepIndex = 0,
                    displayBoard = it.baseBoard,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    lastMove = null
                )
            }
            return
        }

        val idea = state.ideaHintText ?: trainingService.getTier1IdeaHint(state.baseBoard, bestMove)
        _uiState.update {
            it.copy(
                hintLevel = nextLevel,
                ideaHintText = idea,
                displayBoard = if (it.solveState == PuzzleSolveState.INCORRECT) it.baseBoard else it.displayBoard
            )
        }
    }

    fun openSolutionPreview() {
        val state = _uiState.value
        val puzzle = state.puzzle ?: return
        val solved = state.solveState == PuzzleSolveState.CORRECT || state.solveState == PuzzleSolveState.EQUIVALENT_GOOD
        if (!solved && state.hintLevel < 4) {
            viewModelScope.launch {
                trainingService.recordAttemptOutcome(
                    puzzleId = puzzle.id,
                    solvedCorrectly = false,
                    hintLevelUsed = 4
                )
            }
        }
        _uiState.update {
            it.copy(
                hintLevel = maxOf(it.hintLevel, if (solved) it.hintLevel else 4),
                solveState = if (solved) it.solveState else PuzzleSolveState.VIEWED_SOLUTION,
                isPreviewingSolution = true,
                solutionVariationIndex = 0,
                solutionStepIndex = 0,
                displayBoard = it.baseBoard,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMove = null
            )
        }
    }

    fun stepSolution(delta: Int) {
        val state = _uiState.value
        val moves = state.activeVariationMoves
        if (moves.isEmpty()) return
        val targetStep = (state.solutionStepIndex + delta).coerceIn(0, moves.size)
        var board = state.baseBoard
        var lastMv: Move? = null
        for (i in 0 until targetStep) {
            val mv = moves[i]
            if (!RulesEngine.isMoveLegal(board, mv)) break
            board = RulesEngine.applyMoveUnchecked(board, mv)
            lastMv = mv
        }
        _uiState.update {
            it.copy(
                displayBoard = board,
                lastMove = lastMv,
                solutionStepIndex = targetStep
            )
        }
    }

    fun switchSolutionVariation() {
        val state = _uiState.value
        val count = state.puzzle?.solutionVariations?.size ?: 0
        if (count <= 1) return
        val nextIdx = (state.solutionVariationIndex + 1) % count
        _uiState.update {
            it.copy(
                solutionVariationIndex = nextIdx,
                solutionStepIndex = 0,
                displayBoard = it.baseBoard,
                lastMove = null
            )
        }
    }

    fun closeSolutionPreview() {
        _uiState.update {
            it.copy(
                isPreviewingSolution = false,
                displayBoard = it.baseBoard,
                lastMove = null,
                solutionStepIndex = 0
            )
        }
    }

    /**
     * Starts a brand-new practice game against the engine from this puzzle's current board
     * without mutating any original game (Section 15.6).
     */
    fun playFromPosition(onGameCreated: (Long) -> Unit) {
        val state = _uiState.value
        val puzzle = state.puzzle ?: return
        viewModelScope.launch {
            val s = settings.value
            val fen = FenCodec.toFen(state.baseBoard)
            val newGameId = gameRepository.createNewGame(
                playerSide = puzzle.sideToMove,
                engineInfo = engineManager.activeEngineInfo,
                engineLevel = s.defaultEngineLevel,
                mode = GameMode.PRACTICE,
                clockOption = ClockOption.UNLIMITED,
                initialFen = fen
            )
            onGameCreated(newGameId)
        }
    }

    class Factory(
        private val puzzleId: Long,
        private val puzzleRepository: PuzzleRepository,
        private val gameRepository: GameRepository,
        private val trainingService: TrainingService,
        private val engineManager: EngineManager,
        private val settingsStore: UserSettingsStore,
        private val soundEffectManager: SoundEffectManager,
        private val hapticFeedbackManager: HapticFeedbackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PuzzleViewModel(
                initialPuzzleId = puzzleId,
                puzzleRepository = puzzleRepository,
                gameRepository = gameRepository,
                trainingService = trainingService,
                engineManager = engineManager,
                settingsStore = settingsStore,
                soundEffectManager = soundEffectManager,
                hapticFeedbackManager = hapticFeedbackManager
            ) as T
        }
    }
}
