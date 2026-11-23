package com.thanhvan.cotuongluyenky.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val finishedAt: Long? = null,
    val initialFen: String,
    val rulesetId: String,
    val playerSide: String,
    val engineName: String,
    val engineVersion: String,
    val engineLevel: Int,
    val mode: String,
    val result: String,
    val resultReason: String,
    val status: String,
    val hintCount: Int = 0,
    val undoCount: Int = 0,
    val dangerCheckCount: Int = 0,
    val analysisStatus: String,
    val clockInitialMillis: Long = 0L,
    val redRemainingMillis: Long = 0L,
    val blackRemainingMillis: Long = 0L
)
