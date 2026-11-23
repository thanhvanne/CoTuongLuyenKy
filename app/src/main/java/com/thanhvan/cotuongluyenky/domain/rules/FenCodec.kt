package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square

sealed class FenParseResult {
    data class Success(val board: BoardState) : FenParseResult()
    data class Error(val message: String) : FenParseResult()
}

/**
 * Standard Xiangqi UCI/Pikafish FEN encoder and validator.
 */
object FenCodec {
    const val INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

    fun parse(fen: String): FenParseResult {
        val trimmed = fen.trim()
        if (trimmed.isEmpty()) {
            return FenParseResult.Error("Chuỗi FEN không được để trống.")
        }

        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 2) {
            return FenParseResult.Error("Chuỗi FEN thiếu thông tin bên đi.")
        }

        val rankTokens = parts[0].split('/')
        if (rankTokens.size != Square.BOARD_RANKS) {
            return FenParseResult.Error(
                "Bàn cờ phải có đúng ${Square.BOARD_RANKS} hàng (hiện có ${rankTokens.size})."
            )
        }

        val squares = arrayOfNulls<Piece>(Square.BOARD_SQUARES)
        var redGeneralCount = 0
        var blackGeneralCount = 0

        for (fenRowIndex in 0 until Square.BOARD_RANKS) {
            val rank = Square.BOARD_RANKS - 1 - fenRowIndex
            val token = rankTokens[fenRowIndex]
            var file = 0

            for (ch in token) {
                if (ch in '1'..'9') {
                    file += (ch - '0')
                } else {
                    val piece = PieceType.fromFenChar(ch)
                        ?: return FenParseResult.Error("Ký hiệu quân không hợp lệ: '$ch' tại hàng ${fenRowIndex + 1}.")
                    if (file >= Square.BOARD_FILES) {
                        return FenParseResult.Error("Hàng ${fenRowIndex + 1} vượt quá 9 cột.")
                    }
                    val sq = Square(file, rank)
                    if (piece.type == PieceType.GENERAL) {
                        if (!sq.isInPalace(piece.side)) {
                            return FenParseResult.Error("Tướng ${piece.side.vietnameseName} phải nằm trong cung.")
                        }
                        if (piece.side == Side.RED) redGeneralCount++ else blackGeneralCount++
                    } else if (piece.type == PieceType.ADVISOR && !sq.isInPalace(piece.side)) {
                        return FenParseResult.Error("Sĩ ${piece.side.vietnameseName} phải nằm trong cung.")
                    } else if (piece.type == PieceType.ELEPHANT && !sq.isOnOwnSide(piece.side)) {
                        return FenParseResult.Error("Tượng ${piece.side.vietnameseName} không được vượt sông.")
                    }
                    squares[sq.index] = piece
                    file++
                }
            }

            if (file != Square.BOARD_FILES) {
                return FenParseResult.Error(
                    "Hàng ${fenRowIndex + 1} có $file cột (yêu cầu đúng ${Square.BOARD_FILES} cột)."
                )
            }
        }

        if (redGeneralCount != 1) {
            return FenParseResult.Error("Phải có đúng 1 Tướng Đỏ (hiện có $redGeneralCount).")
        }
        if (blackGeneralCount != 1) {
            return FenParseResult.Error("Phải có đúng 1 Tướng Đen (hiện có $blackGeneralCount).")
        }

        val sideToMove = when (parts[1].lowercase()) {
            "w", "r" -> Side.RED
            "b" -> Side.BLACK
            else -> return FenParseResult.Error("Bên đi không hợp lệ: '${parts[1]}' (chấp nhận 'w', 'r' hoặc 'b').")
        }

        val halfMoveClock = if (parts.size >= 5) {
            parts[4].toIntOrNull()?.takeIf { it >= 0 }
                ?: return FenParseResult.Error("Chỉ số nửa lượt không ăn quân không hợp lệ: '${parts[4]}'.")
        } else {
            0
        }

        val fullMoveNumber = if (parts.size >= 6) {
            parts[5].toIntOrNull()?.takeIf { it >= 1 }
                ?: return FenParseResult.Error("Số lượt đi không hợp lệ: '${parts[5]}'.")
        } else {
            1
        }

        val hash = ZobristHash.computeHash(squares, sideToMove)
        val board = BoardState.create(
            squares = squares,
            sideToMove = sideToMove,
            halfMoveClock = halfMoveClock,
            fullMoveNumber = fullMoveNumber,
            positionHash = hash
        )

        if (RulesEngine.areGeneralsFacing(board)) {
            return FenParseResult.Error("Thế cờ không hợp lệ: hai Tướng đang trực diện.")
        }

        // The side NOT to move cannot already be in check, because the side to move could capture the General immediately.
        if (RulesEngine.isInCheck(board, sideToMove.opposite())) {
            return FenParseResult.Error(
                "Thế cờ không hợp lệ: Tướng ${sideToMove.opposite().vietnameseName} đang bị chiếu nhưng không phải lượt đi của họ."
            )
        }

        return FenParseResult.Success(board)
    }

    fun toFen(board: BoardState): String {
        val sb = StringBuilder(96)
        for (rank in Square.BOARD_RANKS - 1 downTo 0) {
            var emptyCount = 0
            for (file in 0 until Square.BOARD_FILES) {
                val piece = board.pieceAt(file, rank)
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(piece.type.fenChar(piece.side))
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount)
            }
            if (rank > 0) {
                sb.append('/')
            }
        }
        sb.append(' ')
        sb.append(board.sideToMove.fenChar)
        sb.append(" - - ")
        sb.append(board.halfMoveClock)
        sb.append(' ')
        sb.append(board.fullMoveNumber)
        return sb.toString()
    }
}
