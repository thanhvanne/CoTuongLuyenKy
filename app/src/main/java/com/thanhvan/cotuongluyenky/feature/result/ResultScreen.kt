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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.thanhvan.cotuongluyenky.core.model.AnalysisStatus
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
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onAnalyzeGame: (Long) -> Unit,
    onOpenPuzzle: (Long) -> Unit,
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

        val analysisSummary = state.analysisSummary
        val isAnalyzed = analysisSummary?.status == AnalysisStatus.COMPLETED ||
            analysisSummary?.status == AnalysisStatus.PARTIAL

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
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
                DetailRow("Số lần xem quân nguy hiểm", game.entity.dangerCheckCount.toString())
            }

            // Post-game analysis summary (Section 18.5: never show fabricated error counts before analysis!)
            LuyenKyCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = AccentEmerald.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Phân tích ván đấu",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                if (isAnalyzed && analysisSummary != null) {
                    DetailRow("Sai lầm nghiêm trọng (??)", analysisSummary.blunders.toString())
                    DetailRow("Sai lầm (?)", analysisSummary.mistakes.toString())
                    DetailRow("Chưa chính xác (?!)", analysisSummary.inaccuracies.toString())
                    DetailRow("Số bước ngoặt phát hiện", analysisSummary.turningPointCount.toString())
                } else {
                    Text(
                        text = "Ván đấu chưa được phân tích. Chạy phân tích để xem biểu đồ lợi thế, nhận xét từng nước, biến đề xuất và thử các nhánh khác.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onAnalyzeGame(game.entity.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isAnalyzed) "Xem phân tích ván" else "Bắt đầu phân tích",
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isAnalyzed && analysisSummary != null && (analysisSummary.blunders + analysisSummary.mistakes > 0)) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.openMistakePractice(
                                onOpenPuzzle = onOpenPuzzle,
                                onNoMistakeFound = { onAnalyzeGame(game.entity.id) }
                            )
                        },
                        enabled = !state.isPreparingPuzzles,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (state.isPreparingPuzzles) "Đang tạo bài sửa sai..." else "Luyện lại lỗi từ ván này",
                            color = InfoBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onPlayNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Chơi ván mới", color = WarningAmber, fontWeight = FontWeight.SemiBold)
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
