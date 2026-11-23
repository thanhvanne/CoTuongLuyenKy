package com.thanhvan.cotuongluyenky.feature.puzzle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.rules.XiangqiNotation
import com.thanhvan.cotuongluyenky.feature.game.components.XiangqiBoard
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun PuzzleScreen(
    viewModel: PuzzleViewModel,
    onNavigateBack: () -> Unit,
    onPlayFromPosition: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BgMain,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.puzzle?.title ?: "Giải bài tập",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    state.puzzle?.let { p ->
                        Text(
                            text = "${p.theme.label} · Lượt ${p.sideToMove.vietnameseName} đi",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = { viewModel.flipBoard() }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Xoay bàn", tint = TextPrimary)
                }
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentEmerald)
                }
            }

            state.loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LuyenKyCard {
                        Text(state.loadError ?: "", color = ErrorRed)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onNavigateBack) { Text("Quay lại") }
                    }
                }
            }

            else -> {
                val puzzle = state.puzzle ?: return@Scaffold
                val statusColor = when (state.solveState) {
                    PuzzleSolveState.CORRECT, PuzzleSolveState.EQUIVALENT_GOOD -> AccentEmerald
                    PuzzleSolveState.INCORRECT -> ErrorRed
                    PuzzleSolveState.CHECKING -> InfoBlue
                    PuzzleSolveState.VIEWED_SOLUTION -> WarningAmber
                    PuzzleSolveState.UNSOLVED -> TextSecondary
                }
                val isSolved = state.solveState == PuzzleSolveState.CORRECT ||
                    state.solveState == PuzzleSolveState.EQUIVALENT_GOOD

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Status & Prompt Banner (Section 18.9)
                    LuyenKyCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = statusColor.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = state.solveState.label,
                                containerColor = statusColor.copy(alpha = 0.18f),
                                contentColor = statusColor
                            )
                            Text(
                                text = "Bên đi: ${puzzle.sideToMove.vietnameseName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.feedbackMessage
                                ?: "Tìm nước đi tối ưu cho bên ${puzzle.sideToMove.vietnameseName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        if (puzzle.playedMistakeMove != null && !isSolved) {
                            val mistakeLabel = if (RulesEngine.isMoveLegal(state.baseBoard, puzzle.playedMistakeMove)) {
                                XiangqiNotation.format(state.baseBoard, puzzle.playedMistakeMove).full
                            } else {
                                puzzle.playedMistakeMove.toUci()
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Trong ván trước bạn đã đi: $mistakeLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = WarningAmber
                            )
                        }
                    }

                    // Board
                    XiangqiBoard(
                        board = state.displayBoard,
                        selectedSquare = state.selectedSquare,
                        legalTargets = state.legalTargets,
                        lastMove = state.lastMove,
                        checkedSide = state.checkedSide,
                        isFlipped = state.isFlipped,
                        pieceDisplayStyle = settings.pieceDisplayStyle,
                        boardThemeStyle = settings.boardThemeStyle,
                        showCoordinates = settings.showCoordinates,
                        reduceMotion = settings.reduceMotion,
                        animationEvent = null,
                        arrows = state.displayArrows,
                        hintPieceSquare = state.hintPieceSquare,
                        moveFeedbackEffect = state.moveFeedbackVisual,
                        enabled = !state.isPreviewingSolution && state.solveState != PuzzleSolveState.CHECKING,
                        onSquareTapped = { viewModel.onSquareTapped(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Multi-tier Hint Feedback (Section 15.4)
                    if (state.hintLevel in 1..3 && !state.isPreviewingSolution) {
                        LuyenKyCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = InfoBlue.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = when (state.hintLevel) {
                                    1 -> "Gợi ý tầng 1 — Ý tưởng"
                                    2 -> "Gợi ý tầng 2 — Quân nên đi (đã đánh dấu trên bàn)"
                                    else -> "Gợi ý tầng 3 — Nước đi cụ thể"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = InfoBlue
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.ideaHintText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            if (state.hintLevel >= 3) {
                                val best = state.primaryBestMove
                                if (best != null && RulesEngine.isMoveLegal(state.baseBoard, best)) {
                                    val formatted = XiangqiNotation.format(state.baseBoard, best)
                                    val label = if (settings.notationStyle == NotationStyle.FULL) formatted.full else formatted.short
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Nước đề xuất: $label",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AccentEmerald
                                    )
                                }
                            }
                        }
                    }

                    // Solution & Variation Preview Card (Section 15.3 & 18.9)
                    if (state.isPreviewingSolution || isSolved) {
                        LuyenKyCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = AccentEmerald.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (state.isPreviewingSolution) {
                                        "Biến minh họa (${state.solutionStepIndex}/${state.activeVariationMoves.size})"
                                    } else {
                                        "Lời giải & Nhận xét"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AccentEmerald
                                )
                                if (state.isPreviewingSolution && (puzzle.solutionVariations.size > 1)) {
                                    TextButton(onClick = { viewModel.switchSolutionVariation() }) {
                                        Text(
                                            "Biến ${state.solutionVariationIndex + 1}/${puzzle.solutionVariations.size}",
                                            color = InfoBlue
                                        )
                                    }
                                }
                            }
                            if (puzzle.commentary.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = puzzle.commentary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                            if (state.isPreviewingSolution && state.activeVariationMoves.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.stepSolution(-1) },
                                        enabled = state.solutionStepIndex > 0,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                                        Text("Lùi", color = TextPrimary)
                                    }
                                    Button(
                                        onClick = { viewModel.stepSolution(1) },
                                        enabled = state.solutionStepIndex < state.activeVariationMoves.size,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("Bước tiếp")
                                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = CommentaryService.VARIATION_DISCLAIMER,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            } else if (!state.isPreviewingSolution && puzzle.solutionVariations.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.openSolutionPreview() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Xem từng bước biến minh họa", color = InfoBlue)
                                }
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.solveState == PuzzleSolveState.INCORRECT || state.isPreviewingSolution) {
                            OutlinedButton(
                                onClick = { viewModel.retryPuzzle() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Thử lại", color = WarningAmber)
                            }
                        } else if (!isSolved) {
                            OutlinedButton(
                                onClick = { viewModel.requestNextHint() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = when (state.hintLevel) {
                                        0 -> "Gợi ý (Tầng 1)"
                                        1 -> "Chỉ quân (Tầng 2)"
                                        2 -> "Chỉ nước (Tầng 3)"
                                        else -> "Xem lời giải"
                                    },
                                    color = InfoBlue
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.openSolutionPreview() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Lời giải", color = TextPrimary)
                            }
                        }
                    }

                    // Bottom CTAs: Play From Position (Section 15.6) + Next Puzzle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.playFromPosition(onPlayFromPosition) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Đấu tiếp từ thế này", color = AccentEmerald, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.nextPuzzleId != null) {
                            Button(
                                onClick = { viewModel.loadPuzzle(state.nextPuzzleId) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                            ) {
                                Text("Bài tiếp theo", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
