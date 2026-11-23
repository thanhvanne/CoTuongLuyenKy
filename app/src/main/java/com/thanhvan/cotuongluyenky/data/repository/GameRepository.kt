package com.thanhvan.cotuongluyenky.data.repository

import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.ClockOption
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameResultReason
import com.thanhvan.cotuongluyenky.core.model.GameStatus
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.RulesetId
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import androidx.room.withTransaction
import com.thanhvan.cotuongluyenky.data.database.CoTuongDatabase
import com.thanhvan.cotuongluyenky.data.database.dao.AnalysisDao
import com.thanhvan.cotuongluyenky.data.database.dao.GameDao
import com.thanhvan.cotuongluyenky.data.database.dao.GameWithMoveCount
import com.thanhvan.cotuongluyenky.data.database.dao.MoveDao
import com.thanhvan.cotuongluyenky.data.database.entity.AnalysisVariationEntity
import com.thanhvan.cotuongluyenky.data.database.entity.GameEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveAnalysisEntity
import com.thanhvan.cotuongluyenky.data.database.entity.MoveEntity
import com.thanhvan.cotuongluyenky.domain.rules.FenCodec
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GameSummary(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val finishedAt: Long?,
    val playerSide: Side,
    val engineName: String,
    val engineLevel: Int,
    val mode: GameMode,
    val result: GameResult,
    val resultReason: GameResultReason,
    val status: GameStatus,
    val undoCount: Int,
    val hintCount: Int,
    val dangerCheckCount: Int = 0,
    val analysisStatus: AnalysisStatus,
    val moveCount: Int,
    val blunderCount: Int = 0
) {
    val playerWon: Boolean
        get() = (playerSide == Side.RED && result == GameResult.RED_WIN) ||
            (playerSide == Side.BLACK && result == GameResult.BLACK_WIN)

    val playerLost: Boolean
        get() = (playerSide == Side.RED && result == GameResult.BLACK_WIN) ||
            (playerSide == Side.BLACK && result == GameResult.RED_WIN)
}

data class PersistedGame(
    val entity: GameEntity,
    val playerSide: Side,
    val mode: GameMode,
    val result: GameResult,
    val resultReason: GameResultReason,
    val status: GameStatus,
    val rulesetId: RulesetId,
    val moves: List<MoveRecord>
)

data class PlayerStatsSnapshot(
    val totalFinishedGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val gamesWithAids: Int = 0
)

