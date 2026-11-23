package com.thanhvan.cotuongluyenky.domain.rules

import com.thanhvan.cotuongluyenky.core.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FenAndNotationTest {

    @Test
    fun initialFen_roundTripsExactly() {
        val parsed = FenCodec.parse(FenCodec.INITIAL_FEN)
        assertTrue(parsed is FenParseResult.Success)
        val board = (parsed as FenParseResult.Success).board
        assertEquals(FenCodec.INITIAL_FEN, FenCodec.toFen(board))
    }

    @Test
    fun fenParser_rejectsMalformedAndIllegalBoards() {
        assertTrue(FenCodec.parse("") is FenParseResult.Error)
        // Only 9 ranks
        assertTrue(FenCodec.parse("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9 w - - 0 1") is FenParseResult.Error)
        // Missing Black General
        assertTrue(FenCodec.parse("rnb1kabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1") is FenParseResult.Success)
        assertTrue(FenCodec.parse("rnba1abnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1") is FenParseResult.Error)
        // Facing Generals
        assertTrue(FenCodec.parse("4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1") is FenParseResult.Error)
        // Elephant across river
        assertTrue(FenCodec.parse("3k5/9/9/4B4/9/9/9/9/9/4K4 w - - 0 1") is FenParseResult.Error)
        // Side NOT to move already in check
        assertTrue(FenCodec.parse("3k5/9/9/9/9/9/9/9/3R5/4K4 w - - 0 1") is FenParseResult.Error)
    }

    @Test
    fun notation_formatsOpeningMovesForBothRedAndBlack() {
        val start = RulesEngine.initialBoard()

        // Red Central Cannon: h2 -> e2 = P2-5 / Pháo 2 bình 5
        val p2b5 = XiangqiNotation.format(start, Move.fromUci("h2e2")!!)
        assertEquals("P2-5", p2b5.short)
        assertEquals("Pháo 2 bình 5", p2b5.full)

        // Red Horse: h0 -> g2 = M2.3 / Mã 2 tấn 3
        val m2t3 = XiangqiNotation.format(start, Move.fromUci("h0g2")!!)
        assertEquals("M2.3", m2t3.short)
        assertEquals("Mã 2 tấn 3", m2t3.full)

        // Apply P2-5, then Black replies Horse b9 -> c7 (Black column 2 to column 3: M2.3)
        val afterRed = RulesEngine.applyMove(start, Move.fromUci("h2e2")!!).newBoard
        val blackHorse = XiangqiNotation.format(afterRed, Move.fromUci("b9c7")!!)
        assertEquals("M2.3", blackHorse.short)
        assertEquals("Mã 2 tấn 3", blackHorse.full)

        // Black Cannon b7 -> e7 (Black column 2 to column 5: P2-5)
        val blackCannon = XiangqiNotation.format(afterRed, Move.fromUci("b7e7")!!)
        assertEquals("P2-5", blackCannon.short)
        assertEquals("Pháo 2 bình 5", blackCannon.full)
    }

    @Test
    fun notation_disambiguatesTwoPiecesOnSameFile() {
        // Two Red Chariots on file i (Red column 1): at i5 (front) and i1 (back)
        val board = (FenCodec.parse("3k5/9/9/9/8R/9/9/9/8R/4K4 w - - 0 1") as FenParseResult.Success).board

        val frontMove = XiangqiNotation.format(board, Move.fromUci("i5i7")!!)
        assertEquals("Tiền X.2", frontMove.short)
        assertEquals("Tiền Xe tấn 2", frontMove.full)

        val backMove = XiangqiNotation.format(board, Move.fromUci("i1h1")!!)
        assertEquals("Hậu X-2", backMove.short)
        assertEquals("Hậu Xe bình 2", backMove.full)
    }
}
