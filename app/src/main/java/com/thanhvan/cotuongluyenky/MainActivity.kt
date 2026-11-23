package com.thanhvan.cotuongluyenky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thanhvan.cotuongluyenky.navigation.CoTuongAppRoot
import com.thanhvan.cotuongluyenky.ui.theme.CoTuongLuyenKyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val container = (application as App).container
        setContent {
            CoTuongLuyenKyTheme {
                CoTuongAppRoot(container = container)
            }
        }
    }
}
