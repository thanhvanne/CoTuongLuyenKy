package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square

data class AppliedMoveResult(
    val newBoard: BoardState,
    val movedPiece: Piece,
    val capturedPiece: Piece?,
    val gaveCheck: Boolean,
    val notationShort: String,
    val notationFull: String
)

data class TerminalEvaluation(
    val result: GameResult,
    val reason: GameResultReason
) {
    val isFinished: Boolean get() = result != GameResult.ONGOING
}

data class ReconstructedGame(
    val initialBoard: BoardState,
    val currentBoard: BoardState,
    val moves: List<MoveRecord>,
    val terminal: TerminalEvaluation
)

/**
 * Pure Kotlin Xiangqi rules engine implementing Ruleset WXF_BASIC_V1.
 *
 * Guarantees:
 * - Complete move legality for all 7 piece types (including horse leg blocking,
 *   elephant eye blocking, river limits, palace bounds, cannon screen capture,
 *   flying general restriction, and self-check prevention).
 * - Checkmate and stalemate both result in a loss for the side with no legal moves.
 * - 60-move no-capture draw (120 plies) and insufficient mating material draw.
 * - 3-fold repetition detection with perpetual-check adjudication (loss for the
 *   perpetually checking side; draw for non-checking repetition in Phase 1).
 *   Note: complex perpetual-chase adjudication is documented as a Phase 4 extension.
 */
object RulesEngine {

    private val ORTHOGONAL_DIRS = arrayOf(
        intArrayOf(1, 0),
        intArrayOf(-1, 0),
        intArrayOf(0, 1),
        intArrayOf(0, -1)
    )

    private val DIAGONAL_DIRS = arrayOf(
        intArrayOf(1, 1),
        intArrayOf(1, -1),
        intArrayOf(-1, 1),
        intArrayOf(-1, -1)
    )

    // Each entry: (legDf, legDr, destDf, destDr)
    private val HORSE_MOVES = arrayOf(
        intArrayOf(1, 0, 2, 1),
        intArrayOf(1, 0, 2, -1),
        intArrayOf(-1, 0, -2, 1),
        intArrayOf(-1, 0, -2, -1),
        intArrayOf(0, 1, 1, 2),
        intArrayOf(0, 1, -1, 2),
        intArrayOf(0, -1, 1, -2),
        intArrayOf(0, -1, -1, -2)
    )

    // Reverse check from target square to potential attacker horse:
    // If horse is at (target + destDf, target + destDr), its blocking leg is at (target + legDf, target + legDr)
    private val HORSE_ATTACKER_OFFSETS = arrayOf(
        intArrayOf(2, 1, 1, 1),
        intArrayOf(2, -1, 1, -1),
        intArrayOf(-2, 1, -1, 1),
        intArrayOf(-2, -1, -1, -1),
        intArrayOf(1, 2, 1, 1),
        intArrayOf(-1, 2, -1, 1),
        intArrayOf(1, -2, 1, -1),
        intArrayOf(-1, -2, -1, -1)
    )

    fun initialBoard(): BoardState {
        return when (val parsed = FenCodec.parse(FenCodec.INITIAL_FEN)) {
            is FenParseResult.Success -> parsed.board
            is FenParseResult.Error -> error("Invalid built-in initial FEN: ${parsed.message}")
        }
    }

    fun areGeneralsFacing(board: BoardState): Boolean {
        val redGen = board.findGeneral(Side.RED) ?: return false
        val blackGen = board.findGeneral(Side.BLACK) ?: return false
        if (redGen.file != blackGen.file) return false
        val minRank = minOf(redGen.rank, blackGen.rank)
        val maxRank = maxOf(redGen.rank, blackGen.rank)
        for (rank in (minRank + 1) until maxRank) {
            if (board.pieceAt(redGen.file, rank) != null) return false
        }
        return true
    }

    fun isInCheck(board: BoardState, side: Side): Boolean {
        val generalSquare = board.findGeneral(side) ?: return true
        if (areGeneralsFacing(board)) return true
        return isSquareAttackedBy(board, generalSquare, side.opposite())
    }

