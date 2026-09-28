package com.lumicode.editor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

/**
 * JVM Desktop entry — Windows / Linux / macOS via Compose Multiplatform Desktop.
 *
 * Window placement uses platform default (no Absolute). AWT [minimumSize] is a
 * JVM-only soft constraint for layout preview; ComposeKN natives skip it.
 */
fun main() = application {
    val windowSize = LumiCodeDesktop.parseSize(System.getenv("LUMICODE_WINDOW_SIZE"))
    val windowState = rememberWindowState(size = windowSize.size)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = LumiCodeDesktop.Title,
    ) {
        val (minW, minH) = LumiCodeDesktop.minSizePx(windowSize.compactPreview)
        window.minimumSize = Dimension(minW, minH)
        LumiCodeDesktopRoot()
    }
}
