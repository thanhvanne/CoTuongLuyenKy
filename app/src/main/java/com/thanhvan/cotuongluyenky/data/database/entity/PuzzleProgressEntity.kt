package com.thanhvan.cotuongluyenky.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Spaced-repetition progress record for a single [PuzzleEntity] (Section 20.7 & 15.5).
 */
@Entity(
    tableName = "puzzle_progress",
    foreignKeys = [
        ForeignKey(
            entity = PuzzleEntity::class,
            parentColumns = ["id"],
            childColumns = ["puzzleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["nextReviewAt"])
    ]
)
data class PuzzleProgressEntity(
    @PrimaryKey
    val puzzleId: Long,
    val attemptCount: Int = 0,
    val correctCount: Int = 0,
    val consecutiveCorrectCount: Int = 0,
    /** Number of correct solves achieved without using any hint (hint level == 0). */
    val unassistedCorrectCount: Int = 0,
    val highestHintLevelUsed: Int = 0,
    val lastAttemptAt: Long? = null,
    val nextReviewAt: Long
)
