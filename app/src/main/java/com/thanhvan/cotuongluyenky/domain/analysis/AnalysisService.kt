package com.thanhvan.cotuongluyenky.domain.analysis

import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.data.database.entity.AnalysisVariationEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveAnalysisEntity
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.domain.commentary.TacticalFactExtractor
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.EngineVariation
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.manager.EngineAnalysisOutcome
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class HintBundle(
    val positionHash: Long,
    val ideaText: String,
    val bestMove: Move,
    val variations: List<EngineVariation>,
    val scoreRed: EngineScore
)

data class LiveMoveEvaluation(
    val plyIndex: Int,
    val positionHashAfter: Long,
    val playedMove: Move,
    val bestMove: Move?,
    val scoreBeforeRed: EngineScore,
    val scoreAfterRed: EngineScore,
    val classification: MoveClassification,
    val commentary: String,
    val variationsFromBefore: List<EngineVariation>,
    val isQuickLiveEval: Boolean = true
)

data class PlyAnalysisModel(
    val plyIndex: Int,
    val scoreBeforeRed: EngineScore?,
    val scoreAfterRed: EngineScore?,
    val bestMove: Move?,
    val playedMove: Move,
    val principalVariation: List<Move>,
    val variations: List<EngineVariation>,
    val classification: MoveClassification,
    val commentary: String,
    val isTurningPoint: Boolean,
    val isQuickLiveEval: Boolean,
    val depth: Int,
    val engineName: String,
    val engineVersion: String
)

data class GameAnalysisProgress(
    val completedPlies: Int,
    val totalPlies: Int,
    val status: AnalysisStatus
)

/**
 * Coordinates live quick evaluation, 4-tier hint generation, and resumable full-game analysis
 * (Sections 9, 10, 13, 21.2).
 */
