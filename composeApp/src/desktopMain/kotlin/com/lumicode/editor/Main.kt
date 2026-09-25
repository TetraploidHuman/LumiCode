package com.lumicode.editor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lumicode.editor.model.SampleWorkspace
import com.lumicode.editor.state.IdeState
import java.awt.Dimension

/**
 * Desktop entry point — this single target produces the Windows and Linux builds
 * (`packageMsi` / `packageDeb`, or `run` for a plain launch).
 */
fun main() = application {
    // LUMICODE_WINDOW_SIZE=420x900 lets you preview the compact (phone) layout on desktop.
    val override = System.getenv("LUMICODE_WINDOW_SIZE")
        ?.split('x')
        ?.mapNotNull { it.trim().toFloatOrNull() }
        ?.takeIf { it.size == 2 }
    val size = if (override != null) DpSize(override[0].dp, override[1].dp) else DpSize(1600.dp, 940.dp)
    val windowState = rememberWindowState(
        position = WindowPosition(0.dp, 0.dp),
        size = size,
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "LUMICODE — ANALYSIS OS",
    ) {
        // A tiny minimum makes it possible to preview the compact layout on desktop.
        window.minimumSize = if (override != null) Dimension(360, 520) else Dimension(1180, 720)
        val state = androidx.compose.runtime.remember { IdeState(SampleWorkspace.files) }
        App(state)
    }
}
