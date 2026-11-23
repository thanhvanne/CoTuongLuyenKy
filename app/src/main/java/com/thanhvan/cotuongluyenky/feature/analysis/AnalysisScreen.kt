package com.thanhvan.cotuongluyenky.feature.analysis

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
import com.thanhvan.cotuongluyenky.core.model.MoveRecord
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.ui.components.AdvantageChart
import com.thanhvan.cotuongluyenky.core.ui.components.EvaluationBar
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.domain.commentary.CommentaryService
import com.thanhvan.cotuongluyenky.domain.rules.XiangqiNotation
import com.thanhvan.cotuongluyenky.feature.game.classificationColor
import com.thanhvan.cotuongluyenky.feature.game.components.XiangqiBoard
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCard
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Respect Android background policy (Section 6.7): pause active foreground analysis on ON_STOP
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pauseAnalysisIfRunning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                        text = "Phân tích ván đấu",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${state.engineName} · Cấp ${state.engineLevel} · ${state.result.vietnameseLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.flipBoard() }) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = "Xoay bàn",
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
                        Text(state.loadError ?: "", color = ErrorRed)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onNavigateBack) { Text("Quay lại") }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Analysis status, progress & summary bar (Section 13.2 & 13.4)
                    AnalysisStatusCard(
                        state = state,
                        onStartOrResume = { viewModel.startOrResumeAnalysis(forceReanalyze = false) },
                        onReanalyze = { viewModel.startOrResumeAnalysis(forceReanalyze = true) },
                        onStop = { viewModel.stopAnalysis() },
                        onJumpTurningPoint = { viewModel.jumpToFirstTurningPoint() }
                    )

                    // 2. Collapsible Advantage Chart synced with board (Section 13.5 & 18.6)
                    if (state.chartPoints.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleChartCollapsed() }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Biểu đồ lợi thế (chạm để nhảy tới nước)",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            Icon(
                                imageVector = if (state.isChartCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = "Thu gọn/mở rộng biểu đồ",
                                tint = TextSecondary
                            )
                        }
                        if (!state.isChartCollapsed) {
                            AdvantageChart(
                                points = state.chartPoints,
                                selectedPlyNumber = state.selectedPlyNumber,
                                onSelectPlyNumber = { viewModel.selectPlyNumber(it) }
                            )
                        }
                    }

                    // 3. Evaluation Bar synced with selected ply / try-out branch
                    EvaluationBar(
                        scoreRed = state.currentEvalScoreRed,
                        isQuickEval = state.selectedPlyAnalysis?.isQuickLiveEval == true
                    )

                    // 4. Try-Out Branch Indicator Banner (Section 11.3)
                    if (state.tryOut.isActive) {
                        TryOutBanner(
                            state = state,
                            notationStyle = settings.notationStyle,
                            onUndoBranchMove = { viewModel.undoTryOutMove() },
                            onEvaluateBranch = { viewModel.evaluateTryOutPosition() },
                            onExitBranch = { viewModel.exitTryOutBranch() }
                        )
                    }

                    // 5. Board
                    XiangqiBoard(
                        board = state.displayBoard,
                        selectedSquare = if (state.tryOut.isActive) state.tryOut.selectedSquare else null,
                        legalTargets = if (state.tryOut.isActive) state.tryOut.legalTargets else emptySet(),
                        lastMove = state.displayLastMove,
                        checkedSide = state.displayCheckedSide,
                        isFlipped = state.isFlipped,
                        pieceDisplayStyle = settings.pieceDisplayStyle,
                        boardThemeStyle = settings.boardThemeStyle,
                        showCoordinates = settings.showCoordinates,
                        reduceMotion = settings.reduceMotion,
                        animationEvent = null,
                        arrows = state.displayArrows,
                        moveFeedbackEffect = state.displayFeedbackVisual,
                        enabled = state.tryOut.isActive,
                        onSquareTapped = { viewModel.onBoardSquareTapped(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 6. Replay Controls (Section 13.7)
                    if (!state.tryOut.isActive && !state.variationPreview.isActive) {
                        ReplayControlsBar(
                            selectedPly = state.selectedPlyNumber,
                            totalPlies = state.totalPlies,
                            isAutoPlaying = state.isAutoPlaying,
                            playbackSpeed = state.playbackSpeed,
                            onFirst = { viewModel.jumpToStart() },
                            onPrev = { viewModel.stepPly(-1) },
                            onTogglePlay = { viewModel.toggleAutoPlay() },
                            onNext = { viewModel.stepPly(1) },
                            onLast = { viewModel.jumpToEnd() },
                            onCycleSpeed = { viewModel.cyclePlaybackSpeed() }
                        )
                    }

                    // 7. Variation Preview Panel OR Grounded Move Commentary Card
                    if (state.variationPreview.isActive) {
                        VariationPreviewCard(
                            state = state,
                            notationStyle = settings.notationStyle,
                            onStep = { viewModel.stepVariation(it) },
                            onSwitch = { viewModel.switchVariation() },
                            onClose = { viewModel.closeVariationPreview() }
                        )
                    } else if (!state.tryOut.isActive) {
                        SelectedMoveCommentaryCard(
                            state = state,
                            notationStyle = settings.notationStyle,
                            onOpenVariation = { viewModel.openVariationPreview() },
                            onStartTryOut = { viewModel.startTryOutBranch() },
                            onSavePuzzle = { viewModel.saveCurrentPositionAsPuzzle() },
                            onDismissSavedMessage = { viewModel.dismissSavedPuzzleMessage() }
                        )
                    }

                    // 8. Filterable Move List (Section 13.6)
                    MoveListSection(
                        state = state,
                        notationStyle = settings.notationStyle,
                        onFilterChange = { viewModel.setMoveFilter(it) },
                        onSelectPly = { viewModel.selectPlyNumber(it) }
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AnalysisStatusCard(
    state: AnalysisUiState,
    onStartOrResume: () -> Unit,
    onReanalyze: () -> Unit,
    onStop: () -> Unit,
    onJumpTurningPoint: () -> Unit
) {
    val isRunning = state.analysisStatus == AnalysisStatus.IN_PROGRESS
    val isComplete = state.analysisStatus == AnalysisStatus.COMPLETED

    LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusLabel = when (state.analysisStatus) {
                AnalysisStatus.NOT_ANALYZED -> "Chưa phân tích"
                AnalysisStatus.IN_PROGRESS -> "Đang phân tích (${state.analyzedCount}/${state.totalPlies})"
                AnalysisStatus.PARTIAL -> "Đã phân tích một phần (${state.analyzedCount}/${state.totalPlies})"
                AnalysisStatus.COMPLETED -> "Đã phân tích hoàn tất"
                AnalysisStatus.INTERRUPTED -> "Phân tích bị gián đoạn"
                AnalysisStatus.ERROR -> "Lỗi phân tích"
            }
            val pillColor = when (state.analysisStatus) {
                AnalysisStatus.COMPLETED -> AccentEmerald
                AnalysisStatus.IN_PROGRESS -> InfoBlue
                AnalysisStatus.ERROR -> ErrorRed
                else -> WarningAmber
            }
            StatusPill(
                text = statusLabel,
                containerColor = pillColor.copy(alpha = 0.18f),
                contentColor = pillColor
            )
            if (isRunning) {
                TextButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dừng", color = ErrorRed)
                }
            } else if (isComplete) {
                TextButton(onClick = onReanalyze) {
                    Text("Phân tích lại", color = TextSecondary)
                }
            }
        }

        if (isRunning && state.totalPlies > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (state.analyzedCount.toFloat() / state.totalPlies).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = AccentEmerald,
                trackColor = BgCardElevated
            )
        }

        if (state.analysisError != null) {
            Spacer(Modifier.height(6.dp))
            Text(state.analysisError, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
        }

        if (!isRunning && !isComplete && state.totalPlies > 0) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStartOrResume,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.analyzedCount > 0) "Tiếp tục phân tích" else "Bắt đầu phân tích ván",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (state.analyzedCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lỗi của bạn: ${state.playerBlunderCount} ?? · ${state.playerMistakeCount} ? · ${state.playerInaccuracyCount} ?!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                if (state.turningPointPlies.isNotEmpty()) {
                    TextButton(
                        onClick = onJumpTurningPoint,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Xem bước ngoặt", color = WarningAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TryOutBanner(
    state: AnalysisUiState,
    notationStyle: NotationStyle,
    onUndoBranchMove: () -> Unit,
    onEvaluateBranch: () -> Unit,
    onExitBranch: () -> Unit
) {
    val tryOut = state.tryOut
    LuyenKyCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = InfoBlue.copy(alpha = 0.7f),
        containerColor = Color(0xFF15202D)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusPill(
                text = "Đang xem biến phân tích",
                containerColor = InfoBlue.copy(alpha = 0.22f),
                contentColor = InfoBlue
            )
            TextButton(onClick = onExitBranch, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Quay về ván gốc", color = AccentEmerald, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = "Chạm quân để đi thử nước khác mà không làm thay đổi ván gốc. (${tryOut.branchMoves.size} nước thử)",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        if (tryOut.branchMoves.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val moveSummary = tryOut.branchMoves.joinToString(" · ") { it.displayNotation(notationStyle) }
            Text(text = moveSummary, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onUndoBranchMove,
                enabled = tryOut.branchMoves.isNotEmpty(),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Lùi 1 nước", color = TextPrimary)
            }
            Button(
                onClick = onEvaluateBranch,
                enabled = !tryOut.isEvaluatingBranch,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = InfoBlue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (tryOut.isEvaluatingBranch) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BgMain)
                } else {
                    Text("Đánh giá thế này", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ReplayControlsBar(
    selectedPly: Int,
    totalPlies: Int,
    isAutoPlaying: Boolean,
    playbackSpeed: PlaybackSpeed,
    onFirst: () -> Unit,
    onPrev: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onFirst, enabled = selectedPly > 0) {
            Icon(Icons.Filled.FirstPage, contentDescription = "Về đầu", tint = if (selectedPly > 0) TextPrimary else TextSecondary.copy(alpha = 0.35f))
        }
        IconButton(onClick = onPrev, enabled = selectedPly > 0) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Nước trước", tint = if (selectedPly > 0) TextPrimary else TextSecondary.copy(alpha = 0.35f))
        }
        IconButton(onClick = onTogglePlay, enabled = totalPlies > 0) {
            Icon(
                imageVector = if (isAutoPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isAutoPlaying) "Tạm dừng" else "Phát tự động",
                tint = AccentEmerald
            )
        }
        IconButton(onClick = onNext, enabled = selectedPly < totalPlies) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Nước tiếp", tint = if (selectedPly < totalPlies) TextPrimary else TextSecondary.copy(alpha = 0.35f))
        }
        IconButton(onClick = onLast, enabled = selectedPly < totalPlies) {
            Icon(Icons.Filled.LastPage, contentDescription = "Đến cuối", tint = if (selectedPly < totalPlies) TextPrimary else TextSecondary.copy(alpha = 0.35f))
        }
        TextButton(onClick = onCycleSpeed, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(playbackSpeed.label, color = InfoBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VariationPreviewCard(
    state: AnalysisUiState,
    notationStyle: NotationStyle,
    onStep: (Int) -> Unit,
    onSwitch: () -> Unit,
    onClose: () -> Unit
) {
    val pv = state.activeVariationMoves()
    val variationCount = state.selectedPlyAnalysis?.variations?.size ?: 1
    val step = state.variationPreview.stepIndex

    LuyenKyCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = AccentEmerald.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Biến đề xuất (${state.variationPreview.variationIndex + 1}/$variationCount) · Bước $step/${pv.size}",
                style = MaterialTheme.typography.titleMedium,
                color = AccentEmerald
            )
            TextButton(onClick = onClose) {
                Text("Đóng", color = TextSecondary)
            }
        }
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
                onClick = { onStep(-1) },
                enabled = step > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Nước trước", color = TextPrimary)
            }
            Button(
                onClick = { onStep(1) },
                enabled = step < pv.size,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Text("Nước tiếp")
            }
        }
        if (variationCount > 1) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onSwitch, modifier = Modifier.fillMaxWidth()) {
                Text("Xem phương án khác", color = TextPrimary)
            }
        }
    }
}

