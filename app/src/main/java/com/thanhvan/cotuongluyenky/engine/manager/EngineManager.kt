package com.thanhvan.cotuongluyenky.engine.manager

import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.PositionWithHistory
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.engine.api.EngineAnalysis
import com.thanhvan.cotuongluyenky.engine.api.EngineConfig
import com.thanhvan.cotuongluyenky.engine.api.EngineDifficulty
import com.thanhvan.cotuongluyenky.engine.api.EngineInfo
import com.thanhvan.cotuongluyenky.engine.api.EngineMoveResult
import com.thanhvan.cotuongluyenky.engine.api.EngineVariation
import com.thanhvan.cotuongluyenky.engine.api.SearchLimit
import com.thanhvan.cotuongluyenky.engine.api.XiangqiEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

sealed class EngineAnalysisOutcome {
    data class Success(
        val requestId: Long,
        val positionHash: Long,
        val analysis: EngineAnalysis
    ) : EngineAnalysisOutcome()

    data class Cancelled(val requestId: Long) : EngineAnalysisOutcome()

    data class Failure(
        val requestId: Long,
        val message: String,
        val cause: Throwable? = null
    ) : EngineAnalysisOutcome()
}

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

    /**
     * Runs a Multi-PV position analysis off the main thread, validating every returned variation
     * through [RulesEngine] and discarding stale results via [requestId] + [positionHash] (Section 6.4).
     */
    suspend fun analyzePosition(
        position: PositionWithHistory,
        limit: SearchLimit,
        variations: Int = 3,
        resourceMode: EngineResourceMode = currentResourceMode,
        preemptActive: Boolean = true
    ): EngineAnalysisOutcome {
        val requestId = if (preemptActive) {
            val id = activeRequestId.incrementAndGet()
            try {
                activeEngine.stop()
            } catch (_: Throwable) {
            }
            id
        } else {
            activeRequestId.get()
        }

        val expectedHash = position.currentBoard.positionHash
        return try {
            ensureInitialized(resourceMode)

            mutex.withLock {
                if (preemptActive && activeRequestId.get() != requestId) {
                    return@withLock EngineAnalysisOutcome.Cancelled(requestId)
                }

                val raw = activeEngine.analyze(
                    position = position,
                    limit = limit,
                    variations = variations.coerceIn(1, 5)
                )

                if (preemptActive && activeRequestId.get() != requestId) {
                    return@withLock EngineAnalysisOutcome.Cancelled(requestId)
                }

                // Strictly verify every move in every variation against RulesEngine (Section 1.4 & 10.2)
                val verifiedVariations = raw.variations.mapNotNull { variation ->
                    val legalPv = verifyVariationLegality(position.currentBoard, variation.moves)
                    if (legalPv.isEmpty()) null else variation.copy(moves = legalPv)
                }

                EngineAnalysisOutcome.Success(
                    requestId = requestId,
                    positionHash = expectedHash,
                    analysis = raw.copy(variations = verifiedVariations)
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            EngineAnalysisOutcome.Failure(
                requestId = requestId,
                message = "Không thể phân tích thế cờ: ${t.message ?: "lỗi không xác định"}",
                cause = t
            )
        }
    }

    private fun verifyVariationLegality(
        startBoard: com.thanhvan.cotuongluyenky.core.model.BoardState,
        moves: List<com.thanhvan.cotuongluyenky.core.model.Move>
    ): List<com.thanhvan.cotuongluyenky.core.model.Move> {
        val valid = ArrayList<com.thanhvan.cotuongluyenky.core.model.Move>(moves.size)
        var current = startBoard
        for (mv in moves) {
            if (!RulesEngine.isMoveLegal(current, mv)) break
            valid.add(mv)
            current = RulesEngine.applyMoveUnchecked(current, mv)
        }
        return valid
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
