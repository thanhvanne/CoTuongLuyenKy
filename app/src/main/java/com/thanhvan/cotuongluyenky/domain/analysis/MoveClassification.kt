package com.thanhvan.cotuongluyenky.domain.analysis

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import kotlin.math.abs

/**
 * Move evaluation labels (Section 9.1 & 9.5).
 * Deliberately avoids auto-labeling "Nước hay (!)" without separate verified criteria.
 */
enum class MoveClassification(
    val vietnameseLabel: String,
    val symbol: String,
    val isWarning: Boolean,
    val isMajorError: Boolean
) {
    GOOD("Tốt", "", false, false),
    ACCEPTABLE("Chấp nhận được", "", false, false),
    INACCURACY("Chưa chính xác", "?!", true, false),
    MISTAKE("Sai lầm", "?", true, true),
    BLUNDER("Sai lầm nghiêm trọng", "??", true, true);

    val badgeText: String
        get() = if (symbol.isEmpty()) vietnameseLabel else "$vietnameseLabel ($symbol)"
}

/**
 * Documented, versioned centipawn-loss thresholds (Section 9.4).
 * Stored alongside analysis config rather than hardcoded inside UI composables.
 */
data class ClassificationThresholds(
    val configVersion: String = "v1-default",
    val goodMaxLossCp: Int = 35,
    val acceptableMaxLossCp: Int = 85,
    val inaccuracyMaxLossCp: Int = 170,
    val mistakeMaxLossCp: Int = 320,
    val turningPointSwingCp: Int = 220
)

/**
 * Normalizes Red-perspective or side-to-move scores and classifies a played move
 * relative to the engine's reference evaluation before and after the move.
 */
object MoveClassifier {

    data class ClassificationOutcome(
        val classification: MoveClassification,
        val centipawnLoss: Int,
        val missedForcedMate: Boolean,
        val allowedForcedMate: Boolean,
        val swingFromAdvantageToDisadvantage: Boolean
    )

    /**
     * Both [scoreBeforeRed] and [scoreAfterRed] MUST already be normalized to Red perspective
     * (positive favors Red, negative favors Black).
     */
    fun classify(
        movingSide: Side,
        playedMove: Move,
        bestMove: Move?,
        scoreBeforeRed: EngineScore,
        scoreAfterRed: EngineScore,
        thresholds: ClassificationThresholds = ClassificationThresholds()
    ): ClassificationOutcome {
        // Convert both scores to the moving player's perspective
        val beforeMover = toPerspective(scoreBeforeRed, movingSide)
        val afterMover = toPerspective(scoreAfterRed, movingSide)

        val missedMate = beforeMover is EngineScore.Mate && beforeMover.movesToMate > 0 &&
            !(afterMover is EngineScore.Mate && afterMover.movesToMate > 0)

        val allowedMate = !(beforeMover is EngineScore.Mate && beforeMover.movesToMate < 0) &&
            (afterMover is EngineScore.Mate && afterMover.movesToMate < 0)

        val beforeCp = approximateCpForSwing(beforeMover)
        val afterCp = approximateCpForSwing(afterMover)
        val cpLoss = (beforeCp - afterCp).coerceAtLeast(0)
        val swingAdvToDisadv = beforeCp >= 120 && afterCp <= -120

        // If the player matched the engine's top move, never penalize due to search-depth noise
        if (bestMove != null && playedMove == bestMove) {
            return ClassificationOutcome(
                classification = MoveClassification.GOOD,
                centipawnLoss = 0,
                missedForcedMate = false,
                allowedForcedMate = false,
                swingFromAdvantageToDisadvantage = false
            )
        }

        val classification = when {
            allowedMate -> MoveClassification.BLUNDER
            missedMate -> {
                // If still overwhelmingly winning despite missing a quick mate, mark as Mistake; otherwise Blunder
                if (afterMover is EngineScore.Centipawns && afterMover.cp >= 500) {
                    MoveClassification.MISTAKE
                } else {
                    MoveClassification.BLUNDER
                }
            }
            cpLoss <= thresholds.goodMaxLossCp -> MoveClassification.GOOD
            cpLoss <= thresholds.acceptableMaxLossCp -> MoveClassification.ACCEPTABLE
            cpLoss <= thresholds.inaccuracyMaxLossCp -> MoveClassification.INACCURACY
            cpLoss <= thresholds.mistakeMaxLossCp -> MoveClassification.MISTAKE
            else -> MoveClassification.BLUNDER
        }

        return ClassificationOutcome(
            classification = classification,
            centipawnLoss = cpLoss,
            missedForcedMate = missedMate,
            allowedForcedMate = allowedMate,
            swingFromAdvantageToDisadvantage = swingAdvToDisadv
        )
    }

    /**
     * Identifies turning point plies across a game based on Red-perspective evaluation swings
     * or mate-status transitions (Section 13.3 & 13.4).
     */
    fun detectTurningPoints(
        plyScores: List<Triple<Int, EngineScore?, EngineScore?>>,
        thresholds: ClassificationThresholds = ClassificationThresholds(),
        maxTurningPoints: Int = 4
    ): Set<Int> {
        val candidates = mutableListOf<Pair<Int, Int>>()
        for ((ply, beforeRed, afterRed) in plyScores) {
            if (beforeRed == null || afterRed == null) continue
            val beforeVal = approximateCpForSwing(beforeRed)
            val afterVal = approximateCpForSwing(afterRed)
            val swing = abs(afterVal - beforeVal)
            val mateChange = (beforeRed is EngineScore.Mate) != (afterRed is EngineScore.Mate)
            if (swing >= thresholds.turningPointSwingCp || mateChange) {
                candidates.add(ply to if (mateChange) swing + 1000 else swing)
            }
        }
        return candidates
            .sortedByDescending { it.second }
            .take(maxTurningPoints)
            .map { it.first }
            .toSet()
    }

    private fun toPerspective(scoreRed: EngineScore, side: Side): EngineScore =
        if (side == Side.RED) {
            scoreRed
        } else {
            when (scoreRed) {
                is EngineScore.Centipawns -> EngineScore.Centipawns(-scoreRed.cp)
                is EngineScore.Mate -> EngineScore.Mate(-scoreRed.movesToMate)
            }
        }

    private fun approximateCpForSwing(score: EngineScore): Int = when (score) {
        is EngineScore.Centipawns -> score.cp.coerceIn(-2000, 2000)
        is EngineScore.Mate -> if (score.movesToMate > 0) 2000 else -2000
    }
}
