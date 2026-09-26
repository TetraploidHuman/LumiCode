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
 * 无界 · Minimalist Futurism palette.
 *
 * 四条规则：
 *  1. 整个界面是**一整块连续的平面**，靠极淡的纵向渐变暗示光源，没有卡片、没有圆角、没有描边；
 *  2. 区域之间只用留白、字号、字重区分，绝不画分隔线；
 *  3. 层次只允许用"极淡的一层色"暗示（[FieldDeep] 一档就够），永远不出现边框和阴影；
 *  4. 只有"活着"的信号才上色 —— [Accent] 是唯一的冷青强调色，其余全是灰阶。
 */
object RlColors {
    // -------------------------------------------------------------------- 场
    /** 场：唯一的底色，连续无边。 */
    val Field = Color(0xFFEFF2F6)
    val FieldTop = Color(0xFFF6F8FB)
    val FieldBottom = Color(0xFFE9EDF3)
    /** 场里再深一档：hover / 当前行 / 内嵌元素用的那"一层色"。 */
    val FieldDeep = Color(0xFFE3E8EF)

    // 兼容旧命名：全部指向同一块场，界面里不再有"面板"这一概念。
    val Paper = Field
    val PaperDeep = FieldDeep
    val Panel = Color(0xFFFBFCFE)
    val PanelGhost = Color(0x33FFFFFF)

    // -------------------------------------------------------------------- 墨
    val Ink = Color(0xFF0A0C10)
    val InkSoft = Color(0xFF3A4048)
    val Muted = Color(0xFF868D96)
    val Faint = Color(0xFFB0B7C0)

    // 几乎不用；保留给"拖拽预览"这类必须有边的极少数场景。
    val Hair = Color(0xFFDFE4EA)
    val HairStrong = Color(0xFFC6CDD6)

    /** 浮层遮罩：一层厚纱，让底下的平面退远，而不是给浮层加个框。 */
    val Scrim = Color(0xEDF0F3F8)

    // ------------------------------------------------------------------ 强调
    /** 唯一强调色：冰青。只用于光标、当前行、活动标签、"运行中"这类活信号。 */
    val Accent = Color(0xFF2C7C8F)
    val AccentDeep = Color(0xFF1F6273)
    val AccentSoft = Color(0x1F2C7C8F)
    /** 更淡的一档，用于非活动命中项。 */
    val AccentGlow = Color(0x102C7C8F)

    // ------------------------------------------------------ 代码 token（冷调）
    val CodeDefault = Color(0xFF1C2126)
    val CodeKeyword = Color(0xFF0A0C10)
    val CodeString = Color(0xFF47705A)
    val CodeNumber = Color(0xFF7C5A2E)
    val CodeComment = Color(0xFFA0A7B0)
    val CodeType = Color(0xFF2F5A69)
    val CodeAnnotation = Color(0xFF6B5636)
    val CodeFunction = Color(0xFF23414C)
    val CodePunct = Color(0xFF6A717A)
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

    /**
     * 报头后半段：靠"字重 + 颜色"拉开层级，而不是靠字号或分栏线。
     * Minimalist Futurism 里最常见的一招 —— 黑体粗字与细字并排。
     */
    val titleSoft: TextStyle get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.06.em,
        color = RlColors.Muted,
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

    /**
     * 行号。字号**必须和代码一致** —— 两者在同一个行框里居中时，字号不同会让基线
     * 差 1px 左右（看起来就是"每一行都偏了一点"）。同字号则字体框高度、基线完全重合，
     * 对齐是构造出来的，不靠补偿。视觉上靠颜色（Faint）+ 字距把它压成次要信息。
     */
    val codeGutter: TextStyle get() = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = RlSettings.codeFontSize.sp,
        letterSpacing = 0.06.em,
        color = RlColors.Faint,
    )
}

object RlDimens {
    val hair = 1.dp

    /** 区域之间只留白。 */
    val seam = 22.dp
    /** 页面外缘留白：文字不贴边，但平面一直铺到窗口边缘。 */
    val pagePad = 22.dp

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
