package com.thanhvan.cotuongluyenky.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted Xiangqi training puzzle (Section 20.6).
 * Can come from a player's own analyzed mistake, manual save, or built-in verified catalog.
 */
@Entity(
    tableName = "puzzles",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["theme"]),
        Index(value = ["sourceGameId", "sourcePlyIndex"])
    ]
)
data class PuzzleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** Catalog key for built-in verified puzzles to guarantee idempotent seeding; null for user puzzles. */
    val catalogKey: String? = null,
    /** GAME_MISTAKE | MANUAL_SAVE | BUILTIN_TACTIC | BUILTIN_OPENING | BUILTIN_ENDGAME | IMPORTED */
    val sourceType: String,
    val sourceGameId: Long? = null,
    val sourcePlyIndex: Int? = null,
    val initialFen: String,
    /** Optional space-separated UCI history leading into this position (for repetition awareness). */
    val historyData: String = "",
    /** "RED" or "BLACK" */
    val sideToMove: String,
    val title: String,
    /** CHECKMATE | FORK | CAPTURE | DEFENSE | COUNTERATTACK | OPENING | ENDGAME | MISTAKE_FIX */
    val theme: String,
    /** The mistaken UCI move played in the original game, if generated from a mistake. */
    val playedMistakeMove: String? = null,
    /** Comma-separated accepted UCI first moves (supports multiple equivalent best moves). */
    val acceptedMoves: String,
    /** Pipe-separated ("|") variations, each variation is space-separated UCI moves. */
    val solutionVariations: String,
    val commentary: String,
    val engineVersion: String,
    val analysisConfig: String,
    val createdAt: Long
)
