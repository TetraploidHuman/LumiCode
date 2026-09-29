package com.lumicode.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lumicode.editor.platform.installPlatformBackends
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.ui.theme.RlSettings

/**
 * Shared desktop-window defaults for JVM Desktop and ComposeKN (linuxX64 / mingwX64).
 *
 * Hosts keep only: env read and optional JVM AWT min-size.
 * ComposeKN: com.composekn.host wraps entry (register + initMainThread).
 * Do not hardcode WindowPosition.Absolute — omit position (PlatformDefault).
 */
object LumiCodeDesktop {
    val DefaultSize: DpSize = DpSize(1600.dp, 940.dp)

    const val Title: String = "LumiCode — ANALYSIS OS"
    const val TitleKn: String = "LumiCode — ANALYSIS OS (ComposeKN)"

    /** JVM AWT `window.minimumSize` only — ComposeKN has no equivalent yet. */
    val CompactMinSizePx: Pair<Int, Int> = 360 to 520
    val DefaultMinSizePx: Pair<Int, Int> = 1180 to 720

    /**
     * Parse `LUMICODE_WINDOW_SIZE=WxH` (e.g. `420x900` for compact preview).
     * Each host reads the env string and passes it here.
     */
    fun parseSize(envValue: String?): DesktopWindowSize {
        val parts = envValue
            ?.split('x')
            ?.mapNotNull { it.trim().toFloatOrNull() }
            ?.takeIf { it.size == 2 }
        return if (parts != null) {
            DesktopWindowSize(
                size = DpSize(parts[0].dp, parts[1].dp),
                compactPreview = true,
            )
        } else {
            DesktopWindowSize(size = DefaultSize, compactPreview = false)
        }
    }

    fun minSizePx(compactPreview: Boolean): Pair<Int, Int> =
        if (compactPreview) CompactMinSizePx else DefaultMinSizePx
}

data class DesktopWindowSize(
    val size: DpSize,
    /** True when [LumiCodeDesktop.parseSize] got an env override (compact preview). */
    val compactPreview: Boolean,
)

/** Window body shared by JVM Desktop and ComposeKN entry points. */
@Composable
fun LumiCodeDesktopRoot() {
    val state = remember {
        RlSettings.load()
        installPlatformBackends()
        IdeState().also { it.loadPanelPrefs() }
    }
    App(state)
}
