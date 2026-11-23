package com.thanhvan.cotuongluyenky.domain.analysis

import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveClassificationTest {

    private val mvPlayed = Move.fromUci("h2e2")!!
    private val mvBest = Move.fromUci("b2e2")!!

    @Test
    fun matchingBestMove_isAlwaysClassifiedAsGood() {
        val outcome = MoveClassifier.classify(
            movingSide = Side.RED,
            playedMove = mvBest,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(150),
            scoreAfterRed = EngineScore.Centipawns(40) // depth noise should not penalize exact best move
        )
        assertEquals(MoveClassification.GOOD, outcome.classification)
        assertEquals(0, outcome.centipawnLoss)
    }

    @Test
    fun redAndBlackPerspectives_classifyBlunderSymmetrically() {
        // Red drops from +200 to -250 -> 450cp loss -> BLUNDER & advantage-to-disadvantage swing
        val redBlunder = MoveClassifier.classify(
            movingSide = Side.RED,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(200),
            scoreAfterRed = EngineScore.Centipawns(-250)
        )
        assertEquals(MoveClassification.BLUNDER, redBlunder.classification)
        assertEquals(450, redBlunder.centipawnLoss)
        assertTrue(redBlunder.swingFromAdvantageToDisadvantage)

        // Black is ahead at -200 (Red perspective), then blunders to +250 (Red perspective) -> 450cp loss for Black!
        val blackBlunder = MoveClassifier.classify(
            movingSide = Side.BLACK,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(-200),
            scoreAfterRed = EngineScore.Centipawns(250)
        )
        assertEquals(MoveClassification.BLUNDER, blackBlunder.classification)
        assertEquals(450, blackBlunder.centipawnLoss)
        assertTrue(blackBlunder.swingFromAdvantageToDisadvantage)
    }

    @Test
    fun centipawnLossBands_mapToExpectedClassifications() {
        val acceptable = MoveClassifier.classify(
            movingSide = Side.RED,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(100),
            scoreAfterRed = EngineScore.Centipawns(40) // 60 cp loss
        )
        assertEquals(MoveClassification.ACCEPTABLE, acceptable.classification)

        val inaccuracy = MoveClassifier.classify(
            movingSide = Side.RED,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(100),
            scoreAfterRed = EngineScore.Centipawns(-20) // 120 cp loss
        )
        assertEquals(MoveClassification.INACCURACY, inaccuracy.classification)

        val mistake = MoveClassifier.classify(
            movingSide = Side.RED,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(100),
            scoreAfterRed = EngineScore.Centipawns(-120) // 220 cp loss
        )
        assertEquals(MoveClassification.MISTAKE, mistake.classification)
    }

    @Test
    fun mateEvents_detectMissedMateAndAllowedMate() {
        // Red had forced mate M2, played a move that dropped to equal (+20 cp) -> BLUNDER + missedForcedMate
        val missed = MoveClassifier.classify(
            movingSide = Side.RED,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Mate(2),
            scoreAfterRed = EngineScore.Centipawns(20)
        )
        assertEquals(MoveClassification.BLUNDER, missed.classification)
        assertTrue(missed.missedForcedMate)
        assertFalse(missed.allowedForcedMate)

        // Equal game, Black plays a move allowing Red Mate in 1 -> BLUNDER + allowedForcedMate
        val allowed = MoveClassifier.classify(
            movingSide = Side.BLACK,
            playedMove = mvPlayed,
            bestMove = mvBest,
            scoreBeforeRed = EngineScore.Centipawns(0),
            scoreAfterRed = EngineScore.Mate(1)
        )
        assertEquals(MoveClassification.BLUNDER, allowed.classification)
        assertTrue(allowed.allowedForcedMate)
    }

    @Test
    fun detectTurningPoints_selectsLargestSwingsAndMateChanges() {
        val scores = listOf(
            Triple(0, EngineScore.Centipawns(20), EngineScore.Centipawns(30)),
            Triple(1, EngineScore.Centipawns(30), EngineScore.Centipawns(-300)), // swing 330
            Triple(2, EngineScore.Centipawns(-300), EngineScore.Centipawns(-280)),
            Triple(3, EngineScore.Centipawns(-280), EngineScore.Mate(-2)) // mate change
        )
        val turning = MoveClassifier.detectTurningPoints(scores)
        assertTrue(1 in turning)
        assertTrue(3 in turning)
        assertFalse(0 in turning)
        assertFalse(2 in turning)
    }
}
