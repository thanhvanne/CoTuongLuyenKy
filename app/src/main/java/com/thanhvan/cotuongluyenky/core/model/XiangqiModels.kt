package com.thanhvan.cotuongluyenky.core.model

enum class Side {
    RED,
    BLACK;

    fun opposite(): Side = if (this == RED) BLACK else RED

    val vietnameseName: String
        get() = if (this == RED) "Đỏ" else "Đen"

    val fenChar: Char
        get() = if (this == RED) 'w' else 'b'
}

enum class PieceType(
    val fenUpper: Char,
    val redHanzi: String,
    val blackHanzi: String,
    val vietnameseName: String,
    val shortCode: String,
    val baseMaterialValue: Int
) {
    GENERAL('K', "帥", "將", "Tướng", "Tg", 10000),
    ADVISOR('A', "仕", "士", "Sĩ", "S", 200),
    ELEPHANT('B', "相", "象", "Tượng", "T", 200),
    CHARIOT('R', "俥", "車", "Xe", "X", 900),
    CANNON('C', "炮", "砲", "Pháo", "P", 450),
    HORSE('N', "傌", "馬", "Mã", "M", 400),
    SOLDIER('P', "兵", "卒", "Tốt", "B", 100);

    fun hanzi(side: Side): String = if (side == Side.RED) redHanzi else blackHanzi

    fun fenChar(side: Side): Char = if (side == Side.RED) fenUpper else fenUpper.lowercaseChar()

    fun boardLabel(side: Side, style: PieceDisplayStyle): String = when (style) {
        PieceDisplayStyle.HANZI -> hanzi(side)
        PieceDisplayStyle.VIETNAMESE -> vietnameseName
    }

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val side = if (c.isUpperCase()) Side.RED else Side.BLACK
            val type = when (c.uppercaseChar()) {
                'K' -> GENERAL
                'A' -> ADVISOR
                'B', 'E' -> ELEPHANT
                'R' -> CHARIOT
                'C' -> CANNON
                'N', 'H' -> HORSE
                'P' -> SOLDIER
                else -> return null
            }
            return Piece(side, type)
        }
    }
}

data class Piece(
    val side: Side,
    val type: PieceType
)

/**
 * Board coordinate in standard Xiangqi UCI convention:
 * - file: 0..8 corresponding to 'a'..'i' (left to right from Red's perspective)
 * - rank: 0..9 corresponding to '0'..'9' (0 = Red's bottom back rank, 9 = Black's top back rank)
 */
data class Square(
    val file: Int,
    val rank: Int
) {
    val index: Int
        get() = rank * BOARD_FILES + file

    val isValid: Boolean
        get() = file in 0 until BOARD_FILES && rank in 0 until BOARD_RANKS

    fun toUci(): String = "${('a' + file)}$rank"

    fun isInPalace(side: Side): Boolean {
        if (file !in 3..5) return false
        return if (side == Side.RED) rank in 0..2 else rank in 7..9
    }

    fun isOnOwnSide(side: Side): Boolean {
        return if (side == Side.RED) rank in 0..4 else rank in 5..9
    }

    fun hasCrossedRiver(side: Side): Boolean = !isOnOwnSide(side)

    companion object {
        const val BOARD_FILES = 9
        const val BOARD_RANKS = 10
        const val BOARD_SQUARES = BOARD_FILES * BOARD_RANKS

        fun fromIndex(index: Int): Square = Square(index % BOARD_FILES, index / BOARD_FILES)

        fun fromUci(uci: String): Square? {
            if (uci.length != 2) return null
            val file = uci[0].lowercaseChar() - 'a'
            val rank = uci[1] - '0'
            val sq = Square(file, rank)
            return if (sq.isValid) sq else null
        }
    }
}

data class Move(
    val from: Square,
    val to: Square
) {
    fun toUci(): String = "${from.toUci()}${to.toUci()}"

    companion object {
        fun fromUci(uci: String): Move? {
            val trimmed = uci.trim()
            if (trimmed.length != 4) return null
            val from = Square.fromUci(trimmed.substring(0, 2)) ?: return null
            val to = Square.fromUci(trimmed.substring(2, 4)) ?: return null
            if (from == to) return null
            return Move(from, to)
        }
    }
}

/**
 * Immutable snapshot of a Xiangqi board position.
 */
class BoardState private constructor(
    private val squares: Array<Piece?>,
    val sideToMove: Side,
    val halfMoveClock: Int,
    val fullMoveNumber: Int,
    val positionHash: Long
) {
    fun pieceAt(square: Square): Piece? =
        if (square.isValid) squares[square.index] else null

    fun pieceAt(file: Int, rank: Int): Piece? =
        if (file in 0 until Square.BOARD_FILES && rank in 0 until Square.BOARD_RANKS) {
            squares[rank * Square.BOARD_FILES + file]
        } else {
            null
        }

    fun pieceAtIndex(index: Int): Piece? = squares[index]

    fun findGeneral(side: Side): Square? {
        val rankRange = if (side == Side.RED) 0..2 else 7..9
        for (rank in rankRange) {
            for (file in 3..5) {
                val p = squares[rank * Square.BOARD_FILES + file]
                if (p != null && p.side == side && p.type == PieceType.GENERAL) {
                    return Square(file, rank)
                }
            }
        }
        // Fallback scan for arbitrary/imported boards
        for (idx in 0 until Square.BOARD_SQUARES) {
            val p = squares[idx]
            if (p != null && p.side == side && p.type == PieceType.GENERAL) {
                return Square.fromIndex(idx)
            }
        }
        return null
    }

    fun allPieces(side: Side? = null): List<Pair<Square, Piece>> {
        val result = ArrayList<Pair<Square, Piece>>(32)
        for (idx in 0 until Square.BOARD_SQUARES) {
            val p = squares[idx]
            if (p != null && (side == null || p.side == side)) {
                result.add(Square.fromIndex(idx) to p)
            }
        }
        return result
    }

    fun copyArray(): Array<Piece?> = squares.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BoardState) return false
        return sideToMove == other.sideToMove &&
            halfMoveClock == other.halfMoveClock &&
            fullMoveNumber == other.fullMoveNumber &&
            positionHash == other.positionHash &&
            squares.contentEquals(other.squares)
    }

    override fun hashCode(): Int {
        var result = squares.contentHashCode()
        result = 31 * result + sideToMove.hashCode()
        result = 31 * result + halfMoveClock
        result = 31 * result + fullMoveNumber
        return result
    }

    companion object {
        fun create(
            squares: Array<Piece?>,
            sideToMove: Side,
            halfMoveClock: Int = 0,
            fullMoveNumber: Int = 1,
            positionHash: Long
        ): BoardState {
            require(squares.size == Square.BOARD_SQUARES) {
                "Board array must have exactly ${Square.BOARD_SQUARES} squares"
            }
            return BoardState(
                squares = squares.copyOf(),
                sideToMove = sideToMove,
                halfMoveClock = halfMoveClock,
                fullMoveNumber = fullMoveNumber,
                positionHash = positionHash
            )
        }
    }
}