    fun isSquareAttackedBy(board: BoardState, target: Square, attacker: Side): Boolean {
        val tf = target.file
        val tr = target.rank

        // 1. Orthogonal rays: Chariot, Cannon, and General (adjacent or flying general)
        for (dir in ORTHOGONAL_DIRS) {
            val df = dir[0]
            val dr = dir[1]
            var f = tf + df
            var r = tr + dr
            var screenFound = false

            while (f in 0 until Square.BOARD_FILES && r in 0 until Square.BOARD_RANKS) {
                val p = board.pieceAt(f, r)
                if (p != null) {
                    if (!screenFound) {
                        if (p.side == attacker) {
                            if (p.type == PieceType.CHARIOT) return true
                            if (p.type == PieceType.GENERAL) {
                                // Adjacent palace general attack or flying general on same file
                                val isAdj = (f == tf + df && r == tr + dr)
                                if (isAdj && target.isInPalace(attacker)) return true
                                if (df == 0) {
                                    // If target is the opponent's general, flying general applies
                                    val targetPiece = board.pieceAt(tf, tr)
                                    if (targetPiece != null && targetPiece.type == PieceType.GENERAL && targetPiece.side != attacker) {
                                        return true
                                    }
                                }
                            }
                        }
                        screenFound = true
                    } else {
                        if (p.side == attacker && p.type == PieceType.CANNON) {
                            return true
                        }
                        break
                    }
                }
                f += df
                r += dr
            }
        }

        // 2. Horse attacks
        for (offset in HORSE_ATTACKER_OFFSETS) {
            val hf = tf + offset[0]
            val hr = tr + offset[1]
            if (hf !in 0 until Square.BOARD_FILES || hr !in 0 until Square.BOARD_RANKS) continue
            val p = board.pieceAt(hf, hr)
            if (p != null && p.side == attacker && p.type == PieceType.HORSE) {
                // Horse leg is adjacent to the horse along the 2-step axis, which equals (tf + offset[2], tr + offset[3])
                val legF = tf + offset[2]
                val legR = tr + offset[3]
                if (board.pieceAt(legF, legR) == null) {
                    return true
                }
            }
        }

        // 3. Soldier attacks
        // Red soldiers move +1 rank (and sideways when rank >= 5). So a Red soldier attacking (tf, tr)
        // must be at (tf, tr - 1), or at (tf ± 1, tr) if tr >= 5.
        val soldierFromRank = if (attacker == Side.RED) tr - 1 else tr + 1
        if (soldierFromRank in 0 until Square.BOARD_RANKS) {
            val p = board.pieceAt(tf, soldierFromRank)
            if (p != null && p.side == attacker && p.type == PieceType.SOLDIER) {
                return true
            }
        }
        val targetOnAttackerEnemyHalf = target.hasCrossedRiver(attacker)
        if (targetOnAttackerEnemyHalf) {
            for (df in intArrayOf(-1, 1)) {
                val sf = tf + df
                if (sf in 0 until Square.BOARD_FILES) {
                    val p = board.pieceAt(sf, tr)
                    if (p != null && p.side == attacker && p.type == PieceType.SOLDIER) {
                        return true
                    }
                }
            }
        }

        // 4. Advisor attacks (only relevant if target is inside attacker's palace)
        if (target.isInPalace(attacker)) {
            for (dir in DIAGONAL_DIRS) {
                val af = tf + dir[0]
                val ar = tr + dir[1]
                val sq = Square(af, ar)
                if (sq.isValid && sq.isInPalace(attacker)) {
                    val p = board.pieceAt(af, ar)
                    if (p != null && p.side == attacker && p.type == PieceType.ADVISOR) {
                        return true
                    }
                }
            }
        }

        // 5. Elephant attacks (only relevant if target is on attacker's own side of the river)
        if (target.isOnOwnSide(attacker)) {
            for (dir in DIAGONAL_DIRS) {
                val ef = tf + dir[0] * 2
                val er = tr + dir[1] * 2
                val eyeF = tf + dir[0]
                val eyeR = tr + dir[1]
                val sq = Square(ef, er)
                if (sq.isValid && sq.isOnOwnSide(attacker)) {
                    val p = board.pieceAt(ef, er)
                    if (p != null && p.side == attacker && p.type == PieceType.ELEPHANT && board.pieceAt(eyeF, eyeR) == null) {
                        return true
                    }
                }
            }
        }

        return false
    }

