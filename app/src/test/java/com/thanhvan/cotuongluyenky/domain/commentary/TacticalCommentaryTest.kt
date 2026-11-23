package com.thanhvan.cotuongluyenky.domain.commentary

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalCommentaryTest {

    private fun boardFromFen(fen: String) =
        (FenCodec.parse(fen) as FenParseResult.Success).board

    @Test
    fun dangerAnalyzer_distinguishesDefendedAttackFromUnprotectedRisk() {
        // Red Chariot at a4 is attacked by Black Chariot at a8, AND defended by Red Chariot at a2.
        // Because attacker (Chariot 900) == victim (Chariot 900) and it IS defended -> UNDER_ATTACK, NOT AT_RISK_OF_LOSS!
        val defendedBoard = boardFromFen("3k5/r8/9/9/9/R8/9/R8/9/4K4 w - - 0 1")
        val defendedInfo = TacticalFactExtractor.analyzeDangerPieces(defendedBoard, Side.RED)
            .firstOrNull { it.square == Square.fromUci("a4") }
        assertNotNull(defendedInfo)
        assertEquals(DangerLevel.UNDER_ATTACK, defendedInfo!!.level)
        assertEquals(1, defendedInfo.attackers.size)
        assertEquals(1, defendedInfo.defenders.size)

        // Remove the defender at a2 -> Red Chariot at a4 is now undefended -> AT_RISK_OF_LOSS!
        val undefendedBoard = boardFromFen("3k5/r8/9/9/9/R8/9/9/9/4K4 w - - 0 1")
        val undefendedInfo = TacticalFactExtractor.analyzeDangerPieces(undefendedBoard, Side.RED)
            .firstOrNull { it.square == Square.fromUci("a4") }
        assertNotNull(undefendedInfo)
        assertEquals(DangerLevel.AT_RISK_OF_LOSS, undefendedInfo!!.level)
        assertTrue(undefendedInfo.defenders.isEmpty())
    }

    @Test
    fun commentary_usesSafeFallbackWhenNoSpecificTacticalFactIsVerified() {
        val board = RulesEngine.initialBoard()
        val bestMove = Move.fromUci("h2e2")!!
        val neutralFacts = TacticalFacts(
            playedMoveGaveCheck = false,
            playedMoveCaptured = null,
            missedForcedMate = false,
            allowedForcedMate = false,
            swingFromAdvantageToDisadvantage = false,
            exposedOwnPieceAtRisk = null,
            bestMoveGaveCheck = false,
            bestMoveCaptured = null,
            bestMoveSavedPiece = null,
            pvFirstCapturedTarget = null
        )

        val text = CommentaryService.buildMoveCommentary(
            boardBefore = board,
            classification = MoveClassification.MISTAKE,
            facts = neutralFacts,
            bestMove = bestMove
        )

        assertTrue("Safe commentary should reference recommended move", text.contains("Pháo 2 bình 5"))
        assertFalse("Must never fabricate mate claim when missedForcedMate is false", text.contains("chiếu bí"))
        assertFalse("Must never fabricate capture claim when no capture fact exists", text.contains("bắt đôi"))
    }

    @Test
    fun commentary_reportsMissedMateOnlyWhenVerified() {
        val board = RulesEngine.initialBoard()
        val facts = TacticalFacts(
            playedMoveGaveCheck = false,
            playedMoveCaptured = null,
            missedForcedMate = true,
            allowedForcedMate = false,
            swingFromAdvantageToDisadvantage = false,
            exposedOwnPieceAtRisk = null,
            bestMoveGaveCheck = true,
            bestMoveCaptured = null,
            bestMoveSavedPiece = null,
            pvFirstCapturedTarget = null
        )

        val text = CommentaryService.buildMoveCommentary(
            boardBefore = board,
            classification = MoveClassification.BLUNDER,
            facts = facts,
            bestMove = Move.fromUci("h2e2")!!
        )
        assertTrue(text.contains("chiếu bí"))
    }

    @Test
    fun tier1IdeaHint_adaptsToCheckCaptureAndQuietMoves() {
        // Check hint: Red Chariot at a8 can check Black General at e9 via a8a9
        val checkBoard = boardFromFen("4k4/R8/9/9/9/9/9/9/9/3K5 w - - 0 1")
        val checkHint = CommentaryService.buildTier1IdeaHint(checkBoard, Move.fromUci("a8a9")!!)
        assertTrue("Expected check hint", checkHint.contains("chiếu"))

        // Quiet opening move hint: h2e2 on initial board
        val quietHint = CommentaryService.buildTier1IdeaHint(RulesEngine.initialBoard(), Move.fromUci("h2e2")!!)
        assertFalse("Quiet move must not falsely mention check", quietHint.contains("chiếu"))
    }
}
