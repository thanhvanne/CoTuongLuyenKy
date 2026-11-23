package com.thanhvan.cotuongluyenky.domain.training

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveEntity
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleEntity
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleProgressEntity
import com.thanhvan.cotuongluyenky.data.importexport.BackupExportImportService
import com.thanhvan.cotuongluyenky.data.importexport.BackupParseResult
import com.thanhvan.cotuongluyenky.data.repository.PuzzleTheme
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.domain.rules.FenParseResult
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 tests verifying:
 * 1) Every built-in seed puzzle has a valid FEN, legal accepted moves, and legal variations.
 * 2) Spaced-repetition scheduler distinguishes unassisted vs assisted vs failed solves.
 * 3) Versioned JSON backup export/import round-trips cleanly and rejects corrupted/newer schemas safely.
 */
class TrainingAndSpacedRepetitionTest {

    @Test
    fun seedPuzzleCatalog_allPositionsAndSolutionsAreStrictlyLegal() {
        assertTrue("Catalog should not be empty", SeedPuzzleCatalog.allSpecs.size >= 8)

        for (spec in SeedPuzzleCatalog.allSpecs) {
            val parsed = FenCodec.parse(spec.initialFen)
            assertTrue("Invalid FEN in catalog ${spec.catalogKey}: $parsed", parsed is FenParseResult.Success)
            val board = (parsed as FenParseResult.Success).board
            assertEquals("Side mismatch in ${spec.catalogKey}", spec.sideToMove, board.sideToMove)

            assertTrue("No accepted moves in ${spec.catalogKey}", spec.acceptedMoves.isNotEmpty())
            for (uci in spec.acceptedMoves) {
                val mv = Move.fromUci(uci)
                assertTrue("Invalid UCI '$uci' in ${spec.catalogKey}", mv != null)
                assertTrue(
                    "Accepted move '$uci' is ILLEGAL in ${spec.catalogKey}",
                    RulesEngine.isMoveLegal(board, mv!!)
                )

                // Verify single-move checkmate puzzles really deliver checkmate!
                if (spec.theme == PuzzleTheme.CHECKMATE && spec.solutionVariations.first().size == 1) {
                    val after = RulesEngine.applyMoveUnchecked(board, mv)
                    assertTrue(
                        "Checkmate puzzle ${spec.catalogKey} move $uci must leave opponent in check",
                        RulesEngine.isInCheck(after, after.sideToMove)
                    )
                    assertTrue(
                        "Checkmate puzzle ${spec.catalogKey} move $uci must leave opponent with 0 legal moves",
                        RulesEngine.generateLegalMoves(after).isEmpty()
                    )
                }
            }

            // Every step in every variation must be sequentially legal!
            for ((vIdx, variation) in spec.solutionVariations.withIndex()) {
                var cur = board
                for ((stepIdx, uci) in variation.withIndex()) {
                    val mv = Move.fromUci(uci)
                    assertTrue("Invalid UCI '$uci' in ${spec.catalogKey} var $vIdx", mv != null)
                    assertTrue(
                        "Illegal variation step $stepIdx ('$uci') in ${spec.catalogKey} var $vIdx",
                        RulesEngine.isMoveLegal(cur, mv!!)
                    )
                    cur = RulesEngine.applyMoveUnchecked(cur, mv)
                }
            }
        }
    }

