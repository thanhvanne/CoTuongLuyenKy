package com.thanhvan.cotuongluyenky.domain.commentary

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.rules.ZobristHash

/**
 * Three explicit certainty levels for threatened pieces (Section 14.2).
 * Never blindly labels every attacked piece as a hanging piece ("quân treo").
 */
enum class DangerLevel(
    val vietnameseLabel: String,
    val description: String
) {
    UNDER_ATTACK(
        "Đang bị tấn công",
        "Xác định bằng luật: quân đối thủ hiện có thể ăn hợp lệ ô này."
    ),
    AT_RISK_OF_LOSS(
        "Có nguy cơ mất quân",
        "Quân đang bị tấn công và không có quân bảo vệ hoặc bị quân giá trị nhỏ hơn đe dọa."
    ),
    ENGINE_LOSS_LINE(
        "Engine tìm thấy biến mất quân",
        "Biến minh họa của engine cho thấy quân này có thể bị bắt."
    )
}

data class PieceDangerInfo(
    val square: Square,
    val piece: Piece,
    val level: DangerLevel,
    val attackers: List<Pair<Square, Piece>>,
    val defenders: List<Pair<Square, Piece>>,
    val lossVariation: List<Move> = emptyList()
)

/**
 * Strictly verifiable board & variation facts extracted before generating commentary (Section 12.2–12.4).
 */
data class TacticalFacts(
    val playedMoveGaveCheck: Boolean,
    val playedMoveCaptured: PieceType?,
    val missedForcedMate: Boolean,
    val allowedForcedMate: Boolean,
    val swingFromAdvantageToDisadvantage: Boolean,
    val exposedOwnPieceAtRisk: Pair<Square, PieceType>?,
    val bestMoveGaveCheck: Boolean,
    val bestMoveCaptured: PieceType?,
    val bestMoveSavedPiece: PieceType?,
    val pvFirstCapturedTarget: PieceType?
)

/**
 * Extracts strictly verifiable facts using [RulesEngine] (Section 12.3 & 14.1).
 */
object TacticalFactExtractor {

    /**
     * Lists all enemy pieces that can LEGALLY capture [target] if it were [attackerSide]'s turn.
     */
    fun findAttackersOf(
        board: BoardState,
        target: Square,
        attackerSide: Side
    ): List<Pair<Square, Piece>> {
        val probeBoard = if (board.sideToMove == attackerSide) {
            board
        } else {
            val copy = board.copyArray()
            BoardState.create(
                squares = copy,
                sideToMove = attackerSide,
                halfMoveClock = board.halfMoveClock,
                fullMoveNumber = board.fullMoveNumber,
                positionHash = ZobristHash.computeHash(copy, attackerSide)
            )
        }

        val attackers = mutableListOf<Pair<Square, Piece>>()
        for ((sq, piece) in probeBoard.allPieces(attackerSide)) {
            if (RulesEngine.isMoveLegal(probeBoard, Move(sq, target))) {
                attackers.add(sq to piece)
            }
        }
        return attackers
    }

    /**
     * Lists friendly pieces of [defenderSide] that legally defend [target]
     * (verified by placing a temporary enemy placeholder at [target] and checking legal recapture).
     */
    fun findDefendersOf(
        board: BoardState,
        target: Square,
        defenderSide: Side
    ): List<Pair<Square, Piece>> {
        val original = board.pieceAt(target) ?: return emptyList()
        if (original.side != defenderSide || original.type == PieceType.GENERAL) return emptyList()

        val testSquares = board.copyArray()
        // Replace target temporarily with an opponent Soldier so defenderSide can legally capture it
        testSquares[target.index] = Piece(defenderSide.opposite(), PieceType.SOLDIER)
        val probeBoard = BoardState.create(
            squares = testSquares,
            sideToMove = defenderSide,
            halfMoveClock = board.halfMoveClock,
            fullMoveNumber = board.fullMoveNumber,
            positionHash = ZobristHash.computeHash(testSquares, defenderSide)
        )

        val defenders = mutableListOf<Pair<Square, Piece>>()
        for ((sq, piece) in probeBoard.allPieces(defenderSide)) {
            if (sq == target) continue
            if (RulesEngine.isMoveLegal(probeBoard, Move(sq, target))) {
                defenders.add(sq to piece)
            }
        }
        return defenders
    }

