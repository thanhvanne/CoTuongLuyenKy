package com.thanhvan.cotuongluyenky.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.GameStatus
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.core.ui.components.formatTimestamp
import com.thanhvan.cotuongluyenky.data.repository.GameSummary
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onResumeGame: (Long) -> Unit,
    onOpenResult: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Lịch sử ván đấu",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
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

        Spacer(Modifier.height(12.dp))

        if (state.games.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Chưa có ván đấu nào",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Các ván đấu bạn chơi với máy sẽ tự động được lưu tại đây để tiếp tục hoặc phân tích sau.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.games, key = { it.id }) { game ->
                    GameHistoryItemCard(
                        game = game,
                        onClick = {
                            if (game.status == GameStatus.IN_PROGRESS) {
                                onResumeGame(game.id)
                            } else {
                                onOpenResult(game.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameHistoryItemCard(
    game: GameSummary,
    onClick: () -> Unit
) {
    val (badgeText, badgeColor) = when {
        game.status == GameStatus.IN_PROGRESS -> "Đang dở" to WarningAmber
        game.result == GameResult.DRAW -> "Hòa" to InfoBlue
        game.playerWon -> "Thắng" to AccentEmerald
        else -> "Thua" to ErrorRed
    }

    LuyenKyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${game.engineName} · Cấp ${game.engineLevel}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            StatusPill(
                text = badgeText,
                containerColor = badgeColor.copy(alpha = 0.18f),
                contentColor = badgeColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Cầm ${game.playerSide.vietnameseName} · ${game.mode.vietnameseName} · ${game.moveCount} nước",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        if (game.status == GameStatus.FINISHED && game.resultReason.vietnameseLabel.isNotBlank()) {
            Text(
                text = game.resultReason.vietnameseLabel,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimestamp(game.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            if (game.undoCount > 0) {
                Text(
                    text = "Đi lại: ${game.undoCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningAmber
                )
            }
        }
    }
}
