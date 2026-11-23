package com.thanhvan.cotuongluyenky.domain.commentary

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 sample-position test suite for grounded advanced tactical commentary (Section 24.6 & 26):
 * - Double attack (Bắt đôi)
 * - Cannon screen gained/lost (Pháo có/mất ngòi)
 * - Blocked horse legs (Mã bị cản chân)
 * - Strategic position with safe non-hallucinated commentary
 */
class AdvancedTacticalCommentaryTest {

    private fun boardFromFen(fen: String) =
        (FenCodec.parse(fen) as FenParseResult.Success).board

    @Test
    fun detectFork_identifiesHorseCheckForkOnGeneralAndChariot() {
        // Black King f9, Black Chariot b7, Red Horse e6 -> e6d8 checks f9 AND attacks b7
        val board = boardFromFen("5k3/9/1r7/4N4/9/9/9/9/9/3K5 w - - 0 1")
        val fork = TacticalFactExtractor.detectFork(board, Move.fromUci("e6d8")!!)
        assertNotNull("Expected verified horse fork", fork)
        assertEquals(PieceType.HORSE, fork!!.attackerType)
        assertTrue(fork.targets.any { it.second == PieceType.GENERAL })
        assertTrue(fork.targets.any { it.second == PieceType.CHARIOT })

        val facts = TacticalFactExtractor.extractMoveFacts(
            boardBefore = board,
            playedMove = Move.fromUci("e6d8")!!,
            boardAfter = RulesEngine.applyMoveUnchecked(board, Move.fromUci("e6d8")!!),
            bestMove = Move.fromUci("e6d8")!!,
            bestPv = listOf(Move.fromUci("e6d8")!!),
            missedForcedMate = false,
            allowedForcedMate = false,
            swingFromAdvantageToDisadvantage = false
        )
        val text = CommentaryService.buildMoveCommentary(
            boardBefore = board,
            classification = MoveClassification.GOOD,
            facts = facts,
            bestMove = Move.fromUci("e6d8")!!
        )
        assertTrue("Commentary should mention grounded fork", text.contains("bắt đôi"))
    }

    @Test
    fun detectCannonScreenEvent_detectsOwnCannonLosingScreen() {
        // Red Cannon at e2 uses Red Soldier at e5 as screen to attack Black Chariot at e8.
        // If Red moves Soldier e5->d5, Red Cannon loses its screen aiming at Black Chariot!
        val boardBefore = boardFromFen("3k5/4r4/9/9/4P4/9/9/4C4/9/4K4 w - - 0 1")
        val move = Move.fromUci("e5d5")!!
        val boardAfter = RulesEngine.applyMoveUnchecked(boardBefore, move)

        val event = TacticalFactExtractor.detectCannonScreenEvent(boardBefore, move, boardAfter)
        assertNotNull("Expected OWN_CANNON_LOST_SCREEN event", event)
        assertEquals(CannonScreenEventType.OWN_CANNON_LOST_SCREEN, event!!.type)
        assertEquals(PieceType.CHARIOT, event.affectedTargetType)
    }

    @Test
    fun detectCannonScreenEvent_detectsOpponentCannonGainingScreen() {
        // Black Cannon at a8, Red Chariot at a2, empty between them.
        // Red moves Soldier b5->a5, accidentally creating a screen on file a so Black Cannon now attacks Red Chariot!
        val boardBefore = boardFromFen("3k5/c8/9/9/1P7/9/9/R8/9/4K4 w - - 0 1")
        val move = Move.fromUci("b5a5")!!
        val boardAfter = RulesEngine.applyMoveUnchecked(boardBefore, move)

        val event = TacticalFactExtractor.detectCannonScreenEvent(boardBefore, move, boardAfter)
        assertNotNull("Expected OPPONENT_CANNON_GAINED_SCREEN_ON_VALUABLE event", event)
        assertEquals(CannonScreenEventType.OPPONENT_CANNON_GAINED_SCREEN_ON_VALUABLE, event!!.type)
        assertEquals(PieceType.CHARIOT, event.affectedTargetType)
    }

    @Test
    fun inspectHorseBlocking_countsBlockedLegsAccurately() {
        // Red Horse at e4 surrounded orthogonally on 3 legs: e5 (Soldier), d4 (Soldier), f4 (Soldier)
        val board = boardFromFen("3k5/9/9/9/4P4/3PNP3/9/9/9/4K4 w - - 0 1")
        val info = TacticalFactExtractor.inspectHorseBlocking(board, Square.fromUci("e4")!!)
        assertNotNull(info)
        assertEquals(3, info!!.blockedDirections)
        assertTrue("Heavily leg-blocked horse should have very few legal moves", info.legalMoveCount <= 2)
    }

    @Test
    fun strategicQuietPosition_neverFabricatesForkOrScreenClaims() {
        val board = RulesEngine.initialBoard()
        val move = Move.fromUci("a3a4")!!
        val after = RulesEngine.applyMoveUnchecked(board, move)

        assertNull(TacticalFactExtractor.detectFork(board, move))
        assertNull(TacticalFactExtractor.detectCannonScreenEvent(board, move, after))

        val facts = TacticalFactExtractor.extractMoveFacts(
            boardBefore = board,
            playedMove = move,
            boardAfter = after,
            bestMove = Move.fromUci("h2e2")!!,
            bestPv = listOf(Move.fromUci("h2e2")!!),
            missedForcedMate = false,
            allowedForcedMate = false,
            swingFromAdvantageToDisadvantage = false
        )
        val commentary = CommentaryService.buildMoveCommentary(
            boardBefore = board,
            classification = MoveClassification.INACCURACY,
            facts = facts,
            bestMove = Move.fromUci("h2e2")!!
        )
        assertFalse(commentary.contains("bắt đôi"))
        assertFalse(commentary.contains("ngòi"))
        assertFalse(commentary.contains("chiếu bí"))
    }
}