@Composable
private fun SelectedMoveCommentaryCard(
    state: AnalysisUiState,
    notationStyle: NotationStyle,
    onOpenVariation: () -> Unit,
    onStartTryOut: () -> Unit,
    onSavePuzzle: () -> Unit,
    onDismissSavedMessage: () -> Unit
) {
    val record = state.selectedMoveRecord
    val analysis = state.selectedPlyAnalysis

    LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
        if (record == null) {
            Text(
                text = "Thế cờ ban đầu (nước 0/${state.totalPlies})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Dùng thanh điều khiển phía trên hoặc chạm vào danh sách nước bên dưới để xem diễn biến từng nước.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else {
            val turnNo = (record.plyIndex / 2) + 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lượt $turnNo · ${record.side.vietnameseName}: ${record.displayNotation(notationStyle)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                if (analysis != null) {
                    val color = classificationColor(analysis.classification)
                    StatusPill(
                        text = analysis.classification.badgeText,
                        containerColor = color.copy(alpha = 0.2f),
                        contentColor = color
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (analysis != null) {
                if (analysis.isTurningPoint) {
                    StatusPill(
                        text = "Bước ngoặt quan trọng",
                        containerColor = WarningAmber.copy(alpha = 0.2f),
                        contentColor = WarningAmber
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = analysis.commentary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                val boardBefore = state.boards.getOrNull(record.plyIndex)
                if (analysis.bestMove != null && boardBefore != null && analysis.bestMove != record.move) {
                    val bestFormatted = XiangqiNotation.format(boardBefore, analysis.bestMove)
                    val bestText = if (notationStyle == NotationStyle.FULL) bestFormatted.full else bestFormatted.short
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Phương án engine đề xuất: $bestText (độ sâu ${analysis.depth})",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentEmerald
                    )
                }
            } else {
                Text(
                    text = "Nước này chưa được phân tích. Nhấn \"Bắt đầu phân tích ván\" để xem đánh giá và biến đề xuất.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val hasVariation = analysis != null && (analysis.principalVariation.isNotEmpty() || analysis.variations.isNotEmpty())
            OutlinedButton(
                onClick = onOpenVariation,
                enabled = hasVariation,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Xem biến", color = if (hasVariation) AccentEmerald else TextSecondary.copy(alpha = 0.4f))
            }
            OutlinedButton(
                onClick = onStartTryOut,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đánh thử", color = TextPrimary)
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onSavePuzzle,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Lưu thế này thành bài tập", color = WarningAmber)
        }
        if (state.savedPuzzleMessage != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.savedPuzzleMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentEmerald,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissSavedMessage) {
                    Text("Đóng", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MoveListSection(
    state: AnalysisUiState,
    notationStyle: NotationStyle,
    onFilterChange: (MoveListFilter) -> Unit,
    onSelectPly: (Int) -> Unit
) {
    val filteredMoves = state.moves.filter { rec ->
        val analysis = state.analysesByPly[rec.plyIndex]
        when (state.moveFilter) {
            MoveListFilter.ALL -> true
            MoveListFilter.MY_ERRORS -> rec.side == state.playerSide && analysis?.classification?.isWarning == true
            MoveListFilter.OPPONENT_ERRORS -> rec.side != state.playerSide && analysis?.classification?.isWarning == true
            MoveListFilter.TURNING_POINTS -> analysis?.isTurningPoint == true
            MoveListFilter.WITH_COMMENTARY -> analysis != null && analysis.commentary.isNotBlank()
        }
    }

    LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Danh sách nước đi (${filteredMoves.size}/${state.totalPlies})",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MoveListFilter.entries) { filter ->
                FilterChip(
                    selected = state.moveFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentEmerald.copy(alpha = 0.22f),
                        selectedLabelColor = AccentEmerald,
                        containerColor = BgCardElevated,
                        labelColor = TextPrimary
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (filteredMoves.isEmpty()) {
            Text(
                text = "Không có nước đi nào khớp bộ lọc hiện tại.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filteredMoves.forEach { rec ->
                    val plyNum = rec.plyIndex + 1
                    val isSelected = state.selectedPlyNumber == plyNum
                    val analysis = state.analysesByPly[rec.plyIndex]
                    MoveListRowItem(
                        record = rec,
                        notationStyle = notationStyle,
                        isSelected = isSelected,
                        classificationSymbol = analysis?.classification?.symbol.orEmpty(),
                        classificationColor = analysis?.let { classificationColor(it.classification) },
                        isTurningPoint = analysis?.isTurningPoint == true,
                        onClick = { onSelectPly(plyNum) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveListRowItem(
    record: MoveRecord,
    notationStyle: NotationStyle,
    isSelected: Boolean,
    classificationSymbol: String,
    classificationColor: Color?,
    isTurningPoint: Boolean,
    onClick: () -> Unit
) {
    val turnNo = (record.plyIndex / 2) + 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentEmerald.copy(alpha = 0.18f) else BgCardElevated)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) AccentEmerald else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$turnNo.",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.width(28.dp)
            )
            Text(
                text = if (record.side == Side.RED) "Đỏ" else "Đen",
                style = MaterialTheme.typography.labelMedium,
                color = if (record.side == Side.RED) Color(0xFFFF8A80) else Color(0xFF90CAF9),
                modifier = Modifier.width(34.dp)
            )
            Text(
                text = record.displayNotation(notationStyle),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = TextPrimary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isTurningPoint) {
                StatusPill(
                    text = "Bước ngoặt",
                    containerColor = WarningAmber.copy(alpha = 0.2f),
                    contentColor = WarningAmber
                )
            }
            if (classificationSymbol.isNotEmpty() && classificationColor != null) {
                StatusPill(
                    text = classificationSymbol,
                    containerColor = classificationColor.copy(alpha = 0.22f),
                    contentColor = classificationColor
                )
            }
        }
    }
}
