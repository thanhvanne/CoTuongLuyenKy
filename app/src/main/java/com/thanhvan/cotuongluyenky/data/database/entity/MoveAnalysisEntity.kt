package com.thanhvan.cotuongluyenky.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted analysis result for a single half-move (ply) of a game (Section 20.4).
 */
@Entity(
    tableName = "move_analysis",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["gameId", "plyIndex"], unique = true),
        Index(value = ["gameId"])
    ]
)
data class MoveAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val gameId: Long,
    val plyIndex: Int,
    val engineName: String,
    val engineVersion: String,
    val analysisConfig: String,
    val scoreBeforeCp: Int?,
    val scoreAfterCp: Int?,
    val scorePerspective: String, // Always "RED" when persisted for unified chart/bar rendering
    val mateBefore: Int? = null,  // Positive = Red mates, Negative = Black mates
    val mateAfter: Int? = null,
    val bestMove: String? = null,
    val playedMove: String,
    val principalVariation: String = "", // Space-separated UCI moves
    val depth: Int = 0,
    val searchTimeMs: Long = 0L,
    val classification: String,
    val commentary: String,
    val isTurningPoint: Boolean = false,
    val isQuickLiveEval: Boolean = false,
    val analysisVersion: Int = 1
)
