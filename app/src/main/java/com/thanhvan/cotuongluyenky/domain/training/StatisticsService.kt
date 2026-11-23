package com.thanhvan.cotuongluyenky.domain.training

import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameStatus
import com.thanhvan.cotuongluyenky.data.repository.GameRepository
import com.thanhvan.cotuongluyenky.data.repository.GameSummary
import com.thanhvan.cotuongluyenky.data.repository.PuzzleItem
import com.thanhvan.cotuongluyenky.data.repository.PuzzleRepository
import com.thanhvan.cotuongluyenky.data.repository.PuzzleSourceType
import com.thanhvan.cotuongluyenky.data.repository.PuzzleTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ModeBreakdown(
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
)

data class WeakTopicInsight(
    val theme: PuzzleTheme,
    val count: Int,
    val sampleGames: Int,
    val summary: String
)

/**
 * Transparent statistics derived strictly from real local Room data (Section 16 & 29).
 * Never displays fabricated accuracy percentages or insights from insufficient sample sizes.
 */
data class TrainingStatistics(
    val totalFinishedGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val practiceStats: ModeBreakdown = ModeBreakdown(),
    val challengeStats: ModeBreakdown = ModeBreakdown(),
    val gamesByLevel: Map<Int, Int> = emptyMap(),
    val analyzedGamesCount: Int = 0,
    val totalPlayerBlunders: Int = 0,
    val avgBlundersPerAnalyzedGame: Double? = null,
    val gamesUsingAids: Int = 0,
    val totalPuzzles: Int = 0,
    val solvedPuzzles: Int = 0,
    val solvedWithoutHintPuzzles: Int = 0,
    val totalPuzzleAttempts: Int = 0,
    val puzzleCorrectRatePercent: Int? = null,
    val duePuzzlesCount: Int = 0,
    val weakTopics: List<WeakTopicInsight> = emptyList(),
    val hasEnoughDataForWeakTopics: Boolean = false
)

class StatisticsService(
    private val gameRepository: GameRepository,
    private val puzzleRepository: PuzzleRepository
) {
    companion object {
        /** Minimum analyzed games required before inferring weak topics (Section 16.2). */
        const val MIN_GAMES_FOR_WEAK_TOPICS = 3
        const val MIN_OCCURRENCES_FOR_TOPIC = 2
    }

    fun observeStatistics(): Flow<TrainingStatistics> = combine(
        gameRepository.observeAllGames(),
        puzzleRepository.observeAllPuzzles()
    ) { games, puzzles ->
        compute(games, puzzles, System.currentTimeMillis())
    }

    fun compute(
        games: List<GameSummary>,
        puzzles: List<PuzzleItem>,
        nowMillis: Long
    ): TrainingStatistics {
        val finished = games.filter { it.status == GameStatus.FINISHED }
        val practice = finished.filter { it.mode == GameMode.PRACTICE }
        val challenge = finished.filter { it.mode == GameMode.CHALLENGE }

        val analyzed = finished.filter {
            it.analysisStatus == AnalysisStatus.COMPLETED || it.analysisStatus == AnalysisStatus.PARTIAL
        }
        val totalBlunders = analyzed.sumOf { it.blunderCount }
        val avgBlunders = if (analyzed.isNotEmpty()) {
            totalBlunders.toDouble() / analyzed.size
        } else null

        val gamesByLevel = finished.groupingBy { it.engineLevel }.eachCount().toSortedMap()

        val totalAttempts = puzzles.sumOf { it.attemptCount }
        val totalCorrect = puzzles.sumOf { it.correctCount }
        val correctRate = if (totalAttempts > 0) {
            ((totalCorrect * 100.0) / totalAttempts).toInt().coerceIn(0, 100)
        } else null

        val dueCount = puzzles.count { it.isDueAt(nowMillis) }
        val solved = puzzles.count { it.isSolved }
        val solvedUnassisted = puzzles.count { it.isSolvedWithoutHint }

        // Weak topics: ONLY derived when analyzedGames >= MIN_GAMES_FOR_WEAK_TOPICS (Section 16.2)
        val hasEnoughData = analyzed.size >= MIN_GAMES_FOR_WEAK_TOPICS
        val weakTopics = if (hasEnoughData) {
            val mistakePuzzles = puzzles.filter { it.sourceType == PuzzleSourceType.GAME_MISTAKE }
            mistakePuzzles
                .groupingBy { it.theme }
                .eachCount()
                .entries
                .filter { it.value >= MIN_OCCURRENCES_FOR_TOPIC }
                .sortedByDescending { it.value }
                .take(4)
                .map { (theme, count) ->
                    WeakTopicInsight(
                        theme = theme,
                        count = count,
                        sampleGames = analyzed.size,
                        summary = "${theme.label}: $count lần trong ${analyzed.size} ván đã phân tích."
                    )
                }
        } else {
            emptyList()
        }

        return TrainingStatistics(
            totalFinishedGames = finished.size,
            wins = finished.count { it.playerWon },
            losses = finished.count { it.playerLost },
            draws = finished.count { it.result == GameResult.DRAW },
            practiceStats = practice.toBreakdown(),
            challengeStats = challenge.toBreakdown(),
            gamesByLevel = gamesByLevel,
            analyzedGamesCount = analyzed.size,
            totalPlayerBlunders = totalBlunders,
            avgBlundersPerAnalyzedGame = avgBlunders,
            gamesUsingAids = finished.count { it.undoCount > 0 || it.hintCount > 0 },
            totalPuzzles = puzzles.size,
            solvedPuzzles = solved,
            solvedWithoutHintPuzzles = solvedUnassisted,
            totalPuzzleAttempts = totalAttempts,
            puzzleCorrectRatePercent = correctRate,
            duePuzzlesCount = dueCount,
            weakTopics = weakTopics,
            hasEnoughDataForWeakTopics = hasEnoughData
        )
    }

    private fun List<GameSummary>.toBreakdown(): ModeBreakdown = ModeBreakdown(
        totalGames = size,
        wins = count { it.playerWon },
        losses = count { it.playerLost },
        draws = count { it.result == GameResult.DRAW }
    )
}