class GameRepository(
    private val database: CoTuongDatabase,
    private val gameDao: GameDao = database.gameDao(),
    private val moveDao: MoveDao = database.moveDao(),
    private val analysisDao: AnalysisDao = database.analysisDao()
) {

    fun observeLatestUnfinishedGame(): Flow<GameSummary?> =
        gameDao.observeLatestUnfinishedGame().map { it?.toSummary() }

    fun observeAllGames(): Flow<List<GameSummary>> =
        gameDao.observeAllGames().map { list -> list.map { it.toSummary() } }

    fun observePlayerStats(): Flow<PlayerStatsSnapshot> =
        observeAllGames().map { games ->
            val finished = games.filter { it.status == GameStatus.FINISHED }
            PlayerStatsSnapshot(
                totalFinishedGames = finished.size,
                wins = finished.count { it.playerWon },
                losses = finished.count { it.playerLost },
                draws = finished.count { it.result == GameResult.DRAW },
                gamesWithAids = finished.count { it.undoCount > 0 || it.hintCount > 0 }
            )
        }

    suspend fun createNewGame(
        playerSide: Side,
        engineInfo: EngineInfo,
        engineLevel: Int,
        mode: GameMode,
        clockOption: ClockOption,
        initialFen: String = FenCodec.INITIAL_FEN
    ): Long {
        val now = System.currentTimeMillis()
        val entity = GameEntity(
            createdAt = now,
            updatedAt = now,
            finishedAt = null,
            initialFen = initialFen,
            rulesetId = RulesetId.WXF_BASIC_V1.name,
            playerSide = playerSide.name,
            engineName = engineInfo.name,
            engineVersion = engineInfo.version,
            engineLevel = engineLevel.coerceIn(1, 10),
            mode = mode.name,
            result = GameResult.ONGOING.name,
            resultReason = GameResultReason.NONE.name,
            status = GameStatus.IN_PROGRESS.name,
            hintCount = 0,
            undoCount = 0,
            dangerCheckCount = 0,
            analysisStatus = AnalysisStatus.NOT_ANALYZED.name,
            clockInitialMillis = clockOption.initialMillis,
            redRemainingMillis = clockOption.initialMillis,
            blackRemainingMillis = clockOption.initialMillis
        )
        return gameDao.insertGame(entity)
    }

    suspend fun loadGame(gameId: Long): PersistedGame? {
        val entity = gameDao.getGameById(gameId) ?: return null
        val moveEntities = moveDao.getMovesForGame(gameId)
        val moves = moveEntities.mapNotNull { it.toDomainMoveRecord() }
        return PersistedGame(
            entity = entity,
            playerSide = enumOrDefault(entity.playerSide, Side.RED),
            mode = enumOrDefault(entity.mode, GameMode.PRACTICE),
            result = enumOrDefault(entity.result, GameResult.ONGOING),
            resultReason = enumOrDefault(entity.resultReason, GameResultReason.NONE),
            status = enumOrDefault(entity.status, GameStatus.IN_PROGRESS),
            rulesetId = enumOrDefault(entity.rulesetId, RulesetId.WXF_BASIC_V1),
            moves = moves
        )
    }

    suspend fun recordMove(
        gameId: Long,
        record: MoveRecord,
        redRemainingMillis: Long,
        blackRemainingMillis: Long,
        engineInfo: EngineInfo? = null
    ) {
        val existing = gameDao.getGameById(gameId) ?: return
        val now = System.currentTimeMillis()
        val moveEntity = MoveEntity(
            gameId = gameId,
            plyIndex = record.plyIndex,
            side = record.side.name,
            fromSquare = record.move.from.toUci(),
            toSquare = record.move.to.toUci(),
            pieceType = record.pieceType.name,
            capturedPieceType = record.capturedPieceType?.name,
            notation = record.notationShort,
            notationFull = record.notationFull,
            positionHashAfter = record.positionHashAfter,
            gaveCheck = record.gaveCheck,
            playedAt = record.playedAt
        )
        val updated = existing.copy(
            updatedAt = now,
            engineName = engineInfo?.name ?: existing.engineName,
            engineVersion = engineInfo?.version ?: existing.engineVersion,
            redRemainingMillis = redRemainingMillis,
            blackRemainingMillis = blackRemainingMillis
        )
        database.withTransaction {
            moveDao.insertMove(moveEntity)
            gameDao.updateGame(updated)
        }
    }

    suspend fun undoMovesFromPly(
        gameId: Long,
        fromPlyIndex: Int,
        newUndoCount: Int,
        redRemainingMillis: Long,
        blackRemainingMillis: Long
    ) {
        val existing = gameDao.getGameById(gameId) ?: return
        val updated = existing.copy(
            updatedAt = System.currentTimeMillis(),
            finishedAt = null,
            result = GameResult.ONGOING.name,
            resultReason = GameResultReason.NONE.name,
            status = GameStatus.IN_PROGRESS.name,
            undoCount = newUndoCount,
            redRemainingMillis = redRemainingMillis,
            blackRemainingMillis = blackRemainingMillis
        )
        database.withTransaction {
            moveDao.deleteMovesFromPly(gameId, fromPlyIndex)
            analysisDao.deleteAnalysisFromPly(gameId, fromPlyIndex)
            gameDao.updateGame(updated)
        }
    }

    suspend fun finishGame(
        gameId: Long,
        result: GameResult,
        reason: GameResultReason,
        redRemainingMillis: Long,
        blackRemainingMillis: Long
    ) {
        val existing = gameDao.getGameById(gameId) ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            updatedAt = now,
            finishedAt = now,
            result = result.name,
            resultReason = reason.name,
            status = GameStatus.FINISHED.name,
            redRemainingMillis = redRemainingMillis,
            blackRemainingMillis = blackRemainingMillis
        )
        gameDao.updateGame(updated)
    }

    suspend fun updateClockSnapshot(
        gameId: Long,
        redRemainingMillis: Long,
        blackRemainingMillis: Long
    ) {
        val existing = gameDao.getGameById(gameId) ?: return
        if (existing.status != GameStatus.IN_PROGRESS.name) return
        gameDao.updateGame(
            existing.copy(
                updatedAt = System.currentTimeMillis(),
                redRemainingMillis = redRemainingMillis,
                blackRemainingMillis = blackRemainingMillis
            )
        )
    }

    suspend fun deleteGame(gameId: Long) {
        gameDao.deleteGameById(gameId)
    }

    suspend fun clearAllHistory() {
        gameDao.deleteAllGames()
    }

    suspend fun clearAllAnalysisCache() {
        database.withTransaction {
            analysisDao.deleteAllAnalysis()
            gameDao.resetAllAnalysisStatuses(System.currentTimeMillis())
        }
    }

    suspend fun incrementHintCount(gameId: Long) {
        gameDao.incrementHintCount(gameId, System.currentTimeMillis())
    }

    suspend fun incrementDangerCheckCount(gameId: Long) {
        gameDao.incrementDangerCheckCount(gameId, System.currentTimeMillis())
    }

    suspend fun updateAnalysisStatus(gameId: Long, status: AnalysisStatus) {
        gameDao.updateAnalysisStatus(gameId, status.name, System.currentTimeMillis())
    }

    fun observeMoveAnalyses(gameId: Long): Flow<List<MoveAnalysisEntity>> =
        analysisDao.observeAnalysisForGame(gameId)

    suspend fun loadMoveAnalyses(gameId: Long): List<MoveAnalysisEntity> =
        analysisDao.getAnalysisForGame(gameId)

    suspend fun loadVariationsForAnalyses(analysisIds: List<Long>): Map<Long, List<AnalysisVariationEntity>> {
        if (analysisIds.isEmpty()) return emptyMap()
        return analysisDao.getVariationsForAnalyses(analysisIds).groupBy { it.analysisId }
    }

    suspend fun saveMoveAnalysis(
        entity: MoveAnalysisEntity,
        variations: List<AnalysisVariationEntity>
    ): Long {
        return database.withTransaction {
            val id = analysisDao.upsertMoveAnalysis(entity)
            analysisDao.deleteVariationsForAnalysis(id)
            if (variations.isNotEmpty()) {
                analysisDao.insertVariations(variations.map { it.copy(analysisId = id) })
            }
            id
        }
    }

    suspend fun updateTurningPoints(gameId: Long, turningPointPlies: Set<Int>) {
        database.withTransaction {
            val existing = analysisDao.getAnalysisForGame(gameId)
            for (row in existing) {
                val shouldMark = row.plyIndex in turningPointPlies
                if (row.isTurningPoint != shouldMark) {
                    analysisDao.setTurningPoint(gameId, row.plyIndex, shouldMark)
                }
            }
        }
    }

    suspend fun clearGameAnalysis(gameId: Long) {
        database.withTransaction {
            analysisDao.deleteAnalysisForGame(gameId)
            gameDao.updateAnalysisStatus(gameId, AnalysisStatus.NOT_ANALYZED.name, System.currentTimeMillis())
        }
    }

    private fun GameWithMoveCount.toSummary(): GameSummary = GameSummary(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
        playerSide = enumOrDefault(playerSide, Side.RED),
        engineName = engineName,
        engineLevel = engineLevel,
        mode = enumOrDefault(mode, GameMode.PRACTICE),
        result = enumOrDefault(result, GameResult.ONGOING),
        resultReason = enumOrDefault(resultReason, GameResultReason.NONE),
        status = enumOrDefault(status, GameStatus.IN_PROGRESS),
        undoCount = undoCount,
        hintCount = hintCount,
        dangerCheckCount = dangerCheckCount,
        analysisStatus = enumOrDefault(analysisStatus, AnalysisStatus.NOT_ANALYZED),
        moveCount = moveCount,
        blunderCount = blunderCount
    )

    private fun MoveEntity.toDomainMoveRecord(): MoveRecord? {
        val from = Square.fromUci(fromSquare) ?: return null
        val to = Square.fromUci(toSquare) ?: return null
        return MoveRecord(
            plyIndex = plyIndex,
            side = enumOrDefault(side, Side.RED),
            move = Move(from, to),
            pieceType = enumOrDefault(pieceType, PieceType.SOLDIER),
            capturedPieceType = capturedPieceType?.let { enumOrDefault(it, PieceType.SOLDIER) },
            notationShort = notation,
            notationFull = notationFull.ifBlank { notation },
            positionHashAfter = positionHashAfter,
            gaveCheck = gaveCheck,
            playedAt = playedAt
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T {
        if (raw == null) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
