package com.thanhvan.cotuongluyenky.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.core.ui.components.ChartPoint
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.domain.analysis.AnalysisService
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.analysis.PlyAnalysisModel
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.manager.EngineAnalysisOutcome
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.feature.game.components.BoardArrow
import com.thanhvan.cotuongluyenky.feature.game.components.MoveFeedbackVisual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MoveListFilter(val label: String) {
    ALL("Tất cả"),
    MY_ERRORS("Lỗi của tôi"),
    OPPONENT_ERRORS("Lỗi đối thủ"),
    TURNING_POINTS("Bước ngoặt"),
    WITH_COMMENTARY("Có nhận xét")
}

enum class PlaybackSpeed(val label: String, val delayMs: Long) {
    SLOW("0.75x", 1450L),
    NORMAL("1x", 1000L),
    FAST("1.5x", 650L),
    VERY_FAST("2x", 420L);

    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]
}

data class VariationPreviewState(
    val isActive: Boolean = false,
    val variationIndex: Int = 0,
    val stepIndex: Int = 0 // 0 = board before selected ply, 1..pv.size = stepped into variation
)

data class TryOutBranchState(
    val isActive: Boolean = false,
    val anchorPlyNumber: Int = 0,
    val baseBoard: BoardState = RulesEngine.initialBoard(),
    val branchMoves: List<MoveRecord> = emptyList(),
    val currentBoard: BoardState = RulesEngine.initialBoard(),
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val isEvaluatingBranch: Boolean = false,
    val branchEvalScoreRed: EngineScore? = null,
    val branchBestMove: Move? = null
)

