package com.skipad.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 内置的"假开屏广告"，用来在没有真实广告时验证跳过引擎。
 * 5 秒倒计时，右上角有「跳过 N」按钮；引擎应该在这个页面被跳过。
 */
class DemoSplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoSplash()
        }
    }

    @Composable
    private fun DemoSplash() {
        var remain by remember { mutableIntStateOf(5) }

        LaunchedEffect(Unit) {
            while (remain > 0) {
                delay(1000)
                remain--
            }
            finish()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141B2D))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "模拟开屏广告",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "这是用来测试跳过引擎的假广告\n（不是真实广告，不会联网）",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                )
            }

            TextButton(
                onClick = { finish() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
            ) {
                Text(
                    "跳过 $remain",
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
