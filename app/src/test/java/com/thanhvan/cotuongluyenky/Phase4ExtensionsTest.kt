package com.thanhvan.cotuongluyenky

import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import com.thanhvan.cotuongluyenky.core.model.GameStatus
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.RulesetId
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveAnalysisEntity
import com.thanhvan.cotuongluyenky.data.repository.GameSummary
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.analysis.ReferenceAccuracyCalculator
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.training.AdaptiveDifficultyAdvisor
import com.thanhvan.cotuongluyenky.domain.training.SeedPuzzleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 verification tests:
 * - Expanded 15-puzzle catalog (FEN + UCI solution legality)
 * - AdaptiveDifficultyAdvisor ("Máy vừa sức", Section 7.3)
 * - ReferenceAccuracyCalculator ("ref-acc-v1", Section 16.3)
 */
class Phase4ExtensionsTest {

    @Test
    fun allSeedPuzzlesHaveValidFenAndLegalSolutionLines() {
        val seeds = SeedPuzzleCatalog.allSpecs
        assertTrue("Expected at least 15 curated puzzles in Phase 4", seeds.size >= 15)

        val uniqueCodes = seeds.map { it.catalogKey }.toSet()
        assertEquals("Puzzle catalogKeys must be unique", seeds.size, uniqueCodes.size)

        seeds.forEach { seed ->
            val parsed = FenCodec.parse(seed.initialFen)
            assertTrue("Invalid FEN for ${seed.catalogKey}: ${seed.initialFen}", parsed is FenParseResult.Success)
            val initialBoard = (parsed as FenParseResult.Success).board

            assertTrue("Accepted moves empty for ${seed.catalogKey}", seed.acceptedMoves.isNotEmpty())
            seed.acceptedMoves.forEach { uci ->
                val move = Move.fromUci(uci)
                assertNotNull("Malformed accepted UCI '$uci' in ${seed.catalogKey}", move)
                assertTrue(
                    "Illegal accepted move '$uci' in ${seed.catalogKey}",
                    RulesEngine.isMoveLegal(initialBoard, move!!)
                )
            }

            assertTrue("Solution variations empty for ${seed.catalogKey}", seed.solutionVariations.isNotEmpty())
            seed.solutionVariations.forEachIndexed { varIdx, line ->
                var board = initialBoard
                line.forEachIndexed { stepIdx, uci ->
                    val move = Move.fromUci(uci)
                    assertNotNull("Malformed UCI '$uci' in ${seed.catalogKey} var $varIdx step $stepIdx", move)
                    assertTrue(
                        "Illegal move '$uci' at var $varIdx step $stepIdx of ${seed.catalogKey} from FEN ${FenCodec.toFen(board)}",
                        RulesEngine.isMoveLegal(board, move!!)
                    )
                    board = RulesEngine.applyMove(board, move).newBoard
                }
            }
        }
    }

    @Test
    fun adaptiveDifficultyAdvisor_suggestsLevelUpOnStrongCleanWinStreak() {
        val games = (1..4).map { idx ->
            sampleSummary(
                id = idx.toLong(),
                level = 3,
                result = GameResult.RED_WIN,
                playerSide = Side.RED,
                hints = 0,
                undos = 0
            )
        }
        val suggestion = AdaptiveDifficultyAdvisor.suggest(games, currentLevel = 3)
        assertNotNull(suggestion)
        assertEquals(4, suggestion!!.suggestedLevel)
    }

    @Test
    fun adaptiveDifficultyAdvisor_suggestsLevelDownOnHeavyLossStreak() {
        val games = (1..4).map { idx ->
            sampleSummary(
                id = idx.toLong(),
                level = 5,
                result = GameResult.BLACK_WIN,
                playerSide = Side.RED,
                hints = 1,
                undos = 0
            )
        }
        val suggestion = AdaptiveDifficultyAdvisor.suggest(games, currentLevel = 5)
        assertNotNull(suggestion)
        assertEquals(4, suggestion!!.suggestedLevel)
    }

    @Test
    fun adaptiveDifficultyAdvisor_returnsNullWhenTooFewGames() {
        val games = listOf(
            sampleSummary(1L, 3, GameResult.RED_WIN, Side.RED, 0, 0),
            sampleSummary(2L, 3, GameResult.RED_WIN, Side.RED, 0, 0)
        )
        assertNull(AdaptiveDifficultyAdvisor.suggest(games, currentLevel = 3))
    }

