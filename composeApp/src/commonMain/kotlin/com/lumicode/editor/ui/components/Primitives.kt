package com.lumicode.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlType

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

/** 1px hairline; the archive grid is built almost entirely from these. */
@Composable
fun Rule(
    modifier: Modifier = Modifier,
    color: Color = RlColors.Hair,
    thickness: Dp = 1.dp,
    vertical: Boolean = false,
) {
    if (vertical) {
        Box(modifier.width(thickness).fillMaxHeight().background(color))
    } else {
        Box(modifier.height(thickness).fillMaxWidth().background(color))
    }
}

/** Section header: micro label with a trailing hairline running to the edge. */
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
                    .background(RlColors.Hair),
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

/** Small bordered box (ESC chip, status pill). */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    borderColor: Color = RlColors.HairStrong,
    textColor: Color = RlColors.Muted,
    background: Color = Color.Transparent,
) {
    Box(
        modifier
            .background(background)
            .border(1.dp, borderColor)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Label(text, style = RlType.label(8.sp, textColor))
    }
}

/** Solid black bar with white micro text: "+ SAVE ARCHIVE ......... 收藏档案". */
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
        hovered -> Color(0xFF2C2C29)
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
            BasicText(trailing, style = RlType.label(9.sp, Color(0xFFB9B9B3)))
        }
    }
}

/** Ghost / text button used for secondary actions. */
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
    Row(
        modifier
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null && glyphLeading) {
            BasicText(glyph, style = RlType.mono.copy(color = if (hovered) RlColors.Ink else RlColors.Muted, fontSize = 11.sp))
            Spacer(Modifier.width(8.dp))
        }
        Label(text, style = RlType.label(9.sp, if (hovered) RlColors.Ink else RlColors.Muted))
        if (glyph != null && !glyphLeading) {
            Spacer(Modifier.width(8.dp))
            BasicText(glyph, style = RlType.mono.copy(color = if (hovered) RlColors.Ink else RlColors.Muted, fontSize = 11.sp))
        }
    }
}

/** Vertical gap filler used to mimic the airy archive layout. */
@Composable
fun VGap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))
