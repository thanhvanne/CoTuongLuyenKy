package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square

/**
 * Deterministic 64-bit Zobrist hash table for Xiangqi positions.
 * Uses a fixed SplitMix64 seed so position hashes remain stable across app restarts
 * and persisted games in Room.
 */
object ZobristHash {
    private const val PIECE_SLOTS = 14 // 2 sides * 7 piece types
    private val pieceSquareKeys = LongArray(PIECE_SLOTS * Square.BOARD_SQUARES)
    private val blackToMoveKey: Long

    init {
        var state = 0x5869_616E_6771_6931L // "Xiangqi1"
        fun nextLong(): Long {
            state += -7046029254386353131L // 0x9E3779B97F4A7C15
            var z = state
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }

        for (i in pieceSquareKeys.indices) {
            pieceSquareKeys[i] = nextLong()
        }
        blackToMoveKey = nextLong()
    }

    private fun pieceSlot(piece: Piece): Int {
        val sideOffset = if (piece.side == Side.RED) 0 else PieceType.entries.size
        return sideOffset + piece.type.ordinal
    }

    fun pieceKey(piece: Piece, squareIndex: Int): Long =
        pieceSquareKeys[pieceSlot(piece) * Square.BOARD_SQUARES + squareIndex]

    fun computeHash(squares: Array<Piece?>, sideToMove: Side): Long {
        var hash = 0L
        for (idx in 0 until Square.BOARD_SQUARES) {
            val piece = squares[idx]
            if (piece != null) {
                hash = hash xor pieceKey(piece, idx)
            }
        }
        if (sideToMove == Side.BLACK) {
            hash = hash xor blackToMoveKey
        }
        return hash
    }
}
