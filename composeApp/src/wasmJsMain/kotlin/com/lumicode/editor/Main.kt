package com.lumicode.editor

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.lumicode.editor.platform.installPlatformBackends
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.ui.theme.RlSettings
import kotlinx.browser.document

/**
 * Web entry point (Kotlin/Wasm). The Compose canvas is mounted into
 * `#composeTarget`, which is sized by index.html.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    RlSettings.load()
    installPlatformBackends()
    val container = document.getElementById("composeTarget") ?: document.body!!
    // 隐藏 HTML 启动页，避免 canvas 盖住后只剩白屏错觉；Compose 自己画场。
    document.getElementById("boot")?.setAttribute("style", "display:none")
    document.getElementById("bootStatus")?.let { it.textContent = "compose ready" }
    ComposeViewport(container) {
        val state = remember {
            IdeState().also { it.loadPanelPrefs() }
        }
        App(state)
    }
}
