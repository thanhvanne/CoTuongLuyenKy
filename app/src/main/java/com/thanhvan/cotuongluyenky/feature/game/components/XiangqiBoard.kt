package com.thanhvan.cotuongluyenky.feature.game.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.BoardThemeStyle
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceDisplayStyle
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
import com.thanhvan.cotuongluyenky.core.ui.accessibility.rememberEffectiveReduceMotion
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.commentary.DangerLevel
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.MinimalFrame
import com.thanhvan.cotuongluyenky.ui.theme.MinimalGridLine
import com.thanhvan.cotuongluyenky.ui.theme.MinimalRiverText
import com.thanhvan.cotuongluyenky.ui.theme.MinimalSurface
import com.thanhvan.cotuongluyenky.ui.theme.PieceBlackInk
import com.thanhvan.cotuongluyenky.ui.theme.PieceDiscLight
import com.thanhvan.cotuongluyenky.ui.theme.PieceDiscMinimalBlack
import com.thanhvan.cotuongluyenky.ui.theme.PieceDiscMinimalRed
import com.thanhvan.cotuongluyenky.ui.theme.PieceDiscShadow
import com.thanhvan.cotuongluyenky.ui.theme.PieceRedInk
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber
import com.thanhvan.cotuongluyenky.ui.theme.WoodFrame
import com.thanhvan.cotuongluyenky.ui.theme.WoodGridLine
import com.thanhvan.cotuongluyenky.ui.theme.WoodRiverText
import com.thanhvan.cotuongluyenky.ui.theme.WoodSurfaceBottom
import com.thanhvan.cotuongluyenky.ui.theme.WoodSurfaceTop
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ephemeral visual animation metadata for the most recent move.
 * Logical state is always already updated; this only animates piece travel & captured fade-out.
 */
data class MoveAnimationEvent(
    val id: Long,
    val move: Move,
    val movedPiece: Piece,
    val capturedPiece: Piece?
)

/**
 * Validated board arrow for hints and engine variations (Sections 10.2 & 17.5).
 * Suggested moves use solid emerald; opponent replies use dashed red/coral for colorblind accessibility.
 */
data class BoardArrow(
    val from: Square,
    val to: Square,
    val isOpponentReply: Boolean = false,
    val stepNumber: Int? = null
)

/**
 * Destination square feedback ring triggered after live move evaluation completes (Section 19.3 & 19.4).
 */
data class MoveFeedbackVisual(
    val square: Square,
    val classification: MoveClassification
)

/**
 * Pure UI Xiangqi Board renderer (Section 17.4, 17.5 & 21.2).
 * Contains ZERO game-rule logic: renders strictly from [board] and state inputs,
 * and maps screen taps back to logical [Square] coordinates respecting [isFlipped].
 */
