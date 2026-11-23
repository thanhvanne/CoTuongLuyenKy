package com.thanhvan.cotuongluyenky.feature.positioneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.Piece
import com.thanhvan.cotuongluyenky.core.model.PieceType
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.ui.components.EvaluationBar
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.domain.rules.RulesEngine
import com.thanhvan.cotuongluyenky.domain.rules.XiangqiNotation
import com.thanhvan.cotuongluyenky.feature.game.components.XiangqiBoard
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.PieceBlackInk
import com.thanhvan.cotuongluyenky.ui.theme.PieceRedInk
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun PositionEditorScreen(
    viewModel: PositionEditorViewModel,
    onNavigateBack: () -> Unit,
    onStartGameFromPosition: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

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
                    Text("Nhập thế cờ & Đặt quân", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("Dán FEN, đặt quân thủ công, phân tích hoặc đấu máy", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                IconButton(onClick = { viewModel.flipBoard() }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Xoay bàn", tint = TextPrimary)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mandatory warning when history is absent (Section 18.10)
            LuyenKyCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = WarningAmber.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Lưu ý: Một số quy tắc liên quan đến lặp thế có thể không xác định đầy đủ khi không có lịch sử nước đi trước đó.",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningAmber
                )
            }

            // Board preview & tap-to-place surface
            XiangqiBoard(
                board = state.board,
                selectedSquare = null,
                legalTargets = emptySet(),
                lastMove = null,
                checkedSide = null,
                isFlipped = state.isFlipped,
                pieceDisplayStyle = settings.pieceDisplayStyle,
                boardThemeStyle = settings.boardThemeStyle,
                showCoordinates = settings.showCoordinates,
                reduceMotion = true,
                animationEvent = null,
                arrows = state.arrows,
                enabled = true,
                onSquareTapped = { viewModel.onSquareTapped(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Side-to-move & Quick board reset controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.board.sideToMove == Side.RED,
                    onClick = { viewModel.setSideToMove(Side.RED) },
                    label = { Text("Đỏ đi") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PieceRedInk.copy(alpha = 0.25f),
                        selectedLabelColor = PieceRedInk,
                        containerColor = BgCardElevated,
                        labelColor = TextPrimary
                    )
                )
                FilterChip(
                    selected = state.board.sideToMove == Side.BLACK,
                    onClick = { viewModel.setSideToMove(Side.BLACK) },
                    label = { Text("Đen đi") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = InfoBlue.copy(alpha = 0.22f),
                        selectedLabelColor = InfoBlue,
                        containerColor = BgCardElevated,
                        labelColor = TextPrimary
                    )
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.resetToInitialPosition() }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Thế ban đầu", tint = TextPrimary)
                }
                IconButton(onClick = { viewModel.clearBoardKeepGenerals() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Xóa bàn", tint = ErrorRed)
                }
            }

            // Piece placement palette (Red, Black, Erase)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Chọn quân rồi chạm lên bàn để đặt hoặc xóa",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                PiecePaletteRow(
                    side = Side.RED,
                    activeTool = state.activeTool,
                    onSelectTool = { viewModel.selectTool(it) }
                )
                Spacer(Modifier.height(6.dp))
                PiecePaletteRow(
                    side = Side.BLACK,
                    activeTool = state.activeTool,
                    onSelectTool = { viewModel.selectTool(it) }
                )
                Spacer(Modifier.height(6.dp))
                FilterChip(
                    selected = state.activeTool == EditorTool.Erase,
                    onClick = { viewModel.selectTool(EditorTool.Erase) },
                    label = { Text("Công cụ Xóa quân (chạm vào quân để xóa)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ErrorRed.copy(alpha = 0.22f),
                        selectedLabelColor = ErrorRed,
                        containerColor = BgCardElevated,
                        labelColor = TextPrimary
                    )
                )
            }

            // Validation error or feedback banner
            if (state.validationError != null) {
                LuyenKyCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = ErrorRed.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Thế cờ chưa hợp lệ: ${state.validationError}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ErrorRed
                    )
                }
            } else if (state.feedbackBanner != null) {
                LuyenKyCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = AccentEmerald.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = state.feedbackBanner ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentEmerald
                    )
                }
            }

            // Analysis Preview Result (if run)
            state.analysisPreview?.let { preview ->
                EvaluationBar(scoreRed = preview.scoreRed)
                LuyenKyCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = InfoBlue.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "Phân tích thế cờ (Độ sâu ${preview.depth})",
                        style = MaterialTheme.typography.titleMedium,
                        color = InfoBlue
                    )
                    Spacer(Modifier.height(6.dp))
                    if (preview.variations.isEmpty()) {
                        Text("Không tìm thấy biến hợp lệ.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        preview.variations.take(3).forEach { line ->
                            val notationText = buildVariationNotation(state.board, line.moves)
                            Text(
                                text = "${line.rank}. $notationText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = CommentaryService.VARIATION_DISCLAIMER,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            // FEN Text Input + Validate + Copy
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Chuỗi FEN", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.fenInput,
                    onValueChange = { viewModel.onFenInputChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        cursorColor = AccentEmerald
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.applyFenInput() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Kiểm tra & Áp dụng FEN", color = AccentEmerald)
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(state.fenInput))
                            viewModel.notifyFenCopied()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Sao chép FEN", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sao chép", color = TextPrimary)
                    }
                }
            }

            // Action Buttons: Analyze, Play vs Engine, Save as Puzzle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.analyzeCurrentPosition() },
                    enabled = state.isBoardValid && !state.isAnalyzing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BgMain)
                    } else {
                        Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Phân tích thế", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { viewModel.playAgainstEngine(onStartGameFromPosition) },
                    enabled = state.isBoardValid,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Đấu với máy", fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = { viewModel.saveAsPuzzle() },
                enabled = state.isBoardValid,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lưu thế này thành bài tập", color = WarningAmber)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PiecePaletteRow(
    side: Side,
    activeTool: EditorTool,
    onSelectTool: (EditorTool) -> Unit
) {
    val types = listOf(
        PieceType.GENERAL,
        PieceType.ADVISOR,
        PieceType.ELEPHANT,
        PieceType.HORSE,
        PieceType.CHARIOT,
        PieceType.CANNON,
        PieceType.SOLDIER
    )
    val accent = if (side == Side.RED) PieceRedInk else Color(0xFFD6DAE2)
    val bgTint = if (side == Side.RED) PieceRedInk.copy(alpha = 0.14f) else PieceBlackInk.copy(alpha = 0.5f)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(types) { type ->
            val piece = Piece(side, type)
            val isSelected = activeTool == EditorTool.Place(piece)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentEmerald.copy(alpha = 0.25f) else bgTint)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) AccentEmerald else BorderSubtle,
                        shape = CircleShape
                    )
                    .clickable { onSelectTool(EditorTool.Place(piece)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = piece.type.hanzi(piece.side),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun buildVariationNotation(
    startBoard: com.thanhvan.cotuongluyenky.core.model.BoardState,
    moves: List<com.thanhvan.cotuongluyenky.core.model.Move>
): String {
    var board = startBoard
    val tokens = mutableListOf<String>()
    for (mv in moves.take(6)) {
        if (!RulesEngine.isMoveLegal(board, mv)) break
        tokens.add(XiangqiNotation.format(board, mv).short)
        board = RulesEngine.applyMoveUnchecked(board, mv)
    }
    return tokens.joinToString(" · ")
}