    @Test
    fun referenceAccuracyCalculator_separatesModesAndEnforcesMinSampleSize() {
        val practiceGame = sampleGameEntity(id = 1L, mode = GameMode.PRACTICE, playerSide = Side.RED)
        val challengeGame = sampleGameEntity(id = 2L, mode = GameMode.CHALLENGE, playerSide = Side.RED)

        // 12 full-analyzed Red moves (even plies: 0, 2, 4, ...) in Practice matching best move -> 100%
        val practiceAnalyses = (0 until 12).map { idx ->
            sampleAnalysisEntity(gameId = 1L, plyIndex = idx * 2, scoreBefore = 50, scoreAfter = 50, matchedBest = true, isQuick = false)
        }
        // Only 5 full-analyzed moves in Challenge -> must be null (below MIN_MOVES_REQUIRED = 10)
        val challengeAnalyses = (0 until 5).map { idx ->
            sampleAnalysisEntity(gameId = 2L, plyIndex = idx * 2, scoreBefore = 50, scoreAfter = -50, matchedBest = false, isQuick = false)
        }
        // Quick live-eval entries must be excluded
        val quickLiveEntries = (5 until 18).map { idx ->
            sampleAnalysisEntity(gameId = 2L, plyIndex = idx * 2, scoreBefore = 50, scoreAfter = -450, matchedBest = false, isQuick = true)
        }

        val summary = ReferenceAccuracyCalculator.computeForGamesAndAnalyses(
            games = listOf(practiceGame, challengeGame),
            analyses = practiceAnalyses + challengeAnalyses + quickLiveEntries
        )

        assertNotNull(summary.practiceReport)
        assertEquals(100, summary.practiceReport!!.scorePercent)
        assertEquals(12, summary.practiceReport!!.sampleMoves)
        assertNull(summary.challengeReport)
        assertTrue(summary.disclaimer.contains("tham khảo", ignoreCase = true))
    }

    @Test
    fun moveAccuracyFormula_isMonotonicallyDecreasingWithCpLoss() {
        val acc0 = ReferenceAccuracyCalculator.moveReferenceScore(0, matchedBestMove = false)
        val acc50 = ReferenceAccuracyCalculator.moveReferenceScore(50, matchedBestMove = false)
        val acc200 = ReferenceAccuracyCalculator.moveReferenceScore(200, matchedBestMove = false)
        val acc600 = ReferenceAccuracyCalculator.moveReferenceScore(600, matchedBestMove = false)

        assertEquals(100.0, acc0, 0.001)
        assertTrue(acc0 > acc50 && acc50 > acc200 && acc200 > acc600)
        assertTrue(acc600 > 0.0)
    }

    private fun sampleSummary(
        id: Long,
        level: Int,
        result: GameResult,
        playerSide: Side,
        hints: Int,
        undos: Int
    ) = GameSummary(
        id = id,
        createdAt = id * 1000L,
        updatedAt = id * 1000L,
        finishedAt = id * 1000L,
        playerSide = playerSide,
        engineName = "TestEngine",
        engineLevel = level,
        mode = GameMode.PRACTICE,
        result = result,
        resultReason = GameResultReason.CHECKMATE,
        status = GameStatus.FINISHED,
        undoCount = undos,
        hintCount = hints,
        dangerCheckCount = 0,
        analysisStatus = AnalysisStatus.COMPLETED,
        moveCount = 40,
        blunderCount = 0
    )

    private fun sampleGameEntity(id: Long, mode: GameMode, playerSide: Side) = GameEntity(
        id = id,
        createdAt = id * 1000L,
        updatedAt = id * 1000L,
        finishedAt = id * 1000L,
        initialFen = FenCodec.INITIAL_FEN,
        rulesetId = RulesetId.WXF_BASIC_V1.name,
        playerSide = playerSide.name,
        engineName = "TestEngine",
        engineVersion = "1.0",
        engineLevel = 3,
        mode = mode.name,
        result = GameResult.RED_WIN.name,
        resultReason = GameResultReason.CHECKMATE.name,
        status = GameStatus.FINISHED.name,
        hintCount = 0,
        undoCount = 0,
        dangerCheckCount = 0,
        analysisStatus = AnalysisStatus.COMPLETED.name
    )

    private fun sampleAnalysisEntity(
        gameId: Long,
        plyIndex: Int,
        scoreBefore: Int,
        scoreAfter: Int,
        matchedBest: Boolean,
        isQuick: Boolean
    ) = MoveAnalysisEntity(
        id = gameId * 1000L + plyIndex,
        gameId = gameId,
        plyIndex = plyIndex,
        engineName = "TestEngine",
        engineVersion = "1.0",
        analysisConfig = "depth=6",
        scoreBeforeCp = scoreBefore,
        scoreAfterCp = scoreAfter,
        scorePerspective = "RED",
        mateBefore = null,
        mateAfter = null,
        bestMove = "h2e2",
        playedMove = if (matchedBest) "h2e2" else "b0c2",
        principalVariation = "h2e2",
        depth = if (isQuick) 2 else 6,
        searchTimeMs = 150L,
        classification = if (matchedBest) MoveClassification.GOOD.name else MoveClassification.INACCURACY.name,
        commentary = "Phân tích mẫu.",
        isTurningPoint = false,
        isQuickLiveEval = isQuick
    )
}