    fun generateLegalMoves(board: BoardState): List<Move> {
        val side = board.sideToMove
        val legalMoves = ArrayList<Move>(44)
        for (idx in 0 until Square.BOARD_SQUARES) {
            val piece = board.pieceAtIndex(idx)
            if (piece != null && piece.side == side) {
                val from = Square.fromIndex(idx)
                generatePseudoLegalMovesForPiece(board, from, piece) { to ->
                    val candidate = Move(from, to)
                    val next = applyMoveUnchecked(board, candidate)
                    if (!isInCheck(next, side)) {
                        legalMoves.add(candidate)
                    }
                }
            }
        }
        return legalMoves
    }

    fun generateLegalMovesFrom(board: BoardState, from: Square): List<Move> {
        val piece = board.pieceAt(from) ?: return emptyList()
        if (piece.side != board.sideToMove) return emptyList()
        val legalMoves = ArrayList<Move>(18)
        generatePseudoLegalMovesForPiece(board, from, piece) { to ->
            val candidate = Move(from, to)
            val next = applyMoveUnchecked(board, candidate)
            if (!isInCheck(next, piece.side)) {
                legalMoves.add(candidate)
            }
        }
        return legalMoves
    }

    fun isMoveLegal(board: BoardState, move: Move): Boolean {
        if (!move.from.isValid || !move.to.isValid || move.from == move.to) return false
        val piece = board.pieceAt(move.from) ?: return false
        if (piece.side != board.sideToMove) return false
        return generateLegalMovesFrom(board, move.from).any { it.to == move.to }
    }

    fun applyMove(board: BoardState, move: Move): AppliedMoveResult {
        require(isMoveLegal(board, move)) { "Illegal move ${move.toUci()} for ${board.sideToMove}" }
        val movedPiece = board.pieceAt(move.from)!!
        val capturedPiece = board.pieceAt(move.to)
        val notation = XiangqiNotation.format(board, move)
        val newBoard = applyMoveUnchecked(board, move)
        val gaveCheck = isInCheck(newBoard, newBoard.sideToMove)
        return AppliedMoveResult(
            newBoard = newBoard,
            movedPiece = movedPiece,
            capturedPiece = capturedPiece,
            gaveCheck = gaveCheck,
            notationShort = notation.short,
            notationFull = notation.full
        )
    }

    /**
     * Fast internal state transition without re-running full legality validation.
     * Callers must validate legality before exposing to gameplay.
     */
    internal fun applyMoveUnchecked(board: BoardState, move: Move): BoardState {
        val squares = board.copyArray()
        val moving = squares[move.from.index]
            ?: error("No piece at ${move.from.toUci()}")
        val captured = squares[move.to.index]

        squares[move.from.index] = null
        squares[move.to.index] = moving

        val nextSide = board.sideToMove.opposite()
        val nextHalfMoveClock = if (captured != null) 0 else board.halfMoveClock + 1
        val nextFullMoveNumber = if (board.sideToMove == Side.BLACK) {
            board.fullMoveNumber + 1
        } else {
            board.fullMoveNumber
        }
        val nextHash = ZobristHash.computeHash(squares, nextSide)

        return BoardState.create(
            squares = squares,
            sideToMove = nextSide,
            halfMoveClock = nextHalfMoveClock,
            fullMoveNumber = nextFullMoveNumber,
            positionHash = nextHash
        )
    }

    fun hasAnyLegalMove(board: BoardState): Boolean {
        val side = board.sideToMove
        for (idx in 0 until Square.BOARD_SQUARES) {
            val piece = board.pieceAtIndex(idx)
            if (piece != null && piece.side == side) {
                val from = Square.fromIndex(idx)
                var found = false
                generatePseudoLegalMovesForPiece(board, from, piece) { to ->
                    if (!found) {
                        val next = applyMoveUnchecked(board, Move(from, to))
                        if (!isInCheck(next, side)) {
                            found = true
                        }
                    }
                }
                if (found) return true
            }
        }
        return false
    }

