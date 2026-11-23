package com.thanhvan.cotuongluyenky.domain.analysis

import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveAnalysisEntity
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import kotlin.math.exp
import kotlin.math.roundToInt

data class ModeAccuracyReport(
    val mode: GameMode,
    val scorePercent: Int,
    val sampleMoves: Int,
    val sampleGames: Int,
    val engineSummary: String
)

data class ReferenceAccuracySummary(
    val practiceReport: ModeAccuracyReport?,
    val challengeReport: ModeAccuracyReport?,
    val formulaVersion: String = FORMULA_VERSION,
    val disclaimer: String = DISCLAIMER
) {
    companion object {
        const val FORMULA_VERSION = "ref-acc-v1"
        const val MIN_MOVES_REQUIRED = 10
        const val DECAY_K = 0.0035
        const val DISCLAIMER =
            "Chỉ số độ chính xác tham khảo nội bộ của Cờ Tướng – Luyện Kỳ (công thức $FORMULA_VERSION), tách biệt Luyện tập & Thử sức, không so sánh trực tiếp với nền tảng khác."
    }
}

/**
 * Transparent, documented reference accuracy calculator (Section 16.3 & Phase 4).
 *
 * Formula (documented in docs/ACCURACY_FORMULA.md):
 * - Considers ONLY full post-game analyses (`!isQuickLiveEval`) of the player's own moves.
 * - For each move, computes centipawn loss `cpLoss >= 0` from the player's perspective.
 * - Per-move reference score: `100 * exp(-0.0035 * cpLoss)` (clamped to [0, 100]).
 * - Exact best-move match scores 100 regardless of minor search depth jitter.
 * - Returns null if fewer than [ReferenceAccuracySummary.MIN_MOVES_REQUIRED] full-analyzed moves exist.
 */
object ReferenceAccuracyCalculator {

    fun moveReferenceScore(cpLoss: Int, matchedBestMove: Boolean): Double {
        if (matchedBestMove || cpLoss <= 0) return 100.0
        val cappedLoss = cpLoss.coerceAtMost(1200)
        return (100.0 * exp(-ReferenceAccuracySummary.DECAY_K * cappedLoss)).coerceIn(0.0, 100.0)
    }

    fun computeForGamesAndAnalyses(
        games: List<GameEntity>,
        analyses: List<MoveAnalysisEntity>
    ): ReferenceAccuracySummary {
        val gameById = games.associateBy { it.id }
        val fullAnalyses = analyses.filter { !it.isQuickLiveEval }

        val practiceReport = buildModeReport(GameMode.PRACTICE, gameById, fullAnalyses)
        val challengeReport = buildModeReport(GameMode.CHALLENGE, gameById, fullAnalyses)

        return ReferenceAccuracySummary(
            practiceReport = practiceReport,
            challengeReport = challengeReport
        )
    }

    private fun buildModeReport(
        mode: GameMode,
        gameById: Map<Long, GameEntity>,
        fullAnalyses: List<MoveAnalysisEntity>
    ): ModeAccuracyReport? {
        val scores = mutableListOf<Double>()
        val gameIds = mutableSetOf<Long>()
        var latestEngine = ""

        for (row in fullAnalyses) {
            val game = gameById[row.gameId] ?: continue
            if (game.mode != mode.name) continue
            val playerSide = runCatching { Side.valueOf(game.playerSide) }.getOrNull() ?: continue
            // Ply 0, 2, 4... are Red's moves; Ply 1, 3, 5... are Black's moves (assuming standard start; verify via even/odd)
            val movingSide = if (row.plyIndex % 2 == 0) Side.RED else Side.BLACK
            if (movingSide != playerSide) continue

            val beforeRed = decodeScore(row.scoreBeforeCp, row.mateBefore) ?: continue
            val afterRed = decodeScore(row.scoreAfterCp, row.mateAfter) ?: continue
            val matchedBest = row.bestMove != null && row.bestMove == row.playedMove

            val outcome = MoveClassifier.classify(
                movingSide = movingSide,
                playedMove = com.thanhvan.cotuongluyenky.core.model.Move.fromUci(row.playedMove) ?: continue,
                bestMove = row.bestMove?.let { com.thanhvan.cotuongluyenky.core.model.Move.fromUci(it) },
                scoreBeforeRed = beforeRed,
                scoreAfterRed = afterRed
            )

            scores.add(moveReferenceScore(outcome.centipawnLoss, matchedBest))
            gameIds.add(game.id)
            latestEngine = "${row.engineName} ${row.engineVersion}"
        }

        if (scores.size < ReferenceAccuracySummary.MIN_MOVES_REQUIRED) return null

        val avg = scores.average().roundToInt().coerceIn(0, 100)
        return ModeAccuracyReport(
            mode = mode,
            scorePercent = avg,
            sampleMoves = scores.size,
            sampleGames = gameIds.size,
            engineSummary = latestEngine.ifBlank { "Engine nội bộ" }
        )
    }

    private fun decodeScore(cp: Int?, mate: Int?): EngineScore? = when {
        mate != null -> EngineScore.Mate(mate)
        cp != null -> EngineScore.Centipawns(cp)
        else -> null
    }
}
