package com.thanhvan.cotuongluyenky.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.data.datastore.UserSettings
import com.thanhvan.cotuongluyenky.data.datastore.UserSettingsStore
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.domain.analysis.AnalysisService
import com.thanhvan.cotuongluyenky.domain.analysis.HintBundle
import com.thanhvan.cotuongluyenky.domain.analysis.LiveMoveEvaluation
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.commentary.DangerLevel
import com.thanhvan.cotuongluyenky.domain.commentary.PieceDangerInfo
import com.thanhvan.cotuongluyenky.domain.commentary.TacticalFactExtractor
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineDifficulty
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.engine.manager.EngineMoveOutcome
import com.thanhvan.cotuongluyenky.feature.game.components.BoardArrow
import com.thanhvan.cotuongluyenky.feature.game.components.MoveAnimationEvent
import com.thanhvan.cotuongluyenky.feature.game.components.MoveFeedbackVisual
import com.thanhvan.cotuongluyenky.service.haptic.HapticFeedbackManager
import com.thanhvan.cotuongluyenky.service.sound.GameSoundEffect
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager
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
import java.util.concurrent.atomic.AtomicLong

/**
 * 4-tier progressive hint state (Section 10.1–10.3).
 */
data class HintUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val tier: Int = 0, // 0 = closed, 1 = idea, 2 = piece, 3 = move+arrow, 4 = step-by-step variation
    val bundle: HintBundle? = null,
    val selectedVariationIndex: Int = 0,
    val previewStep: Int = 0 // 0 = base position, 1..pv.size = stepped into variation
) {
    val isOpen: Boolean get() = isLoading || error != null || (tier > 0 && bundle != null)
}