@Composable
fun XiangqiBoard(
    board: BoardState,
    selectedSquare: Square?,
    legalTargets: Set<Square>,
    lastMove: Move?,
    checkedSide: Side?,
    isFlipped: Boolean,
    pieceDisplayStyle: PieceDisplayStyle,
    boardThemeStyle: BoardThemeStyle,
    showCoordinates: Boolean,
    reduceMotion: Boolean,
    animationEvent: MoveAnimationEvent?,
    arrows: List<BoardArrow> = emptyList(),
    hintPieceSquare: Square? = null,
    moveFeedbackEffect: MoveFeedbackVisual? = null,
    dangerPieces: Map<Square, DangerLevel> = emptyMap(),
    enabled: Boolean = true,
    onSquareTapped: (Square) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveReduceMotion = rememberEffectiveReduceMotion(reduceMotion)
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(animationEvent?.id, effectiveReduceMotion) {
        if (animationEvent != null && !effectiveReduceMotion) {
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 165, easing = FastOutSlowInEasing)
            )
        } else {
            animProgress.snapTo(1f)
        }
    }

    val frameColor = if (boardThemeStyle == BoardThemeStyle.CLASSIC_WOOD) WoodFrame else MinimalFrame
    val boardAccessibilityDesc = buildString {
        append("Bàn cờ tướng, lượt ${board.sideToMove.vietnameseName} đi")
        if (checkedSide != null) append(", ${checkedSide.vietnameseName} đang bị chiếu")
        if (selectedSquare != null) append(", đang chọn ô ${selectedSquare.toUci()} với ${legalTargets.size} nước hợp lệ")
    }

    Box(
        modifier = modifier
            .aspectRatio(9f / 10.2f)
            .clip(RoundedCornerShape(14.dp))
            .background(frameColor)
            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(6.dp)
            .semantics { contentDescription = boardAccessibilityDesc }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(isFlipped, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { tapOffset ->
                        val metrics = computeBoardMetrics(size.width.toFloat(), size.height.toFloat(), showCoordinates)
                        val visualCol = ((tapOffset.x - metrics.gridLeft) / metrics.cellW).roundToInt()
                        val visualRow = ((tapOffset.y - metrics.gridTop) / metrics.cellH).roundToInt()
                        if (visualCol in 0 until Square.BOARD_FILES && visualRow in 0 until Square.BOARD_RANKS) {
                            val square = visualToLogical(visualCol, visualRow, isFlipped)
                            onSquareTapped(square)
                        }
                    }
                }
        ) {
            val metrics = computeBoardMetrics(size.width, size.height, showCoordinates)
            val progress = animProgress.value
            val isAnimatingMove = animationEvent != null && progress < 1f

            // 1. Board surface background
            if (boardThemeStyle == BoardThemeStyle.CLASSIC_WOOD) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(WoodSurfaceTop, WoodSurfaceBottom)
                    )
                )
            } else {
                drawRect(color = MinimalSurface)
            }

            // 2. Grid lines, palaces, river text, star crosses, coordinates
            val lineColor = if (boardThemeStyle == BoardThemeStyle.CLASSIC_WOOD) WoodGridLine else MinimalGridLine
            val riverTextColor = if (boardThemeStyle == BoardThemeStyle.CLASSIC_WOOD) WoodRiverText else MinimalRiverText
            drawXiangqiGrid(
                metrics = metrics,
                lineColor = lineColor,
                riverTextColor = riverTextColor,
                isFlipped = isFlipped,
                showCoordinates = showCoordinates
            )

            // 3. Last move origin & destination highlights
            if (lastMove != null) {
                drawLastMoveMarker(metrics, lastMove.from, isFlipped, isOrigin = true)
                drawLastMoveMarker(metrics, lastMove.to, isFlipped, isOrigin = false)
            }

            // 4. Checked general warning halo
            if (checkedSide != null) {
                val generalSq = board.findGeneral(checkedSide)
                if (generalSq != null) {
                    val center = metrics.squareCenter(generalSq, isFlipped)
                    drawCircle(
                        color = ErrorRed.copy(alpha = 0.35f),
                        radius = metrics.pieceRadius * 1.24f,
                        center = center
                    )
                    drawCircle(
                        color = ErrorRed,
                        radius = metrics.pieceRadius * 1.12f,
                        center = center,
                        style = Stroke(width = metrics.pieceRadius * 0.14f)
                    )
                }
            }

            // 5. Tier 2 Hint Piece Highlight (highlights suggested piece without revealing destination)
            if (hintPieceSquare != null) {
                val center = metrics.squareCenter(hintPieceSquare, isFlipped)
                drawCircle(
                    color = InfoBlue.copy(alpha = 0.30f),
                    radius = metrics.pieceRadius * 1.26f,
                    center = center
                )
                drawCircle(
                    color = InfoBlue,
                    radius = metrics.pieceRadius * 1.14f,
                    center = center,
                    style = Stroke(width = metrics.pieceRadius * 0.14f)
                )
            }

            // 6. Danger piece rings (Section 14.3: distinct styles per certainty level)
            for ((sq, level) in dangerPieces) {
                val center = metrics.squareCenter(sq, isFlipped)
                when (level) {
                    DangerLevel.UNDER_ATTACK -> {
                        drawCircle(
                            color = WarningAmber,
                            radius = metrics.pieceRadius * 1.13f,
                            center = center,
                            style = Stroke(
                                width = metrics.pieceRadius * 0.12f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        )
                    }
                    DangerLevel.AT_RISK_OF_LOSS -> {
                        drawCircle(
                            color = WarningAmber.copy(alpha = 0.25f),
                            radius = metrics.pieceRadius * 1.22f,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFFFF7043),
                            radius = metrics.pieceRadius * 1.14f,
                            center = center,
                            style = Stroke(width = metrics.pieceRadius * 0.14f)
                        )
                    }
                    DangerLevel.ENGINE_LOSS_LINE -> {
                        drawCircle(
                            color = ErrorRed.copy(alpha = 0.32f),
                            radius = metrics.pieceRadius * 1.25f,
                            center = center
                        )
                        drawCircle(
                            color = ErrorRed,
                            radius = metrics.pieceRadius * 1.14f,
                            center = center,
                            style = Stroke(width = metrics.pieceRadius * 0.16f)
                        )
                    }
                }
            }

            // 7. Fading captured piece (during animation only)
            if (isAnimatingMove && animationEvent?.capturedPiece != null) {
                val capCenter = metrics.squareCenter(animationEvent.move.to, isFlipped)
                val alpha = (1f - progress).coerceIn(0f, 1f)
                drawPieceDisc(
                    center = capCenter,
                    radius = metrics.pieceRadius * (0.85f + 0.15f * alpha),
                    piece = animationEvent.capturedPiece,
                    displayStyle = pieceDisplayStyle,
                    boardThemeStyle = boardThemeStyle,
                    isSelected = false,
                    alpha = alpha
                )
            }

            // 8. All stationary pieces on the board
            val animatedDest = if (isAnimatingMove) animationEvent?.move?.to else null
            for (idx in 0 until Square.BOARD_SQUARES) {
                val piece = board.pieceAtIndex(idx) ?: continue
                val sq = Square.fromIndex(idx)
                if (sq == animatedDest) continue
                val center = metrics.squareCenter(sq, isFlipped)
                drawPieceDisc(
                    center = center,
                    radius = metrics.pieceRadius,
                    piece = piece,
                    displayStyle = pieceDisplayStyle,
                    boardThemeStyle = boardThemeStyle,
                    isSelected = (sq == selectedSquare),
                    alpha = 1f
                )
            }

            // 9. Moving piece sliding smoothly from origin to destination
            if (isAnimatingMove && animationEvent != null) {
                val startOffset = metrics.squareCenter(animationEvent.move.from, isFlipped)
                val endOffset = metrics.squareCenter(animationEvent.move.to, isFlipped)
                val curX = startOffset.x + (endOffset.x - startOffset.x) * progress
                val curY = startOffset.y + (endOffset.y - startOffset.y) * progress
                drawPieceDisc(
                    center = Offset(curX, curY),
                    radius = metrics.pieceRadius * 1.04f,
                    piece = animationEvent.movedPiece,
                    displayStyle = pieceDisplayStyle,
                    boardThemeStyle = boardThemeStyle,
                    isSelected = false,
                    alpha = 1f
                )
            }

            // 10. Post-evaluation move feedback ring (Section 19.3 & 19.4)
            if (moveFeedbackEffect != null) {
                val center = metrics.squareCenter(moveFeedbackEffect.square, isFlipped)
                val ringColor = when (moveFeedbackEffect.classification) {
                    MoveClassification.GOOD -> AccentEmerald
                    MoveClassification.INACCURACY -> WarningAmber
                    MoveClassification.MISTAKE, MoveClassification.BLUNDER -> ErrorRed
                    MoveClassification.ACCEPTABLE -> null
                }
                if (ringColor != null) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.24f),
                        radius = metrics.pieceRadius * 1.22f,
                        center = center
                    )
                    drawCircle(
                        color = ringColor,
                        radius = metrics.pieceRadius * 1.09f,
                        center = center,
                        style = Stroke(width = metrics.pieceRadius * 0.13f)
                    )
                }
            }

            // 11. Validated suggestion / variation arrows (Section 10.2 & 17.5)
            for (arrow in arrows.take(4)) {
                drawBoardArrow(metrics, arrow, isFlipped)
            }

            // 12. Legal move targets for the selected piece (drawn above pieces so capture rings stay visible)
            for (target in legalTargets) {
                val center = metrics.squareCenter(target, isFlipped)
                val isCapture = board.pieceAt(target) != null
                if (isCapture) {
                    drawCircle(
                        color = AccentEmerald,
                        radius = metrics.pieceRadius * 1.07f,
                        center = center,
                        style = Stroke(width = metrics.pieceRadius * 0.14f)
                    )
                } else {
                    drawCircle(
                        color = AccentEmerald.copy(alpha = 0.88f),
                        radius = metrics.pieceRadius * 0.28f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = metrics.pieceRadius * 0.12f,
                        center = center
                    )
                }
            }
        }
    }
}

