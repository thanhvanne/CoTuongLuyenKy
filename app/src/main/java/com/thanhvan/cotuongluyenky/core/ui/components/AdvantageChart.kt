package com.thanhvan.cotuongluyenky.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.engine.api.EngineScore
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCard
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.PieceRedInk
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber
import kotlin.math.roundToInt

data class ChartPoint(
    val plyNumber: Int, // 0 = initial board, 1..N = after move index (plyNumber - 1)
    val scoreRed: EngineScore?,
    val classification: MoveClassification? = null,
    val isTurningPoint: Boolean = false
)

/**
 * Interactive Red-perspective Advantage Chart synced with the board and [EvaluationBar]
 * (Section 13.5 & 18.6).
 *
 * - Horizontal axis: game progression (ply 0..N).
 * - Vertical axis: normalized score clamped to [-800..+800] cp; mate positions receive
 *   dedicated diamond/circle markers at the boundary so they are not disguised as regular cp values.
 * - Tapping anywhere jumps the board directly to the corresponding ply.
 */
@Composable
fun AdvantageChart(
    points: List<ChartPoint>,
    selectedPlyNumber: Int,
    onSelectPlyNumber: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .pointerInput(points.size) {
                    if (points.size <= 1) return@pointerInput
                    detectTapGestures { offset ->
                        val padX = 14f
                        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val ratio = ((offset.x - padX) / usableW).coerceIn(0f, 1f)
                        val targetIdx = (ratio * (points.size - 1)).roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onSelectPlyNumber(points[targetIdx].plyNumber)
                    }
                }
        ) {
            val padX = 14f
            val padY = 12f
            val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
            val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
            val midY = padY + usableH / 2f

            // Zero baseline (Red above, Black below)
            drawLine(
                color = BorderSubtle,
                start = Offset(padX, midY),
                end = Offset(size.width - padX, midY),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            if (points.size < 2) return@Canvas

            fun xForIndex(i: Int): Float = padX + (i.toFloat() / (points.size - 1)) * usableW
            fun yForScore(score: EngineScore): Float {
                val norm = when (score) {
                    is EngineScore.Mate -> if (score.movesToMate > 0) 1f else -1f
                    is EngineScore.Centipawns -> (score.cp.coerceIn(-800, 800) / 800f)
                }
                return midY - norm * (usableH * 0.46f)
            }

            // Build polyline across evaluated points
            val linePath = Path()
            val fillPath = Path()
            var started = false
            var lastX = padX

            for (i in points.indices) {
                val sc = points[i].scoreRed ?: continue
                val x = xForIndex(i)
                val y = yForScore(sc)
                if (!started) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, midY)
                    fillPath.lineTo(x, y)
                    started = true
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                lastX = x
            }

            if (started) {
                fillPath.lineTo(lastX, midY)
                fillPath.close()
                drawPath(
                    path = fillPath,
                    color = PieceRedInk.copy(alpha = 0.16f)
                )
                drawPath(
                    path = linePath,
                    color = AccentEmerald,
                    style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                )
            }

            // Highlight turning points, blunders, and Mate positions distinctly
            for (i in points.indices) {
                val pt = points[i]
                val sc = pt.scoreRed ?: continue
                val x = xForIndex(i)
                val y = yForScore(sc)

                when {
                    sc is EngineScore.Mate -> {
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = 5.5f,
                            center = Offset(x, y)
                        )
                    }
                    pt.classification == MoveClassification.BLUNDER -> {
                        drawCircle(color = ErrorRed, radius = 5f, center = Offset(x, y))
                    }
                    pt.isTurningPoint -> {
                        drawCircle(color = WarningAmber, radius = 4.5f, center = Offset(x, y))
                    }
                }
            }

            // Vertical cursor for currently selected ply
            val selectedIdx = points.indexOfFirst { it.plyNumber == selectedPlyNumber }
            if (selectedIdx >= 0) {
                val cx = xForIndex(selectedIdx)
                drawLine(
                    color = Color.White.copy(alpha = 0.65f),
                    start = Offset(cx, padY),
                    end = Offset(cx, size.height - padY),
                    strokeWidth = 2f
                )
                points[selectedIdx].scoreRed?.let { sc ->
                    val cy = yForScore(sc)
                    drawCircle(color = Color.White, radius = 6.5f, center = Offset(cx, cy))
                    drawCircle(color = AccentEmerald, radius = 4.2f, center = Offset(cx, cy))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = CommentaryService.SCORE_DISCLAIMER,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}
