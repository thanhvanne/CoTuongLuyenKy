package com.thanhvan.cotuongluyenky.domain.training

import com.thanhvan.cotuongluyenky.core.model.GameStatus
import com.thanhvan.cotuongluyenky.data.repository.GameSummary

enum class DifficultyAdjustmentDirection {
    INCREASE,
    DECREASE
}

data class AdaptiveDifficultySuggestion(
    val currentLevel: Int,
    val suggestedLevel: Int,
    val direction: DifficultyAdjustmentDirection,
    val reason: String,
    val sampleGames: Int
)

/**
 * Optional "Máy vừa sức" advisor (Section 7.3 & Phase 4).
 * Evaluates recent finished games at the player's default level (win/loss ratio, aid usage, blunders)
 * and suggests a transparent +1 / -1 adjustment without silently overriding user preference.
 */
object AdaptiveDifficultyAdvisor {

    const val MIN_SAMPLE_GAMES = 3
    const val WINDOW_SIZE = 5

    fun suggest(
        allGames: List<GameSummary>,
        currentLevel: Int
    ): AdaptiveDifficultySuggestion? {
        val recentAtLevel = allGames
            .filter { it.status == GameStatus.FINISHED && it.engineLevel == currentLevel }
            .sortedByDescending { it.finishedAt ?: it.updatedAt }
            .take(WINDOW_SIZE)

        if (recentAtLevel.size < MIN_SAMPLE_GAMES) return null

        val wins = recentAtLevel.count { it.playerWon }
        val losses = recentAtLevel.count { it.playerLost }
        val cleanWins = recentAtLevel.count { it.playerWon && it.undoCount <= 1 && it.hintCount <= 1 }
        val total = recentAtLevel.size

        // Suggest leveling up when winning strongly and mostly unaided
        if (currentLevel < 10 && wins >= 3 && (wins * 100 / total) >= 70 && cleanWins >= 2) {
            return AdaptiveDifficultySuggestion(
                currentLevel = currentLevel,
                suggestedLevel = (currentLevel + 1).coerceAtMost(10),
                direction = DifficultyAdjustmentDirection.INCREASE,
                reason = "Bạn đã thắng $wins/$total ván gần nhất ở Cấp $currentLevel (trong đó $cleanWins ván ít dùng trợ giúp). Hãy thử nâng lên Cấp ${currentLevel + 1} để tăng thử thách!",
                sampleGames = total
            )
        }

        // Suggest stepping down when losing repeatedly so learning stays encouraging
        if (currentLevel > 1 && losses >= 3 && (losses * 100 / total) >= 75) {
            return AdaptiveDifficultySuggestion(
                currentLevel = currentLevel,
                suggestedLevel = (currentLevel - 1).coerceAtLeast(1),
                direction = DifficultyAdjustmentDirection.DECREASE,
                reason = "Bạn vừa thua $losses/$total ván gần nhất ở Cấp $currentLevel. Có thể thử giảm xuống Cấp ${currentLevel - 1} để luyện thế vững hơn trước khi quay lại.",
                sampleGames = total
            )
        }

        return null
    }
}
