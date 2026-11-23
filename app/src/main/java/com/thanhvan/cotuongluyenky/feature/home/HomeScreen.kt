package com.thanhvan.cotuongluyenky.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.core.ui.components.formatTimestamp
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSetup: () -> Unit,
    onResumeGame: (Long) -> Unit,
    onOpenPositionEditor: () -> Unit,
    onStartDuePuzzle: (Long) -> Unit,
    onOpenTrainingTab: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header (Section 18.2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Cờ Tướng – Luyện Kỳ",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Chơi offline · ${state.engineInfo.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Hồ sơ và cài đặt",
                    tint = TextPrimary
                )
            }
        }

        // Card 1: Continue Unfinished Game (only shown when an active game exists)
        state.unfinishedGame?.let { unfinished ->
            LuyenKyCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = WarningAmber.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tiếp tục ván dở",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarningAmber
                    )
                    StatusPill(
                        text = unfinished.mode.vietnameseName,
                        containerColor = BgCardElevated,
                        contentColor = TextPrimary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Máy Cấp ${unfinished.engineLevel} · Bạn cầm ${unfinished.playerSide.vietnameseName} · ${unfinished.moveCount} nước",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Lưu lúc ${formatTimestamp(unfinished.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onResumeGame(unfinished.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, tint = Color(0xFF1F1404))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Tiếp tục ván đấu",
                        color = Color(0xFF1F1404),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Card 2: Play vs Engine (Primary CTA)
        LuyenKyCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = AccentEmerald.copy(alpha = 0.55f)
        ) {
            Text(
                text = "Đấu với máy",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Chọn cấp độ, bên cầm và chế độ chơi phù hợp với trình độ của bạn.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = "Cấp ${state.settings.defaultEngineLevel}",
                    containerColor = BgCardElevated,
                    contentColor = AccentEmerald
                )
                StatusPill(
                    text = state.settings.defaultPlayerSide.vietnameseName,
                    containerColor = BgCardElevated,
                    contentColor = TextPrimary
                )
                StatusPill(
                    text = state.settings.defaultGameMode.vietnameseName,
                    containerColor = BgCardElevated,
                    contentColor = InfoBlue
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenSetup,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Bắt đầu", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenSetup,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Thiết lập",
                        tint = TextPrimary
                    )
                }
            }
        }

        // Card 3: Luyện hôm nay (Section 18.2)
        LuyenKyCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = InfoBlue.copy(alpha = 0.55f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = InfoBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Luyện hôm nay", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                StatusPill(
                    text = "${state.duePuzzlesCount} bài cần ôn",
                    containerColor = InfoBlue.copy(alpha = 0.2f),
                    contentColor = InfoBlue
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Đã hoàn thành ${state.solvedPuzzlesCount} bài tập. Giải nhanh các bài đến hạn hoặc ôn lại sai lầm từ ván đã chơi.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val id = state.nextDuePuzzleId
                        if (id != null) onStartDuePuzzle(id) else onOpenTrainingTab()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InfoBlue)
                ) {
                    Text("Luyện ngay", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenPositionEditor,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nhập thế cờ", color = TextPrimary)
                }
            }
        }

        // Card 4: Transparent Stats Snapshot (Section 16.1 & 18.2)
        LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Thống kê của bạn",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            if (state.stats.totalFinishedGames == 0) {
                Text(
                    text = "Chưa có ván đấu hoàn tất nào. Hãy chơi ván đầu tiên để bắt đầu theo dõi kết quả!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell(label = "Tổng ván", value = state.stats.totalFinishedGames.toString(), color = TextPrimary)
                    StatCell(label = "Thắng", value = state.stats.wins.toString(), color = AccentEmerald)
                    StatCell(label = "Hòa", value = state.stats.draws.toString(), color = InfoBlue)
                    StatCell(label = "Thua", value = state.stats.losses.toString(), color = ErrorRed)
                }
                if (state.stats.gamesWithAids > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Số ván có dùng trợ giúp/đi lại: ${state.stats.gamesWithAids}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}