    fun evaluateTerminalState(
        board: BoardState,
        history: List<MoveRecord>,
        initialPositionHash: Long
    ): TerminalEvaluation {
        // 1. No legal moves -> Checkmate or Stalemate (both lose in Xiangqi!)
        if (!hasAnyLegalMove(board)) {
            val winner = if (board.sideToMove == Side.RED) GameResult.BLACK_WIN else GameResult.RED_WIN
            val reason = if (isInCheck(board, board.sideToMove)) {
                GameResultReason.CHECKMATE
            } else {
                GameResultReason.STALEMATE
            }
            return TerminalEvaluation(winner, reason)
        }

        // 2. Threefold position repetition & perpetual check detection
        val currentHash = board.positionHash
        val occurrencePlyIndices = mutableListOf<Int>()
        if (initialPositionHash == currentHash) {
            occurrencePlyIndices.add(-1)
        }
        for (i in history.indices) {
            if (history[i].positionHashAfter == currentHash) {
                occurrencePlyIndices.add(i)
            }
        }
        if (occurrencePlyIndices.size >= 3 && history.isNotEmpty()) {
            val lastMove = history.last()
            val cycleStartIndex = occurrencePlyIndices.first() + 1
            val repeatingSide = lastMove.side
            val opponentSide = repeatingSide.opposite()

            val repeatingSideMovesInCycle = history.subList(cycleStartIndex, history.size)
                .filter { it.side == repeatingSide }
            val opponentMovesInCycle = history.subList(cycleStartIndex, history.size)
                .filter { it.side == opponentSide }

            val repeatingSidePerpetualCheck = repeatingSideMovesInCycle.isNotEmpty() &&
                repeatingSideMovesInCycle.all { it.gaveCheck }
            val opponentPerpetualCheck = opponentMovesInCycle.isNotEmpty() &&
                opponentMovesInCycle.all { it.gaveCheck }

            return when {
                repeatingSidePerpetualCheck && !opponentPerpetualCheck -> {
                    val winner = if (repeatingSide == Side.RED) GameResult.BLACK_WIN else GameResult.RED_WIN
                    TerminalEvaluation(winner, GameResultReason.PERPETUAL_CHECK_LOSS)
                }
                opponentPerpetualCheck && !repeatingSidePerpetualCheck -> {
                    val winner = if (opponentSide == Side.RED) GameResult.BLACK_WIN else GameResult.RED_WIN
                    TerminalEvaluation(winner, GameResultReason.PERPETUAL_CHECK_LOSS)
                }
                else -> TerminalEvaluation(GameResult.DRAW, GameResultReason.DRAW_REPETITION)
            }
        }

        // 3. 60-move no-capture rule (120 plies)
        if (board.halfMoveClock >= 120) {
            return TerminalEvaluation(GameResult.DRAW, GameResultReason.DRAW_SIXTY_MOVE_RULE)
        }

        // 4. Insufficient mating material (only Generals, Advisors, and Elephants remain)
        if (isInsufficientMaterial(board)) {
            return TerminalEvaluation(GameResult.DRAW, GameResultReason.DRAW_INSUFFICIENT_MATERIAL)
        }

        return TerminalEvaluation(GameResult.ONGOING, GameResultReason.NONE)
    }

    fun isInsufficientMaterial(board: BoardState): Boolean {
        for (idx in 0 until Square.BOARD_SQUARES) {
            val piece = board.pieceAtIndex(idx) ?: continue
            when (piece.type) {
                PieceType.CHARIOT,
                PieceType.CANNON,
                PieceType.HORSE,
                PieceType.SOLDIER -> return false
                PieceType.GENERAL,
                PieceType.ADVISOR,
                PieceType.ELEPHANT -> Unit
            }
        }
        return true
    }

    fun reconstructGame(initialFen: String, movesUci: List<String>): Result<ReconstructedGame> {
        val initialBoard = when (val parsed = FenCodec.parse(initialFen)) {
            is FenParseResult.Success -> parsed.board
            is FenParseResult.Error -> return Result.failure(IllegalArgumentException(parsed.message))
        }

        var current = initialBoard
        val records = ArrayList<MoveRecord>(movesUci.size)
        for ((index, uci) in movesUci.withIndex()) {
            val move = Move.fromUci(uci)
                ?: return Result.failure(IllegalArgumentException("Nước đi sai định dạng tại ply $index: $uci"))
            if (!isMoveLegal(current, move)) {
                return Result.failure(IllegalArgumentException("Nước đi không hợp lệ tại ply $index: $uci"))
            }
            val applied = applyMove(current, move)
            records.add(
                MoveRecord(
                    plyIndex = index,
                    side = applied.movedPiece.side,
                    move = move,
                    pieceType = applied.movedPiece.type,
                    capturedPieceType = applied.capturedPiece?.type,
                    notationShort = applied.notationShort,
                    notationFull = applied.notationFull,
                    positionHashAfter = applied.newBoard.positionHash,
                    gaveCheck = applied.gaveCheck
                )
            )
            current = applied.newBoard
        }

        val terminal = evaluateTerminalState(current, records, initialBoard.positionHash)
        return Result.success(
            ReconstructedGame(
                initialBoard = initialBoard,
                currentBoard = current,
                moves = records,
                terminal = terminal
            )
        )
    }

