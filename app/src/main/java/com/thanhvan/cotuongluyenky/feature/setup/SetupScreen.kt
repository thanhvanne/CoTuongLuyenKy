package com.thanhvan.cotuongluyenky.feature.setup

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhvan.cotuongluyenky.core.model.ClockOption
import com.thanhvan.cotuongluyenky.core.model.GameMode
import com.thanhvan.cotuongluyenky.core.model.PlayerSideChoice
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.engine.api.EngineDifficulty
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.BgMain
import com.thanhvan.cotuongluyenky.ui.theme.BorderSubtle
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary
import com.thanhvan.cotuongluyenky.ui.theme.WarningAmber
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onNavigateBack: () -> Unit,
    onGameCreated: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = EngineDifficulty.profileFor(state.engineLevel)

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
                    text = "Thiết lập ván đấu",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { viewModel.startGame(onGameCreated) },
                    enabled = !state.isCreating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    if (state.isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = BgMain
                        )
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Bắt đầu ván", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. AI Level (1..10)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cấp độ máy",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = profile.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentEmerald
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = state.engineLevel.toFloat(),
                    onValueChange = { viewModel.setEngineLevel(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentEmerald,
                        activeTrackColor = AccentEmerald,
                        inactiveTrackColor = BorderSubtle
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cấp 1", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("Cấp 5", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("Cấp 10", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }

            // 2. Player Side
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bên cầm quân",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerSideChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = state.sideChoice == choice,
                            onClick = { viewModel.setSideChoice(choice) },
                            label = { Text(choice.vietnameseName) },
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

            // 3. Game Mode (Practice vs Challenge)
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Chế độ chơi",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { viewModel.setGameMode(mode) },
                            label = { Text(mode.vietnameseName) },
                            modifier = Modifier.weight(1f),
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
                Text(
                    text = state.mode.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (state.mode == GameMode.CHALLENGE) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Đi lại và các trợ giúp trong ván bị khóa ở chế độ Thử sức.",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarningAmber
                        )
                    }
                }
            }

            // 4. Optional Clock
            LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Đồng hồ ván đấu",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClockOption.entries.forEach { option ->
                        FilterChip(
                            selected = state.clockOption == option,
                            onClick = { viewModel.setClockOption(option) },
                            label = { Text(option.vietnameseName) },
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
        }
    }
}
