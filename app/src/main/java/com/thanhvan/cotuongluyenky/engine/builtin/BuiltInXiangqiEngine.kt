package com.thanhvan.cotuongluyenky.engine.builtin

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineAnalysis
import com.thanhvan.cotuongluyenky.engine.api.EngineConfig
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.api.EngineMoveResult
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.EngineVariation
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.api.XiangqiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure-Kotlin offline Xiangqi search engine (Iterative Deepening Negamax + Alpha-Beta +
 * Quiescence + Xiangqi Piece-Square Tables + MultiPV root ordering).
 *
 * Used automatically whenever the native Pikafish binary/NNUE asset is not packaged yet,
 * ensuring the app plays stably offline on every Android 10+ device with 100% legal moves.
 */
class BuiltInXiangqiEngine : XiangqiEngine {

    override val info: EngineInfo = EngineInfo(
        name = "Luyện Kỳ Core",
        version = "1.0.0-alpha-beta",
        isNativePikafish = false
    )

    private val stopRequested = AtomicBoolean(false)
    private var nodeCount: Long = 0L
    private var deadlineMs: Long = Long.MAX_VALUE
    private var maxNodesLimit: Long = Long.MAX_VALUE

    override suspend fun initialize(config: EngineConfig) {
        stopRequested.set(false)
    }

    override suspend fun findBestMove(
        position: PositionWithHistory,
        limit: SearchLimit
    ): EngineMoveResult {
        val analysis = analyze(position, limit, variations = 1)
        val best = analysis.variations.firstOrNull()
            ?: error("Không còn nước đi hợp lệ cho thế cờ hiện tại.")
        return EngineMoveResult(
            bestMove = best.moves.first(),
            score = best.score,
            depth = analysis.depth,
            nodes = analysis.nodes,
            elapsedMs = analysis.elapsedMs,
            principalVariation = best.moves,
            engineInfo = info
        )
    }

    override suspend fun analyze(
        position: PositionWithHistory,
        limit: SearchLimit,
        variations: Int
    ): EngineAnalysis = withContext(Dispatchers.Default) {
        stopRequested.set(false)
        nodeCount = 0L
        val startMs = System.currentTimeMillis()
        deadlineMs = if (limit.moveTimeMs > 0L) startMs + limit.moveTimeMs else Long.MAX_VALUE
        maxNodesLimit = limit.maxNodes ?: Long.MAX_VALUE

        val board = position.currentBoard
        val legalMoves = RulesEngine.generateLegalMoves(board)
        if (legalMoves.isEmpty()) {
            return@withContext EngineAnalysis(
                variations = emptyList(),
                depth = 0,
                nodes = 0L,
                elapsedMs = System.currentTimeMillis() - startMs,
                engineInfo = info
            )
        }

        val recentHashes = buildHistoryHashCounts(position)
        val targetVariations = variations.coerceIn(1, legalMoves.size)
        val targetDepth = limit.maxDepth.coerceIn(1, 8)

        // Seed ordered moves with static ordering so even depth-0 interruption has a valid legal fallback
        var orderedMoves = orderMoves(board, legalMoves, null)
        var bestVariations: List<EngineVariation> = orderedMoves.take(targetVariations).mapIndexed { idx, mv ->
            EngineVariation(
                rank = idx + 1,
                score = EngineScore.Centipawns(0),
                depth = 0,
                moves = listOf(mv)
            )
        }
        var completedDepth = 0

        for (depth in 1..targetDepth) {
            currentCoroutineContext().ensureActive()
            if (shouldAbort()) break

            val scoredRoots = ArrayList<ScoredRootMove>(orderedMoves.size)
            var abortedThisDepth = false

            for (move in orderedMoves) {
                if (shouldAbort()) {
                    abortedThisDepth = true
                    break
                }
                val nextBoard = RulesEngine.applyMoveUnchecked(board, move)
                val nextCount = (recentHashes[nextBoard.positionHash] ?: 0) + 1
                val pvTail = ArrayList<Move>(depth)
                val rawScore = if (nextCount >= 3) {
                    // Penalize 3-fold repetition slightly when ahead so AI doesn't loop needlessly
                    0
                } else {
                    recentHashes[nextBoard.positionHash] = nextCount
                    val childScore = -negamax(
                        board = nextBoard,
                        depth = depth - 1,
                        alpha = -INF,
                        beta = INF,
                        ply = 1,
                        historyHashes = recentHashes,
                        pvOut = pvTail
                    )
                    if (nextCount == 1) recentHashes.remove(nextBoard.positionHash)
                    else recentHashes[nextBoard.positionHash] = nextCount - 1
                    childScore
                }

                if (shouldAbort()) {
                    abortedThisDepth = true
                    break
                }

                val fullPv = ArrayList<Move>(pvTail.size + 1)
                fullPv.add(move)
                fullPv.addAll(pvTail)
                scoredRoots.add(ScoredRootMove(move, rawScore, fullPv))
            }

            if (!abortedThisDepth && scoredRoots.isNotEmpty()) {
                scoredRoots.sortByDescending { it.score }
                orderedMoves = scoredRoots.map { it.move }
                completedDepth = depth
                bestVariations = scoredRoots.take(targetVariations).mapIndexed { idx, item ->
                    EngineVariation(
                        rank = idx + 1,
                        score = toEngineScore(item.score),
                        depth = depth,
                        moves = validatePv(board, item.pv)
                    )
                }

                // Stop iterative deepening early if a forced mate is already found for the top line
                if (targetVariations == 1 && scoredRoots.first().score >= MATE_THRESHOLD) {
                    break
                }
            } else {
                break
            }
        }

        EngineAnalysis(
            variations = bestVariations,
            depth = completedDepth,
            nodes = nodeCount,
            elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
            engineInfo = info
        )
    }

