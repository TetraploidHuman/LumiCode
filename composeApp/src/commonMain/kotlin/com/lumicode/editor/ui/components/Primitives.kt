package com.lumicode.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
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
