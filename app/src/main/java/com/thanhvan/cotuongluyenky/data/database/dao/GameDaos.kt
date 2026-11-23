package com.thanhvan.cotuongluyenky.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveEntity
import kotlinx.coroutines.flow.Flow

data class GameWithMoveCount(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val finishedAt: Long?,
    val playerSide: String,
    val engineName: String,
    val engineLevel: Int,
    val mode: String,
    val result: String,
    val resultReason: String,
    val status: String,
    val undoCount: Int,
    val hintCount: Int,
    val analysisStatus: String,
    val moveCount: Int
)

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("SELECT * FROM games WHERE id = :gameId LIMIT 1")
    suspend fun getGameById(gameId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id = :gameId LIMIT 1")
    fun observeGameById(gameId: Long): Flow<GameEntity?>

    @Query(
        """
        SELECT g.id, g.createdAt, g.updatedAt, g.finishedAt, g.playerSide, g.engineName,
               g.engineLevel, g.mode, g.result, g.resultReason, g.status, g.undoCount,
               g.hintCount, g.analysisStatus,
               (SELECT COUNT(*) FROM moves m WHERE m.gameId = g.id) AS moveCount
        FROM games g
        WHERE g.status = 'IN_PROGRESS'
        ORDER BY g.updatedAt DESC
        LIMIT 1
        """
    )
    fun observeLatestUnfinishedGame(): Flow<GameWithMoveCount?>

    @Query(
        """
        SELECT g.id, g.createdAt, g.updatedAt, g.finishedAt, g.playerSide, g.engineName,
               g.engineLevel, g.mode, g.result, g.resultReason, g.status, g.undoCount,
               g.hintCount, g.analysisStatus,
               (SELECT COUNT(*) FROM moves m WHERE m.gameId = g.id) AS moveCount
        FROM games g
        ORDER BY g.updatedAt DESC
        """
    )
    fun observeAllGames(): Flow<List<GameWithMoveCount>>

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun deleteGameById(gameId: Long)

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()
}

@Dao
interface MoveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMove(move: MoveEntity): Long

    @Query("SELECT * FROM moves WHERE gameId = :gameId ORDER BY plyIndex ASC")
    suspend fun getMovesForGame(gameId: Long): List<MoveEntity>

    @Query("SELECT * FROM moves WHERE gameId = :gameId ORDER BY plyIndex ASC")
    fun observeMovesForGame(gameId: Long): Flow<List<MoveEntity>>

    @Query("DELETE FROM moves WHERE gameId = :gameId AND plyIndex >= :fromPlyIndex")
    suspend fun deleteMovesFromPly(gameId: Long, fromPlyIndex: Int)
}