    override suspend fun stop() {
        stopRequested.set(true)
    }

    override suspend fun close() {
        stopRequested.set(true)
    }

    private fun shouldAbort(): Boolean {
        if (stopRequested.get()) return true
        if ((nodeCount and 255L) == 0L) {
            if (System.currentTimeMillis() >= deadlineMs || nodeCount >= maxNodesLimit) {
                stopRequested.set(true)
                return true
            }
        }
        return false
    }

    private fun negamax(
        board: BoardState,
        depth: Int,
        alpha: Int,
        beta: Int,
        ply: Int,
        historyHashes: HashMap<Long, Int>,
        pvOut: MutableList<Move>
    ): Int {
        nodeCount++
        if (shouldAbort()) return 0

        if (depth <= 0) {
            return quiescence(board, alpha, beta, qDepth = 0)
        }

        val legalMoves = RulesEngine.generateLegalMoves(board)
        if (legalMoves.isEmpty()) {
            // Checkmate or stalemate: both lose in Xiangqi!
            return -MATE_SCORE + ply
        }

        val ordered = orderMoves(board, legalMoves, null)
        var bestAlpha = alpha
        val childPv = ArrayList<Move>(depth)

        for (move in ordered) {
            if (shouldAbort()) return 0
            val nextBoard = RulesEngine.applyMoveUnchecked(board, move)
            val count = (historyHashes[nextBoard.positionHash] ?: 0) + 1
            childPv.clear()

            val score = if (count >= 3) {
                0
            } else {
                historyHashes[nextBoard.positionHash] = count
                val s = -negamax(
                    board = nextBoard,
                    depth = depth - 1,
                    alpha = -beta,
                    beta = -bestAlpha,
                    ply = ply + 1,
                    historyHashes = historyHashes,
                    pvOut = childPv
                )
                if (count == 1) historyHashes.remove(nextBoard.positionHash)
                else historyHashes[nextBoard.positionHash] = count - 1
                s
            }

            if (score >= beta) {
                return beta
            }
            if (score > bestAlpha) {
                bestAlpha = score
                pvOut.clear()
                pvOut.add(move)
                pvOut.addAll(childPv)
            }
        }
        return bestAlpha
    }