    @Test
    fun spacedRepetitionScheduler_distinguishesUnassistedAssistedAndWrongSolves() {
        val cfg = SpacedRepetitionConfig()
        val t0 = 1_700_000_000_000L

        // 1) Wrong answer -> short retry delay & streak resets to 0
        val afterWrong = SpacedRepetitionScheduler.scheduleNext(
            previous = null,
            puzzleId = 1L,
            solvedCorrectly = false,
            hintLevelUsed = 0,
            nowMillis = t0,
            config = cfg
        )
        assertEquals(0, afterWrong.consecutiveCorrectCount)
        assertEquals(0, afterWrong.unassistedCorrectCount)
        assertEquals(t0 + cfg.wrongReviewDelayMillis, afterWrong.nextReviewAt)

        // 2) Correct WITH hint (level 2) -> assisted delay, does NOT increment unassistedCorrectCount
        val afterAssisted = SpacedRepetitionScheduler.scheduleNext(
            previous = afterWrong,
            puzzleId = 1L,
            solvedCorrectly = true,
            hintLevelUsed = 2,
            nowMillis = t0 + 1000L,
            config = cfg
        )
        assertEquals(1, afterAssisted.correctCount)
        assertEquals(0, afterAssisted.unassistedCorrectCount)
        assertEquals(t0 + 1000L + cfg.assistedCorrectDelayMillis, afterAssisted.nextReviewAt)

        // 3) Consecutive unassisted solves -> increasing intervals (index 0, then index 1)
        val firstUnassisted = SpacedRepetitionScheduler.scheduleNext(
            previous = null,
            puzzleId = 2L,
            solvedCorrectly = true,
            hintLevelUsed = 0,
            nowMillis = t0,
            config = cfg
        )
        assertEquals(1, firstUnassisted.consecutiveCorrectCount)
        assertEquals(1, firstUnassisted.unassistedCorrectCount)
        assertEquals(t0 + cfg.unassistedStreakIntervalsMillis[0], firstUnassisted.nextReviewAt)

        val secondUnassisted = SpacedRepetitionScheduler.scheduleNext(
            previous = firstUnassisted,
            puzzleId = 2L,
            solvedCorrectly = true,
            hintLevelUsed = 0,
            nowMillis = t0 + 10_000L,
            config = cfg
        )
        assertEquals(2, secondUnassisted.consecutiveCorrectCount)
        assertEquals(2, secondUnassisted.unassistedCorrectCount)
        assertEquals(t0 + 10_000L + cfg.unassistedStreakIntervalsMillis[1], secondUnassisted.nextReviewAt)
    }

    @Test
    fun backupExportImport_roundTripsValidDataAndRejectsInvalidOrFutureSchema() {
        val board0 = RulesEngine.initialBoard()
        val mv = Move.fromUci("h2e2")!!
        val applied = RulesEngine.applyMove(board0, mv)

        val game = GameEntity(
            id = 7L,
            createdAt = 1000L,
            updatedAt = 2000L,
            finishedAt = 2000L,
            initialFen = FenCodec.INITIAL_FEN,
            rulesetId = "WXF_BASIC_V1",
            playerSide = "RED",
            engineName = "TestEngine",
            engineVersion = "1.0",
            engineLevel = 4,
            mode = "PRACTICE",
            result = "RED_WIN",
            resultReason = "RESIGNATION",
            status = "FINISHED",
            hintCount = 1,
            undoCount = 0,
            dangerCheckCount = 2,
            analysisStatus = "COMPLETED",
            clockInitialMillis = 0L,
            redRemainingMillis = 0L,
            blackRemainingMillis = 0L
        )
        val moveEntity = MoveEntity(
            id = 1L,
            gameId = 7L,
            plyIndex = 0,
            side = "RED",
            fromSquare = "h2",
            toSquare = "e2",
            pieceType = "CANNON",
            capturedPieceType = null,
            notationShort = applied.notationShort,
            notationFull = applied.notationFull,
            positionHashAfter = applied.newBoard.positionHash,
            gaveCheck = false,
            playedAt = 1500L
        )
        val puzzle = SeedPuzzleCatalog.toEntities(3000L).first().copy(id = 10L, catalogKey = null)
        val progress = PuzzleProgressEntity(
            puzzleId = 10L,
            attemptCount = 3,
            correctCount = 2,
            consecutiveCorrectCount = 2,
            unassistedCorrectCount = 2,
            highestHintLevelUsed = 0,
            lastAttemptAt = 3500L,
            nextReviewAt = 5000L
        )

        val json = BackupExportImportService.encodeBackupJson(
            appVersion = "1.0.0-phase3",
            exportedAt = 4000L,
            games = listOf(game to listOf(moveEntity)),
            puzzles = listOf(puzzle to progress)
        )

        val parsed = BackupExportImportService.parseAndValidateBackup(json)
        assertTrue("Valid backup must parse cleanly: $parsed", parsed is BackupParseResult.Valid)
        val valid = parsed as BackupParseResult.Valid
        assertEquals(1, valid.games.size)
        assertEquals(1, valid.games.first().second.size)
        assertEquals(1, valid.puzzles.size)
        assertEquals(2, valid.puzzles.first().second?.unassistedCorrectCount)
        assertEquals(0, valid.skippedInvalidEntries)

        // Newer schemaVersion must be rejected safely (Section 20.9)
        val futureJson = json.replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val futureRes = BackupExportImportService.parseAndValidateBackup(futureJson)
        assertTrue("Newer schema must return Error", futureRes is BackupParseResult.Error)

        // Malformed JSON must not throw or crash
        val corruptRes = BackupExportImportService.parseAndValidateBackup("{not valid json!!!")
        assertTrue("Corrupt JSON must return Error", corruptRes is BackupParseResult.Error)
    }
}
