package com.thanhvan.cotuongluyenky.engine

import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineConfig
import com.thanhvan.cotuongluyenky.engine.api.EngineDifficulty
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.builtin.BuiltInXiangqiEngine
import com.thanhvan.cotuongluyenky.engine.manager.EngineManager
import com.thanhvan.cotuongluyenky.engine.manager.EngineMoveOutcome
import com.thanhvan.cotuongluyenky.engine.protocol.UciProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UciAndEngineTest {

    @Test
    fun uciProtocol_parsesInfoAndBestMoveLines() {
        val info = UciProtocol.parseInfoLine(
            "info depth 12 seldepth 18 multipv 2 score cp 64 nodes 154320 time 420 pv h2e2 h9g7 h0g2"
        )
        assertNotNull(info)
        assertEquals(12, info!!.depth)
        assertEquals(2, info.multiPv)
        assertEquals(EngineScore.Centipawns(64), info.score)
        assertEquals(154320L, info.nodes)
        assertEquals(listOf("h2e2", "h9g7", "h0g2"), info.pv.map { it.toUci() })

        val mateInfo = UciProtocol.parseInfoLine("info depth 8 score mate 2 pv a8a9 e9d9")
        assertEquals(EngineScore.Mate(2), mateInfo!!.score)

        val best = UciProtocol.parseBestMoveLine("bestmove h2e2 ponder h9g7")
        assertEquals("h2e2", best!!.bestMove?.toUci())
        assertEquals("h9g7", best.ponderMove?.toUci())
    }

    @Test
    fun builtInEngine_returnsLegalMoveFromStartPosition_andMultiPv() = runTest {
        val engine = BuiltInXiangqiEngine()
        engine.initialize(EngineConfig())

        val startBoard = RulesEngine.initialBoard()
        val pos = PositionWithHistory(FenCodec.INITIAL_FEN, startBoard, emptyList())
        val analysis = engine.analyze(pos, SearchLimit(moveTimeMs = 500L, maxDepth = 2), variations = 3)

        assertEquals(3, analysis.variations.size)
        for (variation in analysis.variations) {
            assertTrue(variation.moves.isNotEmpty())
            assertTrue(RulesEngine.isMoveLegal(startBoard, variation.moves.first()))
        }
    }

    @Test
    fun builtInEngine_findsForcedMateInOne() = runTest {
        // Black General at e9, Red Chariots at a8 and b7 -> a8a9 is immediate mate!
        // Put Black General at e9 with Black Advisors blocking d9/f9 or Chariot at b8 controlling rank 8.
        // Setup: Black General at e9, Red Chariot at b8 (controls rank 8), Red Chariot at a0 (can move a0a9 for mate!), Red General at d0.
        val fen = "4k4/1R7/9/9/9/9/9/9/9/R2K5 w - - 0 1"
        val board = (FenCodec.parse(fen) as FenParseResult.Success).board
        val pos = PositionWithHistory(fen, board, emptyList())

        val engine = BuiltInXiangqiEngine()
        engine.initialize(EngineConfig())
        val result = engine.findBestMove(pos, SearchLimit(moveTimeMs = 1000L, maxDepth = 2))

        assertTrue(RulesEngine.isMoveLegal(board, result.bestMove))
        val after = RulesEngine.applyMove(board, result.bestMove).newBoard
        assertTrue("Expected mate-in-1 leaving Black with no legal moves", RulesEngine.generateLegalMoves(after).isEmpty())
    }

    @Test
    fun engineManager_allTenLevels_returnStrictlyLegalMoves() = runTest {
        val manager = EngineManager(primaryEngine = null, fallbackEngine = BuiltInXiangqiEngine())
        val startBoard = RulesEngine.initialBoard()
        val pos = PositionWithHistory(FenCodec.INITIAL_FEN, startBoard, emptyList())

        for (level in 1..10) {
            val outcome = manager.requestMove(
                position = pos,
                level = level,
                resourceMode = EngineResourceMode.ECO
            )
            assertTrue("Level $level must succeed", outcome is EngineMoveOutcome.Success)
            val move = (outcome as EngineMoveOutcome.Success).result.bestMove
            assertTrue("Level $level move ${move.toUci()} must be legal", RulesEngine.isMoveLegal(startBoard, move))
        }
    }
}