data class GameUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val gameId: Long = 0L,
    val initialFen: String = FenCodec.INITIAL_FEN,
    val initialPositionHash: Long = RulesEngine.initialBoard().positionHash,
    val board: BoardState = RulesEngine.initialBoard(),
    val moves: List<MoveRecord> = emptyList(),
    val playerSide: Side = Side.RED,
    val engineLevel: Int = 3,
    val engineName: String = "Luyện Kỳ Core",
    val mode: GameMode = GameMode.PRACTICE,
    val result: GameResult = GameResult.ONGOING,
    val resultReason: GameResultReason = GameResultReason.NONE,
    val undoCount: Int = 0,
    val isFlipped: Boolean = false,
    val selectedSquare: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val checkedSide: Side? = null,
    val isEngineThinking: Boolean = false,
    val engineError: String? = null,
    val isTimed: Boolean = false,
    val redRemainingMillis: Long = 0L,
    val blackRemainingMillis: Long = 0L,
    val animationEvent: MoveAnimationEvent? = null,
    val statusBanner: String? = null,
    // Phase 2 live coaching features (disabled in Challenge mode)
    val latestEvalScoreRed: EngineScore? = null,
    val liveEvaluation: LiveMoveEvaluation? = null,
    val moveFeedbackVisual: MoveFeedbackVisual? = null,
    val hintState: HintUiState = HintUiState(),
    val isDangerCheckEnabled: Boolean = false,
    val dangerPieces: List<PieceDangerInfo> = emptyList(),
    val inspectedDangerPiece: PieceDangerInfo? = null
) {
    val isFinished: Boolean get() = result != GameResult.ONGOING
    val isPlayerTurn: Boolean get() = !isFinished && board.sideToMove == playerSide
    val lastMove: Move? get() = moves.lastOrNull()?.move
    val aiSide: Side get() = playerSide.opposite()
    val isPracticeMode: Boolean get() = mode == GameMode.PRACTICE
    val canUndo: Boolean
        get() = isPracticeMode && moves.any { it.side == playerSide }
    val levelTitle: String get() = EngineDifficulty.profileFor(engineLevel).title

    val isPreviewingVariation: Boolean
        get() = hintState.tier == 4 && hintState.bundle != null && hintState.previewStep > 0

    /**
     * Computes the board to display: either the live board, or stepped along the active
     * Tier-4 hint variation preview without mutating the actual game state (Section 10.2).
     */
    val displayBoard: BoardState
        get() {
            if (hintState.tier != 4 || hintState.bundle == null) return board
            val variation = hintState.bundle.variations.getOrNull(hintState.selectedVariationIndex) ?: return board
            var preview = board
            val steps = hintState.previewStep.coerceIn(0, variation.moves.size)
            for (i in 0 until steps) {
                val mv = variation.moves[i]
                if (!RulesEngine.isMoveLegal(preview, mv)) break
                preview = RulesEngine.applyMoveUnchecked(preview, mv)
            }
            return preview
        }

    /**
     * Validated board arrows for Tier 3 & Tier 4 hints (Section 10.2 & 17.5).
     */
    val activeArrows: List<BoardArrow>
        get() {
            val bundle = hintState.bundle ?: return emptyList()
            val variation = bundle.variations.getOrNull(hintState.selectedVariationIndex) ?: return emptyList()
            return when (hintState.tier) {
                3 -> {
                    val mv = variation.moves.firstOrNull() ?: return emptyList()
                    if (RulesEngine.isMoveLegal(board, mv)) {
                        listOf(BoardArrow(mv.from, mv.to, isOpponentReply = false))
                    } else {
                        emptyList()
                    }
                }
                4 -> {
                    val step = hintState.previewStep
                    val mv = variation.moves.getOrNull(step) ?: return emptyList()
                    val previewBoard = displayBoard
                    if (RulesEngine.isMoveLegal(previewBoard, mv)) {
                        val isOpponent = (step % 2 == 1)
                        listOf(BoardArrow(mv.from, mv.to, isOpponentReply = isOpponent, stepNumber = step + 1))
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        }

    val hintPieceSquare: Square?
        get() = if (hintState.tier == 2) hintState.bundle?.bestMove?.from else null

    val dangerOverlayMap: Map<Square, DangerLevel>
        get() = if (!isDangerCheckEnabled || isPreviewingVariation) {
            emptyMap()
        } else {
            dangerPieces.associate { it.square to it.level }
        }
}

class GameViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val engineManager: EngineManager,
    private val analysisService: AnalysisService,
    private val settingsStore: UserSettingsStore,
    private val soundEffectManager: SoundEffectManager,
    private val hapticFeedbackManager: HapticFeedbackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState(gameId = gameId))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings()
    )

    private val animIdCounter = AtomicLong(0L)
    private var engineJob: Job? = null
    private var liveEvalJob: Job? = null
    private var hintJob: Job? = null
    private var clockJob: Job? = null
    private var isClockPaused: Boolean = false

    init {
        loadGameFromDatabase()
    }

    private fun loadGameFromDatabase() {
        viewModelScope.launch {
            val persisted = gameRepository.loadGame(gameId)
            if (persisted == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "Không tìm thấy ván đấu (ID: $gameId)."
                    )
                }
                return@launch
            }

            val reconstructedResult = RulesEngine.reconstructGame(
                initialFen = persisted.entity.initialFen,
                movesUci = persisted.moves.map { it.move.toUci() }
            )
            if (reconstructedResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "Dữ liệu ván đấu không hợp lệ: ${reconstructedResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            val reconstructed = reconstructedResult.getOrThrow()
            val board = reconstructed.currentBoard
            val checkedSide = when {
                RulesEngine.isInCheck(board, Side.RED) -> Side.RED
                RulesEngine.isInCheck(board, Side.BLACK) -> Side.BLACK
                else -> null
            }

            val effectiveResult = if (persisted.result != GameResult.ONGOING) {
                persisted.result
            } else {
                reconstructed.terminal.result
            }
            val effectiveReason = if (persisted.result != GameResult.ONGOING) {
                persisted.resultReason
            } else {
                reconstructed.terminal.reason
            }

            val engineInfo = engineManager.activeEngineInfo
            val isTimed = persisted.entity.clockInitialMillis > 0L

            _uiState.value = GameUiState(
                isLoading = false,
                gameId = gameId,
                initialFen = persisted.entity.initialFen,
                initialPositionHash = reconstructed.initialBoard.positionHash,
                board = board,
                moves = reconstructed.moves,
                playerSide = persisted.playerSide,
                engineLevel = persisted.entity.engineLevel,
                engineName = engineInfo.name,
                mode = persisted.mode,
                result = effectiveResult,
                resultReason = effectiveReason,
                undoCount = persisted.entity.undoCount,
                isFlipped = persisted.playerSide == Side.BLACK,
                checkedSide = checkedSide,
                isTimed = isTimed,
                redRemainingMillis = persisted.entity.redRemainingMillis,
                blackRemainingMillis = persisted.entity.blackRemainingMillis,
                statusBanner = if (checkedSide != null && effectiveResult == GameResult.ONGOING) {
                    "Chiếu Tướng ${checkedSide.vietnameseName}!"
                } else null
            )

            if (isTimed && effectiveResult == GameResult.ONGOING) {
                startClockTicker()
            }

            if (effectiveResult == GameResult.ONGOING && board.sideToMove != persisted.playerSide) {
                triggerEngineMove()
            }
        }
    }

    fun onSquareTapped(square: Square) {
        val state = _uiState.value
        if (state.isLoading || state.isFinished || state.isEngineThinking || state.isPreviewingVariation) {
            return
        }

        // If Danger Check is active and user taps one of their threatened pieces, inspect its attackers/defenders
        if (state.isDangerCheckEnabled) {
            val danger = state.dangerPieces.firstOrNull { it.square == square }
            if (danger != null && state.selectedSquare == null) {
                _uiState.update { it.copy(inspectedDangerPiece = danger) }
            } else {
                _uiState.update { it.copy(inspectedDangerPiece = null) }
            }
        }

        if (!state.isPlayerTurn) return

        val tappedPiece = state.board.pieceAt(square)
        val currentSelected = state.selectedSquare

        // Case 1: User already selected a piece and tapped one of its legal target squares
        if (currentSelected != null && square in state.legalTargets) {
            executeMove(Move(currentSelected, square), isEngineMove = false)
            return
        }

        // Case 2: User tapped one of their own pieces -> select it and compute legal targets
        if (tappedPiece != null && tappedPiece.side == state.playerSide) {
            if (currentSelected == square) {
                _uiState.update { it.copy(selectedSquare = null, legalTargets = emptySet()) }
            } else {
                val targets = RulesEngine.generateLegalMovesFrom(state.board, square)
                    .map { it.to }
                    .toSet()
                hapticFeedbackManager.lightTap(settings.value.hapticEnabled)
                _uiState.update {
                    it.copy(
                        selectedSquare = square,
                        legalTargets = targets
                    )
                }
            }
            return
        }

        // Case 3: Selected a piece, then tapped an illegal square
        if (currentSelected != null) {
            hapticFeedbackManager.illegalMoveBuzz(settings.value.hapticEnabled)
            _uiState.update { it.copy(selectedSquare = null, legalTargets = emptySet()) }
        }
    }

    private fun executeMove(move: Move, isEngineMove: Boolean, engineScoreRed: EngineScore? = null) {
        val state = _uiState.value
        if (state.isFinished) return
        if (!RulesEngine.isMoveLegal(state.board, move)) {
            if (!isEngineMove) {
                hapticFeedbackManager.illegalMoveBuzz(settings.value.hapticEnabled)
            }
            return
        }

        // Cancel stale hint/live-eval tasks when board state changes (Section 6.4)
        hintJob?.cancel()
        if (!isEngineMove) {
            liveEvalJob?.cancel()
        }

        val boardBefore = state.board
        val movesBefore = state.moves
        val applied = RulesEngine.applyMove(boardBefore, move)
        val plyIndex = movesBefore.size
        val record = MoveRecord(
            plyIndex = plyIndex,
            side = applied.movedPiece.side,
            move = move,
            pieceType = applied.movedPiece.type,
            capturedPieceType = applied.capturedPiece?.type,
            notationShort = applied.notationShort,
            notationFull = applied.notationFull,
            positionHashAfter = applied.newBoard.positionHash,
            gaveCheck = applied.gaveCheck
        )

        val updatedMoves = movesBefore + record
        val initialBoard = (FenCodec.parse(state.initialFen) as? FenParseResult.Success)?.board
        val terminal = RulesEngine.evaluateTerminalState(
            board = applied.newBoard,
            history = updatedMoves,
            initialPositionHash = state.initialPositionHash,
            initialBoard = initialBoard
        )

        val checkedSide = if (applied.gaveCheck) applied.newBoard.sideToMove else null
        val animEvent = MoveAnimationEvent(
            id = animIdCounter.incrementAndGet(),
            move = move,
            movedPiece = applied.movedPiece,
            capturedPiece = applied.capturedPiece
        )

        val banner = when {
            terminal.isFinished -> "${terminal.result.vietnameseLabel} · ${terminal.reason.vietnameseLabel}"
            checkedSide != null -> "Chiếu Tướng ${checkedSide.vietnameseName}!"
            else -> null
        }

        val updatedDanger = if (state.isDangerCheckEnabled && !terminal.isFinished) {
            TacticalFactExtractor.analyzeDangerPieces(applied.newBoard, state.playerSide)
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                board = applied.newBoard,
                moves = updatedMoves,
                selectedSquare = null,
                legalTargets = emptySet(),
                checkedSide = checkedSide,
                result = terminal.result,
                resultReason = terminal.reason,
                isEngineThinking = false,
                engineError = null,
                animationEvent = animEvent,
                statusBanner = banner,
                latestEvalScoreRed = engineScoreRed ?: it.latestEvalScoreRed,
                liveEvaluation = if (!isEngineMove) null else it.liveEvaluation,
                moveFeedbackVisual = if (!isEngineMove) null else it.moveFeedbackVisual,
                hintState = HintUiState(),
                dangerPieces = updatedDanger,
                inspectedDangerPiece = null
            )
        }

        // Play audio & haptics (Section 19)
        val curSettings = settings.value
        when {
            terminal.isFinished -> {
                val playerWon = (state.playerSide == Side.RED && terminal.result == GameResult.RED_WIN) ||
                    (state.playerSide == Side.BLACK && terminal.result == GameResult.BLACK_WIN)
                val sound = when {
                    terminal.result == GameResult.DRAW -> GameSoundEffect.DRAW
                    playerWon -> GameSoundEffect.WIN
                    else -> GameSoundEffect.LOSE
                }
                soundEffectManager.play(sound, curSettings.soundEnabled, curSettings.soundVolume)
            }
            applied.gaveCheck -> {
                soundEffectManager.play(GameSoundEffect.CHECK, curSettings.soundEnabled, curSettings.soundVolume)
                hapticFeedbackManager.checkPulse(curSettings.hapticEnabled)
            }
            applied.capturedPiece != null -> {
                soundEffectManager.play(GameSoundEffect.CAPTURE, curSettings.soundEnabled, curSettings.soundVolume)
            }
            else -> {
                soundEffectManager.play(GameSoundEffect.MOVE, curSettings.soundEnabled, curSettings.soundVolume)
            }
        }

        // Persist immediately so closing app never loses a move (Section 18.4 & 19.2)
        val redMs = state.redRemainingMillis
        val blackMs = state.blackRemainingMillis
        val engineInfo = engineManager.activeEngineInfo
        viewModelScope.launch {
            gameRepository.recordMove(
                gameId = state.gameId,
                record = record,
                redRemainingMillis = redMs,
                blackRemainingMillis = blackMs,
                engineInfo = engineInfo
            )
            if (terminal.isFinished) {
                clockJob?.cancel()
                gameRepository.finishGame(
                    gameId = state.gameId,
                    result = terminal.result,
                    reason = terminal.reason,
                    redRemainingMillis = redMs,
                    blackRemainingMillis = blackMs
                )
            }
        }

        // Schedule quick live evaluation of the player's move in Practice mode (Section 9)
        if (!isEngineMove && state.isPracticeMode && curSettings.liveEvaluationEnabled && !terminal.isFinished) {
            scheduleLiveEvaluation(
                initialFen = state.initialFen,
                movesBefore = movesBefore,
                boardBefore = boardBefore,
                playedRecord = record,
                boardAfter = applied.newBoard
            )
        }

        if (!terminal.isFinished && applied.newBoard.sideToMove == state.aiSide) {
            triggerEngineMove()
        }
    }

    private fun scheduleLiveEvaluation(
        initialFen: String,
        movesBefore: List<MoveRecord>,
        boardBefore: BoardState,
        playedRecord: MoveRecord,
        boardAfter: BoardState
    ) {
        liveEvalJob?.cancel()
        val evaluatedPly = playedRecord.plyIndex
        liveEvalJob = viewModelScope.launch {
            try {
                val curSettings = settings.value
                val liveEval = analysisService.evaluateMoveLive(
                    initialFen = initialFen,
                    movesBefore = movesBefore,
                    boardBefore = boardBefore,
                    playedRecord = playedRecord,
                    boardAfter = boardAfter,
                    resourceMode = curSettings.engineResourceMode,
                    notationStyle = curSettings.notationStyle
                ) ?: return@launch

                // Stale-result guard: only display if this move is still the player's most recent move (Section 6.4 & 9.2)
                val latest = _uiState.value
                val lastPlayerMove = latest.moves.lastOrNull { it.side == latest.playerSide }
                if (lastPlayerMove == null || lastPlayerMove.plyIndex != evaluatedPly) {
                    return@launch
                }

                val shouldDisplayFeedback = !curSettings.onlyWarnMajorBlunders ||
                    liveEval.classification.isMajorError

                val feedbackVisual = when {
                    !shouldDisplayFeedback -> null
                    liveEval.classification == MoveClassification.GOOD && !curSettings.showGoodMoveEffects -> null
                    liveEval.classification == MoveClassification.ACCEPTABLE -> null
                    else -> MoveFeedbackVisual(
                        square = playedRecord.move.to,
                        classification = liveEval.classification
                    )
                }

                if (liveEval.classification.isMajorError && curSettings.vibrateOnBlunder && curSettings.hapticEnabled) {
                    hapticFeedbackManager.blunderBuzz(enabled = true)
                }

                _uiState.update {
                    it.copy(
                        latestEvalScoreRed = liveEval.scoreAfterRed,
                        liveEvaluation = if (shouldDisplayFeedback) liveEval else null,
                        moveFeedbackVisual = feedbackVisual
                    )
                }
            } catch (_: CancellationException) {
            } catch (_: Throwable) {
                // Live quick evaluation must never disrupt gameplay
            }
        }
    }

    /**
     * Progressive 4-tier hint workflow (Section 10.1–10.3).
     * Locked in Challenge mode.
     */
    fun requestOrAdvanceHint() {
        val state = _uiState.value
        val curSettings = settings.value
        if (!state.isPracticeMode || !curSettings.hintsEnabled || !state.isPlayerTurn || state.isEngineThinking) {
            return
        }

        val currentHint = state.hintState
        // If we already have a computed hint bundle for this exact board hash, simply advance tier 1 -> 2 -> 3 -> 4!
        if (currentHint.bundle != null && currentHint.bundle.positionHash == state.board.positionHash) {
            val nextTier = (currentHint.tier + 1).coerceAtMost(4)
            _uiState.update {
                it.copy(
                    hintState = currentHint.copy(tier = nextTier, previewStep = 0),
                    selectedSquare = null,
                    legalTargets = emptySet()
                )
            }
            return
        }

        hintJob?.cancel()
        val expectedHash = state.board.positionHash
        _uiState.update {
            it.copy(
                hintState = HintUiState(isLoading = true, tier = 1),
                selectedSquare = null,
                legalTargets = emptySet()
            )
        }

        hintJob = viewModelScope.launch {
            gameRepository.incrementHintCount(state.gameId)
            val position = PositionWithHistory(state.initialFen, state.board, state.moves)
            val result = analysisService.computeHint(position, settings.value.engineResourceMode)

            if (_uiState.value.board.positionHash != expectedHash) return@launch

            result.fold(
                onSuccess = { bundle ->
                    _uiState.update {
                        it.copy(
                            latestEvalScoreRed = bundle.scoreRed,
                            hintState = HintUiState(
                                isLoading = false,
                                tier = 1,
                                bundle = bundle,
                                selectedVariationIndex = 0,
                                previewStep = 0
                            )
                        )
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) return@fold
                    _uiState.update {
                        it.copy(
                            hintState = HintUiState(
                                isLoading = false,
                                error = err.message ?: "Không thể tạo gợi ý lúc này."
                            )
                        )
                    }
                }
            )
        }
    }

    fun jumpHintToTier(tier: Int) {
        val current = _uiState.value.hintState
        if (current.bundle == null) return
        _uiState.update {
            it.copy(
                hintState = current.copy(
                    tier = tier.coerceIn(1, 4),
                    previewStep = 0
                )
            )
        }
    }

    fun stepHintVariation(delta: Int) {
        val current = _uiState.value.hintState
        val variation = current.bundle?.variations?.getOrNull(current.selectedVariationIndex) ?: return
        val nextStep = (current.previewStep + delta).coerceIn(0, variation.moves.size)
        _uiState.update { it.copy(hintState = current.copy(previewStep = nextStep)) }
    }

    fun switchHintVariation() {
        val current = _uiState.value.hintState
        val count = current.bundle?.variations?.size ?: return
        if (count <= 1) return
        val nextIdx = (current.selectedVariationIndex + 1) % count
        _uiState.update {
            it.copy(
                hintState = current.copy(
                    selectedVariationIndex = nextIdx,
                    previewStep = 0
                )
            )
        }
    }

    fun closeHint() {
        hintJob?.cancel()
        _uiState.update { it.copy(hintState = HintUiState()) }
    }

    fun toggleDangerCheck() {
        val state = _uiState.value
        if (!state.isPracticeMode || state.isFinished) return
        val enable = !state.isDangerCheckEnabled
        if (enable) {
            viewModelScope.launch { gameRepository.incrementDangerCheckCount(state.gameId) }
        }
        val pieces = if (enable) {
            TacticalFactExtractor.analyzeDangerPieces(state.board, state.playerSide)
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                isDangerCheckEnabled = enable,
                dangerPieces = pieces,
                inspectedDangerPiece = pieces.firstOrNull()
            )
        }
    }

    fun dismissInspectedDangerPiece() {
        _uiState.update { it.copy(inspectedDangerPiece = null) }
    }

    fun dismissLiveEvaluation() {
        _uiState.update { it.copy(liveEvaluation = null) }
    }

    fun triggerEngineMove() {
        val state = _uiState.value
        if (state.isFinished || state.board.sideToMove != state.aiSide) return

        engineJob?.cancel()
        val expectedHash = state.board.positionHash
        val position = PositionWithHistory(
            initialFen = state.initialFen,
            currentBoard = state.board,
            moves = state.moves
        )

        _uiState.update {
            it.copy(
                isEngineThinking = true,
                engineError = null,
                selectedSquare = null,
                legalTargets = emptySet()
            )
        }

        engineJob = viewModelScope.launch {
            val outcome = engineManager.requestMove(
                position = position,
                level = state.engineLevel,
                resourceMode = settings.value.engineResourceMode
            )

            // Guard against stale results (Section 6.4)
            val latest = _uiState.value
            if (latest.isFinished || latest.board.positionHash != expectedHash) {
                return@launch
            }

            when (outcome) {
                is EngineMoveOutcome.Success -> {
                    val redScore = outcome.result.score.toRedPerspective(state.aiSide)
                    _uiState.update { it.copy(engineName = outcome.result.engineInfo.name) }
                    executeMove(
                        move = outcome.result.bestMove,
                        isEngineMove = true,
                        engineScoreRed = if (state.isPracticeMode) redScore else null
                    )
                }
                is EngineMoveOutcome.Cancelled -> {
                    _uiState.update { it.copy(isEngineThinking = false) }
                }
                is EngineMoveOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isEngineThinking = false,
                            engineError = outcome.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Undo in Practice mode (Section 11.1):
     * - Cancels active AI, hint, and live-evaluation computations immediately.
     * - Reverts a full turn (player move + AI reply if AI already moved).
     * - Reconstructs board cleanly from initial FEN + remaining moves and persists to Room.
     */
    fun undoMove() {
        val state = _uiState.value
        if (!state.canUndo) return

        engineJob?.cancel()
        liveEvalJob?.cancel()
        hintJob?.cancel()

        viewModelScope.launch {
            engineManager.cancelActiveSearch()

            val current = _uiState.value
            if (!current.canUndo) return@launch

            val lastPlayerMoveIdx = current.moves.indexOfLast { it.side == current.playerSide }
            if (lastPlayerMoveIdx < 0) return@launch

            val remainingMoves = current.moves.subList(0, lastPlayerMoveIdx)
            val reconstructed = RulesEngine.reconstructGame(
                initialFen = current.initialFen,
                movesUci = remainingMoves.map { it.move.toUci() }
            ).getOrNull() ?: return@launch

            val board = reconstructed.currentBoard
            val checkedSide = when {
                RulesEngine.isInCheck(board, Side.RED) -> Side.RED
                RulesEngine.isInCheck(board, Side.BLACK) -> Side.BLACK
                else -> null
            }
            val newUndoCount = current.undoCount + 1
            val updatedDanger = if (current.isDangerCheckEnabled) {
                TacticalFactExtractor.analyzeDangerPieces(board, current.playerSide)
            } else {
                emptyList()
            }

            _uiState.update {
                it.copy(
                    board = board,
                    moves = reconstructed.moves,
                    result = GameResult.ONGOING,
                    resultReason = GameResultReason.NONE,
                    undoCount = newUndoCount,
                    selectedSquare = null,
                    legalTargets = emptySet(),
                    checkedSide = checkedSide,
                    isEngineThinking = false,
                    engineError = null,
                    animationEvent = null,
                    liveEvaluation = null,
                    moveFeedbackVisual = null,
                    hintState = HintUiState(),
                    dangerPieces = updatedDanger,
                    inspectedDangerPiece = null,
                    statusBanner = if (checkedSide != null) {
                        "Chiếu Tướng ${checkedSide.vietnameseName}!"
                    } else {
                        "Đã đi lại nước vừa rồi"
                    }
                )
            }

            gameRepository.undoMovesFromPly(
                gameId = current.gameId,
                fromPlyIndex = lastPlayerMoveIdx,
                newUndoCount = newUndoCount,
                redRemainingMillis = current.redRemainingMillis,
                blackRemainingMillis = current.blackRemainingMillis
            )

            if (current.isTimed && clockJob?.isActive != true) {
                startClockTicker()
            }
        }
    }

    fun flipBoard() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun toggleSoundQuick() {
        viewModelScope.launch {
            settingsStore.setSoundEnabled(!settings.value.soundEnabled)
        }
    }

    fun resignGame(onFinished: (Long) -> Unit) {
        val state = _uiState.value
        if (state.isFinished) {
            onFinished(state.gameId)
            return
        }

        engineJob?.cancel()
        liveEvalJob?.cancel()
        hintJob?.cancel()
        clockJob?.cancel()
        val winner = if (state.playerSide == Side.RED) GameResult.BLACK_WIN else GameResult.RED_WIN

        _uiState.update {
            it.copy(
                result = winner,
                resultReason = GameResultReason.RESIGNATION,
                isEngineThinking = false,
                selectedSquare = null,
                legalTargets = emptySet(),
                hintState = HintUiState(),
                statusBanner = "${winner.vietnameseLabel} · Xin thua"
            )
        }

        val curSettings = settings.value
        soundEffectManager.play(GameSoundEffect.LOSE, curSettings.soundEnabled, curSettings.soundVolume)

        viewModelScope.launch {
            engineManager.cancelActiveSearch()
            gameRepository.finishGame(
                gameId = state.gameId,
                result = winner,
                reason = GameResultReason.RESIGNATION,
                redRemainingMillis = state.redRemainingMillis,
                blackRemainingMillis = state.blackRemainingMillis
            )
            onFinished(state.gameId)
        }
    }

    fun onPauseLifecycle() {
        isClockPaused = true
        val state = _uiState.value
        if (state.isTimed && !state.isFinished) {
            viewModelScope.launch {
                gameRepository.updateClockSnapshot(
                    gameId = state.gameId,
                    redRemainingMillis = state.redRemainingMillis,
                    blackRemainingMillis = state.blackRemainingMillis
                )
            }
        }
    }

    fun onResumeLifecycle() {
        isClockPaused = false
    }

    private fun startClockTicker() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (isActive) {
                delay(250L)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTickMs).coerceIn(0L, 1000L)
                lastTickMs = now

                val state = _uiState.value
                if (state.isFinished || !state.isTimed || isClockPaused) continue

                val turn = state.board.sideToMove
                val newRed = if (turn == Side.RED) (state.redRemainingMillis - elapsed).coerceAtLeast(0L) else state.redRemainingMillis
                val newBlack = if (turn == Side.BLACK) (state.blackRemainingMillis - elapsed).coerceAtLeast(0L) else state.blackRemainingMillis

                if ((turn == Side.RED && newRed <= 0L) || (turn == Side.BLACK && newBlack <= 0L)) {
                    engineJob?.cancel()
                    liveEvalJob?.cancel()
                    hintJob?.cancel()
                    engineManager.cancelActiveSearch()
                    val winner = if (turn == Side.RED) GameResult.BLACK_WIN else GameResult.RED_WIN
                    _uiState.update {
                        it.copy(
                            redRemainingMillis = newRed,
                            blackRemainingMillis = newBlack,
                            result = winner,
                            resultReason = GameResultReason.TIMEOUT,
                            isEngineThinking = false,
                            selectedSquare = null,
                            legalTargets = emptySet(),
                            hintState = HintUiState(),
                            statusBanner = "${winner.vietnameseLabel} · Hết giờ"
                        )
                    }
                    val curSettings = settings.value
                    val playerWon = (state.playerSide == Side.RED && winner == GameResult.RED_WIN) ||
                        (state.playerSide == Side.BLACK && winner == GameResult.BLACK_WIN)
                    soundEffectManager.play(
                        if (playerWon) GameSoundEffect.WIN else GameSoundEffect.LOSE,
                        curSettings.soundEnabled,
                        curSettings.soundVolume
                    )
                    gameRepository.finishGame(
                        gameId = state.gameId,
                        result = winner,
                        reason = GameResultReason.TIMEOUT,
                        redRemainingMillis = newRed,
                        blackRemainingMillis = newBlack
                    )
                    break
                } else {
                    _uiState.update {
                        it.copy(
                            redRemainingMillis = newRed,
                            blackRemainingMillis = newBlack
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        engineJob?.cancel()
        liveEvalJob?.cancel()
        hintJob?.cancel()
        clockJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val gameId: Long,
        private val gameRepository: GameRepository,
        private val engineManager: EngineManager,
        private val analysisService: AnalysisService,
        private val settingsStore: UserSettingsStore,
        private val soundEffectManager: SoundEffectManager,
        private val hapticFeedbackManager: HapticFeedbackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameViewModel(
                gameId = gameId,
                gameRepository = gameRepository,
                engineManager = engineManager,
                analysisService = analysisService,
                settingsStore = settingsStore,
                soundEffectManager = soundEffectManager,
                hapticFeedbackManager = hapticFeedbackManager
            ) as T
        }
    }
}
