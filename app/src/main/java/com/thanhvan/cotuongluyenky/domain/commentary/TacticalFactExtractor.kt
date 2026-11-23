package com.thanhvan.cotuongluyenky.domain.commentary

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.data.repository.PuzzleTheme
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
 * Grounded fork (bắt đôi) detection result (Phase 3 — Section 24.6 & 26).
 */
data class ForkInfo(
    val attackerSquare: Square,
    val attackerType: PieceType,
    val targets: List<Pair<Square, PieceType>>
)

/**
 * Grounded cannon-screen event (Pháo có/mất ngòi, Phase 3 — Section 24.6 & 26).
 */
enum class CannonScreenEventType {
    OWN_CANNON_LOST_SCREEN,
    OPPONENT_CANNON_GAINED_SCREEN_ON_VALUABLE
}

data class CannonScreenEvent(
    val type: CannonScreenEventType,
    val cannonSquare: Square,
    val affectedTargetType: PieceType
)

/**
 * Grounded blocked-horse detection (Mã bị cản chân, Phase 3 — Section 24.6 & 26).
 */
data class BlockedHorseInfo(
    val horseSquare: Square,
    val blockedDirections: Int,
    val legalMoveCount: Int
)

/**
 * Strictly verifiable board & variation facts extracted before generating commentary (Section 12.2–12.4 & 24.6).
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
    val pvFirstCapturedTarget: PieceType?,
    // Phase 3 grounded advanced tactical facts (default null so Phase 2 callers/tests remain compatible)
    val playedMoveFork: ForkInfo? = null,
    val bestMoveFork: ForkInfo? = null,
    val cannonScreenEvent: CannonScreenEvent? = null,
    val playedHorseBlocked: BlockedHorseInfo? = null
)

/**
 * Extracts strictly verifiable facts using [RulesEngine] (Section 12.3, 14.1, 24.6).
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
        val probeBoard = withSideToMove(board, attackerSide)
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

    /**
     * Detects a grounded double attack (bắt đôi) created by [move] on [boardBefore].
     * A fork is verified only if:
     * 1) The moved piece at `move.to` is NOT immediately lost for free (or gives check), AND
     * 2) From `move.to`, it legally attacks >= 2 distinct valuable/undefended enemy pieces (including General).
     */
    fun detectFork(boardBefore: BoardState, move: Move): ForkInfo? {
        if (!RulesEngine.isMoveLegal(boardBefore, move)) return null
        val mover = boardBefore.sideToMove
        val enemy = mover.opposite()
        val movedPiece = boardBefore.pieceAt(move.from) ?: return null
        val boardAfter = RulesEngine.applyMoveUnchecked(boardBefore, move)

        // Probe from mover's perspective on boardAfter to see what movedPiece legally threatens
        val moverProbe = withSideToMove(boardAfter, mover)
        val threatenedTargets = mutableListOf<Pair<Square, PieceType>>()

        for ((sq, targetPiece) in moverProbe.allPieces(enemy)) {
            if (!RulesEngine.isMoveLegal(moverProbe, Move(move.to, sq))) continue
            val isGeneral = targetPiece.type == PieceType.GENERAL
            val isValuable = targetPiece.type in setOf(PieceType.CHARIOT, PieceType.HORSE, PieceType.CANNON)
            val isUndefended = !isGeneral && findDefendersOf(boardAfter, sq, enemy).isEmpty()
            val outvaluesAttacker = targetPiece.type.baseMaterialValue > movedPiece.type.baseMaterialValue
            if (isGeneral || isValuable || isUndefended || outvaluesAttacker) {
                threatenedTargets.add(sq to targetPiece.type)
            }
        }

        if (threatenedTargets.size < 2) return null

        // Verify the forking piece isn't simply hanging to a lower-value or undefended immediate capture
        // unless one of the targets is the enemy General (check-fork)
        val givesCheck = threatenedTargets.any { it.second == PieceType.GENERAL }
        if (!givesCheck) {
            val enemyAttackers = findAttackersOf(boardAfter, move.to, enemy)
            val ownDefenders = findDefendersOf(boardAfter, move.to, mover)
            if (enemyAttackers.isNotEmpty()) {
                val minEnemyAttacker = enemyAttackers.minOf { it.second.type.baseMaterialValue }
                if (ownDefenders.isEmpty() || minEnemyAttacker < movedPiece.type.baseMaterialValue) {
                    return null
                }
            }
        }

        return ForkInfo(
            attackerSquare = move.to,
            attackerType = movedPiece.type,
            targets = threatenedTargets.sortedByDescending { it.second.baseMaterialValue }
        )
    }

    /**
     * Detects grounded cannon-screen events caused by [playedMove]:
     * - [CannonScreenEventType.OPPONENT_CANNON_GAINED_SCREEN_ON_VALUABLE]:
     *   An enemy Cannon could NOT legally attack one of mover's valuable pieces (or General) before the move,
     *   but CAN legally attack it immediately after because the moved piece created/exposed a screen.
     * - [CannonScreenEventType.OWN_CANNON_LOST_SCREEN]:
     *   Mover's own Cannon could legally attack an enemy General/Chariot/Horse/Cannon before the move,
     *   and lost that attack after a different friendly piece vacated its screen.
     */
    fun detectCannonScreenEvent(
        boardBefore: BoardState,
        playedMove: Move,
        boardAfter: BoardState
    ): CannonScreenEvent? {
        val mover = boardBefore.sideToMove
        val enemy = mover.opposite()

        // 1) Did an enemy Cannon gain a brand-new attack on mover's General/Chariot/Horse/Cannon?
        val enemyProbeBefore = withSideToMove(boardBefore, enemy)
        val enemyProbeAfter = withSideToMove(boardAfter, enemy)
        for ((cannonSq, cannonPiece) in enemyProbeAfter.allPieces(enemy)) {
            if (cannonPiece.type != PieceType.CANNON) continue
            for ((ownSq, ownPiece) in boardAfter.allPieces(mover)) {
                if (ownPiece.type !in setOf(PieceType.GENERAL, PieceType.CHARIOT, PieceType.HORSE, PieceType.CANNON)) {
                    continue
                }
                val attacksAfter = RulesEngine.isMoveLegal(enemyProbeAfter, Move(cannonSq, ownSq))
                if (!attacksAfter) continue
                // Confirm the piece didn't simply walk onto a previously attacked square; check screen involvement
                val sameLine = cannonSq.file == ownSq.file || cannonSq.rank == ownSq.rank
                if (!sameLine) continue
                val attackedBefore = boardBefore.pieceAt(ownSq)?.side == mover &&
                    RulesEngine.isMoveLegal(enemyProbeBefore, Move(cannonSq, ownSq))
                if (!attackedBefore && isBetweenOrthogonally(playedMove.to, cannonSq, ownSq)) {
                    return CannonScreenEvent(
                        type = CannonScreenEventType.OPPONENT_CANNON_GAINED_SCREEN_ON_VALUABLE,
                        cannonSquare = cannonSq,
                        affectedTargetType = ownPiece.type
                    )
                }
            }
        }

        // 2) Did mover's own Cannon lose a screen that was aiming at a valuable enemy piece?
        val movedPiece = boardBefore.pieceAt(playedMove.from)
        if (movedPiece?.type != PieceType.CANNON) {
            val moverProbeBefore = withSideToMove(boardBefore, mover)
            val moverProbeAfter = withSideToMove(boardAfter, mover)
            for ((cannonSq, cannonPiece) in boardBefore.allPieces(mover)) {
                if (cannonPiece.type != PieceType.CANNON) continue
                if (boardAfter.pieceAt(cannonSq) != cannonPiece) continue
                for ((enemySq, enemyPiece) in boardBefore.allPieces(enemy)) {
                    if (enemyPiece.type !in setOf(PieceType.GENERAL, PieceType.CHARIOT, PieceType.HORSE, PieceType.CANNON)) {
                        continue
                    }
                    if (boardAfter.pieceAt(enemySq) != enemyPiece) continue
                    if (!isBetweenOrthogonally(playedMove.from, cannonSq, enemySq)) continue
                    val attackedBefore = RulesEngine.isMoveLegal(moverProbeBefore, Move(cannonSq, enemySq))
                    val attackedAfter = RulesEngine.isMoveLegal(moverProbeAfter, Move(cannonSq, enemySq))
                    if (attackedBefore && !attackedAfter) {
                        return CannonScreenEvent(
                            type = CannonScreenEventType.OWN_CANNON_LOST_SCREEN,
                            cannonSquare = cannonSq,
                            affectedTargetType = enemyPiece.type
                        )
                    }
                }
            }
        }

        return null
    }

    /**
     * Checks whether the Horse at [horseSquare] is heavily leg-blocked (cản chân Mã).
     * Counts orthogonal adjacent leg squares that are occupied (or out-of-bounds) and total legal moves.
     */
    fun inspectHorseBlocking(board: BoardState, horseSquare: Square): BlockedHorseInfo? {
        val piece = board.pieceAt(horseSquare) ?: return null
        if (piece.type != PieceType.HORSE) return null

        val legOffsets = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)
        var occupiedLegs = 0
        for ((df, dr) in legOffsets) {
            val legSq = Square(horseSquare.file + df, horseSquare.rank + dr)
            if (legSq.isValid && board.pieceAt(legSq) != null) {
                occupiedLegs++
            }
        }

        val probe = withSideToMove(board, piece.side)
        val legalMoves = RulesEngine.generateLegalMovesFrom(probe, horseSquare).size
        return BlockedHorseInfo(
            horseSquare = horseSquare,
            blockedDirections = occupiedLegs,
            legalMoveCount = legalMoves
        )
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
        var bestFork: ForkInfo? = null

        if (bestMove != null && RulesEngine.isMoveLegal(boardBefore, bestMove)) {
            bestCaptured = boardBefore.pieceAt(bestMove.to)?.type
            val afterBest = RulesEngine.applyMoveUnchecked(boardBefore, bestMove)
            bestGaveCheck = RulesEngine.isInCheck(afterBest, mover.opposite())
            bestFork = detectFork(boardBefore, bestMove)

            val movingPiece = boardBefore.pieceAt(bestMove.from)
            if (movingPiece != null && bestMove.from in riskBefore) {
                val stillAttacked = findAttackersOf(afterBest, bestMove.to, mover.opposite()).isNotEmpty()
                if (!stillAttacked) {
                    bestSaved = movingPiece.type
                }
            }
        }

        val playedFork = detectFork(boardBefore, playedMove)
        val cannonEvent = detectCannonScreenEvent(boardBefore, playedMove, boardAfter)

        // Check if mover moved a Horse into a severely leg-blocked square (blockedDirections >= 2 && legalMoveCount <= 1)
        val movedPiece = boardBefore.pieceAt(playedMove.from)
        val blockedHorse = if (movedPiece?.type == PieceType.HORSE) {
            inspectHorseBlocking(boardAfter, playedMove.to)
                ?.takeIf { it.blockedDirections >= 2 && it.legalMoveCount <= 1 }
        } else {
            null
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
            pvFirstCapturedTarget = pvFirstCapture,
            playedMoveFork = playedFork,
            bestMoveFork = bestFork,
            cannonScreenEvent = cannonEvent,
            playedHorseBlocked = blockedHorse
        )
    }

    /**
     * Infers a grounded [PuzzleTheme] from verified [TacticalFacts] (Section 15.2).
     */
    fun inferPuzzleTheme(
        facts: TacticalFacts,
        plyIndex: Int,
        totalPiecesOnBoard: Int
    ): PuzzleTheme = when {
        facts.missedForcedMate -> PuzzleTheme.CHECKMATE
        facts.bestMoveFork != null -> PuzzleTheme.FORK
        facts.bestMoveSavedPiece != null || facts.allowedForcedMate || facts.exposedOwnPieceAtRisk != null ->
            PuzzleTheme.DEFENSE
        facts.bestMoveCaptured != null -> PuzzleTheme.CAPTURE
        facts.bestMoveGaveCheck -> PuzzleTheme.COUNTERATTACK
        plyIndex < 12 -> PuzzleTheme.OPENING
        totalPiecesOnBoard <= 12 -> PuzzleTheme.ENDGAME
        else -> PuzzleTheme.MISTAKE_FIX
    }

    private fun isBetweenOrthogonally(mid: Square, a: Square, b: Square): Boolean {
        return when {
            a.file == b.file && mid.file == a.file ->
                mid.rank in (minOf(a.rank, b.rank) + 1) until maxOf(a.rank, b.rank)
            a.rank == b.rank && mid.rank == a.rank ->
                mid.file in (minOf(a.file, b.file) + 1) until maxOf(a.file, b.file)
            else -> false
        }
    }

    private fun withSideToMove(board: BoardState, side: Side): BoardState {
        if (board.sideToMove == side) return board
        val copy = board.copyArray()
        return BoardState.create(
            squares = copy,
            sideToMove = side,
            halfMoveClock = board.halfMoveClock,
            fullMoveNumber = board.fullMoveNumber,
            positionHash = ZobristHash.computeHash(copy, side)
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
