package com.lumicode.editor.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Palette lifted from the "RHINE LAB ANALYSIS OS" archival console look:
 * warm paper background, hairline rules, near-black ink, almost no chroma.
 */
object RlColors {
    val Paper = Color(0xFFF1F1EF)
    val PaperDeep = Color(0xFFEAEAE7)
    val Panel = Color(0xFFF7F7F5)
    val PanelGhost = Color(0x66FFFFFF)
    val Ink = Color(0xFF121211)
    val InkSoft = Color(0xFF3B3B38)
    val Muted = Color(0xFF8C8C85)
    val Faint = Color(0xFFB4B4AD)
    val Hair = Color(0xFFDBDBD5)
    val HairStrong = Color(0xFFC4C4BD)
    val Scrim = Color(0xCCE9E9E5)

    // Code token colours: monochrome base with a single muted accent family.
    val CodeDefault = Color(0xFF232322)
    val CodeKeyword = Color(0xFF121211)
    val CodeString = Color(0xFF5C6A52)
    val CodeNumber = Color(0xFF6E5636)
    val CodeComment = Color(0xFFA6A69E)
    val CodeType = Color(0xFF3F4A55)
    val CodeAnnotation = Color(0xFF7A6A4C)
    val CodeFunction = Color(0xFF2F3A44)
    val CodePunct = Color(0xFF6E6E67)
}

/**
 * 用户可在设置页调整的项。用 Compose state 保存，改完立刻全局生效。
 */
object RlSettings {
    /** 代码字号（sp）。行距由字体自身行高决定（见 RlType.code）。 */
    var codeFontSize by mutableStateOf(14f)
    var tabWidth by mutableStateOf(4)
    var showLineNumbers by mutableStateOf(true)
    var showRail by mutableStateOf(true)

    /** 仅作为首帧的估值：真实行距由 CodeEditor 从文本布局里量出来（见 onTextLayout）。 */
    val codeLineHeight: Dp get() = (codeFontSize + 9f).dp

    fun lineHeightSp(): Float = codeFontSize + 9f
}

/**
 * Font families actually used for rendering. The shell swaps in the bundled Noto Sans CJK
 * subsets once the resources are loaded, which keeps Latin *and* Chinese glyphs identical on
 * Android, desktop and Wasm (Skiko has no system CJK fallback on the web target).
 */
object RlFonts {
    var sans: FontFamily by mutableStateOf(FontFamily.SansSerif)
    var mono: FontFamily by mutableStateOf(FontFamily.Monospace)
}

/** Typography. The look leans on tight display sans + wide-tracked micro mono. */
object RlType {
    val Display: FontFamily get() = RlFonts.sans
    val Mono: FontFamily get() = RlFonts.mono

    val title: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em,
        color = RlColors.Ink,
    )

    val pageTitle: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.025).em,
        color = RlColors.Ink,
    )

    val sectionTitle: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.01).em,
        color = RlColors.Ink,
    )

    val body: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.0.em,
        color = RlColors.InkSoft,
    )

    val bodySmall: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        color = RlColors.InkSoft,
    )

    /** Micro label: uppercase, wide tracking, monospace — used everywhere as chrome. */
    fun label(size: TextUnit = 11.sp, color: Color = RlColors.Muted) = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = size,
        lineHeight = size * 1.35f,
        letterSpacing = 0.16.em,
        color = color,
    )

    val value: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.0.em,
        color = RlColors.Ink,
    )

    val mono: TextStyle get() = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.02.em,
        color = RlColors.InkSoft,
    )

    val monoMicro: TextStyle get() = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.04.em,
        color = RlColors.Faint,
    )

    val code: TextStyle get() = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = RlSettings.codeFontSize.sp,
        // 不设 lineHeight：让行框 = 字体自身行高（ascent+descent）。这样字形正好填满行框，
        // 行阴影带天然包住字形、视觉居中；显式加大行高时 Compose 会把字形贴顶排、leading 留在下方。
        letterSpacing = 0.0.em,
        color = RlColors.CodeDefault,
    )

    val codeGutter: TextStyle get() = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = (RlSettings.codeFontSize - 1.5f).sp,
        letterSpacing = 0.02.em,
        color = RlColors.Faint,
    )
}

object RlDimens {
    val hair = 1.dp
    val gutterWidth = 56.dp
    val explorerWidth = 236.dp
    val referenceWidth = 320.dp
    val railWidth = 116.dp
    val topBarHeight = 56.dp
    val statusBarHeight = 26.dp
    // 代码行高由 RlSettings.codeLineHeight 动态给出
    val codePaddingStart = 14.dp
    val codePaddingTop = 12.dp
}
