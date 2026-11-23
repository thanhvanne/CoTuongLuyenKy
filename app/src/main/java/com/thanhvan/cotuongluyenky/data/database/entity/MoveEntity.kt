package com.thanhvan.cotuongluyenky.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moves",
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
data class MoveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val gameId: Long,
    val plyIndex: Int,
    val side: String,
    val fromSquare: String,
    val toSquare: String,
    val pieceType: String,
    val capturedPieceType: String? = null,
    val notation: String,
    val notationFull: String,
    val positionHashAfter: Long,
    val gaveCheck: Boolean = false,
    val playedAt: Long
)
