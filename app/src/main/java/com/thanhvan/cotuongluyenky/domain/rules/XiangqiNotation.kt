package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import kotlin.math.abs

/**
 * Vietnamese Xiangqi notation formatter supporting both short (e.g. "P2-5")
 * and full (e.g. "Pháo 2 bình 5") formats.
 *
 * File numbering from each player's perspective (right to left, 1..9):
 * - Red:   internal file 8 -> 1, ..., file 0 -> 9
 * - Black: internal file 0 -> 1, ..., file 8 -> 9
 */
object XiangqiNotation {

    data class FormattedNotation(
        val short: String,
        val full: String
    )

    fun format(boardBefore: BoardState, move: Move): FormattedNotation {
        val piece = boardBefore.pieceAt(move.from)
            ?: return FormattedNotation(move.toUci(), move.toUci())

        val side = piece.side
        val fromCol = toDisplayColumn(move.from.file, side)
        val toCol = toDisplayColumn(move.to.file, side)

        // Find all identical pieces of the same side on the same file
        val sameFilePieces = mutableListOf<Square>()
        for (rank in 0 until Square.BOARD_RANKS) {
            val candidate = boardBefore.pieceAt(move.from.file, rank)
            if (candidate != null && candidate.side == side && candidate.type == piece.type) {
                sameFilePieces.add(Square(move.from.file, rank))
            }
        }

        // Sort front-to-back (front = closest to opponent's back rank)
        val sortedSameFile = if (side == Side.RED) {
            sameFilePieces.sortedByDescending { it.rank }
        } else {
            sameFilePieces.sortedBy { it.rank }
        }

        val (prefixShort, prefixFull) = if (sortedSameFile.size <= 1) {
            "${piece.type.shortCode}$fromCol" to "${piece.type.vietnameseName} $fromCol"
        } else {
            val rankPos = sortedSameFile.indexOf(move.from)
            // Check if another file ALSO has multiple identical pieces (possible for Soldiers)
            val otherFileHasMulti = hasMultipleOnAnotherFile(boardBefore, side, piece.type, move.from.file)
            val disambigShort: String
            val disambigFull: String
            when (sortedSameFile.size) {
                2 -> {
                    disambigShort = if (rankPos == 0) "Tiền" else "Hậu"
                    disambigFull = disambigShort
                }
                3 -> {
                    disambigShort = when (rankPos) {
                        0 -> "Tiền"
                        1 -> "Trung"
                        else -> "Hậu"
                    }
                    disambigFull = disambigShort
                }
                else -> {
                    val num = rankPos + 1
                    disambigShort = "$num"
                    disambigFull = when (num) {
                        1 -> "Nhất"
                        2 -> "Nhị"
                        3 -> "Tam"
                        4 -> "Tứ"
                        else -> "Ngũ"
                    }
                }
            }
            if (otherFileHasMulti) {
                "$disambigShort ${piece.type.shortCode}$fromCol" to "$disambigFull ${piece.type.vietnameseName} $fromCol"
            } else {
                "$disambigShort ${piece.type.shortCode}" to "$disambigFull ${piece.type.vietnameseName}"
            }
        }

        val deltaRank = move.to.rank - move.from.rank
        val isForward = if (side == Side.RED) deltaRank > 0 else deltaRank < 0

        val isDiagonalOrKnight = piece.type == PieceType.ADVISOR ||
            piece.type == PieceType.ELEPHANT ||
            piece.type == PieceType.HORSE

        val (actionShort, actionFull, targetNumber) = when {
            deltaRank == 0 -> Triple("-", "bình", toCol)
            isForward -> {
                val target = if (isDiagonalOrKnight) toCol else abs(deltaRank)
                Triple(".", "tấn", target)
            }
            else -> {
                val target = if (isDiagonalOrKnight) toCol else abs(deltaRank)
                Triple("/", "thoái", target)
            }
        }

        val shortText = "$prefixShort$actionShort$targetNumber"
        val fullText = "$prefixFull $actionFull $targetNumber"
        return FormattedNotation(short = shortText, full = fullText)
    }

    fun toDisplayColumn(file: Int, side: Side): Int =
        if (side == Side.RED) Square.BOARD_FILES - file else file + 1

    private fun hasMultipleOnAnotherFile(
        board: BoardState,
        side: Side,
        type: PieceType,
        excludedFile: Int
    ): Boolean {
        for (file in 0 until Square.BOARD_FILES) {
            if (file == excludedFile) continue
            var count = 0
            for (rank in 0 until Square.BOARD_RANKS) {
                val p = board.pieceAt(file, rank)
                if (p != null && p.side == side && p.type == type) {
                    count++
                    if (count >= 2) return true
                }
            }
        }
        return false
    }
}
