package com.thanhvan.cotuongluyenky.core.ui.accessibility

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Respects both the in-app Reduce Motion preference and Android's system-wide
 * animator duration scale (`ANIMATOR_DURATION_SCALE == 0f`) (Sections 19.1 & 19.7).
 */
@Composable
fun rememberEffectiveReduceMotion(userReduceMotion: Boolean): Boolean {
    val context = LocalContext.current
    val systemReduced = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    return userReduceMotion || systemReduced
}