enum class GameMode(val vietnameseName: String, val description: String) {
    PRACTICE(
        "Luyện tập",
        "Cho phép đi lại không giới hạn, hỗ trợ học tập và rèn thế cờ."
    ),
    CHALLENGE(
        "Thử sức",
        "Không đi lại và tắt trợ giúp trong ván để đánh giá thực lực khách quan."
    )
}

enum class PlayerSideChoice(val vietnameseName: String) {
    RED("Cầm Đỏ (đi trước)"),
    BLACK("Cầm Đen (đi sau)"),
    RANDOM("Ngẫu nhiên")
}

enum class GameResult(val vietnameseLabel: String) {
    ONGOING("Đang diễn ra"),
    RED_WIN("Đỏ thắng"),
    BLACK_WIN("Đen thắng"),
    DRAW("Hòa")
}

enum class GameResultReason(val vietnameseLabel: String) {
    NONE(""),
    CHECKMATE("Chiếu bí"),
    STALEMATE("Hết nước đi hợp lệ (Khốn bí)"),
    RESIGNATION("Xin thua"),
    TIMEOUT("Hết giờ"),
    DRAW_REPETITION("Hòa do lặp thế 3 lần"),
    DRAW_SIXTY_MOVE_RULE("Hòa theo luật 60 nước không ăn quân"),
    DRAW_INSUFFICIENT_MATERIAL("Hòa do không đủ lực chiếu bí"),
    PERPETUAL_CHECK_LOSS("Xử thua do chiếu kéo dài (lặp 3 lần)"),
    PERPETUAL_CHASE_LOSS("Xử thua do đuổi quân kéo dài (lặp 3 lần)")
}

enum class GameStatus {
    IN_PROGRESS,
    FINISHED,
    ABANDONED
}

enum class AnalysisStatus {
    NOT_ANALYZED,
    IN_PROGRESS,
    PARTIAL,
    COMPLETED,
    INTERRUPTED,
    ERROR
}

enum class RulesetId(val displayName: String) {
    WXF_BASIC_V1("Luật Cờ Tướng WXF cơ bản (v1)")
}

enum class PieceDisplayStyle(val vietnameseName: String) {
    HANZI("Chữ Hán truyền thống"),
    VIETNAMESE("Tên tiếng Việt")
}

enum class BoardThemeStyle(val vietnameseName: String) {
    CLASSIC_WOOD("Gỗ truyền thống"),
    MINIMAL_DARK("Tối giản dễ đọc")
}

enum class NotationStyle(val vietnameseName: String) {
    SHORT("Rút gọn (P2-5)"),
    FULL("Đầy đủ (Pháo 2 bình 5)")
}

enum class EngineResourceMode(
    val vietnameseName: String,
    val description: String,
    val threads: Int,
    val hashMb: Int,
    val timeScale: Float
) {
    ECO("Tiết kiệm", "Ít CPU, mát máy và tiết kiệm pin hơn.", 1, 16, 0.7f),
    BALANCED("Cân bằng", "Mặc định hài hòa giữa tốc độ và sức mạnh.", 2, 32, 1.0f),
    DEEP("Phân tích sâu", "Tính toán sâu hơn, có thể tốn pin và ấm máy hơn.", 4, 64, 1.4f)
}

enum class ClockOption(val vietnameseName: String, val initialMillis: Long) {
    UNLIMITED("Không giới hạn", 0L),
    TEN_MINUTES("10 phút", 10L * 60_000L),
    FIFTEEN_MINUTES("15 phút", 15L * 60_000L),
    THIRTY_MINUTES("30 phút", 30L * 60_000L);

    val isTimed: Boolean get() = initialMillis > 0L

    companion object {
        fun fromMillis(millis: Long): ClockOption =
            entries.firstOrNull { it.initialMillis == millis } ?: UNLIMITED
    }
}

data class MoveRecord(
    val plyIndex: Int,
    val side: Side,
    val move: Move,
    val pieceType: PieceType,
    val capturedPieceType: PieceType?,
    val notationShort: String,
    val notationFull: String,
    val positionHashAfter: Long,
    val gaveCheck: Boolean = false,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun displayNotation(style: NotationStyle): String =
        if (style == NotationStyle.FULL) notationFull else notationShort
}

data class PositionWithHistory(
    val initialFen: String,
    val currentBoard: BoardState,
    val moves: List<MoveRecord>
)
