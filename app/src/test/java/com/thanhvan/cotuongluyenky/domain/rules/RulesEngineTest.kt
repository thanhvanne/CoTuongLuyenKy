package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {

    private fun boardFromFen(fen: String) =
        (FenCodec.parse(fen) as FenParseResult.Success).board

    private fun move(uci: String): Move = Move.fromUci(uci)!!

    @Test
    fun initialPosition_has32Pieces_and44LegalMovesForRed() {
        val board = RulesEngine.initialBoard()
        assertEquals(32, board.allPieces().size)
        assertEquals(Side.RED, board.sideToMove)
        assertFalse(RulesEngine.isInCheck(board, Side.RED))
        assertFalse(RulesEngine.isInCheck(board, Side.BLACK))

        // Standard Xiangqi perft(1) from initial position is 44 legal moves
        val legalMoves = RulesEngine.generateLegalMoves(board)
        assertEquals(44, legalMoves.size)
    }

    @Test
    fun horse_blockedByLeg_cannotJumpOverBlockingPiece() {
        // Red Horse at e4, friendly Red Soldier at e5 blocks upward jumps (d6, f6),
        // while side & downward jumps remain open.
        val board = boardFromFen("3k5/9/9/9/4P4/4N4/9/9/9/4K4 w - - 0 1")
        val horseMoves = RulesEngine.generateLegalMovesFrom(board, Square.fromUci("e4")!!)
            .map { it.to.toUci() }
            .toSet()

        assertFalse("Upward-left jump d6 must be blocked by leg e5", "d6" in horseMoves)
        assertFalse("Upward-right jump f6 must be blocked by leg e5", "f6" in horseMoves)
        assertTrue("Right-up jump g5 should be legal", "g5" in horseMoves)
        assertTrue("Left-up jump c5 should be legal", "c5" in horseMoves)
        assertTrue("Downward-left jump d2 should be legal", "d2" in horseMoves)
        assertTrue("Downward-right jump f2 should be legal", "f2" in horseMoves)
    }

    @Test
    fun elephant_cannotCrossRiver_andBlockedByEye() {
        // Red Elephant at e2 (center of Red's half): can reach c4, g4, c0, g0 unless eye blocked.
        // Put a blocking piece at d3 (blocks c4).
        val board = boardFromFen("3k5/9/9/9/9/9/3P5/4B4/9/4K4 w - - 0 1")
        val elephantMoves = RulesEngine.generateLegalMovesFrom(board, Square.fromUci("e2")!!)
            .map { it.to.toUci() }
            .toSet()

        assertFalse("c4 must be blocked by eye at d3", "c4" in elephantMoves)
        assertTrue("g4 should be legal", "g4" in elephantMoves)
        assertTrue("c0 should be legal", "c0" in elephantMoves)
        assertTrue("g0 should be legal", "g0" in elephantMoves)

        // Elephant at c4 (on river bank) CANNOT move to a6 or e6 across the river
        val bankBoard = boardFromFen("3k5/9/9/9/9/2B6/9/9/9/4K4 w - - 0 1")
        val bankMoves = RulesEngine.generateLegalMovesFrom(bankBoard, Square.fromUci("c4")!!)
        assertTrue(bankMoves.all { it.to.rank <= 4 })
    }

    @Test
    fun cannon_requiresExactlyOneScreenToCapture_andZeroScreensToSlide() {
        // File e: Black Chariot e9, Black Soldier e7, Red Soldier e5, Red Cannon e2
        // Wait: keep Generals off file e so no facing-general issue!
        val board = boardFromFen("3k1r3/9/4p4/9/4P4/9/9/4C4/9/3K5 w - - 0 1")
        // Add an enemy piece at e9 too:
        val board2 = boardFromFen("3kr4/9/4p4/9/4P4/9/9/4C4/9/3K5 w - - 0 1")
        val cannonMoves = RulesEngine.generateLegalMovesFrom(board2, Square.fromUci("e2")!!)
            .map { it.to.toUci() }
            .toSet()

        // Can slide to e3, e4 (empty, 0 screens)
        assertTrue("e3" in cannonMoves)
        assertTrue("e4" in cannonMoves)
        // Cannot capture own piece e5
        assertFalse("e5" in cannonMoves)
        // Cannot land on empty e6 after jumping screen e5
        assertFalse("e6" in cannonMoves)
        // CAN capture Black Soldier at e7 via screen e5!
        assertTrue("Cannon must capture e7 using e5 as screen", "e7" in cannonMoves)
        // CANNOT capture Black Chariot at e9 (would require jumping 2 screens: e5 and e7)
        assertFalse("Cannon must not jump two screens to capture e9", "e9" in cannonMoves)
    }

    @Test
    fun soldier_beforeAndAfterRiverMovement() {
        // Red Soldier at c3 (own half): only forward to c4
        val ownHalf = boardFromFen("3k5/9/9/9/9/9/2P6/9/9/4K4 w - - 0 1")
        val ownHalfMoves = RulesEngine.generateLegalMovesFrom(ownHalf, Square.fromUci("c3")!!)
            .map { it.to.toUci() }
            .toSet()
        assertEquals(setOf("c4"), ownHalfMoves)

        // Red Soldier at c5 (crossed river): forward c6, left b5, right d5; never backward c4
        val crossed = boardFromFen("3k5/9/9/9/2P6/9/9/9/9/4K4 w - - 0 1")
        val crossedMoves = RulesEngine.generateLegalMovesFrom(crossed, Square.fromUci("c5")!!)
            .map { it.to.toUci() }
            .toSet()
        assertEquals(setOf("c6", "b5", "d5"), crossedMoves)
    }

    @Test
    fun generalAndAdvisor_stayInsidePalace() {
        val board = boardFromFen("3k5/9/9/9/9/9/9/9/4A4/4K4 w - - 0 1")
        val advisorMoves = RulesEngine.generateLegalMovesFrom(board, Square.fromUci("e1")!!)
        assertTrue(advisorMoves.all { it.to.isInPalace(Side.RED) })
        assertEquals(4, advisorMoves.size)

        val generalMoves = RulesEngine.generateLegalMovesFrom(board, Square.fromUci("e0")!!)
        assertTrue(generalMoves.all { it.to.isInPalace(Side.RED) })
        // e0 can go to d0 or f0 (e1 is occupied by Advisor)
        // Wait: Black General is at d9 -> moving Red General to d0 would face Black General!
        assertEquals(setOf("f0"), generalMoves.map { it.to.toUci() }.toSet())
    }

    @Test
    fun flyingGeneral_preventsExposingGeneralsOnSameFile() {
        // Black General e9, Red Chariot e4 (only blocker), Red General e0
        val board = boardFromFen("4k4/9/9/9/9/4R4/9/9/9/4K4 w - - 0 1")
        val rookMoves = RulesEngine.generateLegalMovesFrom(board, Square.fromUci("e4")!!)
        // Red Chariot can only move vertically along file e, NEVER leave file e!
        assertTrue(rookMoves.isNotEmpty())
        assertTrue("Pinned Chariot cannot leave file e due to Flying General rule", rookMoves.all { it.to.file == 4 })
    }

    @Test
    fun checkmate_detectedCorrectly() {
        // Black General at e9 is checkmated by Red Chariot at e8 protected by Red General at e0 (Wait: e8 blocks facing, and Red Chariot at d8/f8 or Red Chariot at a9 + b8).
        // Classic two-chariot mate: Black General at e9, Red Chariots at a9 and b8, Red General at d0.
        val board = boardFromFen("R3k4/1R7/9/9/9/9/9/9/9/3K5 b - - 0 1")
        assertTrue(RulesEngine.isInCheck(board, Side.BLACK))
        assertTrue(RulesEngine.generateLegalMoves(board).isEmpty())

        val terminal = RulesEngine.evaluateTerminalState(board, emptyList(), board.positionHash)
        assertEquals(GameResult.RED_WIN, terminal.result)
        assertEquals(GameResultReason.CHECKMATE, terminal.reason)
    }

    @Test
    fun stalemate_inXiangqi_isALossForSideWithNoLegalMoves() {
        // Black General at d9 is NOT in check, but:
        // - e9 is attacked by Red Chariot on e1 (Wait: put Red Chariot on e5 and Red General on d0? No, d0 faces d9!)
        // Put Black General at d9, Red General at e0, Red Chariot at e5 (controls file e), Red Chariot at a8 (controls rank 8).
        // Is Black General at d9 in check? No!
        // Can Black General go to e9? Attacked by Red Chariot e5.
        // Can Black General go to d8? Attacked by Red Chariot a8.
        // Therefore Black has ZERO legal moves while NOT in check => Stalemate (Khốn bí) => RED_WIN!
        val board = boardFromFen("3k5/R8/9/9/4R4/9/9/9/9/4K4 b - - 0 1")
        assertFalse("Black General must NOT be in check for stalemate test", RulesEngine.isInCheck(board, Side.BLACK))
        assertTrue("Black must have no legal moves", RulesEngine.generateLegalMoves(board).isEmpty())

        val terminal = RulesEngine.evaluateTerminalState(board, emptyList(), board.positionHash)
        assertEquals(GameResult.RED_WIN, terminal.result)
        assertEquals(GameResultReason.STALEMATE, terminal.reason)
    }

    @Test
    fun sixtyMoveRule_andInsufficientMaterial_areDraws() {
        // 60-move rule (120 plies) with chariots still on board
        val sixtyMoveBoard = boardFromFen("3k5/9/9/9/9/9/9/9/4r4/4K2R1 w - - 120 70")
        val terminal60 = RulesEngine.evaluateTerminalState(sixtyMoveBoard, emptyList(), sixtyMoveBoard.positionHash)
        assertEquals(GameResult.DRAW, terminal60.result)
        assertEquals(GameResultReason.DRAW_SIXTY_MOVE_RULE, terminal60.reason)

        // Insufficient material: only Generals + Advisors + Elephants
        val insuffBoard = boardFromFen("3k1a3/9/4b4/9/9/9/9/4B4/4A4/4K4 w - - 0 40")
        val terminalInsuff = RulesEngine.evaluateTerminalState(insuffBoard, emptyList(), insuffBoard.positionHash)
        assertEquals(GameResult.DRAW, terminalInsuff.result)
        assertEquals(GameResultReason.DRAW_INSUFFICIENT_MATERIAL, terminalInsuff.reason)
    }

    @Test
    fun repetition_nonChecking_isDraw_andPerpetualCheck_isLossForChecker() {
        // 1. Non-checking 3-fold repetition -> DRAW_REPETITION
        val quietStart = boardFromFen("3k5/9/9/9/9/9/9/9/r8/4K2R1 w - - 0 1")
        val quietSeq = listOf("h0g0", "a1b1", "g0h0", "b1a1", "h0g0", "a1b1", "g0h0", "b1a1")
        val quietReconstructed = RulesEngine.reconstructGame(FenCodec.toFen(quietStart), quietSeq).getOrThrow()
        assertEquals(GameResult.DRAW, quietReconstructed.terminal.result)
        assertEquals(GameResultReason.DRAW_REPETITION, quietReconstructed.terminal.reason)

        // 2. Perpetual check by Red Chariot chasing Black General back and forth 3 times -> BLACK_WIN (PERPETUAL_CHECK_LOSS)
        // Black General at d9, Red Chariot at e5, Red General at f0.
        // Red checks d5 -> Black flees e9 -> Red checks e5 -> Black flees d9 (cycle repeated twice -> 3 occurrences of start!)
        val checkStart = boardFromFen("3k5/9/9/9/4R4/9/9/9/9/5K3 w - - 0 1")
        val checkSeq = listOf(
            "e5d5", "d9e9",
            "d5e5", "e9d9",
            "e5d5", "d9e9",
            "d5e5", "e9d9"
        )
        val checkReconstructed = RulesEngine.reconstructGame(FenCodec.toFen(checkStart), checkSeq).getOrThrow()
        assertEquals(GameResult.BLACK_WIN, checkReconstructed.terminal.result)
        assertEquals(GameResultReason.PERPETUAL_CHECK_LOSS, checkReconstructed.terminal.reason)
    }

    @Test
    fun reconstructGame_rejectsIllegalMove() {
        // Trying to jump Horse h0 -> h2 (illegal)
        val result = RulesEngine.reconstructGame(FenCodec.INITIAL_FEN, listOf("h0h2"))
        assertTrue(result.isFailure)
    }
}
