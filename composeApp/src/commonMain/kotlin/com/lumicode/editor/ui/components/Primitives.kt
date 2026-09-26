package com.lumicode.editor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlMotion
import com.lumicode.editor.ui.theme.RlType

// ------------------------------------------------------------------ 无界几何
//
// 规则：没有卡片、没有圆角、没有描边、没有投影。
// 整块界面是一个连续的平面，区域之间只靠留白和字号/字重区分。

/**
 * 通铺：一层极淡的色块，直角。
 *
 * 只用在「必须回应指针」的地方（hover / 选中 / 当前行），并且尽量淡到像光线扫过，
 * 而不是像贴了一张卡片上去。
 */
fun Modifier.wash(color: Color): Modifier =
    if (color == Color.Transparent) this else this.background(color)

/** 强调刻度：一条直角冰青细条，用来标记"活的"位置。 */
@Composable
fun AccentTick(
    modifier: Modifier = Modifier,
    length: Dp = 26.dp,
    thickness: Dp = 2.dp,
    color: Color = RlColors.Accent,
) {
    Box(modifier.height(thickness).width(length).background(color))
}

/**
 * 无反馈点击。
 *
 * 无界界面里不需要 ripple，也不需要桌面端默认那个「hover 灰方块」——
 * 那会凭空长出一个矩形，正好破坏"没有框"这件事。所有可点区域都用这个。
 */
fun Modifier.clickableFlat(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.composed {
        val interaction = remember { MutableInteractionSource() }
        clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    }


/**
 * 无界标签条：一排标签 + **一条会滑过去的冰青刻度**。
 *
 * 刻度位置取自 `onGloballyPositioned` 的真实布局，而不是文本度量 ——
 * wasm 上内置字体是异步加载的，按度量算会在字体就绪那一刻整体跳一下。
 */
@Composable
fun TabRow(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    gap: Dp = 20.dp,
    activeColor: Color = RlColors.Ink,
    hoverColor: Color = RlColors.InkSoft,
    idleColor: Color = RlColors.Faint,
    /** hover 底色相对文字向外扩出多少；文字不会被推离对齐线。 */
    cellHPad: Dp = 8.dp,
    cellVPad: Dp = 5.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    // index -> (x, width)，单位 px；值相等就不写，避免布局期间反复触发重组
    var bounds by remember(titles.size) { mutableStateOf(List(titles.size) { 0f to 0f }) }
    val target = bounds.getOrNull(selected) ?: (0f to 0f)
    val tickX by animateDpAsState(with(density) { target.first.toDp() }, RlMotion.snap(), label = "tickX")
    val tickW by animateDpAsState(with(density) { target.second.toDp() }, RlMotion.snap(), label = "tickW")
    val padX = with(density) { cellHPad.toPx() }
    val padY = with(density) { cellVPad.toPx() }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            titles.forEachIndexed { index, title ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .onGloballyPositioned { coords ->
                            val x = coords.positionInParent().x
                            val w = coords.size.width.toFloat()
                            val current = bounds.getOrNull(index)
                            if (current == null || current.first != x || current.second != w) {
                                bounds = bounds.toMutableList().also { it[index] = x to w }
                            }
                        }
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                ) {
                    // hover 反馈：一层极淡的色，**从文字框向四周外扩**。
                    // 文字本身一动不动，所以标签永远落在面板的对齐线上。
                    if (hovered && index != selected) {
                        Canvas(Modifier.matchParentSize()) {
                            drawRect(
                                color = RlColors.FieldDeep,
                                topLeft = Offset(-padX, -padY),
                                size = Size(size.width + padX * 2f, size.height + padY * 2f),
                            )
                        }
                    }
                    val ink by animateColorAsState(
                        targetValue = when {
                            index == selected -> activeColor
                            hovered -> hoverColor
                            else -> idleColor
                        },
                        animationSpec = RlMotion.enter(150),
                        label = "tabInk",
                    )
                    LabelRaw(text = title, style = RlType.label(fontSize, ink))
                }
                if (index != titles.lastIndex) Spacer(Modifier.width(gap))
            }
            if (trailing != null) {
                Spacer(Modifier.width(gap))
                trailing()
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            Box(
                Modifier
                    .offset(x = tickX)
                    .width(tickW.coerceAtLeast(0.dp))
                    .height(2.dp)
                    .background(RlColors.Accent),
            )
        }
    }
}

