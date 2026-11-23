package com.thanhvan.cotuongluyenky.data.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleEntity
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleProgressEntity
import kotlinx.coroutines.flow.Flow

data class PuzzleWithProgress(
    @Embedded val puzzle: PuzzleEntity,
    val attemptCount: Int?,
    val correctCount: Int?,
    val consecutiveCorrectCount: Int?,
    val unassistedCorrectCount: Int?,
    val highestHintLevelUsed: Int?,
    val lastAttemptAt: Long?,
    val nextReviewAt: Long?
)

@Dao
interface PuzzleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPuzzle(puzzle: PuzzleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: PuzzleProgressEntity)

    @Query("SELECT * FROM puzzles WHERE id = :puzzleId LIMIT 1")
    suspend fun getPuzzleById(puzzleId: Long): PuzzleEntity?

    @Query("SELECT * FROM puzzle_progress WHERE puzzleId = :puzzleId LIMIT 1")
    suspend fun getProgressByPuzzleId(puzzleId: Long): PuzzleProgressEntity?

    @Query("SELECT * FROM puzzles WHERE catalogKey = :catalogKey LIMIT 1")
    suspend fun getPuzzleByCatalogKey(catalogKey: String): PuzzleEntity?

    @Query(
        """
        SELECT * FROM puzzles
        WHERE sourceType = 'GAME_MISTAKE' AND sourceGameId = :gameId AND sourcePlyIndex = :plyIndex
        LIMIT 1
        """
    )
    suspend fun findGameMistakePuzzle(gameId: Long, plyIndex: Int): PuzzleEntity?

    @Query(
        """
        SELECT p.*,
               pr.attemptCount AS attemptCount,
               pr.correctCount AS correctCount,
               pr.consecutiveCorrectCount AS consecutiveCorrectCount,
               pr.unassistedCorrectCount AS unassistedCorrectCount,
               pr.highestHintLevelUsed AS highestHintLevelUsed,
               pr.lastAttemptAt AS lastAttemptAt,
               pr.nextReviewAt AS nextReviewAt
        FROM puzzles p
        LEFT JOIN puzzle_progress pr ON pr.puzzleId = p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observeAllPuzzlesWithProgress(): Flow<List<PuzzleWithProgress>>

    @Query(
        """
        SELECT p.*,
               pr.attemptCount AS attemptCount,
               pr.correctCount AS correctCount,
               pr.consecutiveCorrectCount AS consecutiveCorrectCount,
               pr.unassistedCorrectCount AS unassistedCorrectCount,
               pr.highestHintLevelUsed AS highestHintLevelUsed,
               pr.lastAttemptAt AS lastAttemptAt,
               pr.nextReviewAt AS nextReviewAt
        FROM puzzles p
        LEFT JOIN puzzle_progress pr ON pr.puzzleId = p.id
        ORDER BY p.createdAt DESC
        """
    )
    suspend fun getAllPuzzlesWithProgress(): List<PuzzleWithProgress>

    @Query("SELECT * FROM puzzles ORDER BY id ASC")
    suspend fun getAllPuzzlesRaw(): List<PuzzleEntity>

    @Query("SELECT * FROM puzzle_progress ORDER BY puzzleId ASC")
    suspend fun getAllProgressRaw(): List<PuzzleProgressEntity>

    @Query("DELETE FROM puzzles WHERE id = :puzzleId")
    suspend fun deletePuzzleById(puzzleId: Long)

    @Query("DELETE FROM puzzles WHERE catalogKey IS NULL")
    suspend fun deleteUserPuzzles()

    @Query("DELETE FROM puzzles")
    suspend fun deleteAllPuzzles()
}
