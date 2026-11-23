package com.thanhvan.cotuongluyenky.feature.training

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.data.repository.PuzzleItem
import com.thanhvan.cotuongluyenky.domain.training.TrainingStatistics
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel,
    onOpenPuzzle: (Long) -> Unit,
    onOpenPositionEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Luyện tập cá nhân hóa",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Sửa sai từ ván đấu, bài ôn tập ngắt quãng & thống kê",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = onOpenPositionEditor,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nhập thế", color = InfoBlue)
                }
            }
        }

        // 1. Due Today Card (Section 18.8)
        item {
            LuyenKyCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = AccentEmerald.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = AccentEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bài cần ôn hôm nay",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    StatusPill(
                        text = "${state.stats.duePuzzlesCount} bài đến hạn",
                        containerColor = AccentEmerald.copy(alpha = 0.2f),
                        contentColor = AccentEmerald
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ôn lại các bài đến hạn theo lịch ngắt quãng để ghi nhớ bền vững hơn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { state.duePuzzleId?.let(onOpenPuzzle) },
                        enabled = state.duePuzzleId != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Luyện ngay", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.syncMistakePuzzles() },
                        enabled = !state.isSyncingMistakes,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.isSyncingMistakes) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WarningAmber)
                        } else {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Tạo bài sửa sai", color = WarningAmber)
                    }
                }
                if (state.statusMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.statusMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = InfoBlue,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissStatusMessage() }) {
                            Text("Đóng", color = TextSecondary)
                        }
                    }
                }
            }
        }

        // 2. Category Chips (Section 15.1 & 18.8)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TrainingCategory.entries) { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentEmerald.copy(alpha = 0.22f),
                            selectedLabelColor = AccentEmerald,
                            containerColor = BgCardElevated,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
        }

        // 3. Puzzle List
        if (state.puzzles.isEmpty()) {
            item {
                LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Chưa có bài tập trong mục \"${state.selectedCategory.label}\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (state.selectedCategory) {
                            TrainingCategory.DUE -> "Tuyệt vời! Hiện bạn không còn bài nào quá hạn ôn tập."
                            TrainingCategory.MISTAKE_FIX -> "Hãy phân tích một ván đấu đã chơi rồi bấm \"Tạo bài sửa sai\" — cả ván thắng cũng có thể có sai lầm đáng luyện."
                            TrainingCategory.SAVED -> "Bạn có thể lưu bất kỳ thế cờ nào từ màn Phân tích ván hoặc Trình nhập thế cờ."
                            else -> "Chọn mục khác để tiếp tục luyện tập."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(state.puzzles, key = { it.id }) { puzzle ->
                PuzzleListItemCard(
                    puzzle = puzzle,
                    onClick = { onOpenPuzzle(puzzle.id) },
                    onDelete = if (puzzle.catalogKey == null) {
                        { viewModel.deletePuzzle(puzzle.id) }
                    } else null
                )
            }
        }

        // 4. Transparent Statistics & Progress Card (Section 16)
        item {
            Spacer(Modifier.height(4.dp))
            StatisticsOverviewCard(stats = state.stats)
        }
    }
}