private data class BoardMetrics(
    val gridLeft: Float,
    val gridTop: Float,
    val cellW: Float,
    val cellH: Float,
    val pieceRadius: Float
) {
    val gridRight: Float get() = gridLeft + cellW * 8f
    val gridBottom: Float get() = gridTop + cellH * 9f

    fun intersection(visualCol: Int, visualRow: Int): Offset =
        Offset(gridLeft + visualCol * cellW, gridTop + visualRow * cellH)

    fun squareCenter(square: Square, isFlipped: Boolean): Offset {
        val (vc, vr) = logicalToVisual(square, isFlipped)
        return intersection(vc, vr)
    }
}

private fun computeBoardMetrics(width: Float, height: Float, showCoordinates: Boolean): BoardMetrics {
    val padX = width * 0.075f
    val padY = height * (if (showCoordinates) 0.085f else 0.068f)
    val usableW = width - padX * 2f
    val usableH = height - padY * 2f
    val cellW = usableW / 8f
    val cellH = usableH / 9f
    val radius = minOf(cellW, cellH) * 0.435f
    return BoardMetrics(
        gridLeft = padX,
        gridTop = padY,
        cellW = cellW,
        cellH = cellH,
        pieceRadius = radius
    )
}

private fun logicalToVisual(square: Square, isFlipped: Boolean): Pair<Int, Int> {
    return if (!isFlipped) {
        square.file to (9 - square.rank)
    } else {
        (8 - square.file) to square.rank
    }
}

