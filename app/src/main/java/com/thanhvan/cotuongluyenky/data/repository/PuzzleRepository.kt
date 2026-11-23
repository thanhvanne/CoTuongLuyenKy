package com.thanhvan.cotuongluyenky.data.repository

import androidx.room.withTransaction
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.data.database.CoTuongDatabase
import com.thanhvan.cotuongluyenky.data.database.dao.PuzzleDao
import com.thanhvan.cotuongluyenky.data.database.dao.PuzzleWithProgress
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleEntity
import com.thanhvan.cotuongluyenky.data.database.entity.PuzzleProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class PuzzleSourceType(val label: String) {
    GAME_MISTAKE("Sửa sai từ ván"),
    MANUAL_SAVE("Bài đã lưu"),
    BUILTIN_TACTIC("Chiến thuật"),
    BUILTIN_OPENING("Khai cuộc"),
    BUILTIN_ENDGAME("Tàn cuộc"),
    IMPORTED("Đã nhập")
}

enum class PuzzleTheme(val label: String) {
    CHECKMATE("Chiếu bí"),
    FORK("Bắt đôi"),
    CAPTURE("Bắt quân"),
    DEFENSE("Phòng thủ"),
    COUNTERATTACK("Phản đòn"),
    OPENING("Khai cuộc"),
    ENDGAME("Tàn cuộc"),
    MISTAKE_FIX("Sửa sai lầm")
}

data class PuzzleItem(
    val id: Long,
    val catalogKey: String?,
    val sourceType: PuzzleSourceType,
    val sourceGameId: Long?,
    val sourcePlyIndex: Int?,
    val initialFen: String,
    val historyUci: List<String>,
    val sideToMove: Side,
    val title: String,
    val theme: PuzzleTheme,
    val playedMistakeMove: Move?,
    val acceptedMoves: List<Move>,
    val solutionVariations: List<List<Move>>,
    val commentary: String,
    val engineVersion: String,
    val analysisConfig: String,
    val createdAt: Long,
    val attemptCount: Int,
    val correctCount: Int,
    val consecutiveCorrectCount: Int,
    val unassistedCorrectCount: Int,
    val highestHintLevelUsed: Int,
    val lastAttemptAt: Long?,
    val nextReviewAt: Long
) {
    fun isDueAt(nowMillis: Long): Boolean = nextReviewAt <= nowMillis
    val isSolved: Boolean get() = correctCount > 0
    val isSolvedWithoutHint: Boolean get() = unassistedCorrectCount > 0
}

