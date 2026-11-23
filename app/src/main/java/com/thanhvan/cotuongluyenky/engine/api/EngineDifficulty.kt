package com.thanhvan.cotuongluyenky.engine.api

import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 10-level AI difficulty model (Section 7.1 & 7.2).
 * Does NOT claim unverified international Elo ratings.
 */
data class DifficultyProfile(
    val level: Int,
    val title: String,
    val description: String,
    val baseMaxDepth: Int,
    val baseMoveTimeMs: Long,
    val multiPvCount: Int,
    val maxScoreDropCp: Int,
    val temperatureCp: Double
) {
    fun toSearchLimit(resourceMode: EngineResourceMode): SearchLimit {
        val scaledTime = (baseMoveTimeMs * resourceMode.timeScale).roundToLong().coerceAtLeast(80L)
        val adjustedDepth = when (resourceMode) {
            EngineResourceMode.ECO -> (baseMaxDepth - 1).coerceAtLeast(1)
            EngineResourceMode.BALANCED -> baseMaxDepth
            EngineResourceMode.DEEP -> baseMaxDepth + if (level >= 7) 1 else 0
        }
        return SearchLimit(
            moveTimeMs = scaledTime,
            maxDepth = adjustedDepth
        )
    }
}

object EngineDifficulty {

    val profiles: List<DifficultyProfile> = listOf(
        DifficultyProfile(
            level = 1,
            title = "Cấp 1 · Nhập môn",
            description = "Dành cho người mới biết luật",
            baseMaxDepth = 1,
            baseMoveTimeMs = 150L,
            multiPvCount = 6,
            maxScoreDropCp = 260,
            temperatureCp = 180.0
        ),
        DifficultyProfile(
            level = 2,
            title = "Cấp 2 · Tập sự",
            description = "Chơi đơn giản, có sai sót",
            baseMaxDepth = 2,
            baseMoveTimeMs = 200L,
            multiPvCount = 5,
            maxScoreDropCp = 200,
            temperatureCp = 135.0
        ),
        DifficultyProfile(
            level = 3,
            title = "Cấp 3 · Cơ bản",
            description = "Biết phản ứng trước nguy cơ rõ ràng",
            baseMaxDepth = 2,
            baseMoveTimeMs = 260L,
            multiPvCount = 4,
            maxScoreDropCp = 150,
            temperatureCp = 100.0
        ),
        DifficultyProfile(
            level = 4,
            title = "Cấp 4 · Giữ quân",
            description = "Giữ quân khá hơn",
            baseMaxDepth = 3,
            baseMoveTimeMs = 340L,
            multiPvCount = 4,
            maxScoreDropCp = 110,
            temperatureCp = 75.0
        ),
        DifficultyProfile(
            level = 5,
            title = "Cấp 5 · Chiến thuật cơ bản",
            description = "Biết chiến thuật cơ bản",
            baseMaxDepth = 3,
            baseMoveTimeMs = 450L,
            multiPvCount = 3,
            maxScoreDropCp = 80,
            temperatureCp = 55.0
        ),
        DifficultyProfile(
            level = 6,
            title = "Cấp 6 · Phản đòn",
            description = "Có khả năng phản đòn",
            baseMaxDepth = 4,
            baseMoveTimeMs = 550L,
            multiPvCount = 3,
            maxScoreDropCp = 55,
            temperatureCp = 38.0
        ),
        DifficultyProfile(
            level = 7,
            title = "Cấp 7 · Chắc chắn",
            description = "Chơi tương đối chắc",
            baseMaxDepth = 4,
            baseMoveTimeMs = 700L,
            multiPvCount = 2,
            maxScoreDropCp = 35,
            temperatureCp = 25.0
        ),
        DifficultyProfile(
            level = 8,
            title = "Cấp 8 · Tính sâu",
            description = "Tính toán sâu hơn",
            baseMaxDepth = 5,
            baseMoveTimeMs = 900L,
            multiPvCount = 2,
            maxScoreDropCp = 20,
            temperatureCp = 14.0
        ),
        DifficultyProfile(
            level = 9,
            title = "Cấp 9 · Thử thách cao",
            description = "Khó đối với người chơi thông thường",
            baseMaxDepth = 6,
            baseMoveTimeMs = 1200L,
            multiPvCount = 1,
            maxScoreDropCp = 0,
            temperatureCp = 1.0
        ),
        DifficultyProfile(
            level = 10,
            title = "Cấp 10 · Tối đa",
            description = "Mức mạnh theo giới hạn engine và thiết bị",
            baseMaxDepth = 8,
            baseMoveTimeMs = 1800L,
            multiPvCount = 1,
            maxScoreDropCp = 0,
            temperatureCp = 1.0
        )
    )

    fun profileFor(level: Int): DifficultyProfile {
        val clamped = level.coerceIn(1, profiles.size)
        return profiles[clamped - 1]
    }

    /**
     * Selects a strictly legal move from engine candidate variations according to the level's
     * softmax distribution while filtering out catastrophic blunders beyond [DifficultyProfile.maxScoreDropCp].
     */
    fun selectMoveFromVariations(
        profile: DifficultyProfile,
        variations: List<EngineVariation>,
        board: BoardState,
        random: Random = Random.Default
    ): EngineVariation? {
        val legalVariations = variations.filter { v ->
            val firstMove = v.moves.firstOrNull()
            firstMove != null && RulesEngine.isMoveLegal(board, firstMove)
        }.sortedByDescending { it.score.sortableNumericValue() }

        if (legalVariations.isEmpty()) return null
        if (profile.multiPvCount <= 1 || legalVariations.size == 1) {
            return legalVariations.first()
        }

        val best = legalVariations.first()
        // If there is a forced mate found, levels 5+ always play the winning mate
        if (best.score is EngineScore.Mate && best.score.movesToMate > 0 && profile.level >= 5) {
            return best
        }

        val bestVal = best.score.sortableNumericValue()
        val eligible = legalVariations.filter { candidate ->
            val drop = bestVal - candidate.score.sortableNumericValue()
            drop <= profile.maxScoreDropCp
        }.ifEmpty { listOf(best) }

        if (eligible.size == 1) return eligible.first()

        val temp = profile.temperatureCp.coerceAtLeast(1.0)
        val weights = eligible.map { candidate ->
            val drop = (bestVal - candidate.score.sortableNumericValue()).coerceAtLeast(0)
            exp(-drop.toDouble() / temp)
        }
        val totalWeight = weights.sum()
        if (totalWeight <= 0.0) return best

        var target = random.nextDouble() * totalWeight
        for (i in eligible.indices) {
            target -= weights[i]
            if (target <= 0.0) return eligible[i]
        }
        return eligible.last()
    }
}
