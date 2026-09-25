package com.lumicode.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.lumicode.editor.platform.clockLabel
import kotlinx.coroutines.delay

/** Wall clock, refreshed once per second. */
@Composable
fun rememberClock(): String {
    var clock by remember { mutableStateOf(clockLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            clock = clockLabel()
        }
    }
    return clock
}

/** Real frame rate, sampled once per second — the rail is not decorative only. */
@Composable
fun rememberFps(): Int {
    var fps by remember { mutableStateOf(60) }
    LaunchedEffect(Unit) {
        var frames = 0
        var windowStart = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (windowStart == 0L) windowStart = now
            frames++
            if (now - windowStart >= 1_000_000_000L) {
                fps = frames
                frames = 0
                windowStart = now
            }
        }
    }
    return fps
}