class AnalysisService(
    private val engineManager: EngineManager,
    private val gameRepository: GameRepository
) {
    private val thresholds = ClassificationThresholds()

    /**
     * Generates a 4-tier hint bundle for the current board (Section 10.1–10.2).
     * Every variation is verified legal by [EngineManager] + [RulesEngine].
     */
    suspend fun computeHint(
        position: PositionWithHistory,
        resourceMode: EngineResourceMode
    ): Result<HintBundle> {
        val limit = hintLimitFor(resourceMode)
        return when (
            val outcome = engineManager.analyzePosition(
                position = position,
                limit = limit,
                variations = 3,
                resourceMode = resourceMode,
                preemptActive = true
            )
        ) {
            is EngineAnalysisOutcome.Success -> {
                val top = outcome.analysis.variations.firstOrNull()
                    ?: return Result.failure(IllegalStateException("Không tìm thấy gợi ý cho thế cờ hiện tại."))
                val bestMove = top.moves.firstOrNull()
                    ?: return Result.failure(IllegalStateException("Biến gợi ý trống."))
                val idea = CommentaryService.buildTier1IdeaHint(position.currentBoard, bestMove)
                val redScore = top.score.toRedPerspective(position.currentBoard.sideToMove)
                Result.success(
                    HintBundle(
                        positionHash = position.currentBoard.positionHash,
                        ideaText = idea,
                        bestMove = bestMove,
                        variations = outcome.analysis.variations,
                        scoreRed = redScore
                    )
                )
            }
            is EngineAnalysisOutcome.Cancelled ->
                Result.failure(CancellationException("Gợi ý đã bị hủy."))
            is EngineAnalysisOutcome.Failure ->
                Result.failure(IllegalStateException(outcome.message, outcome.cause))
        }
    }

    /**
     * Performs a lightweight "Đánh giá nhanh" after a player's move during a live game (Section 9.2–9.3).
     */
    suspend fun evaluateMoveLive(
        initialFen: String,
        movesBefore: List<MoveRecord>,
        boardBefore: BoardState,
        playedRecord: MoveRecord,
        boardAfter: BoardState,
        resourceMode: EngineResourceMode,
        notationStyle: NotationStyle
    ): LiveMoveEvaluation? {
        val quickLimit = quickEvalLimitFor(resourceMode)

        val beforeOutcome = engineManager.analyzePosition(
            position = PositionWithHistory(initialFen, boardBefore, movesBefore),
            limit = quickLimit,
            variations = 2,
            resourceMode = resourceMode,
            preemptActive = false
        )
        if (beforeOutcome !is EngineAnalysisOutcome.Success) return null
        val beforeTop = beforeOutcome.analysis.variations.firstOrNull() ?: return null
        val scoreBeforeRed = beforeTop.score.toRedPerspective(boardBefore.sideToMove)
        val bestMove = beforeTop.moves.firstOrNull()

        // If player played the best move, reuse before score directly to stay fast & responsive
        val scoreAfterRed = if (bestMove == playedRecord.move) {
            scoreBeforeRed
        } else {
            val afterOutcome = engineManager.analyzePosition(
                position = PositionWithHistory(initialFen, boardAfter, movesBefore + playedRecord),
                limit = quickLimit,
                variations = 1,
                resourceMode = resourceMode,
                preemptActive = false
            )
            if (afterOutcome !is EngineAnalysisOutcome.Success) return null
            val afterTop = afterOutcome.analysis.variations.firstOrNull()
            afterTop?.score?.toRedPerspective(boardAfter.sideToMove)
                ?: inferTerminalRedScore(boardAfter)
        }

        val outcome = MoveClassifier.classify(
            movingSide = playedRecord.side,
            playedMove = playedRecord.move,
            bestMove = bestMove,
            scoreBeforeRed = scoreBeforeRed,
            scoreAfterRed = scoreAfterRed,
            thresholds = thresholds
        )

        val facts = TacticalFactExtractor.extractMoveFacts(
            boardBefore = boardBefore,
            playedMove = playedRecord.move,
            boardAfter = boardAfter,
            bestMove = bestMove,
            bestPv = beforeTop.moves,
            missedForcedMate = outcome.missedForcedMate,
            allowedForcedMate = outcome.allowedForcedMate,
            swingFromAdvantageToDisadvantage = outcome.swingFromAdvantageToDisadvantage
        )

        val commentary = CommentaryService.buildMoveCommentary(
            boardBefore = boardBefore,
            classification = outcome.classification,
            facts = facts,
            bestMove = bestMove,
            notationStyle = notationStyle
        )

        return LiveMoveEvaluation(
            plyIndex = playedRecord.plyIndex,
            positionHashAfter = boardAfter.positionHash,
            playedMove = playedRecord.move,
            bestMove = bestMove,
            scoreBeforeRed = scoreBeforeRed,
            scoreAfterRed = scoreAfterRed,
            classification = outcome.classification,
            commentary = commentary,
            variationsFromBefore = beforeOutcome.analysis.variations,
            isQuickLiveEval = true
        )
    }

    /**
     * Full post-game analysis with per-ply Room persistence and seamless resume after interruption
     * (Sections 6.7 & 13.2–13.3).
     */
    suspend fun analyzeFullGame(
        gameId: Long,
        resourceMode: EngineResourceMode,
        notationStyle: NotationStyle,
        forceReanalyze: Boolean = false,
        onProgress: (GameAnalysisProgress) -> Unit = {}
    ): Result<Unit> {
        val persisted = gameRepository.loadGame(gameId)
            ?: return Result.failure(IllegalArgumentException("Không tìm thấy ván đấu để phân tích."))

        val reconstructed = RulesEngine.reconstructGame(
            initialFen = persisted.entity.initialFen,
            movesUci = persisted.moves.map { it.move.toUci() }
        ).getOrElse { return Result.failure(it) }

        val totalPlies = reconstructed.moves.size
        if (totalPlies == 0) {
            gameRepository.updateAnalysisStatus(gameId, AnalysisStatus.COMPLETED)
            onProgress(GameAnalysisProgress(0, 0, AnalysisStatus.COMPLETED))
            return Result.success(Unit)
        }

        if (forceReanalyze) {
            gameRepository.clearGameAnalysis(gameId)
        }

        val existingByPly = gameRepository.loadMoveAnalyses(gameId)
            .filter { !it.isQuickLiveEval }
            .associateBy { it.plyIndex }

        gameRepository.updateAnalysisStatus(gameId, AnalysisStatus.IN_PROGRESS)
        onProgress(GameAnalysisProgress(existingByPly.size, totalPlies, AnalysisStatus.IN_PROGRESS))

        // Build board sequence: boards[0] = initial, boards[k+1] = after move k
        val boards = ArrayList<BoardState>(totalPlies + 1)
        boards.add(reconstructed.initialBoard)
        var cur = reconstructed.initialBoard
        for (moveRec in reconstructed.moves) {
            cur = RulesEngine.applyMoveUnchecked(cur, moveRec.move)
            boards.add(cur)
        }

        val limit = postGameLimitFor(resourceMode)
        val configLabel = "postgame-${resourceMode.name.lowercase()}-d${limit.maxDepth}-t${limit.moveTimeMs}-${thresholds.configVersion}"

        // Cache evaluation of board[i] to halve engine calls (after[i-1] == before[i]!)
        val boardEvalCache = HashMap<Int, Pair<EngineScore, List<EngineVariation>>>()

        try {
            var completedCount = existingByPly.size
            for (ply in 0 until totalPlies) {
                currentCoroutineContext().ensureActive()
                if (!forceReanalyze && ply in existingByPly) {
                    continue
                }

                val moveRec = reconstructed.moves[ply]
                val boardBefore = boards[ply]
                val boardAfter = boards[ply + 1]

                // Evaluate boardBefore (with MultiPV = 3)
                val beforeEval = boardEvalCache[ply] ?: run {
                    val res = engineManager.analyzePosition(
                        position = PositionWithHistory(
                            initialFen = persisted.entity.initialFen,
                            currentBoard = boardBefore,
                            moves = reconstructed.moves.subList(0, ply)
                        ),
                        limit = limit,
                        variations = 3,
                        resourceMode = resourceMode,
                        preemptActive = true
                    )
                    when (res) {
                        is EngineAnalysisOutcome.Success -> {
                            val top = res.analysis.variations.firstOrNull()
                            val scoreRed = top?.score?.toRedPerspective(boardBefore.sideToMove)
                                ?: inferTerminalRedScore(boardBefore)
                            val pair = scoreRed to res.analysis.variations
                            boardEvalCache[ply] = pair
                            pair
                        }
                        is EngineAnalysisOutcome.Cancelled -> throw CancellationException("Phân tích bị dừng.")
                        is EngineAnalysisOutcome.Failure -> throw IllegalStateException(res.message, res.cause)
                    }
                }

                // Evaluate boardAfter (will also serve as boardBefore for ply + 1!)
                val afterEval = boardEvalCache[ply + 1] ?: run {
                    if (!RulesEngine.hasAnyLegalMove(boardAfter)) {
                        val termScore = inferTerminalRedScore(boardAfter)
                        val pair = termScore to emptyList<EngineVariation>()
                        boardEvalCache[ply + 1] = pair
                        pair
                    } else {
                        val res = engineManager.analyzePosition(
                            position = PositionWithHistory(
                                initialFen = persisted.entity.initialFen,
                                currentBoard = boardAfter,
                                moves = reconstructed.moves.subList(0, ply + 1)
                            ),
                            limit = limit,
                            variations = if (ply + 1 < totalPlies) 3 else 1,
                            resourceMode = resourceMode,
                            preemptActive = true
                        )
                        when (res) {
                            is EngineAnalysisOutcome.Success -> {
                                val top = res.analysis.variations.firstOrNull()
                                val scoreRed = top?.score?.toRedPerspective(boardAfter.sideToMove)
                                    ?: inferTerminalRedScore(boardAfter)
                                val pair = scoreRed to res.analysis.variations
                                boardEvalCache[ply + 1] = pair
                                pair
                            }
                            is EngineAnalysisOutcome.Cancelled -> throw CancellationException("Phân tích bị dừng.")
                            is EngineAnalysisOutcome.Failure -> throw IllegalStateException(res.message, res.cause)
                        }
                    }
                }

                val scoreBeforeRed = beforeEval.first
                val variationsBefore = beforeEval.second
                val scoreAfterRed = afterEval.first
                val bestVariation = variationsBefore.firstOrNull()
                val bestMove = bestVariation?.moves?.firstOrNull()

                val classified = MoveClassifier.classify(
                    movingSide = moveRec.side,
                    playedMove = moveRec.move,
                    bestMove = bestMove,
                    scoreBeforeRed = scoreBeforeRed,
                    scoreAfterRed = scoreAfterRed,
                    thresholds = thresholds
                )

                val facts = TacticalFactExtractor.extractMoveFacts(
                    boardBefore = boardBefore,
                    playedMove = moveRec.move,
                    boardAfter = boardAfter,
                    bestMove = bestMove,
                    bestPv = bestVariation?.moves.orEmpty(),
                    missedForcedMate = classified.missedForcedMate,
                    allowedForcedMate = classified.allowedForcedMate,
                    swingFromAdvantageToDisadvantage = classified.swingFromAdvantageToDisadvantage
                )

                val commentary = CommentaryService.buildMoveCommentary(
                    boardBefore = boardBefore,
                    classification = classified.classification,
                    facts = facts,
                    bestMove = bestMove,
                    notationStyle = notationStyle
                )

                val engineInfo = engineManager.activeEngineInfo
                val entity = MoveAnalysisEntity(
                    gameId = gameId,
                    plyIndex = ply,
                    engineName = engineInfo.name,
                    engineVersion = engineInfo.version,
                    analysisConfig = configLabel,
                    scoreBeforeCp = (scoreBeforeRed as? EngineScore.Centipawns)?.cp,
                    scoreAfterCp = (scoreAfterRed as? EngineScore.Centipawns)?.cp,
                    scorePerspective = "RED",
                    mateBefore = (scoreBeforeRed as? EngineScore.Mate)?.movesToMate,
                    mateAfter = (scoreAfterRed as? EngineScore.Mate)?.movesToMate,
                    bestMove = bestMove?.toUci(),
                    playedMove = moveRec.move.toUci(),
                    principalVariation = bestVariation?.moves?.joinToString(" ") { it.toUci() }.orEmpty(),
                    depth = bestVariation?.depth ?: limit.maxDepth,
                    searchTimeMs = limit.moveTimeMs,
                    classification = classified.classification.name,
                    commentary = commentary,
                    isTurningPoint = false,
                    isQuickLiveEval = false,
                    analysisVersion = 1
                )

                val variationEntities = variationsBefore.map { v ->
                    val vRed = v.score.toRedPerspective(boardBefore.sideToMove)
                    AnalysisVariationEntity(
                        analysisId = 0L,
                        rank = v.rank,
                        scoreCp = (vRed as? EngineScore.Centipawns)?.cp,
                        mateIn = (vRed as? EngineScore.Mate)?.movesToMate,
                        moves = v.moves.joinToString(" ") { it.toUci() }
                    )
                }

                gameRepository.saveMoveAnalysis(entity, variationEntities)
                completedCount++
                onProgress(GameAnalysisProgress(completedCount, totalPlies, AnalysisStatus.IN_PROGRESS))
            }

            // Compute & persist turning points across all analyzed plies
            val allRows = gameRepository.loadMoveAnalyses(gameId)
            val turningPlies = MoveClassifier.detectTurningPoints(
                plyScores = allRows.map { row ->
                    Triple(
                        row.plyIndex,
                        decodeScore(row.scoreBeforeCp, row.mateBefore),
                        decodeScore(row.scoreAfterCp, row.mateAfter)
                    )
                },
                thresholds = thresholds
            )
            gameRepository.updateTurningPoints(gameId, turningPlies)
            gameRepository.updateAnalysisStatus(gameId, AnalysisStatus.COMPLETED)
            onProgress(GameAnalysisProgress(totalPlies, totalPlies, AnalysisStatus.COMPLETED))
            return Result.success(Unit)
        } catch (ce: CancellationException) {
            val savedCount = gameRepository.loadMoveAnalyses(gameId).count { !it.isQuickLiveEval }
            val nextStatus = if (savedCount > 0) AnalysisStatus.PARTIAL else AnalysisStatus.INTERRUPTED
            gameRepository.updateAnalysisStatus(gameId, nextStatus)
            onProgress(GameAnalysisProgress(savedCount, totalPlies, nextStatus))
            throw ce
        } catch (t: Throwable) {
            val savedCount = gameRepository.loadMoveAnalyses(gameId).count { !it.isQuickLiveEval }
            gameRepository.updateAnalysisStatus(gameId, AnalysisStatus.ERROR)
            onProgress(GameAnalysisProgress(savedCount, totalPlies, AnalysisStatus.ERROR))
            return Result.failure(t)
        }
    }

    /**
     * Loads persisted move analyses + multi-PV variations into domain models.
     */
    suspend fun loadGameAnalysisModels(gameId: Long): Map<Int, PlyAnalysisModel> {
        val rows = gameRepository.loadMoveAnalyses(gameId)
        if (rows.isEmpty()) return emptyMap()
        val variationsMap = gameRepository.loadVariationsForAnalyses(rows.map { it.id })

        return rows.associate { row ->
            val played = Move.fromUci(row.playedMove) ?: Move(
                com.thanhvan.cotuongluyenky.core.model.Square(0, 0),
                com.thanhvan.cotuongluyenky.core.model.Square(0, 1)
            )
            val best = row.bestMove?.let { Move.fromUci(it) }
            val pvMoves = row.principalVariation
                .split(' ')
                .mapNotNull { Move.fromUci(it) }
            val multiVars = variationsMap[row.id].orEmpty().mapNotNull { v ->
                val vMoves = v.moves.split(' ').mapNotNull { Move.fromUci(it) }
                if (vMoves.isEmpty()) null else EngineVariation(
                    rank = v.rank,
                    score = decodeScore(v.scoreCp, v.mateIn) ?: EngineScore.Centipawns(0),
                    depth = row.depth,
                    moves = vMoves
                )
            }
            val classification = runCatching { MoveClassification.valueOf(row.classification) }
                .getOrDefault(MoveClassification.ACCEPTABLE)

            row.plyIndex to PlyAnalysisModel(
                plyIndex = row.plyIndex,
                scoreBeforeRed = decodeScore(row.scoreBeforeCp, row.mateBefore),
                scoreAfterRed = decodeScore(row.scoreAfterCp, row.mateAfter),
                bestMove = best,
                playedMove = played,
                principalVariation = pvMoves,
                variations = multiVars,
                classification = classification,
                commentary = row.commentary,
                isTurningPoint = row.isTurningPoint,
                isQuickLiveEval = row.isQuickLiveEval,
                depth = row.depth,
                engineName = row.engineName,
                engineVersion = row.engineVersion
            )
        }
    }

    private fun inferTerminalRedScore(board: BoardState): EngineScore {
        if (!RulesEngine.hasAnyLegalMove(board)) {
            return if (board.sideToMove == com.thanhvan.cotuongluyenky.core.model.Side.RED) {
                EngineScore.Mate(-1)
            } else {
                EngineScore.Mate(1)
            }
        }
        return EngineScore.Centipawns(0)
    }

    private fun decodeScore(cp: Int?, mate: Int?): EngineScore? = when {
        mate != null -> EngineScore.Mate(mate)
        cp != null -> EngineScore.Centipawns(cp)
        else -> null
    }

    private fun quickEvalLimitFor(mode: EngineResourceMode): SearchLimit = when (mode) {
        EngineResourceMode.ECO -> SearchLimit(moveTimeMs = 180L, maxDepth = 2)
        EngineResourceMode.BALANCED -> SearchLimit(moveTimeMs = 280L, maxDepth = 3)
        EngineResourceMode.DEEP -> SearchLimit(moveTimeMs = 450L, maxDepth = 4)
    }

    private fun hintLimitFor(mode: EngineResourceMode): SearchLimit = when (mode) {
        EngineResourceMode.ECO -> SearchLimit(moveTimeMs = 350L, maxDepth = 3)
        EngineResourceMode.BALANCED -> SearchLimit(moveTimeMs = 600L, maxDepth = 4)
        EngineResourceMode.DEEP -> SearchLimit(moveTimeMs = 1100L, maxDepth = 5)
    }

    private fun postGameLimitFor(mode: EngineResourceMode): SearchLimit = when (mode) {
        EngineResourceMode.ECO -> SearchLimit(moveTimeMs = 240L, maxDepth = 3)
        EngineResourceMode.BALANCED -> SearchLimit(moveTimeMs = 420L, maxDepth = 4)
        EngineResourceMode.DEEP -> SearchLimit(moveTimeMs = 850L, maxDepth = 5)
    }
}
