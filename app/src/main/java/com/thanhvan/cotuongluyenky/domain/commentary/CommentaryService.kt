package com.thanhvan.cotuongluyenky.domain.commentary

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.rules.XiangqiNotation

/**
 * Generates concise, grounded Vietnamese commentary strictly from verified [TacticalFacts]
 * and [MoveClassification] (Sections 10.1, 12.1–12.5, 29).
 *
 * Core safety rule: never assert a forced loss, double attack, or mate unless the corresponding
 * verified fact is true; otherwise emit an honest safe template referencing the illustration line.
 */
object CommentaryService {

    const val VARIATION_DISCLAIMER =
        "Đây là một phương án engine dự kiến, không phải đối thủ bắt buộc đi như vậy."

    const val SCORE_DISCLAIMER =
        "Điểm đánh giá là giá trị tham khảo của engine, không phải xác suất thắng chắc chắn."

    fun buildMoveCommentary(
        boardBefore: BoardState,
        classification: MoveClassification,
        facts: TacticalFacts,
        bestMove: Move?,
        notationStyle: NotationStyle = NotationStyle.FULL
    ): String {
        val bestNotation = bestMove
            ?.takeIf { RulesEngine.isMoveLegal(boardBefore, it) }
            ?.let {
                val formatted = XiangqiNotation.format(boardBefore, it)
                if (notationStyle == NotationStyle.FULL) formatted.full else formatted.short
            }

        // Priority 1: Mate-related verified events
        if (facts.missedForcedMate) {
            val suffix = if (bestNotation != null) " Phương án tham khảo: $bestNotation." else ""
            return "Engine đã tìm thấy một biến chiếu bí từ thế trước nước này.$suffix Hãy xem biến minh họa."
        }
        if (facts.allowedForcedMate) {
            val suffix = if (bestNotation != null) " Engine đề xuất $bestNotation." else ""
            return "Sau nước này, đối thủ có biến chiếu bí theo tính toán của engine.$suffix"
        }

        // Priority 2: Classification-specific grounded statements
        return when (classification) {
            MoveClassification.GOOD -> when {
                facts.playedMoveGaveCheck && facts.playedMoveCaptured != null ->
                    "Nước đi tốt: vừa bắt ${facts.playedMoveCaptured.vietnameseName} vừa chiếu Tướng, duy trì sức ép trực tiếp."
                facts.playedMoveGaveCheck ->
                    "Nước đi tốt: tạo nước chiếu giữ thế chủ động."
                facts.playedMoveCaptured != null ->
                    "Nước đi tốt: ăn ${facts.playedMoveCaptured.vietnameseName} hợp lý theo đánh giá của engine."
                else ->
                    "Nước đi tốt, giữ vững thế trận theo đánh giá của engine."
            }

            MoveClassification.ACCEPTABLE -> when {
                bestNotation != null ->
                    "Nước đi chấp nhận được. Engine hơi ưu tiên phương án $bestNotation hơn."
                else ->
                    "Nước đi chấp nhận được và vẫn duy trì thế cân bằng tương đối."
            }

            MoveClassification.INACCURACY -> when {
                facts.exposedOwnPieceAtRisk != null -> {
                    val pieceName = facts.exposedOwnPieceAtRisk.second.vietnameseName
                    val rec = if (bestNotation != null) " Engine đề xuất $bestNotation." else ""
                    "Nước đi chưa chính xác: $pieceName của bạn hiện đang bị đe dọa.$rec"
                }
                facts.bestMoveGaveCheck && bestNotation != null ->
                    "Nước đi chưa chính xác. Phương án chiếu $bestNotation giúp tạo áp lực rõ rệt hơn."
                facts.bestMoveCaptured != null && bestNotation != null ->
                    "Nước đi chưa chính xác. Engine đánh giá phương án $bestNotation (bắt ${facts.bestMoveCaptured.vietnameseName}) tốt hơn."
                bestNotation != null ->
                    "Nước này cho đối thủ thêm cơ hội phản công. Engine đề xuất $bestNotation."
                else ->
                    "Engine đánh giá nước này hơi kém hơn phương án tối ưu. Hãy xem biến minh họa."
            }

            MoveClassification.MISTAKE, MoveClassification.BLUNDER -> {
                val severityLabel = if (classification == MoveClassification.BLUNDER) {
                    "Sai lầm nghiêm trọng"
                } else {
                    "Sai lầm"
                }
                when {
                    facts.exposedOwnPieceAtRisk != null -> {
                        val pieceName = facts.exposedOwnPieceAtRisk.second.vietnameseName
                        val rec = if (bestNotation != null) " Nên xem phương án $bestNotation." else ""
                        "$severityLabel: sau nước này, $pieceName của bạn đang bị tấn công và có nguy cơ gặp bất lợi.$rec"
                    }
                    facts.swingFromAdvantageToDisadvantage && bestNotation != null ->
                        "$severityLabel: nước đi làm mất lợi thế và chuyển sang thế bất lợi theo đánh giá engine. Engine đề xuất $bestNotation."
                    facts.bestMoveSavedPiece != null && bestNotation != null ->
                        "$severityLabel: bạn đã bỏ lỡ phương án $bestNotation để giữ an toàn cho ${facts.bestMoveSavedPiece.vietnameseName}."
                    facts.bestMoveCaptured != null && bestNotation != null ->
                        "$severityLabel: bỏ lỡ phương án $bestNotation bắt ${facts.bestMoveCaptured.vietnameseName}. Hãy xem biến minh họa."
                    bestNotation != null ->
                        "Engine đánh giá nước này kém rõ rệt hơn phương án đề xuất ($bestNotation). Hãy xem biến minh họa."
                    else ->
                        "Phương án của bạn được engine đánh giá thấp hơn rõ rệt. Hãy xem biến minh họa."
                }
            }
        }
    }

    /**
     * Generates a Tier 1 Idea Hint strictly grounded in board facts (Section 10.1).
     */
    fun buildTier1IdeaHint(board: BoardState, bestMove: Move): String {
        if (!RulesEngine.isMoveLegal(board, bestMove)) {
            return "Hãy xem kỹ các nước có thể tạo sức ép."
        }
        val mover = board.sideToMove
        val captured = board.pieceAt(bestMove.to)
        val afterBest = RulesEngine.applyMoveUnchecked(board, bestMove)
        val givesCheck = RulesEngine.isInCheck(afterBest, mover.opposite())

        val endangeredBefore = TacticalFactExtractor.analyzeDangerPieces(board, mover)
            .filter { it.level != DangerLevel.UNDER_ATTACK }
            .map { it.square }
            .toSet()

        val savesOwnPiece = bestMove.from in endangeredBefore &&
            TacticalFactExtractor.findAttackersOf(afterBest, bestMove.to, mover.opposite()).isEmpty()

        return when {
            givesCheck && captured != null ->
                "Hãy tìm một nước vừa chiếu Tướng vừa bắt quân để tạo sức ép lớn."
            givesCheck ->
                "Hãy tìm một nước chiếu Tướng có thể giành thế chủ động."
            savesOwnPiece ->
                "Có một quân của bạn đang gặp nguy hiểm — hãy ưu tiên đưa quân đó về vị trí an toàn."
            captured != null ->
                "Hãy quan sát xem có quân nào của đối thủ đang sơ hở có thể ăn được."
            else ->
                "Hãy xem kỹ các nước có thể cải thiện vị trí quân và tạo sức ép lên đối thủ."
        }
    }
}
