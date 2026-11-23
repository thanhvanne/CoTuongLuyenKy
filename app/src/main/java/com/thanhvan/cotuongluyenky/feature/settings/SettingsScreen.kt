package com.thanhvan.cotuongluyenky.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val engineInfo = viewModel.engineInfo
    val powerSnapshot = remember(settings.autoPowerThermalGuard) { viewModel.devicePowerSnapshot }
    val maxSafeThreads = remember { viewModel.maxSafeThreads }
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearPuzzlesDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    // System file pickers for JSON Export / Import (Section 20.9 & 23.3 — no storage permissions needed)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup { json ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            if (content != null) {
                viewModel.inspectImportJson(content)
            }
        }
    }

    if (showClearPuzzlesDialog) {
        AlertDialog(
            onDismissRequest = { showClearPuzzlesDialog = false },
            containerColor = BgCard,
            title = { Text("Xóa bài tập cá nhân?", color = TextPrimary) },
            text = {
                Text(
                    "Thao tác này sẽ xóa toàn bộ bài tập sửa sai và bài tự lưu của bạn (vẫn giữ lại bộ bài tập chuẩn ban đầu).",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearPuzzlesDialog = false
                        viewModel.clearUserPuzzles()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) {
                    Text("Xóa bài tập", color = BgMain)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPuzzlesDialog = false }) {
                    Text("Hủy", color = TextPrimary)
                }
            }
        )
    }

    backupState.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingImport() },
            containerColor = BgCard,
            title = { Text("Xác nhận nhập dữ liệu sao lưu", color = TextPrimary) },
            text = {
                Text(
                    text = "File hợp lệ (phiên bản v${pending.schemaVersion}, ứng dụng ${pending.appVersion}): " +
                        "${pending.games.size} ván đấu và ${pending.puzzles.size} bài tập" +
                        (if (pending.skippedInvalidEntries > 0) " (${pending.skippedInvalidEntries} mục không hợp lệ sẽ được bỏ qua)" else "") +
                        ". Bạn muốn gộp thêm hay thay thế dữ liệu cũ?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.confirmImport(replaceExisting = true) }) {
                        Text("Thay thế", color = ErrorRed)
                    }
                    Button(
                        onClick = { viewModel.confirmImport(replaceExisting = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                    ) {
                        Text("Gộp thêm")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingImport() }) {
                    Text("Hủy", color = TextPrimary)
                }
            }
        )
    }

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

            // Engine resource mode & thermal/battery guard (Sections 6.6 & 18.11)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Tài nguyên máy cờ & Pin / Nhiệt độ", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
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
                Spacer(Modifier.height(8.dp))
                SettingsToggleRow(
                    title = "Tự hạ tải khi máy nóng / pin yếu",
                    checked = settings.autoPowerThermalGuard,
                    onCheckedChange = { viewModel.setAutoPowerThermalGuard(it) }
                )
                Text(
                    text = if (powerSnapshot.shouldThrottle) {
                        "Đang kích hoạt bảo vệ: ${powerSnapshot.throttleReason ?: "Thiết bị đang tiết kiệm pin / nhiệt cao"}."
                    } else {
                        val bat = if (powerSnapshot.batteryPercent >= 0) "${powerSnapshot.batteryPercent}%" else "Không rõ"
                        "Trạng thái thiết bị: Bình thường (Pin $bat · Tối đa $maxSafeThreads luồng an toàn)."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (powerSnapshot.shouldThrottle) WarningAmber else TextSecondary
                )

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                Text("Tùy chỉnh nâng cao (Engine)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("Số luồng CPU", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                val threadOptions = listOf(0, 1, 2, 4).filter { it == 0 || it <= maxSafeThreads }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    threadOptions.forEach { t ->
                        FilterChip(
                            selected = settings.advancedThreadsOverride == t,
                            onClick = { viewModel.setAdvancedThreadsOverride(t) },
                            label = { Text(if (t == 0) "Tự động" else "$t luồng") },
                            colors = settingsChipColors()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Bộ nhớ Hash", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 16, 32, 64).forEach { hash ->
                        FilterChip(
                            selected = settings.advancedHashMbOverride == hash,
                            onClick = { viewModel.setAdvancedHashMbOverride(hash) },
                            label = { Text(if (hash == 0) "Tự động" else "$hash MB") },
                            colors = settingsChipColors()
                        )
                    }
                }
            }

            // Local data & Export/Import (Section 18.11 & 20.9)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Dữ liệu cục bộ & Sao lưu", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Toàn bộ ván đấu và bài tập được lưu offline trên thiết bị. Dung lượng dữ liệu: ${backupState.storageSizeText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (backupState.statusMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = backupState.statusMessage ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (backupState.isError) ErrorRed else AccentEmerald
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("cotuong_luyenky_backup.json") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xuất dữ liệu (JSON)", color = AccentEmerald)
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Nhập dữ liệu", color = InfoBlue)
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearAnalysisCache() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa cache phân tích", color = TextPrimary)
                    }
                    OutlinedButton(
                        onClick = { showClearPuzzlesDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa bài tập cá nhân", color = WarningAmber)
                    }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Xóa lịch sử", color = ErrorRed)
                    }
                }
            }

            // About & Licenses (Section 4.3, 18.11 & 25.5)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text("Thông tin, Quyền riêng tư & Giấy phép", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                InfoLine("Ứng dụng", "Cờ Tướng – Luyện Kỳ v${BuildConfig.VERSION_NAME}")
                InfoLine("Bộ luật", RulesetId.WXF_BASIC_V1.displayName)
                InfoLine("Engine đang dùng", "${engineInfo.name} (${engineInfo.version})")
                InfoLine("Mạng & Dữ liệu", "100% Offline · Không quảng cáo · Không theo dõi")
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Ứng dụng không yêu cầu quyền Internet, không tạo tài khoản và không gửi dữ liệu ra ngoài thiết bị. Thành phần nguồn mở tích hợp tùy chọn: Pikafish (GNU GPL-3.0).",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showLicensesDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Xem giấy phép nguồn mở & Cam kết riêng tư", color = InfoBlue)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            containerColor = BgCardElevated,
            title = { Text("Giấy phép nguồn mở & Riêng tư", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Quyền riêng tư (Offline-first): Cờ Tướng – Luyện Kỳ không khai báo quyền INTERNET, không gắn SDK quảng cáo/theo dõi, mọi ván đấu và thống kê chỉ lưu cục bộ bằng Room & DataStore.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "• Pikafish Xiangqi Engine (tích hợp native tùy chọn): Phát hành theo giấy phép GNU General Public License v3.0 (GPL-3.0). Nguồn: https://github.com/official-pikafish/Pikafish",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "• Thư viện AndroidX (Compose, Room, DataStore, Navigation) & Kotlin Coroutines: Giấy phép Apache License 2.0.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "• Hiệu ứng âm thanh (WAV) & bàn cờ Vector Canvas: Tổng hợp nội bộ trong dự án, không sử dụng tài nguyên bản quyền bên thứ ba.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Đóng", color = AccentEmerald)
                }
            }
        )
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
