package com.thanhvan.cotuongluyenky.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.GameResult
import com.thanhvan.cotuongluyenky.core.model.Side
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.formatTimestamp
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary

@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onPlayNewGame: () -> Unit,
    onBackToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BgMain
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentEmerald)
            }
            return@Scaffold
        }

        val game = state.game
        if (game == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                LuyenKyCard {
                    Text("Không tìm thấy dữ liệu ván đấu.", color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onBackToHome,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                    ) {
                        Text("Về sảnh chính")
                    }
                }
            }
            return@Scaffold
        }

        val playerWon = (game.playerSide == Side.RED && game.result == GameResult.RED_WIN) ||
            (game.playerSide == Side.BLACK && game.result == GameResult.BLACK_WIN)
        val isDraw = game.result == GameResult.DRAW

        val headlineText = when {
            isDraw -> "Hòa cờ"
            playerWon -> "Chiến thắng!"
            else -> "Thất bại"
        }
        val headlineColor = when {
            isDraw -> InfoBlue
            playerWon -> AccentEmerald
            else -> ErrorRed
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            LuyenKyCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = headlineColor.copy(alpha = 0.65f)
            ) {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = headlineColor
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${game.result.vietnameseLabel} · ${game.resultReason.vietnameseLabel}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                game.entity.finishedAt?.let { finished ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Kết thúc lúc ${formatTimestamp(finished)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Chi tiết ván đấu",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                DetailRow("Máy cờ", "${game.entity.engineName} (Cấp ${game.entity.engineLevel})")
                DetailRow("Bên bạn cầm", game.playerSide.vietnameseName)
                DetailRow("Chế độ", game.mode.vietnameseName)
                DetailRow("Tổng số nửa lượt", "${game.moves.size} nước")
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                DetailRow("Số lần đi lại", game.entity.undoCount.toString())
                DetailRow("Số lần dùng gợi ý", game.entity.hintCount.toString())
            }

            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Phân tích sau ván",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ván đấu đã được lưu đầy đủ lịch sử nước đi vào cơ sở dữ liệu cục bộ. Tính năng phân tích từng nước, biểu đồ lợi thế và tạo bài tập từ sai lầm sẽ mở khóa ở Phase 2 & 3.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onPlayNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
            ) {
                Text("Chơi ván mới", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onBackToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Về sảnh chính", color = TextPrimary)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}