    private fun quiescence(
        board: BoardState,
        alpha: Int,
        beta: Int,
        qDepth: Int
    ): Int {
        nodeCount++
        if (shouldAbort()) return 0

        val standPat = evaluateBoard(board)
        if (standPat >= beta) return beta
        var bestAlpha = if (standPat > alpha) standPat else alpha

        if (qDepth >= MAX_QUIESCENCE_DEPTH) return bestAlpha

        val legalMoves = RulesEngine.generateLegalMoves(board)
        if (legalMoves.isEmpty()) {
            return -MATE_SCORE + 32
        }

        val captures = legalMoves.filter { board.pieceAt(it.to) != null }
        if (captures.isEmpty()) return bestAlpha

        val orderedCaptures = orderMoves(board, captures, null)
        for (move in orderedCaptures) {
            if (shouldAbort()) return 0
            val nextBoard = RulesEngine.applyMoveUnchecked(board, move)
            val score = -quiescence(nextBoard, -beta, -bestAlpha, qDepth + 1)
            if (score >= beta) return beta
            if (score > bestAlpha) bestAlpha = score
        }
        return bestAlpha
    }

    private fun orderMoves(
        board: BoardState,
        moves: List<Move>,
        preferredMove: Move?
    ): List<Move> {
        return moves.sortedByDescending { move ->
            if (move == preferredMove) return@sortedByDescending 100_000
            val attacker = board.pieceAt(move.from)
            val victim = board.pieceAt(move.to)
            var score = 0
            if (victim != null && attacker != null) {
                // MVV-LVA capture ordering
                score += 10_000 + victim.type.baseMaterialValue * 10 - attacker.type.baseMaterialValue
            }
            if (attacker != null) {
                score += pieceSquareBonus(attacker, move.to) - pieceSquareBonus(attacker, move.from)
            }
            score
        }
    }

    private fun evaluateBoard(board: BoardState): Int {
        var redScore = 0
        var blackScore = 0

        for (idx in 0 until Square.BOARD_SQUARES) {
            val piece = board.pieceAtIndex(idx) ?: continue
            val sq = Square.fromIndex(idx)
            val value = piece.type.baseMaterialValue + pieceSquareBonus(piece, sq)
            if (piece.side == Side.RED) {
                redScore += value
            } else {
                blackScore += value
            }
        }

        val diff = redScore - blackScore
        return if (board.sideToMove == Side.RED) diff else -diff
    }

    private fun pieceSquareBonus(piece: Piece, square: Square): Int {
        // Normalize rank so 0 = own back rank, 9 = opponent back rank
        val ownRank = if (piece.side == Side.RED) square.rank else 9 - square.rank
        val file = square.file
        val idx = ownRank * 9 + file
        return when (piece.type) {
            PieceType.SOLDIER -> PST_SOLDIER[idx]
            PieceType.HORSE -> PST_HORSE[idx]
            PieceType.CANNON -> PST_CANNON[idx]
            PieceType.CHARIOT -> PST_CHARIOT[idx]
            PieceType.ADVISOR -> PST_ADVISOR[idx]
            PieceType.ELEPHANT -> PST_ELEPHANT[idx]
            PieceType.GENERAL -> PST_GENERAL[idx]
        }
    }

    private fun buildHistoryHashCounts(position: PositionWithHistory): HashMap<Long, Int> {
        val map = HashMap<Long, Int>()
        for (record in position.moves) {
            map[record.positionHashAfter] = (map[record.positionHashAfter] ?: 0) + 1
        }
        return map
    }

    private fun validatePv(startBoard: BoardState, pv: List<Move>): List<Move> {
        val valid = ArrayList<Move>(pv.size)
        var current = startBoard
        for (move in pv) {
            if (!RulesEngine.isMoveLegal(current, move)) break
            valid.add(move)
            current = RulesEngine.applyMoveUnchecked(current, move)
        }
        return valid
    }

    private fun toEngineScore(raw: Int): EngineScore {
        return when {
            raw >= MATE_THRESHOLD -> {
                val plies = (MATE_SCORE - raw).coerceAtLeast(1)
                EngineScore.Mate((plies + 1) / 2)
            }
            raw <= -MATE_THRESHOLD -> {
                val plies = (MATE_SCORE + raw).coerceAtLeast(1)
                EngineScore.Mate(-((plies + 1) / 2))
            }
            else -> EngineScore.Centipawns(raw)
        }
    }

