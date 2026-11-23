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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.thanhvan.cotuongluyenky.core.model.BoardState
import com.thanhvan.cotuongluyenky.core.model.BoardThemeStyle
import com.thanhvan.cotuongluyenky.core.model.Move
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceDisplayStyle
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.model.Square
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
import kotlin.math.roundToInt

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
 * Pure UI Xiangqi Board renderer (Section 17.4 & 21.2).
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
    enabled: Boolean = true,
    onSquareTapped: (Square) -> Unit,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(animationEvent?.id, reduceMotion) {
        if (animationEvent != null && !reduceMotion) {
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

    Box(
        modifier = modifier
            .aspectRatio(9f / 10.2f)
            .clip(RoundedCornerShape(14.dp))
            .background(frameColor)
            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(6.dp)
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

            // 5. Fading captured piece (during animation only)
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

            // 6. All stationary pieces on the board
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

            // 7. Moving piece sliding smoothly from origin to destination
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

            // 8. Legal move targets for the selected piece (drawn above pieces so capture rings stay visible)
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

private fun DrawScope.drawXiangqiGrid(
    metrics: BoardMetrics,
    lineColor: Color,
    riverTextColor: Color,
    isFlipped: Boolean,
    showCoordinates: Boolean
) {
    val strokeW = (metrics.cellW * 0.035f).coerceIn(1.5f, 3.2f)
    val outerGap = strokeW * 2.4f

    // Double outer border
    drawRect(
        color = lineColor,
        topLeft = Offset(metrics.gridLeft - outerGap, metrics.gridTop - outerGap),
        size = Size(
            (metrics.gridRight - metrics.gridLeft) + outerGap * 2f,
            (metrics.gridBottom - metrics.gridTop) + outerGap * 2f
        ),
        style = Stroke(width = strokeW * 1.4f)
    )

    // 10 horizontal lines
    for (row in 0..9) {
        val y = metrics.gridTop + row * metrics.cellH
        drawLine(
            color = lineColor,
            start = Offset(metrics.gridLeft, y),
            end = Offset(metrics.gridRight, y),
            strokeWidth = strokeW
        )
    }

    // 9 vertical lines (inner 7 files split across the river between rows 4 and 5)
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

    // Palace diagonals (top palace: rows 0..2, cols 3..5; bottom palace: rows 7..9, cols 3..5)
    drawLine(lineColor, metrics.intersection(3, 0), metrics.intersection(5, 2), strokeWidth = strokeW)
    drawLine(lineColor, metrics.intersection(5, 0), metrics.intersection(3, 2), strokeWidth = strokeW)
    drawLine(lineColor, metrics.intersection(3, 7), metrics.intersection(5, 9), strokeWidth = strokeW)
    drawLine(lineColor, metrics.intersection(5, 7), metrics.intersection(3, 9), strokeWidth = strokeW)

    // Cannon and Soldier star-point corner marks
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
            // Left corners
            drawLine(lineColor, Offset(c.x - gap - arm, c.y - gap), Offset(c.x - gap, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x - gap, c.y - gap - arm), Offset(c.x - gap, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x - gap - arm, c.y + gap), Offset(c.x - gap, c.y + gap), strokeW)
            drawLine(lineColor, Offset(c.x - gap, c.y + gap + arm), Offset(c.x - gap, c.y + gap), strokeW)
        }
        if (col < 8) {
            // Right corners
            drawLine(lineColor, Offset(c.x + gap, c.y - gap), Offset(c.x + gap + arm, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x + gap, c.y - gap - arm), Offset(c.x + gap, c.y - gap), strokeW)
            drawLine(lineColor, Offset(c.x + gap, c.y + gap), Offset(c.x + gap + arm, c.y + gap), strokeW)
            drawLine(lineColor, Offset(c.x + gap, c.y + gap + arm), Offset(c.x + gap, c.y + gap), strokeW)
        }
    }

    // River labels ("SỞ HÀ" and "HÁN GIỚI") strictly centered in the river band (Section 17.4)
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

    // Optional file coordinates (1..9 from each side's perspective)
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

    // Soft drop shadow
    drawCircle(
        color = PieceDiscShadow.copy(alpha = PieceDiscShadow.alpha * alpha),
        radius = radius,
        center = Offset(center.x + radius * 0.06f, center.y + radius * 0.09f)
    )

    // Selection glow ring
    if (isSelected) {
        drawCircle(
            color = AccentEmerald.copy(alpha = 0.32f),
            radius = radius * 1.2f,
            center = center
        )
    }

    // Base disc
    drawCircle(
        color = discColor.copy(alpha = alpha),
        radius = radius,
        center = center
    )

    // Outer rim
    drawCircle(
        color = (if (isSelected) AccentEmerald else inkColor.copy(alpha = 0.72f)).copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = radius * (if (isSelected) 0.14f else 0.08f))
    )

    // Traditional inner ring
    drawCircle(
        color = inkColor.copy(alpha = 0.55f * alpha),
        radius = radius * 0.82f,
        center = center,
        style = Stroke(width = radius * 0.045f)
    )

    // Piece label (Hanzi or Vietnamese)
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