private fun visualToLogical(visualCol: Int, visualRow: Int, isFlipped: Boolean): Square {
    return if (!isFlipped) {
        Square(file = visualCol, rank = 9 - visualRow)
    } else {
        Square(file = 8 - visualCol, rank = visualRow)
    }
}

private fun DrawScope.drawBoardArrow(
    metrics: BoardMetrics,
    arrow: BoardArrow,
    isFlipped: Boolean
) {
    val start = metrics.squareCenter(arrow.from, isFlipped)
    val end = metrics.squareCenter(arrow.to, isFlipped)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dist = hypot(dx, dy)
    if (dist < 4f) return

    val ux = dx / dist
    val uy = dy / dist
    val trimStart = metrics.pieceRadius * 0.42f
    val headLen = metrics.pieceRadius * 0.68f
    val headHalfW = metrics.pieceRadius * 0.42f
    val shaftWidth = (metrics.pieceRadius * 0.24f).coerceIn(5f, 14f)

    val lineStart = Offset(start.x + ux * trimStart, start.y + uy * trimStart)
    val tip = Offset(end.x - ux * (metrics.pieceRadius * 0.16f), end.y - uy * (metrics.pieceRadius * 0.16f))
    val shaftEnd = Offset(tip.x - ux * (headLen * 0.85f), tip.y - uy * (headLen * 0.85f))

    val arrowColor = if (arrow.isOpponentReply) {
        ErrorRed.copy(alpha = 0.88f)
    } else {
        AccentEmerald.copy(alpha = 0.90f)
    }

    val dashEffect = if (arrow.isOpponentReply) {
        PathEffect.dashPathEffect(floatArrayOf(shaftWidth * 1.6f, shaftWidth * 0.9f), 0f)
    } else {
        null
    }

    // Shaft
    drawLine(
        color = arrowColor,
        start = lineStart,
        end = shaftEnd,
        strokeWidth = shaftWidth,
        cap = StrokeCap.Round,
        pathEffect = dashEffect
    )

    // Arrowhead triangle
    val angle = atan2(dy.toDouble(), dx.toDouble())
    val perpX = -sin(angle).toFloat()
    val perpY = cos(angle).toFloat()
    val baseCenter = Offset(tip.x - ux * headLen, tip.y - uy * headLen)
    val leftWing = Offset(baseCenter.x + perpX * headHalfW, baseCenter.y + perpY * headHalfW)
    val rightWing = Offset(baseCenter.x - perpX * headHalfW, baseCenter.y - perpY * headHalfW)

    val headPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(leftWing.x, leftWing.y)
        lineTo(rightWing.x, rightWing.y)
        close()
    }
    drawPath(path = headPath, color = arrowColor)

    // Optional step badge in overview mode
    if (arrow.stepNumber != null) {
        val mid = Offset((lineStart.x + tip.x) / 2f, (lineStart.y + tip.y) / 2f)
        val badgeRadius = metrics.pieceRadius * 0.34f
        drawCircle(color = Color(0xFF121316), radius = badgeRadius, center = mid)
        drawCircle(color = arrowColor, radius = badgeRadius, center = mid, style = Stroke(width = 2.5f))
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = badgeRadius * 1.25f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val baseline = (textPaint.descent() + textPaint.ascent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(
            arrow.stepNumber.toString(),
            mid.x,
            mid.y - baseline,
            textPaint
        )
    }
}

