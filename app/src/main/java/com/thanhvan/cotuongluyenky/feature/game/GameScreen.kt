package com.thanhvan.cotuongluyenky.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.ui.components.EvaluationBar
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.core.ui.components.formatClockMillis
import com.thanhvan.cotuongluyenky.domain.analysis.LiveMoveEvaluation
import com.thanhvan.cotuongluyenky.domain.analysis.MoveClassification
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.domain.commentary.DangerLevel
import com.thanhvan.cotuongluyenky.domain.commentary.PieceDangerInfo
import com.thanhvan.cotuongluyenky.domain.rules.XiangqiNotation
import com.thanhvan.cotuongluyenky.feature.game.components.XiangqiBoard
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCard
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.PieceRedInk
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (Long) -> Unit,
    onNavigateToAnalysis: (Long) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showResignDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onPauseLifecycle()
                Lifecycle.Event.ON_RESUME -> viewModel.onResumeLifecycle()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val moveListState = rememberLazyListState()
    LaunchedEffect(state.moves.size) {
        if (state.moves.isNotEmpty()) {
            moveListState.animateScrollToItem(state.moves.lastIndex)
        }
    }

    if (showResignDialog) {
        AlertDialog(
            onDismissRequest = { showResignDialog = false },
            containerColor = BgCard,
            title = { Text("Xác nhận xin thua?", color = TextPrimary) },
            text = {
                Text(
                    "Ván đấu sẽ kết thúc và được lưu vào lịch sử. Bạn chắc chắn muốn xin thua chứ?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResignDialog = false
                        viewModel.resignGame(onNavigateToResult)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Xin thua", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResignDialog = false }) {
                    Text("Tiếp tục chơi", color = TextPrimary)
                }
            }
        )
    }

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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${state.engineName} · Cấp ${state.engineLevel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.mode.vietnameseName} · Lượt ${state.board.fullMoveNumber} (${state.moves.size} nước)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = { viewModel.toggleSoundQuick() }) {
                    Icon(
                        imageVector = if (settings.soundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = "Bật/tắt âm thanh",
                        tint = if (settings.soundEnabled) AccentEmerald else TextSecondary
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Cài đặt",
                        tint = TextPrimary
                    )
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
                        Text(
                            text = "Không thể mở ván đấu",
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorRed
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = state.loadError ?: "", color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                        ) {
                            Text("Quay về sảnh")
                        }
                    }
                }
            }

            else -> {
                val topSide = if (!state.isFlipped) Side.BLACK else Side.RED
                val bottomSide = if (!state.isFlipped) Side.RED else Side.BLACK

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Opponent / Top player bar
                    PlayerStrip(
                        name = if (topSide == state.aiSide) "Máy (Cấp ${state.engineLevel})" else "Bạn",
                        side = topSide,
                        isTurn = !state.isFinished && state.board.sideToMove == topSide,
                        isThinking = topSide == state.aiSide && state.isEngineThinking,
                        isTimed = state.isTimed,
                        remainingMillis = if (topSide == Side.RED) state.redRemainingMillis else state.blackRemainingMillis
                    )

                    // Optional Evaluation Bar in Practice mode (Section 9.6 & 18.4)
                    if (state.isPracticeMode && settings.showEvalBar && state.latestEvalScoreRed != null) {
                        EvaluationBar(
                            scoreRed = state.latestEvalScoreRed,
                            isQuickEval = true
                        )
                    }

                    // Engine error recovery banner (Section 22.2)
                    if (state.engineError != null) {
                        LuyenKyCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = ErrorRed,
                            containerColor = Color(0xFF2A1617)
                        ) {
                            Text(
                                text = "Không thể nhận nước từ máy cờ. Ván của bạn đã được lưu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.triggerEngineMove() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Thử lại")
                                }
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Quay về sảnh", color = TextPrimary)
                                }
                            }
                        }
                    }

                    // Main Xiangqi Board (renders live board OR Tier-4 variation preview)
                    XiangqiBoard(
                        board = state.displayBoard,
                        selectedSquare = if (state.isPreviewingVariation) null else state.selectedSquare,
                        legalTargets = if (state.isPreviewingVariation) emptySet() else state.legalTargets,
                        lastMove = if (state.isPreviewingVariation) null else state.lastMove,
                        checkedSide = if (state.isPreviewingVariation) null else state.checkedSide,
                        isFlipped = state.isFlipped,
                        pieceDisplayStyle = settings.pieceDisplayStyle,
                        boardThemeStyle = settings.boardThemeStyle,
                        showCoordinates = settings.showCoordinates,
                        reduceMotion = settings.reduceMotion,
                        animationEvent = if (state.isPreviewingVariation) null else state.animationEvent,
                        arrows = state.activeArrows,
                        hintPieceSquare = state.hintPieceSquare,
                        moveFeedbackEffect = if (state.isPreviewingVariation) null else state.moveFeedbackVisual,
                        dangerPieces = state.dangerOverlayMap,
                        enabled = (state.isPlayerTurn && !state.isEngineThinking && !state.isPreviewingVariation) || state.isDangerCheckEnabled,
                        onSquareTapped = { viewModel.onSquareTapped(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Bottom player bar
                    PlayerStrip(
                        name = if (bottomSide == state.playerSide) "Bạn" else "Máy (Cấp ${state.engineLevel})",
                        side = bottomSide,
                        isTurn = !state.isFinished && state.board.sideToMove == bottomSide,
                        isThinking = bottomSide == state.aiSide && state.isEngineThinking,
                        isTimed = state.isTimed,
                        remainingMillis = if (bottomSide == Side.RED) state.redRemainingMillis else state.blackRemainingMillis
                    )

                    // Tiered Hint Panel (Section 10.1–10.3)
                    if (state.hintState.isOpen) {
                        HintPanelCard(
                            state = state,
                            notationStyle = settings.notationStyle,
                            onAdvanceTier = { viewModel.requestOrAdvanceHint() },
                            onJumpTier = { viewModel.jumpHintToTier(it) },
                            onStepVariation = { viewModel.stepHintVariation(it) },
                            onSwitchVariation = { viewModel.switchHintVariation() },
                            onClose = { viewModel.closeHint() }
                        )
                    }

                    // Danger piece detail inspector (Section 14.1–14.3)
                    if (state.isDangerCheckEnabled && state.inspectedDangerPiece != null) {
                        DangerPieceInfoCard(
                            info = state.inspectedDangerPiece!!,
                            totalDangerCount = state.dangerPieces.size,
                            onDismiss = { viewModel.dismissInspectedDangerPiece() },
                            onTurnOff = { viewModel.toggleDangerCheck() }
                        )
                    } else if (state.isDangerCheckEnabled && state.dangerPieces.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgCard)
                                .border(1.dp, AccentEmerald.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hiện không có quân nào của bạn đang bị tấn công trực tiếp.",
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.toggleDangerCheck() }) {
                                Text("Tắt", color = TextSecondary)
                            }
                        }
                    }

                    // Live Quick Evaluation Strip (Section 9.1–9.3 & 18.4)
                    if (!state.isFinished && !state.hintState.isOpen && state.liveEvaluation != null) {
                        LiveEvaluationCard(
                            eval = state.liveEvaluation!!,
                            onDismiss = { viewModel.dismissLiveEvaluation() }
                        )
                    }

                    // Game Finished Banner with direct link to Phase 2 Analysis
                    if (state.isFinished) {
                        LuyenKyCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = AccentEmerald.copy(alpha = 0.65f),
                            containerColor = BgCardElevated
                        ) {
                            Text(
                                text = "${state.result.vietnameseLabel} · ${state.resultReason.vietnameseLabel}",
                                style = MaterialTheme.typography.titleMedium,
                                color = AccentEmerald
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateToAnalysis(state.gameId) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                                ) {
                                    Text("Phân tích ván", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onNavigateToResult(state.gameId) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Xem kết quả", color = TextPrimary)
                                }
                            }
                        }
                    } else {
                        if (state.statusBanner != null) {
                            Text(
                                text = state.statusBanner ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.checkedSide != null) ErrorRed else WarningAmber,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        if (state.moves.isNotEmpty()) {
                            LazyRow(
                                state = moveListState,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(state.moves, key = { it.plyIndex }) { moveRec ->
                                    val isRed = moveRec.side == Side.RED
                                    val turnNo = (moveRec.plyIndex / 2) + 1
                                    val prefix = if (isRed) "$turnNo. " else ""
                                    StatusPill(
                                        text = "$prefix${moveRec.displayNotation(settings.notationStyle)}",
                                        containerColor = if (isRed) Color(0xFF2B1A1C) else BgCardElevated,
                                        contentColor = if (isRed) Color(0xFFFF8A80) else TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Action Bar (Section 18.4: Undo, Hint, Flip, Danger Check, Resign)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GameActionButton(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            label = "Đi lại",
                            enabled = state.canUndo,
                            onClick = { viewModel.undoMove() },
                            modifier = Modifier.weight(1f)
                        )
                        GameActionButton(
                            icon = Icons.Filled.Lightbulb,
                            label = "Gợi ý",
                            enabled = state.isPracticeMode && settings.hintsEnabled && state.isPlayerTurn && !state.isEngineThinking,
                            tint = if (state.hintState.isOpen) WarningAmber else AccentEmerald,
                            onClick = { viewModel.requestOrAdvanceHint() },
                            modifier = Modifier.weight(1f)
                        )
                        GameActionButton(
                            icon = Icons.Filled.Shield,
                            label = "Nguy hiểm",
                            enabled = state.isPracticeMode && !state.isFinished,
                            tint = if (state.isDangerCheckEnabled) WarningAmber else TextPrimary,
                            onClick = { viewModel.toggleDangerCheck() },
                            modifier = Modifier.weight(1f)
                        )
                        GameActionButton(
                            icon = Icons.Filled.SwapVert,
                            label = "Xoay",
                            enabled = true,
                            onClick = { viewModel.flipBoard() },
                            modifier = Modifier.weight(1f)
                        )
                        GameActionButton(
                            icon = Icons.Filled.Flag,
                            label = if (state.isFinished) "Kết quả" else "Thua",
                            enabled = true,
                            tint = if (state.isFinished) AccentEmerald else ErrorRed,
                            onClick = {
                                if (state.isFinished) {
                                    onNavigateToResult(state.gameId)
                                } else {
                                    showResignDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveEvaluationCard(
    eval: LiveMoveEvaluation,
    onDismiss: () -> Unit
) {
    val badgeColor = classificationColor(eval.classification)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = eval.classification.badgeText,
                    containerColor = badgeColor.copy(alpha = 0.2f),
                    contentColor = badgeColor
                )
                Text(
                    text = "Đánh giá nhanh",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = eval.commentary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HintPanelCard(
    state: GameUiState,
    notationStyle: NotationStyle,
    onAdvanceTier: () -> Unit,
    onJumpTier: (Int) -> Unit,
    onStepVariation: (Int) -> Unit,
    onSwitchVariation: () -> Unit,
    onClose: () -> Unit
) {
    val hint = state.hintState
    LuyenKyCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = InfoBlue.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tierLabel = when (hint.tier) {
                1 -> "Gợi ý · Tầng 1 (Ý tưởng)"
                2 -> "Gợi ý · Tầng 2 (Chỉ quân)"
                3 -> "Gợi ý · Tầng 3 (Chỉ nước)"
                4 -> "Gợi ý · Tầng 4 (Xem biến)"
                else -> "Gợi ý"
            }
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.titleMedium,
                color = InfoBlue
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Đóng gợi ý", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        when {
            hint.isLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = InfoBlue)
                    Spacer(Modifier.width(10.dp))
                    Text("Đang tìm phương án gợi ý...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            hint.error != null -> {
                Text(text = hint.error, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
            }
            hint.bundle != null -> {
                val bundle = hint.bundle
                val variation = bundle.variations.getOrNull(hint.selectedVariationIndex)
                val bestFormatted = XiangqiNotation.format(state.board, bundle.bestMove)
                val bestNotation = if (notationStyle == NotationStyle.FULL) bestFormatted.full else bestFormatted.short

                when (hint.tier) {
                    1 -> Text(text = bundle.ideaText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    2 -> {
                        val piece = state.board.pieceAt(bundle.bestMove.from)
                        Text(
                            text = "Hãy chú ý quân ${piece?.type?.vietnameseName ?: "được đánh dấu xanh"} trên bàn cờ.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    3 -> {
                        Text(
                            text = "Nước đề xuất: $bestNotation (xem mũi tên xanh trên bàn cờ).",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentEmerald
                        )
                    }
                    4 -> {
                        val stepCount = variation?.moves?.size ?: 0
                        Text(
                            text = "Phương án ${hint.selectedVariationIndex + 1}/${bundle.variations.size} · Bước ${hint.previewStep}/$stepCount",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentEmerald
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = CommentaryService.VARIATION_DISCLAIMER,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onStepVariation(-1) },
                                enabled = hint.previewStep > 0,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Nước trước")
                            }
                            Button(
                                onClick = { onStepVariation(1) },
                                enabled = hint.previewStep < stepCount,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Nước tiếp")
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (bundle.variations.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = onSwitchVariation,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Xem phương án khác", color = TextPrimary)
                            }
                        }
                    }
                }

                if (hint.tier < 4) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAdvanceTier,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (hint.tier) {
                                    1 -> "Gợi ý thêm (Chỉ quân)"
                                    2 -> "Hiện nước đi"
                                    else -> "Xem biến"
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (hint.tier < 3) {
                            OutlinedButton(
                                onClick = { onJumpTier(3) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Hiện nước đi", color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DangerPieceInfoCard(
    info: PieceDangerInfo,
    totalDangerCount: Int,
    onDismiss: () -> Unit,
    onTurnOff: () -> Unit
) {
    val accent = when (info.level) {
        DangerLevel.UNDER_ATTACK -> WarningAmber
        DangerLevel.AT_RISK_OF_LOSS -> Color(0xFFFF7043)
        DangerLevel.ENGINE_LOSS_LINE -> ErrorRed
    }
    LuyenKyCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accent.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusPill(
                text = "${info.piece.type.vietnameseName} · ${info.level.vietnameseLabel}",
                containerColor = accent.copy(alpha = 0.2f),
                contentColor = accent
            )
            TextButton(onClick = onTurnOff, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Tắt kiểm tra", color = TextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = info.level.description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        val attackerNames = info.attackers.joinToString(", ") { it.second.type.vietnameseName }
        val defenderNames = if (info.defenders.isEmpty()) "Không có" else info.defenders.joinToString(", ") { it.second.type.vietnameseName }
        Text(
            text = "Đang bị tấn công bởi: $attackerNames · Quân bảo vệ: $defenderNames",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        if (totalDangerCount > 1) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Có $totalDangerCount quân đang bị đe dọa — chạm vào từng quân được khoanh viền để xem chi tiết.",
                style = MaterialTheme.typography.labelMedium,
                color = InfoBlue
            )
        }
    }
}

@Composable
private fun PlayerStrip(
    name: String,
    side: Side,
    isTurn: Boolean,
    isThinking: Boolean,
    isTimed: Boolean,
    remainingMillis: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(
                width = if (isTurn) 1.5.dp else 1.dp,
                color = if (isTurn) AccentEmerald else BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (side == Side.RED) PieceRedInk else Color(0xFF27303C))
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (side == Side.RED) "Đ" else "Đn",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$name · Bên ${side.vietnameseName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                if (isThinking) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AccentEmerald
                    )
                }
            }
            Text(
                text = when {
                    isThinking -> "Máy đang tính nước..."
                    isTurn -> "Đang tới lượt đi"
                    else -> "Đang chờ lượt"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isTurn) AccentEmerald else TextSecondary
            )
        }
        if (isTimed) {
            val lowTime = remainingMillis <= 30_000L
            StatusPill(
                text = formatClockMillis(remainingMillis),
                containerColor = if (lowTime) Color(0xFF3A1818) else BgCardElevated,
                contentColor = if (lowTime) ErrorRed else if (isTurn) AccentEmerald else TextPrimary
            )
        } else if (isTurn) {
            StatusPill(
                text = "Lượt đi",
                containerColor = Color(0xFF0E2C23),
                contentColor = AccentEmerald
            )
        }
    }
}

@Composable
private fun GameActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = TextPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BgCard,
            disabledContainerColor = Color(0xFF15171B)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else TextSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun classificationColor(classification: MoveClassification): Color = when (classification) {
    MoveClassification.GOOD -> AccentEmerald
    MoveClassification.ACCEPTABLE -> InfoBlue
    MoveClassification.INACCURACY -> WarningAmber
    MoveClassification.MISTAKE -> Color(0xFFFF7043)
    MoveClassification.BLUNDER -> ErrorRed
}
