package com.thanhvan.cotuongluyenky.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thanhvan.cotuongluyenky.data.database.entity.AnalysisVariationEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMoveAnalysis(analysis: MoveAnalysisEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariations(variations: List<AnalysisVariationEntity>)

    @Query("DELETE FROM analysis_variations WHERE analysisId = :analysisId")
    suspend fun deleteVariationsForAnalysis(analysisId: Long)

    @Query("SELECT * FROM move_analysis WHERE gameId = :gameId ORDER BY plyIndex ASC")
    suspend fun getAnalysisForGame(gameId: Long): List<MoveAnalysisEntity>

    @Query("SELECT * FROM move_analysis WHERE gameId = :gameId ORDER BY plyIndex ASC")
    fun observeAnalysisForGame(gameId: Long): Flow<List<MoveAnalysisEntity>>

    @Query("SELECT * FROM analysis_variations WHERE analysisId IN (:analysisIds) ORDER BY rank ASC")
    suspend fun getVariationsForAnalyses(analysisIds: List<Long>): List<AnalysisVariationEntity>

    @Query("SELECT * FROM move_analysis WHERE gameId = :gameId AND plyIndex = :plyIndex LIMIT 1")
    suspend fun getAnalysisAtPly(gameId: Long, plyIndex: Int): MoveAnalysisEntity?

    @Query("UPDATE move_analysis SET isTurningPoint = :isTurningPoint WHERE gameId = :gameId AND plyIndex = :plyIndex")
    suspend fun setTurningPoint(gameId: Long, plyIndex: Int, isTurningPoint: Boolean)

    @Query("SELECT * FROM move_analysis ORDER BY gameId ASC, plyIndex ASC")
    suspend fun getAllAnalysisRaw(): List<MoveAnalysisEntity>

    @Query("DELETE FROM move_analysis WHERE gameId = :gameId AND plyIndex >= :fromPlyIndex")
    suspend fun deleteAnalysisFromPly(gameId: Long, fromPlyIndex: Int)

    @Query("DELETE FROM move_analysis WHERE gameId = :gameId")
    suspend fun deleteAnalysisForGame(gameId: Long)

    @Query("DELETE FROM move_analysis")
    suspend fun deleteAllAnalysis()
}
