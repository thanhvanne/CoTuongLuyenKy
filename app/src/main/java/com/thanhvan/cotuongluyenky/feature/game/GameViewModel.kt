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
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineDifficulty
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.engine.manager.EngineMoveOutcome
import com.thanhvan.cotuongluyenky.feature.game.components.MoveAnimationEvent
import com.thanhvan.cotuongluyenky.service.haptic.HapticFeedbackManager
import com.thanhvan.cotuongluyenky.service.sound.GameSoundEffect
import com.thanhvan.cotuongluyenky.service.sound.SoundEffectManager
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
    val statusBanner: String? = null
) {
    val isFinished: Boolean get() = result != GameResult.ONGOING
    val isPlayerTurn: Boolean get() = !isFinished && board.sideToMove == playerSide
    val lastMove: Move? get() = moves.lastOrNull()?.move
    val aiSide: Side get() = playerSide.opposite()
    val canUndo: Boolean
        get() = mode == GameMode.PRACTICE && moves.any { it.side == playerSide }
    val levelTitle: String get() = EngineDifficulty.profileFor(engineLevel).title
}

class GameViewModel(
    private val gameId: Long,
    private val gameRepository: GameRepository,
    private val engineManager: EngineManager,
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
        if (state.isLoading || state.isFinished || state.isEngineThinking || !state.isPlayerTurn) {
            return
        }

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

    private fun executeMove(move: Move, isEngineMove: Boolean) {
        val state = _uiState.value
        if (state.isFinished) return
        if (!RulesEngine.isMoveLegal(state.board, move)) {
            if (!isEngineMove) {
                hapticFeedbackManager.illegalMoveBuzz(settings.value.hapticEnabled)
            }
            return
        }

        val applied = RulesEngine.applyMove(state.board, move)
        val plyIndex = state.moves.size
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

        val updatedMoves = state.moves + record
        val terminal = RulesEngine.evaluateTerminalState(
            board = applied.newBoard,
            history = updatedMoves,
            initialPositionHash = state.initialPositionHash
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
                statusBanner = banner
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

        if (!terminal.isFinished && applied.newBoard.sideToMove == state.aiSide) {
            triggerEngineMove()
        }
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
                    _uiState.update { it.copy(engineName = outcome.result.engineInfo.name) }
                    executeMove(outcome.result.bestMove, isEngineMove = true)
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
     * - Cancels active AI computation immediately.
     * - Reverts a full turn (player move + AI reply if AI already moved), or just the player's
     *   move if AI has not responded yet.
     * - Reconstructs board cleanly from initial FEN + remaining moves and persists to Room.
     */
    fun undoMove() {
        val state = _uiState.value
        if (!state.canUndo) return

        engineJob?.cancel()
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
        clockJob?.cancel()
        val winner = if (state.playerSide == Side.RED) GameResult.BLACK_WIN else GameResult.RED_WIN

        _uiState.update {
            it.copy(
                result = winner,
                resultReason = GameResultReason.RESIGNATION,
                isEngineThinking = false,
                selectedSquare = null,
                legalTargets = emptySet(),
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
        clockJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val gameId: Long,
        private val gameRepository: GameRepository,
        private val engineManager: EngineManager,
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
                settingsStore = settingsStore,
                soundEffectManager = soundEffectManager,
                hapticFeedbackManager = hapticFeedbackManager
            ) as T
        }
    }
}