@Composable
private fun PuzzleListItemCard(
    puzzle: PuzzleItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val now = System.currentTimeMillis()
    val isDue = puzzle.isDueAt(now)
    val (progressLabel, progressColor) = when {
        puzzle.isSolvedWithoutHint && !isDue -> "Đã giải (không gợi ý)" to AccentEmerald
        puzzle.isSolved && !isDue -> "Đã giải" to InfoBlue
        puzzle.attemptCount > 0 && isDue -> "Cần ôn lại" to WarningAmber
        else -> "Chưa giải" to TextSecondary
    }

    LuyenKyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(
                    text = puzzle.theme.label,
                    containerColor = InfoBlue.copy(alpha = 0.18f),
                    contentColor = InfoBlue
                )
                StatusPill(
                    text = progressLabel,
                    containerColor = progressColor.copy(alpha = 0.16f),
                    contentColor = progressColor
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Xóa bài tập",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = puzzle.title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Bên đi: ${puzzle.sideToMove.vietnameseName} · Nguồn: ${puzzle.sourceType.label}",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        if (puzzle.attemptCount > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Đã thử ${puzzle.attemptCount} lần · Đúng ${puzzle.correctCount} · Chuỗi không gợi ý: ${puzzle.consecutiveCorrectCount}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun StatisticsOverviewCard(stats: TrainingStatistics) {
    LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Thống kê & Tiến bộ",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))

        StatLine("Tổng số ván hoàn thành", stats.totalFinishedGames.toString())
        StatLine("Thắng / Thua / Hòa", "${stats.wins} / ${stats.losses} / ${stats.draws}")
        StatLine(
            "Chế độ Luyện tập",
            "${stats.practiceStats.totalGames} ván (${stats.practiceStats.wins}T-${stats.practiceStats.losses}B-${stats.practiceStats.draws}H)"
        )
        StatLine(
            "Chế độ Thử sức",
            "${stats.challengeStats.totalGames} ván (${stats.challengeStats.wins}T-${stats.challengeStats.losses}B-${stats.challengeStats.draws}H)"
        )
        StatLine("Ván có dùng gợi ý / đi lại", stats.gamesUsingAids.toString())
        if (stats.avgBlundersPerAnalyzedGame != null) {
            StatLine(
                "Lỗi lớn (??) trung bình / ván",
                String.format(Locale.US, "%.1f (%d ván đã phân tích)", stats.avgBlundersPerAnalyzedGame, stats.analyzedGamesCount)
            )
        }

        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

        StatLine("Bài tập đã giải", "${stats.solvedPuzzles} / ${stats.totalPuzzles}")
        StatLine("Giải đúng không dùng gợi ý", stats.solvedWithoutHintPuzzles.toString())
        StatLine(
            "Tỉ lệ giải đúng",
            stats.puzzleCorrectRatePercent?.let { "$it% (${stats.totalPuzzleAttempts} lượt thử)" } ?: "Chưa có dữ liệu"
        )
        StatLine("Bài đến hạn ôn", stats.duePuzzlesCount.toString())

        if (stats.gamesByLevel.isNotEmpty()) {
            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
            Text("Số ván theo cấp máy", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            val levelText = stats.gamesByLevel.entries.joinToString(" · ") { "Cấp ${it.key}: ${it.value}" }
            Text(levelText, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }

        // Reference Accuracy (Section 16.3: transparent, mode-separated, minimum sample guard)
        stats.referenceAccuracy?.let { refAcc ->
            if (refAcc.practiceReport != null || refAcc.challengeReport != null) {
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                Text("Độ chính xác tham khảo", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                refAcc.practiceReport?.let { r ->
                    StatLine("Luyện tập (${r.sampleMoves} nước)", "${r.scorePercent}%")
                }
                refAcc.challengeReport?.let { r ->
                    StatLine("Thử sức (${r.sampleMoves} nước)", "${r.scorePercent}%")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = refAcc.disclaimer,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        // Weak topics (Section 16.2: only display when enough verified data exists)
        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
        Text("Chủ đề cần chú ý", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        if (!stats.hasEnoughDataForWeakTopics) {
            Text(
                text = "Cần tối thiểu ${com.thanhvan.cotuongluyenky.domain.training.StatisticsService.MIN_GAMES_FOR_WEAK_TOPICS} ván đã phân tích để thống kê chủ đề yếu chính xác (hiện có ${stats.analyzedGamesCount} ván).",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        } else if (stats.weakTopics.isEmpty()) {
            Text(
                text = "Chưa ghi nhận chủ đề sai lầm lặp lại nổi bật từ các ván đã phân tích.",
                style = MaterialTheme.typography.bodyMedium,
                color = AccentEmerald
            )
        } else {
            stats.weakTopics.forEach { topic ->
                Text(
                    text = "• ${topic.summary}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}
