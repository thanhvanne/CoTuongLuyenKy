package com.thanhvan.cotuongluyenky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoTuongDarkColorScheme = darkColorScheme(
    primary = AccentEmerald,
    onPrimary = Color(0xFF062319),
    primaryContainer = AccentEmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = InfoBlue,
    onSecondary = Color(0xFF0C1A29),
    tertiary = WarningAmber,
    onTertiary = Color(0xFF291A04),
    background = BgMain,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = Color(0xFF2B2E35),
    error = ErrorRed,
    onError = Color(0xFF2E0B0A)
)

@Composable
fun CoTuongLuyenKyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CoTuongDarkColorScheme,
        typography = Typography,
        content = content
    )
}