    private data class ScoredRootMove(
        val move: Move,
        val score: Int,
        val pv: List<Move>
    )

    companion object {
        private const val INF = 1_000_000
        private const val MATE_SCORE = 500_000
        private const val MATE_THRESHOLD = 490_000
        private const val MAX_QUIESCENCE_DEPTH = 4

        // Piece-Square Tables indexed by ownRank (0..9, 0 = own back rank) * 9 + file (0..8)
        private val PST_SOLDIER = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 10, 0, 0, 0, 0,
            5, 0, 10, 0, 18, 0, 10, 0, 5,
            25, 35, 45, 55, 65, 55, 45, 35, 25,
            40, 55, 70, 85, 95, 85, 70, 55, 40,
            55, 75, 95, 110, 120, 110, 95, 75, 55,
            45, 70, 95, 115, 120, 115, 95, 70, 45,
            10, 20, 30, 40, 45, 40, 30, 20, 10
        )

        private val PST_HORSE = intArrayOf(
            -10, -5, 0, 0, 0, 0, 0, -5, -10,
            -5, 5, 10, 12, 5, 12, 10, 5, -5,
            0, 12, 22, 20, 25, 20, 22, 12, 0,
            5, 18, 25, 28, 30, 28, 25, 18, 5,
            8, 20, 30, 34, 36, 34, 30, 20, 8,
            10, 25, 35, 40, 42, 40, 35, 25, 10,
            12, 28, 40, 46, 48, 46, 40, 28, 12,
            10, 24, 38, 44, 42, 44, 38, 24, 10,
            5, 18, 30, 34, 30, 34, 30, 18, 5,
            0, 8, 15, 18, 15, 18, 15, 8, 0
        )

        private val PST_CANNON = intArrayOf(
            0, 5, 8, 12, 14, 12, 8, 5, 0,
            0, 5, 10, 12, 16, 12, 10, 5, 0,
            4, 12, 14, 18, 32, 18, 14, 12, 4,
            4, 10, 14, 16, 26, 16, 14, 10, 4,
            6, 12, 16, 18, 24, 18, 16, 12, 6,
            6, 12, 16, 20, 24, 20, 16, 12, 6,
            8, 14, 18, 22, 26, 22, 18, 14, 8,
            10, 16, 20, 24, 28, 24, 20, 16, 10,
            8, 14, 18, 20, 22, 20, 18, 14, 8,
            6, 10, 14, 16, 16, 16, 14, 10, 6
        )

        private val PST_CHARIOT = intArrayOf(
            -5, 10, 8, 16, 12, 16, 8, 10, -5,
            5, 16, 14, 22, 18, 22, 14, 16, 5,
            8, 18, 16, 24, 20, 24, 16, 18, 8,
            12, 22, 20, 28, 24, 28, 20, 22, 12,
            16, 26, 24, 32, 28, 32, 24, 26, 16,
            18, 28, 28, 36, 32, 36, 28, 28, 18,
            20, 32, 32, 40, 36, 40, 32, 32, 20,
            22, 34, 34, 42, 38, 42, 34, 34, 22,
            20, 34, 34, 40, 38, 40, 34, 34, 20,
            16, 28, 28, 34, 30, 34, 28, 28, 16
        )

        private val PST_ADVISOR = intArrayOf(
            0, 0, 0, 10, 0, 10, 0, 0, 0,
            0, 0, 0, 0, 22, 0, 0, 0, 0,
            0, 0, 0, 8, 0, 8, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        private val PST_ELEPHANT = intArrayOf(
            0, 0, 8, 0, 0, 0, 8, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            6, 0, 0, 0, 22, 0, 0, 0, 6,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 10, 0, 0, 0, 10, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        private val PST_GENERAL = intArrayOf(
            0, 0, 0, 8, 16, 8, 0, 0, 0,
            0, 0, 0, 4, 8, 4, 0, 0, 0,
            0, 0, 0, -4, 0, -4, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0
        )
    }
}
