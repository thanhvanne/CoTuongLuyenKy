package com.thanhvan.cotuongluyenky.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thanhvan.cotuongluyenky.core.ui.components.LuyenKyCard
import com.thanhvan.cotuongluyenky.core.ui.components.StatusPill
import com.thanhvan.cotuongluyenky.ui.theme.AccentEmerald
import com.thanhvan.cotuongluyenky.ui.theme.BgCardElevated
import com.thanhvan.cotuongluyenky.ui.theme.InfoBlue
import com.thanhvan.cotuongluyenky.ui.theme.TextPrimary
import com.thanhvan.cotuongluyenky.ui.theme.TextSecondary

/**
 * Phase 1 Training tab shell: clearly communicates upcoming Phase 2/3 modules
 * without showing fabricated puzzles or mock statistics (Section 29).
 */
@Composable
fun TrainingScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Luyện tập",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = "Các bài tập cá nhân hóa sẽ được kích hoạt sau khi hoàn thiện lõi phân tích ở Phase 2 và hệ thống ôn tập ở Phase 3.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        TrainingModulePreviewCard(
            title = "Gợi ý nhiều tầng & Phân tích cuối ván",
            phaseBadge = "Đã mở (Phase 2)",
            description = "Đã sẵn sàng! Bạn có thể mở Gợi ý 4 tầng ngay trong màn Bàn cờ hoặc vào tab Lịch sử để xem biểu đồ lợi thế, nhận xét có căn cứ và đánh thử biến phân tích."
        )

        TrainingModulePreviewCard(
            title = "Sửa sai từ ván đã chơi",
            phaseBadge = "Phase 3",
            description = "Tự động chuyển những sai lầm nghiêm trọng trong ván của bạn thành bài tập giải lại."
        )

        TrainingModulePreviewCard(
            title = "Chiến thuật, Khai cuộc & Tàn cuộc",
            phaseBadge = "Phase 3",
            description = "Luyện chiếu bí, bắt đôi, phòng thủ, phản đòn và ôn tập ngắt quãng theo lịch thông minh."
        )
    }
}

@Composable
private fun TrainingModulePreviewCard(
    title: String,
    phaseBadge: String,
    description: String
) {
    LuyenKyCard(modifier = Modifier.fillMaxWidth()) {
        StatusPill(
            text = phaseBadge,
            containerColor = BgCardElevated,
            contentColor = if (phaseBadge == "Phase 2") AccentEmerald else InfoBlue
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