private fun DrawScope.drawXiangqiGrid(
    metrics: BoardMetrics,
    lineColor: Color,
    riverTextColor: Color,
    isFlipped: Boolean,
    showCoordinates: Boolean
) {
    val strokeW = (metrics.cellW * 0.035f).coerceIn(1.5f, 3.2f)
    val outerGap = strokeW * 2.4f

    drawRect(
        color = lineColor,
        topLeft = Offset(metrics.gridLeft - outerGap, metrics.gridTop - outerGap),
        size = Size(
            (metrics.gridRight - metrics.gridLeft) + outerGap * 2f,
            (metrics.gridBottom - metrics.gridTop) + outerGap * 2f
        ),
        style = Stroke(width = strokeW * 1.4f)
    )

    for (row in 0..9) {
        val y = metrics.gridTop + row * metrics.cellH
        drawLine(
            color = lineColor,
            start = Offset(metrics.gridLeft, y),
            end = Offset(metrics.gridRight, y),
            strokeWidth = strokeW
        )
    }

    for (col in 0..8) {
        val x = metrics.gridLeft + col * metrics.cellW
        if (col == 0 || col == 8) {
            drawLine(
                color = lineColor,
                start = Offset(x, metrics.gridTop),
                end = Offset(x, metrics.gridBottom),
                strokeWidth = strokeW
            )
        } else {
            drawLine(
                color = lineColor,
                start = Offset(x, metrics.gridTop),
                end = Offset(x, metrics.gridTop + 4 * metrics.cellH),
                strokeWidth = strokeW
            )
            drawLine(
                color = lineColor,
                start = Offset(x, metrics.gridTop + 5 * metrics.cellH),
                end = Offset(x, metrics.gridBottom),
                strokeWidth = strokeW
            )
        }
    }

    drawLine(lineColor, metrics.intersection(3, 0), metrics.intersection(5, 2), strokeWidth = strokeW)
    drawLine(lineColor, metrics.intersection(5, 0), metrics.intersection(3, 2), strokeWidth = strokeW)
    drawLine(lineColor, metrics.intersection(3, 7), metrics.intersection(5, 9), strokeWidth = strokeW)
    drawLine(lineColor, metrics.intersection(5, 7), metrics.intersection(3, 9), strokeWidth = strokeW)

    val markerPoints = listOf(
        1 to 2, 7 to 2,
        1 to 7, 7 to 7,
        0 to 3, 2 to 3, 4 to 3, 6 to 3, 8 to 3,
        0 to 6, 2 to 6, 4 to 6, 6 to 6, 8 to 6
    )
    val gap = metrics.cellW * 0.09f
    val arm = metrics.cellW * 0.16f
    for ((col, row) in markerPoints) {
        val c = metrics.intersection(col, row)
        if (col > 0) {
            drawLine(lineColor, Offset(c.x - gap - arm, c.y - gap), Offset(c.x - gap, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x - gap, c.y - gap - arm), Offset(c.x - gap, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x - gap - arm, c.y + gap), Offset(c.x - gap, c.y + gap), strokeW)
            drawLine(lineColor, Offset(c.x - gap, c.y + gap + arm), Offset(c.x - gap, c.y + gap), strokeW)
        }
        if (col < 8) {
            drawLine(lineColor, Offset(c.x + gap, c.y - gap), Offset(c.x + gap + arm, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x + gap, c.y - gap - arm), Offset(c.x + gap, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x + gap, c.y + gap), Offset(c.x + gap + arm, c.y + gap), strokeW)
            drawLine(lineColor, Offset(c.x + gap, c.y + gap + arm), Offset(c.x + gap, c.y + gap), strokeW)
        }
    }

    val riverCenterY = metrics.gridTop + 4.5f * metrics.cellH
    val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = riverTextColor.copy(alpha = 0.82f).toArgb()
        textAlign = Paint.Align.CENTER
        textSize = (metrics.cellH * 0.36f).coerceIn(14f, 38f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        letterSpacing = 0.12f
    }
    val baselineOffset = (riverPaint.descent() + riverPaint.ascent()) / 2f
    val leftLabel = if (!isFlipped) "SỞ HÀ" else "HÁN GIỚI"
    val rightLabel = if (!isFlipped) "HÁN GIỚI" else "SỞ HÀ"
    drawContext.canvas.nativeCanvas.drawText(
        leftLabel,
        metrics.gridLeft + metrics.cellW * 2.0f,
        riverCenterY - baselineOffset,
        riverPaint
    )
    drawContext.canvas.nativeCanvas.drawText(
        rightLabel,
        metrics.gridLeft + metrics.cellW * 6.0f,
        riverCenterY - baselineOffset,
        riverPaint
    )

    if (showCoordinates) {
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = riverTextColor.copy(alpha = 0.78f).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = (metrics.cellW * 0.25f).coerceIn(11f, 24f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val coordBaseline = (coordPaint.descent() + coordPaint.ascent()) / 2f
        val topY = metrics.gridTop * 0.45f - coordBaseline
        val bottomY = metrics.gridBottom + (size.height - metrics.gridBottom) * 0.58f - coordBaseline

        for (col in 0..8) {
            val x = metrics.gridLeft + col * metrics.cellW
            val topNum = if (!isFlipped) (col + 1).toString() else (9 - col).toString()
            val bottomNum = if (!isFlipped) (9 - col).toString() else (col + 1).toString()
            drawContext.canvas.nativeCanvas.drawText(topNum, x, topY, coordPaint)
            drawContext.canvas.nativeCanvas.drawText(bottomNum, x, bottomY, coordPaint)
        }
    }
}

private fun DrawScope.drawLastMoveMarker(
    metrics: BoardMetrics,
    square: Square,
    isFlipped: Boolean,
    isOrigin: Boolean
) {
    val center = metrics.squareCenter(square, isFlipped)
    val boxSize = metrics.pieceRadius * (if (isOrigin) 1.45f else 2.08f)
    val half = boxSize / 2f
    val color = if (isOrigin) InfoBlue.copy(alpha = 0.42f) else WarningAmber.copy(alpha = 0.32f)
    val borderColor = if (isOrigin) InfoBlue.copy(alpha = 0.85f) else WarningAmber.copy(alpha = 0.9f)

    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - half, center.y - half),
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(metrics.pieceRadius * 0.35f)
    )
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(center.x - half, center.y - half),
        size = Size(boxSize, boxSize),
        cornerRadius = CornerRadius(metrics.pieceRadius * 0.35f),
        style = Stroke(width = (metrics.pieceRadius * 0.08f).coerceAtLeast(1.8f))
    )
}