class PuzzleRepository(
    private val database: CoTuongDatabase,
    private val puzzleDao: PuzzleDao = database.puzzleDao()
) {

    fun observeAllPuzzles(): Flow<List<PuzzleItem>> =
        puzzleDao.observeAllPuzzlesWithProgress().map { list -> list.map { it.toDomain() } }

    suspend fun getAllPuzzles(): List<PuzzleItem> =
        puzzleDao.getAllPuzzlesWithProgress().map { it.toDomain() }

    suspend fun getPuzzleById(puzzleId: Long): PuzzleItem? {
        val entity = puzzleDao.getPuzzleById(puzzleId) ?: return null
        val progress = puzzleDao.getProgressByPuzzleId(puzzleId)
        return PuzzleWithProgress(
            puzzle = entity,
            attemptCount = progress?.attemptCount,
            correctCount = progress?.correctCount,
            consecutiveCorrectCount = progress?.consecutiveCorrectCount,
            unassistedCorrectCount = progress?.unassistedCorrectCount,
            highestHintLevelUsed = progress?.highestHintLevelUsed,
            lastAttemptAt = progress?.lastAttemptAt,
            nextReviewAt = progress?.nextReviewAt
        ).toDomain()
    }

    suspend fun insertPuzzleIfNewCatalogKey(entity: PuzzleEntity): Long? {
        val key = entity.catalogKey
        if (key != null && puzzleDao.getPuzzleByCatalogKey(key) != null) {
            return null
        }
        return createPuzzleWithInitialProgress(entity)
    }

    suspend fun insertGameMistakeIfNotExists(entity: PuzzleEntity): Long? {
        val gameId = entity.sourceGameId
        val ply = entity.sourcePlyIndex
        if (gameId != null && ply != null) {
            val existing = puzzleDao.findGameMistakePuzzle(gameId, ply)
            if (existing != null) return null
        }
        return createPuzzleWithInitialProgress(entity)
    }

    suspend fun createPuzzleWithInitialProgress(entity: PuzzleEntity): Long {
        return database.withTransaction {
            val id = puzzleDao.insertPuzzle(entity)
            puzzleDao.upsertProgress(
                PuzzleProgressEntity(
                    puzzleId = id,
                    attemptCount = 0,
                    correctCount = 0,
                    consecutiveCorrectCount = 0,
                    unassistedCorrectCount = 0,
                    highestHintLevelUsed = 0,
                    lastAttemptAt = null,
                    nextReviewAt = entity.createdAt // Due immediately until solved
                )
            )
            id
        }
    }

    suspend fun saveProgress(progress: PuzzleProgressEntity) {
        puzzleDao.upsertProgress(progress)
    }

    suspend fun getProgress(puzzleId: Long): PuzzleProgressEntity? =
        puzzleDao.getProgressByPuzzleId(puzzleId)

    suspend fun deletePuzzle(puzzleId: Long) {
        puzzleDao.deletePuzzleById(puzzleId)
    }

    suspend fun clearUserPuzzles() {
        puzzleDao.deleteUserPuzzles()
    }

    suspend fun getAllRawForExport(): Pair<List<PuzzleEntity>, List<PuzzleProgressEntity>> =
        puzzleDao.getAllPuzzlesRaw() to puzzleDao.getAllProgressRaw()

    suspend fun importPuzzles(
        puzzles: List<Pair<PuzzleEntity, PuzzleProgressEntity?>>,
        replaceExisting: Boolean
    ): Int {
        var count = 0
        database.withTransaction {
            if (replaceExisting) {
                puzzleDao.deleteUserPuzzles()
            }
            for ((puzzle, progress) in puzzles) {
                // Skip built-in catalog duplicates
                if (puzzle.catalogKey != null && puzzleDao.getPuzzleByCatalogKey(puzzle.catalogKey) != null) {
                    continue
                }
                val newId = puzzleDao.insertPuzzle(puzzle.copy(id = 0L))
                val pr = progress?.copy(puzzleId = newId) ?: PuzzleProgressEntity(
                    puzzleId = newId,
                    nextReviewAt = puzzle.createdAt
                )
                puzzleDao.upsertProgress(pr)
                count++
            }
        }
        return count
    }

    private fun PuzzleWithProgress.toDomain(): PuzzleItem {
        val accepted = puzzle.acceptedMoves
            .split(",")
            .mapNotNull { Move.fromUci(it.trim()) }
        val variations = puzzle.solutionVariations
            .split("|")
            .mapNotNull { line ->
                val moves = line.trim().split(Regex("\\s+")).mapNotNull { Move.fromUci(it) }
                moves.takeIf { it.isNotEmpty() }
            }
        return PuzzleItem(
            id = puzzle.id,
            catalogKey = puzzle.catalogKey,
            sourceType = runCatching { PuzzleSourceType.valueOf(puzzle.sourceType) }
                .getOrDefault(PuzzleSourceType.MANUAL_SAVE),
            sourceGameId = puzzle.sourceGameId,
            sourcePlyIndex = puzzle.sourcePlyIndex,
            initialFen = puzzle.initialFen,
            historyUci = puzzle.historyData.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+")) ?: emptyList(),
            sideToMove = runCatching { Side.valueOf(puzzle.sideToMove) }.getOrDefault(Side.RED),
            title = puzzle.title,
            theme = runCatching { PuzzleTheme.valueOf(puzzle.theme) }.getOrDefault(PuzzleTheme.MISTAKE_FIX),
            playedMistakeMove = puzzle.playedMistakeMove?.let { Move.fromUci(it) },
            acceptedMoves = accepted,
            solutionVariations = variations,
            commentary = puzzle.commentary,
            engineVersion = puzzle.engineVersion,
            analysisConfig = puzzle.analysisConfig,
            createdAt = puzzle.createdAt,
            attemptCount = attemptCount ?: 0,
            correctCount = correctCount ?: 0,
            consecutiveCorrectCount = consecutiveCorrectCount ?: 0,
            unassistedCorrectCount = unassistedCorrectCount ?: 0,
            highestHintLevelUsed = highestHintLevelUsed ?: 0,
            lastAttemptAt = lastAttemptAt,
            nextReviewAt = nextReviewAt ?: puzzle.createdAt
        )
    }
}