/** Micro uppercase label — the most repeated element of the archive chrome. */
@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = RlType.label(),
    align: TextAlign? = null,
) {
    BasicText(
        text = text.uppercase(),
        modifier = modifier,
        style = if (align != null) style.copy(textAlign = align) else style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LabelRaw(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = RlType.label(),
    maxLines: Int = 1,
) {
    BasicText(text = text, modifier = modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, style: TextStyle = RlType.body, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text = text, modifier = modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun AnnotatedBody(text: AnnotatedString, modifier: Modifier = Modifier, style: TextStyle = RlType.body) {
    BasicText(text = text, modifier = modifier, style = style)
}

/** Section header: micro label with a fading seam running to the edge. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(title)
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .height(1.dp)
                    .weight(1f)
                    .background(
                        Brush.horizontalGradient(
                            listOf(RlColors.Hair, RlColors.Hair, Color.Transparent),
                        ),
                    ),
            )
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Label(trailing, style = RlType.label(8.5.sp, RlColors.Faint))
            }
        }
    }
}

/** Label over value pair, e.g. DEPARTMENT / 科室 + "总辖构件科". */
@Composable
fun MetaBlock(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = RlColors.Ink) {
    Column(modifier) {
        Label(label, style = RlType.label(8.5.sp, RlColors.Muted))
        Spacer(Modifier.height(5.dp))
        BasicText(value, style = RlType.value.copy(color = valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small flat chip (ESC hint, shortcut). 直角、无描边。 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = RlColors.PaperDeep,
    textColor: Color = RlColors.Muted,
) {
    Box(
        modifier
            .wash(fill)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Label(text, style = RlType.label(8.sp, textColor))
    }
}

/** Solid bar button: 直角实心色块，hover 时点亮成冰青。 */
@Composable
fun SolidBarButton(
    text: String,
    trailing: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        !enabled -> RlColors.Faint
        hovered -> RlColors.AccentDeep
        else -> RlColors.Ink
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("+", style = RlType.mono.copy(color = Color.White, fontSize = 11.sp))
        Spacer(Modifier.width(8.dp))
        BasicText(text.uppercase(), style = RlType.label(9.sp, Color.White))
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            BasicText(trailing, style = RlType.label(9.sp, Color(0xFFB6BDC6)))
        }
    }
}

/** Ghost / text button used for secondary actions. Hover 时浮出一枚无边药丸。 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    glyphLeading: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = if (hovered) RlColors.Ink else RlColors.Muted
    Row(
        modifier
            .wash(if (hovered) RlColors.FieldDeep else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null && glyphLeading) {
            BasicText(glyph, style = RlType.mono.copy(color = tint, fontSize = 11.sp))
            Spacer(Modifier.width(8.dp))
        }
        Label(text, style = RlType.label(9.sp, tint))
        if (glyph != null && !glyphLeading) {
            Spacer(Modifier.width(8.dp))
            BasicText(glyph, style = RlType.mono.copy(color = tint, fontSize = 11.sp))
        }
    }
}

/** 设置齿轮：用 Canvas 画，避免依赖字体里是否含 U+2699。 */
@Composable
fun GearIcon(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = RlColors.Ink,
) {
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension / 2f
        val teeth = 8
        repeat(teeth) { index ->
            rotate(index * (360f / teeth), center) {
                drawRect(
                    color = color,
                    topLeft = Offset(center.x - radius * 0.15f, center.y - radius),
                    size = Size(radius * 0.30f, radius * 0.52f),
                )
            }
        }
        drawCircle(
            color = color,
            radius = radius * 0.60f,
            center = center,
            style = Stroke(width = radius * 0.26f),
        )
    }
}

/** Vertical gap filler used to mimic the airy archive layout. */
@Composable
fun VGap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))
