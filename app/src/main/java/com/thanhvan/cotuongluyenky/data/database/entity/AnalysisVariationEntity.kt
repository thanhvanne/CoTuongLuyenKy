package com.thanhvan.cotuongluyenky.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Multi-PV candidate variation associated with a [MoveAnalysisEntity] (Section 20.5).
 */
@Entity(
    tableName = "analysis_variations",
    foreignKeys = [
        ForeignKey(
            entity = MoveAnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["analysisId"])
    ]
)
data class AnalysisVariationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val analysisId: Long,
    val rank: Int,
    val scoreCp: Int?,
    val mateIn: Int? = null,
    val moves: String // Space-separated UCI moves
)
