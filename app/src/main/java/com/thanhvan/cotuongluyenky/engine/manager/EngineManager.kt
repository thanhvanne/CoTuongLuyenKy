package com.thanhvan.cotuongluyenky.engine.manager

import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineConfig
import com.thanhvan.cotuongluyenky.engine.api.EngineDifficulty
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.api.EngineMoveResult
import com.thanhvan.cotuongluyenky.engine.api.XiangqiEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

sealed class EngineMoveOutcome {
    data class Success(
        val requestId: Long,
        val positionHash: Long,
        val result: EngineMoveResult
    ) : EngineMoveOutcome()

    data class Cancelled(val requestId: Long) : EngineMoveOutcome()

    data class Failure(
        val requestId: Long,
        val message: String,
        val cause: Throwable? = null
    ) : EngineMoveOutcome()
}

/**
 * Coordinates single-flight engine lifecycle, request cancellation, stale-result rejection
 * (via requestId + positionHash), difficulty selection, and automatic fallback from
 * native Pikafish to the built-in offline engine (Section 6.1, 6.4, 7.2).
 */
class EngineManager(
    private val primaryEngine: XiangqiEngine?,
    private val fallbackEngine: XiangqiEngine
) {
    private val mutex = Mutex()
    private val activeRequestId = AtomicLong(0L)

    @Volatile
    private var activeEngine: XiangqiEngine = fallbackEngine

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var currentResourceMode: EngineResourceMode = EngineResourceMode.BALANCED

    val activeEngineInfo: EngineInfo
        get() = activeEngine.info

    suspend fun ensureInitialized(resourceMode: EngineResourceMode = currentResourceMode): EngineInfo {
        return mutex.withLock {
            if (initialized && currentResourceMode == resourceMode) {
                return@withLock activeEngine.info
            }
            currentResourceMode = resourceMode
            val config = EngineConfig(
                threads = resourceMode.threads,
                hashMb = resourceMode.hashMb,
                resourceMode = resourceMode
            )

            if (primaryEngine != null) {
                try {
                    primaryEngine.initialize(config)
                    activeEngine = primaryEngine
                    initialized = true
                    return@withLock activeEngine.info
                } catch (_: Throwable) {
                    try {
                        primaryEngine.close()
                    } catch (_: Throwable) {
                    }
                }
            }

            fallbackEngine.initialize(config)
            activeEngine = fallbackEngine
            initialized = true
            activeEngine.info
        }
    }

    /**
     * Cancels any currently running engine search so stale results will never be applied.
     */
    suspend fun cancelActiveSearch(): Long {
        val nextId = activeRequestId.incrementAndGet()
        try {
            activeEngine.stop()
        } catch (_: Throwable) {
        }
        return nextId
    }

    suspend fun requestMove(
        position: PositionWithHistory,
        level: Int,
        resourceMode: EngineResourceMode
    ): EngineMoveOutcome {
        val requestId = activeRequestId.incrementAndGet()
        val expectedHash = position.currentBoard.positionHash

        // Signal any in-flight search to stop before acquiring the engine lock
        try {
            activeEngine.stop()
        } catch (_: Throwable) {
        }

        return try {
            ensureInitialized(resourceMode)

            mutex.withLock {
                if (activeRequestId.get() != requestId) {
                    return@withLock EngineMoveOutcome.Cancelled(requestId)
                }

                val profile = EngineDifficulty.profileFor(level)
                val limit = profile.toSearchLimit(resourceMode)
                val analysis = activeEngine.analyze(
                    position = position,
                    limit = limit,
                    variations = profile.multiPvCount
                )

                if (activeRequestId.get() != requestId) {
                    return@withLock EngineMoveOutcome.Cancelled(requestId)
                }

                val chosen = EngineDifficulty.selectMoveFromVariations(
                    profile = profile,
                    variations = analysis.variations,
                    board = position.currentBoard
                )

                val chosenMove = chosen?.moves?.firstOrNull()
                    ?: return@withLock EngineMoveOutcome.Failure(
                        requestId = requestId,
                        message = "Máy cờ không tìm thấy nước đi hợp lệ cho thế cờ hiện tại."
                    )

                // Final strict legality guard (Section 7.2 & 22.3)
                if (!RulesEngine.isMoveLegal(position.currentBoard, chosenMove)) {
                    return@withLock EngineMoveOutcome.Failure(
                        requestId = requestId,
                        message = "Engine trả về nước đi không hợp lệ (${chosenMove.toUci()})."
                    )
                }

                val moveResult = EngineMoveResult(
                    bestMove = chosenMove,
                    score = chosen.score,
                    depth = chosen.depth.coerceAtLeast(analysis.depth),
                    nodes = analysis.nodes,
                    elapsedMs = analysis.elapsedMs,
                    principalVariation = chosen.moves,
                    engineInfo = analysis.engineInfo
                )

                EngineMoveOutcome.Success(
                    requestId = requestId,
                    positionHash = expectedHash,
                    result = moveResult
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            EngineMoveOutcome.Failure(
                requestId = requestId,
                message = "Không thể tính nước đi từ máy cờ: ${t.message ?: "lỗi không xác định"}",
                cause = t
            )
        }
    }

    suspend fun shutdown() {
        activeRequestId.incrementAndGet()
        mutex.withLock {
            try {
                primaryEngine?.close()
            } catch (_: Throwable) {
            }
            try {
                fallbackEngine.close()
            } catch (_: Throwable) {
            }
            initialized = false
        }
    }
}
