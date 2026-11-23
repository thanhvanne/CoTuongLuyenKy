package com.thanhvan.cotuongluyenky.domain.training

import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleProgressEntity
import java.util.concurrent.TimeUnit

/**
 * Configurable spaced-repetition intervals (Section 15.5 — never hard-coded in UI).
 */
data class SpacedRepetitionConfig(
    val wrongReviewDelayMillis: Long = TimeUnit.HOURS.toMillis(4),
    val assistedCorrectDelayMillis: Long = TimeUnit.HOURS.toMillis(16),
    val unassistedStreakIntervalsMillis: List<Long> = listOf(
        TimeUnit.DAYS.toMillis(1),
        TimeUnit.DAYS.toMillis(3),
        TimeUnit.DAYS.toMillis(7),
        TimeUnit.DAYS.toMillis(14),
        TimeUnit.DAYS.toMillis(30)
    )
)

/**
 * Pure spaced-repetition scheduler (Section 15.5 & 26).
 * Distinguishes unassisted solves (`highestHintLevelUsed == 0`) from assisted solves.
 */
object SpacedRepetitionScheduler {

    fun scheduleNext(
        previous: PuzzleProgressEntity?,
        puzzleId: Long,
        solvedCorrectly: Boolean,
        hintLevelUsed: Int,
        nowMillis: Long,
        config: SpacedRepetitionConfig = SpacedRepetitionConfig()
    ): PuzzleProgressEntity {
        val prevAttempts = previous?.attemptCount ?: 0
        val prevCorrect = previous?.correctCount ?: 0
        val prevStreak = previous?.consecutiveCorrectCount ?: 0
        val prevUnassisted = previous?.unassistedCorrectCount ?: 0

        if (!solvedCorrectly) {
            return PuzzleProgressEntity(
                puzzleId = puzzleId,
                attemptCount = prevAttempts + 1,
                correctCount = prevCorrect,
                consecutiveCorrectCount = 0,
                unassistedCorrectCount = prevUnassisted,
                highestHintLevelUsed = maxOf(previous?.highestHintLevelUsed ?: 0, hintLevelUsed),
                lastAttemptAt = nowMillis,
                nextReviewAt = nowMillis + config.wrongReviewDelayMillis
            )
        }

        val unassisted = hintLevelUsed == 0
        val nextStreak = if (unassisted) prevStreak + 1 else maxOf(1, prevStreak)
        val delayMillis = if (!unassisted) {
            // Solved with hints: review sooner so the user practices recall independently
            config.assistedCorrectDelayMillis
        } else {
            val idx = (nextStreak - 1).coerceIn(0, config.unassistedStreakIntervalsMillis.lastIndex)
            config.unassistedStreakIntervalsMillis[idx]
        }

        return PuzzleProgressEntity(
            puzzleId = puzzleId,
            attemptCount = prevAttempts + 1,
            correctCount = prevCorrect + 1,
            consecutiveCorrectCount = nextStreak,
            unassistedCorrectCount = prevUnassisted + if (unassisted) 1 else 0,
            highestHintLevelUsed = hintLevelUsed,
            lastAttemptAt = nowMillis,
            nextReviewAt = nowMillis + delayMillis
        )
    }
}