    private inline fun generatePseudoLegalMovesForPiece(
        board: BoardState,
        from: Square,
        piece: Piece,
        onTarget: (Square) -> Unit
    ) {
        val side = piece.side
        val f = from.file
        val r = from.rank

        when (piece.type) {
            PieceType.GENERAL -> {
                for (dir in ORTHOGONAL_DIRS) {
                    val to = Square(f + dir[0], r + dir[1])
                    if (to.isValid && to.isInPalace(side)) {
                        val target = board.pieceAt(to)
                        if (target == null || target.side != side) {
                            onTarget(to)
                        }
                    }
                }
            }

            PieceType.ADVISOR -> {
                for (dir in DIAGONAL_DIRS) {
                    val to = Square(f + dir[0], r + dir[1])
                    if (to.isValid && to.isInPalace(side)) {
                        val target = board.pieceAt(to)
                        if (target == null || target.side != side) {
                            onTarget(to)
                        }
                    }
                }
            }

            PieceType.ELEPHANT -> {
                for (dir in DIAGONAL_DIRS) {
                    val eyeF = f + dir[0]
                    val eyeR = r + dir[1]
                    val to = Square(f + dir[0] * 2, r + dir[1] * 2)
                    if (to.isValid && to.isOnOwnSide(side) && board.pieceAt(eyeF, eyeR) == null) {
                        val target = board.pieceAt(to)
                        if (target == null || target.side != side) {
                            onTarget(to)
                        }
                    }
                }
            }

            PieceType.HORSE -> {
                for (hm in HORSE_MOVES) {
                    val legF = f + hm[0]
                    val legR = r + hm[1]
                    val to = Square(f + hm[2], r + hm[3])
                    if (to.isValid && board.pieceAt(legF, legR) == null) {
                        val target = board.pieceAt(to)
                        if (target == null || target.side != side) {
                            onTarget(to)
                        }
                    }
                }
            }

            PieceType.CHARIOT -> {
                for (dir in ORTHOGONAL_DIRS) {
                    var nf = f + dir[0]
                    var nr = r + dir[1]
                    while (nf in 0 until Square.BOARD_FILES && nr in 0 until Square.BOARD_RANKS) {
                        val target = board.pieceAt(nf, nr)
                        if (target == null) {
                            onTarget(Square(nf, nr))
                        } else {
                            if (target.side != side) {
                                onTarget(Square(nf, nr))
                            }
                            break
                        }
                        nf += dir[0]
                        nr += dir[1]
                    }
                }
            }

            PieceType.CANNON -> {
                for (dir in ORTHOGONAL_DIRS) {
                    var nf = f + dir[0]
                    var nr = r + dir[1]
                    var screenFound = false
                    while (nf in 0 until Square.BOARD_FILES && nr in 0 until Square.BOARD_RANKS) {
                        val target = board.pieceAt(nf, nr)
                        if (!screenFound) {
                            if (target == null) {
                                onTarget(Square(nf, nr))
                            } else {
                                screenFound = true
                            }
                        } else {
                            if (target != null) {
                                if (target.side != side) {
                                    onTarget(Square(nf, nr))
                                }
                                break
                            }
                        }
                        nf += dir[0]
                        nr += dir[1]
                    }
                }
            }

            PieceType.SOLDIER -> {
                val forwardRank = if (side == Side.RED) r + 1 else r - 1
                if (forwardRank in 0 until Square.BOARD_RANKS) {
                    val to = Square(f, forwardRank)
                    val target = board.pieceAt(to)
                    if (target == null || target.side != side) {
                        onTarget(to)
                    }
                }
                if (from.hasCrossedRiver(side)) {
                    for (df in intArrayOf(-1, 1)) {
                        val nf = f + df
                        if (nf in 0 until Square.BOARD_FILES) {
                            val to = Square(nf, r)
                            val target = board.pieceAt(to)
                            if (target == null || target.side != side) {
                                onTarget(to)
                            }
                        }
                    }
                }
            }
        }
    }
}
