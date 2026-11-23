package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 tests for WXF perpetual chase vs perpetual check vs threefold repetition draw
 * (Section 5.6 & docs/RULESET_SPEC.md).
 */
class PerpetualChaseRulesTest {

    @Test
    fun unilateralRookChasingUnprotectedHorse_triggersPerpetualChaseLoss() {
        // Rank 9: 4k4 (Black King e9)
        // Rank 8: 9
        // Rank 7: 1n7 (Black Horse b7, unprotected)
        // Rank 6: 1R7 (Red Rook b6 attacking b7)
        // Rank 0: 3K5 (Red King d0 — off e-file so no facing kings)
        // Black Horse toggles b7 <-> d8; Red Rook chases b6 <-> d6!
        val fen = "4k4/9/1n7/1R7/9/9/9/9/9/3K5 b - - 0 1"
        val chaseCycle = listOf(
            "b7d8", "b6d6",
            "d8b7", "d6b6",
            "b7d8", "b6d6",
            "d8b7", "d6b6"
        )

        val result = RulesEngine.reconstructGame(fen, chaseCycle)
        assertTrue("Reconstruct must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val reconstructed = result.getOrThrow()

        assertEquals(GameResult.BLACK_WIN, reconstructed.terminal.result)
        assertEquals(GameResultReason.PERPETUAL_CHASE_LOSS, reconstructed.terminal.reason)
    }

    @Test
    fun kingIdleOscillationWithoutChase_triggersThreefoldRepetitionDraw() {
        // Red King d0 <-> d1, Black King f9 <-> f8 (different files -> never facing, no checks/chases).
        val fen = "5k3/9/9/9/9/9/9/9/9/3K5 w - - 0 1"
        val idleCycle = listOf(
            "d0d1", "f9f8",
            "d1d0", "f8f9",
            "d0d1", "f9f8",
            "d1d0", "f8f9"
        )

        val result = RulesEngine.reconstructGame(fen, idleCycle)
        assertTrue("Reconstruct must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val reconstructed = result.getOrThrow()

        assertEquals(GameResult.DRAW, reconstructed.terminal.result)
        assertEquals(GameResultReason.DRAW_REPETITION, reconstructed.terminal.reason)
    }

    @Test
    fun soldierChasingRook_isExemptAndTriggersThreefoldRepetitionDraw() {
        // Rank 9: 5k3 (Black King f9)
        // Rank 7: 4r4 (Black Rook e7)
        // Rank 6: 4P4 (Red crossed Soldier e6 attacking e7)
        // Rank 0: 3K5 (Red King d0)
        // Under WXF, Generals and Soldiers are exempt from perpetual chase loss -> Draw!
        val soldierChaseFen = "5k3/9/4r4/4P4/9/9/9/9/9/3K5 b - - 0 1"
        val cycle = listOf(
            "e7d7", "e6d6",
            "d7e7", "d6e6",
            "e7d7", "e6d6",
            "d7e7", "d6e6"
        )

        val result = RulesEngine.reconstructGame(soldierChaseFen, cycle)
        assertTrue("Reconstruct must succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val reconstructed = result.getOrThrow()

        assertEquals(GameResult.DRAW, reconstructed.terminal.result)
        assertEquals(GameResultReason.DRAW_REPETITION, reconstructed.terminal.reason)
        assertEquals(8, reconstructed.moves.size)
    }
}
