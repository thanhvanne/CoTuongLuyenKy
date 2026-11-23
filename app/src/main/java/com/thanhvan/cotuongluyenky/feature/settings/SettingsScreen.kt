package com.thanhvan.cotuongluyenky.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.BuildConfig
import com.thanhvan.cotuongluyenky.core.model.BoardThemeStyle
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode
import com.thanhvan.cotuongluyenky.core.model.NotationStyle
import com.thanhvan.cotuongluyenky.core.model.PieceDisplayStyle
import com.thanhvan.cotuongluyenky.core.model.RulesetId
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCard
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.ErrorRed
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val engineInfo = viewModel.engineInfo
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = BgCard,
            title = { Text("Xóa toàn bộ lịch sử?", color = TextPrimary) },
            text = {
                Text(
                    "Thao tác này sẽ xóa toàn bộ các ván đấu đã lưu trên thiết bị và không thể hoàn tác.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Xóa tất cả", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Hủy", color = TextPrimary)
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
                Text(
                    text = "Cài đặt & Thông tin",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Appearance
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Giao diện bàn cờ", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(10.dp))

                Text("Kiểu quân cờ", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PieceDisplayStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.pieceDisplayStyle == style,
                            onClick = { viewModel.setPieceDisplayStyle(style) },
                            label = { Text(style.vietnameseName) },
                            colors = settingsChipColors()
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Kiểu bàn cờ", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardThemeStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.boardThemeStyle == style,
                            onClick = { viewModel.setBoardThemeStyle(style) },
                            label = { Text(style.vietnameseName) },
                            colors = settingsChipColors()
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Ký phổ hiển thị", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotationStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.notationStyle == style,
                            onClick = { viewModel.setNotationStyle(style) },
                            label = { Text(style.vietnameseName) },
                            colors = settingsChipColors()
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                SettingsToggleRow(
                    title = "Hiển thị tọa độ cột (1–9)",
                    checked = settings.showCoordinates,
                    onCheckedChange = { viewModel.setShowCoordinates(it) }
                )
                SettingsToggleRow(
                    title = "Giảm chuyển động (animation)",
                    checked = settings.reduceMotion,
                    onCheckedChange = { viewModel.setReduceMotion(it) }
                )
            }

            // Audio & Haptics
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Âm thanh & Rung", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                SettingsToggleRow(
                    title = "Bật âm thanh đi quân / ăn quân",
                    checked = settings.soundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) }
                )
                if (settings.soundEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Âm lượng: ${(settings.soundVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        TextButton(onClick = { viewModel.testSound() }) {
                            Text("Nghe thử", color = AccentEmerald)
                        }
                    }
                    Slider(
                        value = settings.soundVolume,
                        onValueChange = { viewModel.setSoundVolume(it) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentEmerald,
                            activeTrackColor = AccentEmerald,
                            inactiveTrackColor = BorderSubtle
                        )
                    )
                }
                SettingsToggleRow(
                    title = "Phản hồi rung nhẹ",
                    checked = settings.hapticEnabled,
                    onCheckedChange = { viewModel.setHapticEnabled(it) }
                )
            }

            // Live coaching & hints (Phase 2 — Sections 9.6 & 18.11)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Trợ giúp & Đánh giá trực tiếp (Luyện tập)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                SettingsToggleRow(
                    title = "Bật gợi ý nhiều tầng",
                    checked = settings.hintsEnabled,
                    onCheckedChange = { viewModel.setHintsEnabled(it) }
                )
                SettingsToggleRow(
                    title = "Đánh giá nhanh sau mỗi nước đi",
                    checked = settings.liveEvaluationEnabled,
                    onCheckedChange = { viewModel.setLiveEvaluationEnabled(it) }
                )
                SettingsToggleRow(
                    title = "Hiển thị thanh lợi thế",
                    checked = settings.showEvalBar,
                    onCheckedChange = { viewModel.setShowEvalBar(it) }
                )
                SettingsToggleRow(
                    title = "Chỉ cảnh báo sai lầm lớn (? / ??)",
                    checked = settings.onlyWarnMajorBlunders,
                    onCheckedChange = { viewModel.setOnlyWarnMajorBlunders(it) }
                )
                SettingsToggleRow(
                    title = "Hiệu ứng nước tốt",
                    checked = settings.showGoodMoveEffects,
                    onCheckedChange = { viewModel.setShowGoodMoveEffects(it) }
                )
                SettingsToggleRow(
                    title = "Rung nhẹ khi phát hiện sai lầm",
                    checked = settings.vibrateOnBlunder,
                    onCheckedChange = { viewModel.setVibrateOnBlunder(it) }
                )
            }

            // Engine resource mode
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Tài nguyên máy cờ", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineResourceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.engineResourceMode == mode,
                            onClick = { viewModel.setEngineResourceMode(mode) },
                            label = { Text(mode.vietnameseName) },
                            colors = settingsChipColors()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = settings.engineResourceMode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Local data
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Dữ liệu cục bộ", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Toàn bộ ván đấu được lưu offline trong cơ sở dữ liệu Room trên thiết bị của bạn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.clearAnalysisCache() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa cache phân tích", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa lịch sử", color = ErrorRed)
                    }
                }
            }

            // About & Licenses (Section 4.3 & 18.11)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Thông tin & Giấy phép", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                InfoLine("Ứng dụng", "Cờ Tướng – Luyện Kỳ v${BuildConfig.VERSION_NAME}")
                InfoLine("Bộ luật", RulesetId.WXF_BASIC_V1.displayName)
                InfoLine("Engine đang dùng", "${engineInfo.name} (${engineInfo.version})")
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Thành phần nguồn mở dự kiến tích hợp: Pikafish (https://github.com/official-pikafish/Pikafish) theo giấy phép GNU GPL-3.0. Hiệu ứng âm thanh được tổng hợp nội bộ, không sử dụng tài nguyên bản quyền bên thứ ba.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun settingsChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AccentEmerald.copy(alpha = 0.22f),
    selectedLabelColor = AccentEmerald,
    containerColor = BgCardElevated,
    labelColor = TextPrimary
)

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BgMain,
                checkedTrackColor = AccentEmerald
            )
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
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
