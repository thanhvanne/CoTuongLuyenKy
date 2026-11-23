package com.thanhvan.cotuongluyenky.engine.api

import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.core.model.Side

data class EngineInfo(
    val name: String,
    val version: String,
    val isNativePikafish: Boolean
)

data class EngineConfig(
    val threads: Int = 2,
    val hashMb: Int = 32,
    val evalFilePath: String? = null,
    val resourceMode: EngineResourceMode = EngineResourceMode.BALANCED
)

data class SearchLimit(
    val moveTimeMs: Long = 800L,
    val maxDepth: Int = 6,
    val maxNodes: Long? = null
)

/**
 * Evaluation score from the perspective of the side to move,
 * matching standard UCI/Pikafish convention.
 */
sealed class EngineScore {
    data class Centipawns(val cp: Int) : EngineScore()
    data class Mate(val movesToMate: Int) : EngineScore()

    /**
     * Normalizes score so positive always favors Red and negative favors Black.
     */
    fun toRedPerspective(sideToMove: Side): EngineScore = when (this) {
        is Centipawns -> if (sideToMove == Side.RED) this else Centipawns(-cp)
        is Mate -> if (sideToMove == Side.RED) this else Mate(-movesToMate)
    }

    fun sortableNumericValue(): Int = when (this) {
        is Centipawns -> cp
        is Mate -> if (movesToMate > 0) 100_000 - movesToMate else -100_000 - movesToMate
    }
}

data class EngineVariation(
    val rank: Int,
    val score: EngineScore,
    val depth: Int,
    val moves: List<Move>
)

data class EngineMoveResult(
    val bestMove: Move,
    val score: EngineScore,
    val depth: Int,
    val nodes: Long,
    val elapsedMs: Long,
    val principalVariation: List<Move>,
    val engineInfo: EngineInfo
)

data class EngineAnalysis(
    val variations: List<EngineVariation>,
    val depth: Int,
    val nodes: Long,
    val elapsedMs: Long,
    val engineInfo: EngineInfo
)

/**
 * Engine-agnostic Xiangqi engine interface (Section 6.3).
 * Implementations MUST never run heavy search on the main thread.
 */
interface XiangqiEngine {
    val info: EngineInfo

    suspend fun initialize(config: EngineConfig)

    suspend fun findBestMove(
        position: PositionWithHistory,
        limit: SearchLimit
    ): EngineMoveResult

    suspend fun analyze(
        position: PositionWithHistory,
        limit: SearchLimit,
        variations: Int = 1
    ): EngineAnalysis

    suspend fun stop()

    suspend fun close()
}