    /**
     * Evaluates all non-General pieces of [side] that are currently under legal attack (Section 14).
     */
    fun analyzeDangerPieces(
        board: BoardState,
        side: Side,
        opponentPv: List<Move> = emptyList()
    ): List<PieceDangerInfo> {
        val enemy = side.opposite()
        val pvCapturedSquares = findCapturedSquaresInPv(board, opponentPv, victimSide = side)
        val result = mutableListOf<PieceDangerInfo>()

        for ((sq, piece) in board.allPieces(side)) {
            if (piece.type == PieceType.GENERAL) continue
            val attackers = findAttackersOf(board, sq, enemy)
            if (attackers.isEmpty()) continue

            val defenders = findDefendersOf(board, sq, side)
            val minAttackerVal = attackers.minOf { it.second.type.baseMaterialValue }
            val isUnprotectedOrOutvalued = defenders.isEmpty() || minAttackerVal < piece.type.baseMaterialValue

            val level = when {
                sq in pvCapturedSquares && opponentPv.isNotEmpty() -> DangerLevel.ENGINE_LOSS_LINE
                isUnprotectedOrOutvalued -> DangerLevel.AT_RISK_OF_LOSS
                else -> DangerLevel.UNDER_ATTACK
            }

            result.add(
                PieceDangerInfo(
                    square = sq,
                    piece = piece,
                    level = level,
                    attackers = attackers,
                    defenders = defenders,
                    lossVariation = if (level == DangerLevel.ENGINE_LOSS_LINE) opponentPv else emptyList()
                )
            )
        }

        return result.sortedByDescending { it.piece.type.baseMaterialValue }
    }

    fun extractMoveFacts(
        boardBefore: BoardState,
        playedMove: Move,
        boardAfter: BoardState,
        bestMove: Move?,
        bestPv: List<Move>,
        missedForcedMate: Boolean,
        allowedForcedMate: Boolean,
        swingFromAdvantageToDisadvantage: Boolean
    ): TacticalFacts {
        val mover = boardBefore.sideToMove
        val playedCaptured = boardBefore.pieceAt(playedMove.to)?.type
        val playedGaveCheck = RulesEngine.isInCheck(boardAfter, mover.opposite())

        // Detect newly exposed valuable piece of mover that is AT_RISK_OF_LOSS after the move
        val riskBefore = analyzeDangerPieces(boardBefore, mover)
            .filter { it.level != DangerLevel.UNDER_ATTACK }
            .map { it.square }
            .toSet()
        val riskAfter = analyzeDangerPieces(boardAfter, mover)
            .filter { it.level != DangerLevel.UNDER_ATTACK }
        val newlyExposed = riskAfter
            .firstOrNull { it.square !in riskBefore || it.square == playedMove.to }
            ?.let { it.square to it.piece.type }

        var bestGaveCheck = false
        var bestCaptured: PieceType? = null
        var bestSaved: PieceType? = null
        if (bestMove != null && RulesEngine.isMoveLegal(boardBefore, bestMove)) {
            bestCaptured = boardBefore.pieceAt(bestMove.to)?.type
            val afterBest = RulesEngine.applyMoveUnchecked(boardBefore, bestMove)
            bestGaveCheck = RulesEngine.isInCheck(afterBest, mover.opposite())

            val movingPiece = boardBefore.pieceAt(bestMove.from)
            if (movingPiece != null && bestMove.from in riskBefore) {
                val stillAttacked = findAttackersOf(afterBest, bestMove.to, mover.opposite()).isNotEmpty()
                if (!stillAttacked) {
                    bestSaved = movingPiece.type
                }
            }
        }

        val pvFirstCapture = findFirstMeaningfulCaptureInPv(boardBefore, bestPv)

        return TacticalFacts(
            playedMoveGaveCheck = playedGaveCheck,
            playedMoveCaptured = playedCaptured,
            missedForcedMate = missedForcedMate,
            allowedForcedMate = allowedForcedMate,
            swingFromAdvantageToDisadvantage = swingFromAdvantageToDisadvantage,
            exposedOwnPieceAtRisk = newlyExposed,
            bestMoveGaveCheck = bestGaveCheck,
            bestMoveCaptured = bestCaptured,
            bestMoveSavedPiece = bestSaved,
            pvFirstCapturedTarget = pvFirstCapture
        )
    }

    private fun findCapturedSquaresInPv(
        startBoard: BoardState,
        pv: List<Move>,
        victimSide: Side
    ): Set<Square> {
        val captured = mutableSetOf<Square>()
        var current = startBoard
        for (move in pv.take(6)) {
            if (!RulesEngine.isMoveLegal(current, move)) break
            val target = current.pieceAt(move.to)
            if (target != null && target.side == victimSide) {
                captured.add(move.to)
            }
            current = RulesEngine.applyMoveUnchecked(current, move)
        }
        return captured
    }

    private fun findFirstMeaningfulCaptureInPv(startBoard: BoardState, pv: List<Move>): PieceType? {
        var current = startBoard
        for (move in pv.take(4)) {
            if (!RulesEngine.isMoveLegal(current, move)) break
            val target = current.pieceAt(move.to)
            if (target != null && target.type != PieceType.SOLDIER) {
                return target.type
            }
            current = RulesEngine.applyMoveUnchecked(current, move)
        }
        return null
    }
}
