package com.lumicode.editor

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.lumicode.editor.model.SampleWorkspace
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
    val container = document.getElementById("composeTarget") ?: document.body!!
    ComposeViewport(container) {
        val state = remember {
            IdeState(SampleWorkspace.files).also { it.loadPanelPrefs() }
        }
        App(state)
    }
}