data class AnalysisUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val gameId: Long = 0L,
    val initialFen: String = FenCodec.INITIAL_FEN,
    val playerSide: Side = Side.RED,
    val engineName: String = "",
    val engineLevel: Int = 3,
    val result: GameResult = GameResult.ONGOING,
    val resultReason: GameResultReason = GameResultReason.NONE,
    val boards: List<BoardState> = listOf(RulesEngine.initialBoard()), // index 0 = initial, k = after move k-1
    val moves: List<MoveRecord> = emptyList(),
    val analysesByPly: Map<Int, PlyAnalysisModel> = emptyMap(),
    val analysisStatus: AnalysisStatus = AnalysisStatus.NOT_ANALYZED,
    val analyzedCount: Int = 0,
    val analysisError: String? = null,
    val selectedPlyNumber: Int = 0, // 0..moves.size
    val isFlipped: Boolean = false,
    val isChartCollapsed: Boolean = false,
    val moveFilter: MoveListFilter = MoveListFilter.ALL,
    val isAutoPlaying: Boolean = false,
    val playbackSpeed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val variationPreview: VariationPreviewState = VariationPreviewState(),
    val tryOut: TryOutBranchState = TryOutBranchState()
) {
    val totalPlies: Int get() = moves.size

    /**
     * Index in [moves] for the currently viewed ply (null when viewing initial board plyNumber == 0).
     */
    val selectedMoveIndex: Int?
        get() = if (selectedPlyNumber in 1..moves.size) selectedPlyNumber - 1 else null

    val selectedMoveRecord: MoveRecord?
        get() = selectedMoveIndex?.let { moves.getOrNull(it) }

    val selectedPlyAnalysis: PlyAnalysisModel?
        get() = selectedMoveIndex?.let { analysesByPly[it] }

    val playerBlunderCount: Int
        get() = moves.count { mv ->
            mv.side == playerSide && analysesByPly[mv.plyIndex]?.classification == MoveClassification.BLUNDER
        }

    val playerMistakeCount: Int
        get() = moves.count { mv ->
            mv.side == playerSide && analysesByPly[mv.plyIndex]?.classification == MoveClassification.MISTAKE
        }

    val playerInaccuracyCount: Int
        get() = moves.count { mv ->
            mv.side == playerSide && analysesByPly[mv.plyIndex]?.classification == MoveClassification.INACCURACY
        }

    val turningPointPlies: List<Int>
        get() = analysesByPly.values
            .filter { it.isTurningPoint }
            .map { it.plyIndex }
            .sorted()

    /**
     * Exact Red-perspective score synced between [EvaluationBar] and [AdvantageChart] (Section 13.5).
     */
    val currentEvalScoreRed: EngineScore?
        get() = when {
            tryOut.isActive -> tryOut.branchEvalScoreRed
            selectedPlyNumber == 0 -> analysesByPly[0]?.scoreBeforeRed
            else -> selectedPlyAnalysis?.scoreAfterRed
        }

    /**
     * Chart data points (0..N), strictly derived from [analysesByPly].
     */
    val chartPoints: List<ChartPoint>
        get() {
            if (moves.isEmpty()) return emptyList()
            val pts = ArrayList<ChartPoint>(moves.size + 1)
            pts.add(
                ChartPoint(
                    plyNumber = 0,
                    scoreRed = analysesByPly[0]?.scoreBeforeRed
                )
            )
            for (ply in moves.indices) {
                val analysis = analysesByPly[ply]
                pts.add(
                    ChartPoint(
                        plyNumber = ply + 1,
                        scoreRed = analysis?.scoreAfterRed,
                        classification = analysis?.classification,
                        isTurningPoint = analysis?.isTurningPoint == true
                    )
                )
            }
            return pts
        }

    /**
     * Effective board rendered on screen: Try-Out branch -> Variation Preview -> Main Game ply.
     */
    val displayBoard: BoardState
        get() = when {
            tryOut.isActive -> tryOut.currentBoard
            variationPreview.isActive -> computeVariationBoard()
            else -> boards.getOrElse(selectedPlyNumber) { boards.first() }
        }

    val displayLastMove: Move?
        get() = when {
            tryOut.isActive -> tryOut.branchMoves.lastOrNull()?.move
            variationPreview.isActive -> {
                val pv = activeVariationMoves()
                val idx = variationPreview.stepIndex - 1
                if (idx in pv.indices) pv[idx] else null
            }
            else -> selectedMoveRecord?.move
        }

    val displayCheckedSide: Side?
        get() {
            val b = displayBoard
            return when {
                RulesEngine.isInCheck(b, Side.RED) -> Side.RED
                RulesEngine.isInCheck(b, Side.BLACK) -> Side.BLACK
                else -> null
            }
        }

    val displayFeedbackVisual: MoveFeedbackVisual?
        get() {
            if (tryOut.isActive || variationPreview.isActive) return null
            val rec = selectedMoveRecord ?: return null
            val analysis = selectedPlyAnalysis ?: return null
            return MoveFeedbackVisual(rec.move.to, analysis.classification)
        }

    val displayArrows: List<BoardArrow>
        get() = when {
            tryOut.isActive -> {
                val best = tryOut.branchBestMove
                if (best != null && RulesEngine.isMoveLegal(tryOut.currentBoard, best)) {
                    listOf(BoardArrow(best.from, best.to, isOpponentReply = false))
                } else {
                    emptyList()
                }
            }
            variationPreview.isActive -> {
                val pv = activeVariationMoves()
                val step = variationPreview.stepIndex
                val mv = pv.getOrNull(step) ?: return emptyList()
                val b = displayBoard
                if (RulesEngine.isMoveLegal(b, mv)) {
                    listOf(BoardArrow(mv.from, mv.to, isOpponentReply = (step % 2 == 1), stepNumber = step + 1))
                } else {
                    emptyList()
                }
            }
            else -> {
                // On main game view, if selected move had a better alternative, show it subtly only when user views the ply before OR when variation is opened.
                // Show emerald arrow for recommended move from the position AFTER the current ply when available (next ply's bestMove)
                val nextBest = analysesByPly[selectedPlyNumber]?.bestMove
                val currentBoard = boards.getOrElse(selectedPlyNumber) { return emptyList() }
                if (nextBest != null && RulesEngine.isMoveLegal(currentBoard, nextBest)) {
                    listOf(BoardArrow(nextBest.from, nextBest.to, isOpponentReply = false))
                } else {
                    emptyList()
                }
            }
        }

    fun activeVariationMoves(): List<Move> {
        val analysis = selectedPlyAnalysis ?: return emptyList()
        if (analysis.variations.isNotEmpty()) {
            return analysis.variations.getOrNull(variationPreview.variationIndex)?.moves.orEmpty()
        }
        return analysis.principalVariation
    }

    private fun computeVariationBoard(): BoardState {
        val moveIdx = selectedMoveIndex ?: return boards.first()
        var current = boards.getOrElse(moveIdx) { boards.first() }
        val pv = activeVariationMoves()
        val steps = variationPreview.stepIndex.coerceIn(0, pv.size)
        for (i in 0 until steps) {
            val mv = pv[i]
            if (!RulesEngine.isMoveLegal(current, mv)) break
            current = RulesEngine.applyMoveUnchecked(current, mv)
        }
        return current
    }
}

class AnalysisViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val analysisService: AnalysisService,
    private val engineManager: EngineManager,
    private val settingsStore: UserSettingsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState(gameId = gameId))
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    private var analysisJob: Job? = null
    private var autoPlayJob: Job? = null
    private var branchEvalJob: Job? = null

    init {
        loadGameAndAnalysis()
    }

    private fun loadGameAndAnalysis() {
        viewModelScope.launch {
            val persisted = gameRepository.loadGame(gameId)
            if (persisted == null) {
                _uiState.update {
                    it.copy(isLoading = false, loadError = "Không tìm thấy ván đấu để phân tích.")
                }
                return@launch
            }

            val reconstructed = RulesEngine.reconstructGame(
                initialFen = persisted.entity.initialFen,
                movesUci = persisted.moves.map { it.move.toUci() }
            ).getOrElse { err ->
                _uiState.update {
                    it.copy(isLoading = false, loadError = err.message ?: "Dữ liệu ván đấu không hợp lệ.")
                }
                return@launch
            }

            val boards = ArrayList<BoardState>(reconstructed.moves.size + 1)
            boards.add(reconstructed.initialBoard)
            var cur = reconstructed.initialBoard
            for (m in reconstructed.moves) {
                cur = RulesEngine.applyMoveUnchecked(cur, m.move)
                boards.add(cur)
            }

            val savedAnalyses = analysisService.loadGameAnalysisModels(gameId)
            val status = runCatching { AnalysisStatus.valueOf(persisted.entity.analysisStatus) }
                .getOrDefault(AnalysisStatus.NOT_ANALYZED)

            _uiState.value = AnalysisUiState(
                isLoading = false,
                gameId = gameId,
                initialFen = persisted.entity.initialFen,
                playerSide = persisted.playerSide,
                engineName = persisted.entity.engineName,
                engineLevel = persisted.entity.engineLevel,
                result = persisted.result,
                resultReason = persisted.resultReason,
                boards = boards,
                moves = reconstructed.moves,
                analysesByPly = savedAnalyses,
                analysisStatus = if (status == AnalysisStatus.IN_PROGRESS) AnalysisStatus.PARTIAL else status,
                analyzedCount = savedAnalyses.count { !it.value.isQuickLiveEval },
                selectedPlyNumber = reconstructed.moves.size,
                isFlipped = persisted.playerSide == Side.BLACK
            )

            // Observe live updates to persisted analyses so chart/badges refresh as each ply finishes!
            launch {
                gameRepository.observeMoveAnalyses(gameId).collect {
                    val refreshed = analysisService.loadGameAnalysisModels(gameId)
                    _uiState.update { state ->
                        state.copy(
                            analysesByPly = refreshed,
                            analyzedCount = refreshed.count { !it.value.isQuickLiveEval }
                        )
                    }
                }
            }
        }
    }

    fun startOrResumeAnalysis(forceReanalyze: Boolean = false) {
        if (analysisJob?.isActive == true) return
        stopAutoPlay()
        exitTryOutBranch()
        closeVariationPreview()

        _uiState.update {
            it.copy(
                analysisStatus = AnalysisStatus.IN_PROGRESS,
                analysisError = null
            )
        }

        analysisJob = viewModelScope.launch {
            val curSettings = settings.value
            val result = analysisService.analyzeFullGame(
                gameId = gameId,
                resourceMode = curSettings.engineResourceMode,
                notationStyle = curSettings.notationStyle,
                forceReanalyze = forceReanalyze,
                onProgress = { progress ->
                    _uiState.update {
                        it.copy(
                            analyzedCount = progress.completedPlies,
                            analysisStatus = progress.status
                        )
                    }
                }
            )
            result.onFailure { err ->
                if (err is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        analysisStatus = AnalysisStatus.ERROR,
                        analysisError = err.message ?: "Lỗi trong quá trình phân tích ván."
                    )
                }
            }
        }
    }

    fun pauseAnalysisIfRunning() {
        if (_uiState.value.analysisStatus == AnalysisStatus.IN_PROGRESS) {
            stopAnalysis()
        }
    }

    fun stopAnalysis() {
        analysisJob?.cancel()
        viewModelScope.launch {
            engineManager.cancelActiveSearch()
        }
    }

    fun selectPlyNumber(plyNumber: Int) {
        stopAutoPlay()
        val state = _uiState.value
        val clamped = plyNumber.coerceIn(0, state.totalPlies)
        _uiState.update {
            it.copy(
                selectedPlyNumber = clamped,
                variationPreview = VariationPreviewState(),
                tryOut = TryOutBranchState()
            )
        }
    }

    fun stepPly(delta: Int) {
        selectPlyNumber(_uiState.value.selectedPlyNumber + delta)
    }

    fun jumpToStart() = selectPlyNumber(0)

    fun jumpToEnd() = selectPlyNumber(_uiState.value.totalPlies)

    fun jumpToFirstTurningPoint() {
        val state = _uiState.value
        val turning = state.turningPointPlies
        if (turning.isEmpty()) return
        val currentIdx = state.selectedMoveIndex ?: -1
        val next = turning.firstOrNull { it > currentIdx } ?: turning.first()
        selectPlyNumber(next + 1)
    }

    fun toggleAutoPlay() {
        val state = _uiState.value
        if (state.isAutoPlaying) {
            stopAutoPlay()
            return
        }
        exitTryOutBranch()
        closeVariationPreview()
        if (state.selectedPlyNumber >= state.totalPlies) {
            _uiState.update { it.copy(selectedPlyNumber = 0) }
        }
        _uiState.update { it.copy(isAutoPlaying = true) }

        autoPlayJob = viewModelScope.launch {
            while (isActive) {
                val cur = _uiState.value
                if (!cur.isAutoPlaying) break
                delay(cur.playbackSpeed.delayMs)
                val latest = _uiState.value
                if (latest.selectedPlyNumber >= latest.totalPlies) {
                    _uiState.update { it.copy(isAutoPlaying = false) }
                    break
                }
                _uiState.update { it.copy(selectedPlyNumber = it.selectedPlyNumber + 1) }
            }
        }
    }

    fun stopAutoPlay() {
        autoPlayJob?.cancel()
        _uiState.update { it.copy(isAutoPlaying = false) }
    }

    fun cyclePlaybackSpeed() {
        _uiState.update { it.copy(playbackSpeed = it.playbackSpeed.next()) }
    }

    fun toggleChartCollapsed() {
        _uiState.update { it.copy(isChartCollapsed = !it.isChartCollapsed) }
    }

    fun flipBoard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun setMoveFilter(filter: MoveListFilter) {
        _uiState.update { it.copy(moveFilter = filter) }
    }

    // --- Variation preview controls (Section 10.2 & 13.1) ---

    fun openVariationPreview() {
        val state = _uiState.value
        val analysis = state.selectedPlyAnalysis ?: return
        if (analysis.principalVariation.isEmpty() && analysis.variations.isEmpty()) return
        stopAutoPlay()
        exitTryOutBranch()
        _uiState.update {
            it.copy(
                variationPreview = VariationPreviewState(
                    isActive = true,
                    variationIndex = 0,
                    stepIndex = 0
                )
            )
        }
    }

    fun stepVariation(delta: Int) {
        val state = _uiState.value
        if (!state.variationPreview.isActive) return
        val maxSteps = state.activeVariationMoves().size
        val next = (state.variationPreview.stepIndex + delta).coerceIn(0, maxSteps)
        _uiState.update { it.copy(variationPreview = it.variationPreview.copy(stepIndex = next)) }
    }

    fun switchVariation() {
        val state = _uiState.value
        val count = state.selectedPlyAnalysis?.variations?.size ?: 0
        if (count <= 1) return
        val nextIdx = (state.variationPreview.variationIndex + 1) % count
        _uiState.update {
            it.copy(
                variationPreview = state.variationPreview.copy(
                    variationIndex = nextIdx,
                    stepIndex = 0
                )
            )
        }
    }

    fun closeVariationPreview() {
        _uiState.update { it.copy(variationPreview = VariationPreviewState()) }
    }

    // --- Try-Out Analysis Branch (Section 11.3: never modifies the original game!) ---

    fun startTryOutBranch() {
        stopAutoPlay()
        closeVariationPreview()
        val state = _uiState.value
        val baseBoard = state.boards.getOrElse(state.selectedPlyNumber) { state.boards.first() }
        _uiState.update {
            it.copy(
                tryOut = TryOutBranchState(
                    isActive = true,
                    anchorPlyNumber = state.selectedPlyNumber,
                    baseBoard = baseBoard,
                    branchMoves = emptyList(),
                    currentBoard = baseBoard
                )
            )
        }
    }

    fun onBoardSquareTapped(square: Square) {
        val state = _uiState.value
        if (!state.tryOut.isActive) return

        val tryOut = state.tryOut
        val currentBoard = tryOut.currentBoard
        val selected = tryOut.selectedSquare

        if (selected != null && square in tryOut.legalTargets) {
            val mv = Move(selected, square)
            if (RulesEngine.isMoveLegal(currentBoard, mv)) {
                branchEvalJob?.cancel()
                val applied = RulesEngine.applyMove(currentBoard, mv)
                val record = MoveRecord(
                    plyIndex = tryOut.branchMoves.size,
                    side = applied.movedPiece.side,
                    move = mv,
                    pieceType = applied.movedPiece.type,
                    capturedPieceType = applied.capturedPiece?.type,
                    notationShort = applied.notationShort,
                    notationFull = applied.notationFull,
                    positionHashAfter = applied.newBoard.positionHash,
                    gaveCheck = applied.gaveCheck
                )
                _uiState.update {
                    it.copy(
                        tryOut = tryOut.copy(
                            branchMoves = tryOut.branchMoves + record,
                            currentBoard = applied.newBoard,
                            selectedSquare = null,
                            legalTargets = emptySet(),
                            branchEvalScoreRed = null,
                            branchBestMove = null,
                            isEvaluatingBranch = false
                        )
                    )
                }
            }
            return
        }

        val piece = currentBoard.pieceAt(square)
        if (piece != null && piece.side == currentBoard.sideToMove) {
            val targets = RulesEngine.generateLegalMovesFrom(currentBoard, square).map { it.to }.toSet()
            _uiState.update {
                it.copy(
                    tryOut = tryOut.copy(
                        selectedSquare = if (selected == square) null else square,
                        legalTargets = if (selected == square) emptySet() else targets
                    )
                )
            }
        } else {
            _uiState.update {
                it.copy(tryOut = tryOut.copy(selectedSquare = null, legalTargets = emptySet()))
            }
        }
    }

    fun undoTryOutMove() {
        val state = _uiState.value
        val tryOut = state.tryOut
        if (!tryOut.isActive || tryOut.branchMoves.isEmpty()) return
        branchEvalJob?.cancel()

        val remaining = tryOut.branchMoves.dropLast(1)
        var cur = tryOut.baseBoard
        for (m in remaining) {
            cur = RulesEngine.applyMoveUnchecked(cur, m.move)
        }
        _uiState.update {
            it.copy(
                tryOut = tryOut.copy(
                    branchMoves = remaining,
                    currentBoard = cur,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    branchEvalScoreRed = null,
                    branchBestMove = null,
                    isEvaluatingBranch = false
                )
            )
        }
    }

    fun evaluateTryOutPosition() {
        val state = _uiState.value
        val tryOut = state.tryOut
        if (!tryOut.isActive || tryOut.isEvaluatingBranch) return

        branchEvalJob?.cancel()
        val expectedHash = tryOut.currentBoard.positionHash
        _uiState.update { it.copy(tryOut = tryOut.copy(isEvaluatingBranch = true)) }

        branchEvalJob = viewModelScope.launch {
            val fen = FenCodec.toFen(tryOut.currentBoard)
            val outcome = engineManager.analyzePosition(
                position = PositionWithHistory(fen, tryOut.currentBoard, emptyList()),
                limit = SearchLimit(moveTimeMs = 450L, maxDepth = 4),
                variations = 1,
                resourceMode = settings.value.engineResourceMode,
                preemptActive = true
            )
            if (_uiState.value.tryOut.currentBoard.positionHash != expectedHash) return@launch

            when (outcome) {
                is EngineAnalysisOutcome.Success -> {
                    val top = outcome.analysis.variations.firstOrNull()
                    val scoreRed = top?.score?.toRedPerspective(tryOut.currentBoard.sideToMove)
                    _uiState.update {
                        it.copy(
                            tryOut = it.tryOut.copy(
                                isEvaluatingBranch = false,
                                branchEvalScoreRed = scoreRed,
                                branchBestMove = top?.moves?.firstOrNull()
                            )
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(tryOut = it.tryOut.copy(isEvaluatingBranch = false)) }
                }
            }
        }
    }

    fun exitTryOutBranch() {
        branchEvalJob?.cancel()
        _uiState.update { it.copy(tryOut = TryOutBranchState()) }
    }

    override fun onCleared() {
        analysisJob?.cancel()
        autoPlayJob?.cancel()
        branchEvalJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val gameId: Long,
        private val gameRepository: GameRepository,
        private val analysisService: AnalysisService,
        private val engineManager: EngineManager,
        private val settingsStore: UserSettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalysisViewModel(
                gameId = gameId,
                gameRepository = gameRepository,
                analysisService = analysisService,
                engineManager = engineManager,
                settingsStore = settingsStore
            ) as T
        }
    }
}
