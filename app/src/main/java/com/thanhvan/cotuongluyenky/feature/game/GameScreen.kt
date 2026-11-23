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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.core.ui.components.formatClockMillis
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
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

                    // Engine error recovery banner (Section 22.2)
                    if (state.engineError != null) {
                        LuyenKyCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
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

                    // Main Xiangqi Board
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        contentAlignment = Alignment.Center
                    ) {
                        XiangqiBoard(
                            board = state.board,
                            selectedSquare = state.selectedSquare,
                            legalTargets = state.legalTargets,
                            lastMove = state.lastMove,
                            checkedSide = state.checkedSide,
                            isFlipped = state.isFlipped,
                            pieceDisplayStyle = settings.pieceDisplayStyle,
                            boardThemeStyle = settings.boardThemeStyle,
                            showCoordinates = settings.showCoordinates,
                            reduceMotion = settings.reduceMotion,
                            animationEvent = state.animationEvent,
                            enabled = state.isPlayerTurn && !state.isEngineThinking,
                            onSquareTapped = { viewModel.onSquareTapped(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Bottom player bar
                    PlayerStrip(
                        name = if (bottomSide == state.playerSide) "Bạn" else "Máy (Cấp ${state.engineLevel})",
                        side = bottomSide,
                        isTurn = !state.isFinished && state.board.sideToMove == bottomSide,
                        isThinking = bottomSide == state.aiSide && state.isEngineThinking,
                        isTimed = state.isTimed,
                        remainingMillis = if (bottomSide == Side.RED) state.redRemainingMillis else state.blackRemainingMillis
                    )

                    // Status / Finished banner OR Recent move notation strip
                    if (state.isFinished) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgCardElevated)
                                .border(1.dp, AccentEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.result.vietnameseLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AccentEmerald
                                )
                                Text(
                                    text = state.resultReason.vietnameseLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            Button(
                                onClick = { onNavigateToResult(state.gameId) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                            ) {
                                Text("Xem kết quả")
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (state.statusBanner != null) {
                                Text(
                                    text = state.statusBanner ?: "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (state.checkedSide != null) ErrorRed else WarningAmber,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }
                            if (state.moves.isNotEmpty()) {
                                LazyRow(
                                    state = moveListState,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
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
                    }

                    // Bottom Action Bar (Section 18.4)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GameActionButton(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            label = if (state.mode == GameMode.CHALLENGE) "Khóa đi lại" else "Đi lại",
                            enabled = state.canUndo,
                            onClick = { viewModel.undoMove() },
                            modifier = Modifier.weight(1f)
                        )
                        GameActionButton(
                            icon = Icons.Filled.SwapVert,
                            label = "Xoay bàn",
                            enabled = true,
                            onClick = { viewModel.flipBoard() },
                            modifier = Modifier.weight(1f)
                        )
                        GameActionButton(
                            icon = Icons.Filled.Flag,
                            label = if (state.isFinished) "Kết quả" else "Xin thua",
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
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(
                width = if (isTurn) 1.5.dp else 1.dp,
                color = if (isTurn) AccentEmerald else BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
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
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BgCard,
            disabledContainerColor = Color(0xFF15171B)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
