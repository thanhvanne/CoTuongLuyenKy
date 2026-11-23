package com.thanhvan.cotuongluyenky.domain.training

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleEntity
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleItem
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleSourceType
import com.thanhvan.cotuongluyenky.data.repository.PuzzleTheme
import com.thanhvan.cotuongluyenky.domain.analysis.AnalysisService
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.domain.commentary.TacticalFactExtractor
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.rules.XiangqiNotation
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.manager.EngineAnalysisOutcome
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager

enum class PuzzleAttemptStatus {
    CORRECT,
    EQUIVALENT_GOOD,
    INCORRECT,
    ILLEGAL
}

data class PuzzleAttemptResult(
    val status: PuzzleAttemptStatus,
    val message: String,
    val playedMove: Move
)

/**
 * Coordinates personalized puzzle generation, verification (including equivalent alternative moves),
 * catalog seeding, and spaced-repetition updates (Sections 15, 21.2, 26).
 */
class TrainingService(
    private val gameRepository: GameRepository,
    private val puzzleRepository: PuzzleRepository,
    private val analysisService: AnalysisService,
    private val engineManager: EngineManager
) {
    /**
     * Ensures the verified built-in catalog is seeded once (idempotent via catalogKey).
     */
    suspend fun ensureCatalogSeeded() {
        val now = System.currentTimeMillis()
        for (entity in SeedPuzzleCatalog.toEntities(now)) {
            if (isValidSeedPuzzle(entity)) {
                puzzleRepository.insertPuzzleIfNewCatalogKey(entity)
            }
        }
    }

    /**
     * Generates mistake-fix puzzles from a single analyzed game (whether won, lost, or drawn — Section 18.8!).
     * Only creates a puzzle when the move is classified as MISTAKE or BLUNDER and has a verified legal bestMove.
     */
    suspend fun generateMistakePuzzlesForGame(gameId: Long): List<Long> {
        val game = gameRepository.loadGame(gameId) ?: return emptyList()
        val analyses = analysisService.loadGameAnalysisModels(gameId)
        if (analyses.isEmpty()) return emptyList()

        val initialBoard = when (val parsed = FenCodec.parse(game.entity.initialFen)) {
            is FenParseResult.Success -> parsed.board
            is FenParseResult.Error -> RulesEngine.initialBoard()
        }

        val createdIds = mutableListOf<Long>()
        var currentBoard = initialBoard
        val historyUci = mutableListOf<String>()

        for (record in game.moves) {
            val boardBefore = currentBoard
            val move = record.move
            if (!RulesEngine.isMoveLegal(boardBefore, move)) break
            val boardAfter = RulesEngine.applyMoveUnchecked(boardBefore, move)

            val analysis = analyses[record.plyIndex]
            if (
                record.side == game.playerSide &&
                analysis != null &&
                !analysis.isQuickLiveEval &&
                (analysis.classification == MoveClassification.BLUNDER || analysis.classification == MoveClassification.MISTAKE) &&
                analysis.bestMove != null &&
                analysis.bestMove != move &&
                RulesEngine.isMoveLegal(boardBefore, analysis.bestMove)
            ) {
                val bestMove = analysis.bestMove
                val accepted = buildList {
                    add(bestMove)
                    val bestScoreRed = analysis.scoreBeforeRed
                    val mover = boardBefore.sideToMove
                    for (v in analysis.variations.drop(1)) {
                        val altFirst = v.moves.firstOrNull() ?: continue
                        if (altFirst == move || altFirst in this) continue
                        if (!RulesEngine.isMoveLegal(boardBefore, altFirst)) continue
                        if (isEquivalentScore(mover, bestScoreRed, v.score)) {
                            add(altFirst)
                        }
                    }
                }

                val variations = buildList {
                    if (analysis.principalVariation.isNotEmpty()) {
                        add(analysis.principalVariation)
                    } else {
                        add(listOf(bestMove))
                    }
                    for (v in analysis.variations) {
                        if (v.moves.isNotEmpty() && v.moves !in this) {
                            add(v.moves)
                        }
                    }
                }

                val facts = TacticalFactExtractor.extractMoveFacts(
                    boardBefore = boardBefore,
                    playedMove = move,
                    boardAfter = boardAfter,
                    bestMove = bestMove,
                    bestPv = analysis.principalVariation,
                    missedForcedMate = (analysis.scoreBeforeRed is EngineScore.Mate),
                    allowedForcedMate = (analysis.scoreAfterRed is EngineScore.Mate),
                    swingFromAdvantageToDisadvantage = analysis.isTurningPoint
                )
                val theme = TacticalFactExtractor.inferPuzzleTheme(
                    facts = facts,
                    plyIndex = record.plyIndex,
                    totalPiecesOnBoard = boardBefore.allPieces(Side.RED).size + boardBefore.allPieces(Side.BLACK).size
                )

                val moveNumber = (record.plyIndex / 2) + 1
                val entity = PuzzleEntity(
                    catalogKey = null,
                    sourceType = PuzzleSourceType.GAME_MISTAKE.name,
                    sourceGameId = gameId,
                    sourcePlyIndex = record.plyIndex,
                    initialFen = FenCodec.toFen(boardBefore),
                    historyData = historyUci.joinToString(" "),
                    sideToMove = boardBefore.sideToMove.name,
                    title = "Sửa sai nước $moveNumber (Cấp ${game.entity.engineLevel})",
                    theme = theme.name,
                    playedMistakeMove = move.toUci(),
                    acceptedMoves = accepted.joinToString(",") { it.toUci() },
                    solutionVariations = variations.joinToString("|") { line -> line.joinToString(" ") { it.toUci() } },
                    commentary = analysis.commentary,
                    engineVersion = analysis.engineVersion,
                    analysisConfig = "depth=${analysis.depth}",
                    createdAt = System.currentTimeMillis()
                )
                val newId = puzzleRepository.insertGameMistakeIfNotExists(entity)
                if (newId != null) {
                    createdIds.add(newId)
                }
            }

            historyUci.add(move.toUci())
            currentBoard = boardAfter
        }

        return createdIds
    }

    /**
     * Scans all analyzed games and generates missing mistake-fix puzzles.
     */
    suspend fun syncMistakePuzzlesFromAllGames(): Int {
        val games = gameRepository.getAllGamesWithRawMoves()
        var created = 0
        for ((game, _) in games) {
            if (game.analysisStatus == "COMPLETED" || game.analysisStatus == "PARTIAL") {
                created += generateMistakePuzzlesForGame(game.id).size
            }
        }
        return created
    }

    /**
     * Manually saves a position (from AnalysisScreen or PositionEditorScreen) as a personal puzzle.
     */
    suspend fun saveManualPuzzle(
        board: BoardState,
        title: String,
        theme: PuzzleTheme,
        acceptedMoves: List<Move>,
        variations: List<List<Move>>,
        commentary: String,
        sourceGameId: Long? = null,
        sourcePlyIndex: Int? = null,
        historyUci: List<String> = emptyList(),
        playedMistakeMove: Move? = null
    ): Long {
        val fen = FenCodec.toFen(board)
        val legalAccepted = acceptedMoves.filter { RulesEngine.isMoveLegal(board, it) }
        var computedVariations = variations.filter { it.isNotEmpty() }

        val finalAccepted = if (legalAccepted.isNotEmpty()) {
            legalAccepted
        } else {
            val outcome = engineManager.analyzePosition(
                position = PositionWithHistory(fen, board, emptyList()),
                limit = SearchLimit(moveTimeMs = 550L, maxDepth = 4),
                variations = 2,
                resourceMode = EngineResourceMode.BALANCED,
                preemptActive = true
            )
            if (outcome is EngineAnalysisOutcome.Success) {
                val top = outcome.analysis.variations.firstOrNull()?.moves?.firstOrNull()
                if (computedVariations.isEmpty()) {
                    computedVariations = outcome.analysis.variations.map { it.moves }.filter { it.isNotEmpty() }
                }
                listOfNotNull(top).filter { RulesEngine.isMoveLegal(board, it) }
            } else {
                emptyList()
            }
        }

        val finalVariations = computedVariations.ifEmpty {
            finalAccepted.firstOrNull()?.let { listOf(listOf(it)) } ?: emptyList()
        }
        val engineInfo = engineManager.activeEngineInfo
        val entity = PuzzleEntity(
            catalogKey = null,
            sourceType = PuzzleSourceType.MANUAL_SAVE.name,
            sourceGameId = sourceGameId,
            sourcePlyIndex = sourcePlyIndex,
            initialFen = fen,
            historyData = historyUci.joinToString(" "),
            sideToMove = board.sideToMove.name,
            title = title,
            theme = theme.name,
            playedMistakeMove = playedMistakeMove?.toUci(),
            acceptedMoves = finalAccepted.joinToString(",") { it.toUci() },
            solutionVariations = finalVariations.joinToString("|") { line -> line.joinToString(" ") { it.toUci() } },
            commentary = commentary,
            engineVersion = engineInfo.version,
            analysisConfig = "manual-save",
            createdAt = System.currentTimeMillis()
        )
        return puzzleRepository.createPuzzleWithInitialProgress(entity)
    }

    /**
     * Verifies a user's attempted move on a puzzle (Section 15.3 & 26):
     * 1) Legal check via [RulesEngine].
     * 2) Immediate match against stored `acceptedMoves`.
     * 3) Immediate checkmate detection (any move delivering legal checkmate is always correct!).
     * 4) Engine verification for equivalent strong alternatives ("Nước khác nhưng cũng tốt").
     */
    suspend fun verifyAttempt(
        puzzle: PuzzleItem,
        board: BoardState,
        playedMove: Move,
        notationStyle: NotationStyle = NotationStyle.FULL
    ): PuzzleAttemptResult {
        if (!RulesEngine.isMoveLegal(board, playedMove)) {
            return PuzzleAttemptResult(
                status = PuzzleAttemptStatus.ILLEGAL,
                message = "Nước đi không hợp lệ theo luật Cờ Tướng.",
                playedMove = playedMove
            )
        }

        val notation = XiangqiNotation.format(board, playedMove).let {
            if (notationStyle == NotationStyle.FULL) it.full else it.short
        }

        if (playedMove in puzzle.acceptedMoves) {
            return PuzzleAttemptResult(
                status = PuzzleAttemptStatus.CORRECT,
                message = "Chính xác! $notation là phương án tối ưu.",
                playedMove = playedMove
            )
        }

        val afterPlayed = RulesEngine.applyMoveUnchecked(board, playedMove)
        val deliveredMate = RulesEngine.isInCheck(afterPlayed, afterPlayed.sideToMove) &&
            RulesEngine.generateLegalMoves(afterPlayed).isEmpty()
        if (deliveredMate) {
            return PuzzleAttemptResult(
                status = PuzzleAttemptStatus.EQUIVALENT_GOOD,
                message = "Nước khác nhưng cũng chiếu bí ngay ($notation)! Rất tốt.",
                playedMove = playedMove
            )
        }

        if (puzzle.playedMistakeMove != null && playedMove == puzzle.playedMistakeMove) {
            return PuzzleAttemptResult(
                status = PuzzleAttemptStatus.INCORRECT,
                message = "Đây chính là nước sai đã đi trong ván ($notation). Hãy thử tìm phương án mạnh hơn.",
                playedMove = playedMove
            )
        }

        val bestMove = puzzle.acceptedMoves.firstOrNull()
        if (bestMove != null && RulesEngine.isMoveLegal(board, bestMove)) {
            val mover = board.sideToMove
            val afterBest = RulesEngine.applyMoveUnchecked(board, bestMove)
            val bestOutcome = engineManager.analyzePosition(
                position = PositionWithHistory(FenCodec.toFen(afterBest), afterBest, emptyList()),
                limit = SearchLimit(moveTimeMs = 320L, maxDepth = 4),
                variations = 1,
                resourceMode = EngineResourceMode.BALANCED,
                preemptActive = true
            )
            val playedOutcome = engineManager.analyzePosition(
                position = PositionWithHistory(FenCodec.toFen(afterPlayed), afterPlayed, emptyList()),
                limit = SearchLimit(moveTimeMs = 320L, maxDepth = 4),
                variations = 1,
                resourceMode = EngineResourceMode.BALANCED,
                preemptActive = true
            )
            if (bestOutcome is EngineAnalysisOutcome.Success && playedOutcome is EngineAnalysisOutcome.Success) {
                val bestTopScore = bestOutcome.analysis.variations.firstOrNull()?.score
                val playedTopScore = playedOutcome.analysis.variations.firstOrNull()?.score
                val bestScoreRed = bestTopScore?.toRedPerspective(afterBest.sideToMove)
                val playedScoreRed = playedTopScore?.toRedPerspective(afterPlayed.sideToMove)
                if (isEquivalentScore(mover, bestScoreRed, playedScoreRed, toleranceCp = 45)) {
                    return PuzzleAttemptResult(
                        status = PuzzleAttemptStatus.EQUIVALENT_GOOD,
                        message = "Nước khác nhưng cũng tốt! Engine xác minh $notation mang lại hiệu quả tương đương phương án chính.",
                        playedMove = playedMove
                    )
                }
            }
        }

        return PuzzleAttemptResult(
            status = PuzzleAttemptStatus.INCORRECT,
            message = "$notation chưa phải phương án tốt nhất trong thế này. Hãy thử lại hoặc mở Gợi ý.",
            playedMove = playedMove
        )
    }

    suspend fun recordAttemptOutcome(
        puzzleId: Long,
        solvedCorrectly: Boolean,
        hintLevelUsed: Int
    ) {
        val prev = puzzleRepository.getProgress(puzzleId)
        val updated = SpacedRepetitionScheduler.scheduleNext(
            previous = prev,
            puzzleId = puzzleId,
            solvedCorrectly = solvedCorrectly,
            hintLevelUsed = hintLevelUsed,
            nowMillis = System.currentTimeMillis()
        )
        puzzleRepository.saveProgress(updated)
    }

    fun getTier1IdeaHint(board: BoardState, bestMove: Move): String =
        CommentaryService.buildTier1IdeaHint(board, bestMove)

    private fun isEquivalentScore(
        mover: Side,
        bestScoreRed: EngineScore?,
        candidateScoreRed: EngineScore?,
        toleranceCp: Int = 40
    ): Boolean {
        if (bestScoreRed == null || candidateScoreRed == null) return false
        val bestMover = scoreForMover(mover, bestScoreRed)
        val candMover = scoreForMover(mover, candidateScoreRed)
        if (bestMover > 20_000) return candMover > 20_000
        return (bestMover - candMover) <= toleranceCp
    }

    private fun scoreForMover(mover: Side, scoreRed: EngineScore): Int = when (scoreRed) {
        is EngineScore.Centipawns -> if (mover == Side.RED) scoreRed.cp else -scoreRed.cp
        is EngineScore.Mate -> {
            val signed = if (mover == Side.RED) scoreRed.movesToMate else -scoreRed.movesToMate
            if (signed > 0) 30_000 - signed else -30_000 - signed
        }
    }

    private fun isValidSeedPuzzle(entity: PuzzleEntity): Boolean {
        val parsed = FenCodec.parse(entity.initialFen) as? FenParseResult.Success ?: return false
        val accepted = entity.acceptedMoves.split(",").mapNotNull { Move.fromUci(it.trim()) }
        if (accepted.isEmpty()) return false
        return accepted.all { RulesEngine.isMoveLegal(parsed.board, it) }
    }
}