private fun DrawScope.drawPieceDisc(
    center: Offset,
    radius: Float,
    piece: Piece,
    displayStyle: PieceDisplayStyle,
    boardThemeStyle: BoardThemeStyle,
    isSelected: Boolean,
    alpha: Float
) {
    val isWood = boardThemeStyle == BoardThemeStyle.CLASSIC_WOOD
    val inkColor = if (piece.side == Side.RED) {
        if (isWood) PieceRedInk else Color(0xFFFF6B6B)
    } else {
        if (isWood) PieceBlackInk else Color(0xFFE2E8F0)
    }
    val discColor = when {
        isWood -> PieceDiscLight
        piece.side == Side.RED -> PieceDiscMinimalRed
        else -> PieceDiscMinimalBlack
    }

    drawCircle(
        color = PieceDiscShadow.copy(alpha = PieceDiscShadow.alpha * alpha),
        radius = radius,
        center = Offset(center.x + radius * 0.06f, center.y + radius * 0.09f)
    )

    if (isSelected) {
        drawCircle(
            color = AccentEmerald.copy(alpha = 0.32f),
            radius = radius * 1.2f,
            center = center
        )
    }

    drawCircle(
        color = discColor.copy(alpha = alpha),
        radius = radius,
        center = center
    )

    drawCircle(
        color = (if (isSelected) AccentEmerald else inkColor.copy(alpha = 0.72f)).copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = radius * (if (isSelected) 0.14f else 0.08f))
    )

    drawCircle(
        color = inkColor.copy(alpha = 0.55f * alpha),
        radius = radius * 0.82f,
        center = center,
        style = Stroke(width = radius * 0.045f)
    )

    val label = piece.type.boardLabel(piece.side, displayStyle)
    val textScale = when {
        displayStyle == PieceDisplayStyle.HANZI -> 1.06f
        label.length >= 5 -> 0.50f
        label.length == 4 -> 0.58f
        else -> 0.68f
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor.copy(alpha = alpha).toArgb()
        textAlign = Paint.Align.CENTER
        textSize = radius * textScale
        typeface = if (displayStyle == PieceDisplayStyle.HANZI) {
            Typeface.create(Typeface.SERIF, Typeface.BOLD)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val baselineOffset = (textPaint.descent() + textPaint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(
        label,
        center.x,
        center.y - baselineOffset,
        textPaint
    )
}
