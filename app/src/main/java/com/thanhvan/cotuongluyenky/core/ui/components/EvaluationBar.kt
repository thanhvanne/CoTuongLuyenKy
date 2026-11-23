package com.thanhvan.cotuongluyenky.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.PieceRedInk
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.tanh

/**
 * Red-perspective advantage bar (Section 13.5 & 18.4).
 * Positive values favor Red; negative values favor Black.
 * Uses the exact same [EngineScore] object as [AdvantageChart] so they stay strictly synced.
 */
@Composable
fun EvaluationBar(
    scoreRed: EngineScore?,
    isQuickEval: Boolean = false,
    modifier: Modifier = Modifier
) {
    val targetRedRatio = scoreToRedShare(scoreRed)
    val animatedRedRatio by animateFloatAsState(
        targetValue = targetRedRatio,
        label = "eval_bar_ratio"
    )
    val formattedScore = formatRedScore(scoreRed)
    val labelSuffix = if (isQuickEval) " · Đánh giá nhanh" else ""

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lợi thế (dương lợi Đỏ)$labelSuffix",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Text(
                text = formattedScore,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    scoreRed == null -> TextSecondary
                    scoreRed.sortableNumericValue() > 30 -> Color(0xFFFF8A80)
                    scoreRed.sortableNumericValue() < -30 -> Color(0xFF90CAF9)
                    else -> TextPrimary
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF28313D))
                .border(0.8.dp, BorderSubtle, RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRedRatio.coerceIn(0.04f, 0.96f))
                    .fillMaxHeight()
                    .background(PieceRedInk)
            )
            // Center balance marker
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .background(Color(0xAAFFFFFF))
                    .padding(horizontal = 0.75.dp)
            )
        }
    }
}

fun scoreToRedShare(scoreRed: EngineScore?): Float {
    if (scoreRed == null) return 0.5f
    return when (scoreRed) {
        is EngineScore.Mate -> if (scoreRed.movesToMate > 0) 0.96f else 0.04f
        is EngineScore.Centipawns -> {
            // Smooth tanh mapping: ±400 cp -> ~82%/18%
            val normalized = tanh(scoreRed.cp / 500.0).toFloat()
            (0.5f + normalized * 0.46f).coerceIn(0.05f, 0.95f)
        }
    }
}

fun formatRedScore(scoreRed: EngineScore?): String {
    if (scoreRed == null) return "--"
    return when (scoreRed) {
        is EngineScore.Mate -> {
            if (scoreRed.movesToMate > 0) "Chiếu bí Đỏ M${scoreRed.movesToMate}"
            else "Chiếu bí Đen -M${-scoreRed.movesToMate}"
        }
        is EngineScore.Centipawns -> {
            val pawns = scoreRed.cp / 100.0
            String.format(Locale.US, "%+.2f", pawns)
        }
    }
}
