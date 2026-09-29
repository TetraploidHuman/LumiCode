package com.lumicode.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumicode.editor.state.CollabState
import com.lumicode.editor.state.CollabTask
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.state.labelZh
import com.lumicode.editor.ui.components.GhostButton
import com.lumicode.editor.ui.components.Label
import com.lumicode.editor.ui.components.LabelRaw
import com.lumicode.editor.ui.components.wash
import com.lumicode.editor.ui.theme.RlColors
import com.lumicode.editor.ui.theme.RlMotion
import com.lumicode.editor.ui.theme.RlType

/**
 * 给人看的提案汇报：对照用户已知情报后组织人话 + 只解释未知术语。
 */
@Composable
fun ProposalUserBrief(
    task: CollabTask,
    collab: CollabState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val brief = task.userBrief ?: task.proposalSummary
    val unknown = collab.termsNeedingExplain(task.exploredTerms)
    val assumed = collab.termsAssumedKnown(task.exploredTerms)
    if (brief.isNullOrBlank() && unknown.isEmpty() && assumed.isEmpty()) return
    var termsOpen by remember(task.id) { mutableStateOf(!compact && unknown.isNotEmpty()) }
    Column(modifier.fillMaxWidth()) {
        if (!brief.isNullOrBlank()) {
            LabelRaw(
                text = "给人看的说法 · 口径「${collab.understandingLevel.labelZh()}」",
                style = RlType.label(10.sp, RlColors.Faint),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = brief,
                style = RlType.mono.copy(
                    fontSize = if (compact) 11.sp else 12.sp,
                    color = RlColors.InkSoft,
                ),
                maxLines = if (compact) 5 else 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (assumed.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            LabelRaw(
                text = "已按你的已知情报省略 · ${assumed.joinToString("、") { it.term }}",
                style = RlType.label(10.sp, RlColors.CodeString),
                maxLines = 2,
            )
        }
        if (unknown.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelRaw(
                    text = "你可能还不熟（${unknown.size}）",
                    style = RlType.label(10.sp, RlColors.Faint),
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = if (termsOpen) "收起对照" else "看人话对照",
                    onClick = { termsOpen = !termsOpen },
                )
            }
            AnimatedVisibility(
                visible = termsOpen,
                enter = fadeIn(RlMotion.enter()) + expandVertically(RlMotion.enter()),
                exit = fadeOut(RlMotion.exit()) + shrinkVertically(RlMotion.exit()),
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    unknown.forEach { t ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .wash(RlColors.FieldDeep)
                                .padding(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LabelRaw(
                                    text = t.term,
                                    style = RlType.label(11.sp, RlColors.AccentDeep),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                GhostButton(text = "记入已知", onClick = {
                                    collab.markTermKnown(t)
                                })
                            }
                            Spacer(Modifier.height(2.dp))
                            LabelRaw(text = t.plain, style = RlType.label(11.sp, RlColors.InkSoft), maxLines = 3)
                            t.where?.let {
                                Spacer(Modifier.height(2.dp))
                                LabelRaw(text = "碰到于 · $it", style = RlType.label(10.sp, RlColors.Faint), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        task.proposalNote?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(4.dp))
            LabelRaw(text = "附注 · $note", style = RlType.label(10.sp, RlColors.Faint), maxLines = 2)
        }
    }
}

/** 维护用户已知情报与汇报口径。 */
@Composable
fun UserKnownIntelPanel(
    collab: CollabState,
    state: IdeState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(
                if (compact) "已知情报（${collab.knownFacts.size}）"
                else "用户已知情报（${collab.knownFacts.size}）",
                style = RlType.label(if (compact) 10.5.sp else 11.sp, RlColors.Faint),
            )
            Spacer(Modifier.weight(1f))
            GhostButton(
                text = "口径 · ${collab.understandingLevel.labelZh()}",
                onClick = {
                    collab.cycleUnderstandingLevel()
                    state.statusMessage = "小队 · 汇报口径「${collab.understandingLevel.labelZh()}」"
                },
            )
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        if (!compact) {
            LabelRaw(
                text = "同伴汇报前会先对照这份清单：已知的不重复解释，未知的用人话补上，并按口径深浅组织措辞。",
                style = RlType.label(11.sp, RlColors.Faint),
                maxLines = 3,
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .wash(RlColors.FieldDeep)
                .padding(if (compact) 8.dp else 12.dp),
        ) {
            collab.knownFacts.take(if (compact) 4 else 12).forEach { fact ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        LabelRaw(text = fact.label, style = RlType.label(if (compact) 11.sp else 12.sp, RlColors.Ink), maxLines = 1)
                        if (fact.detail.isNotBlank() && !compact) {
                            LabelRaw(text = fact.detail, style = RlType.label(10.sp, RlColors.Faint), maxLines = 2)
                        }
                    }
                    GhostButton(text = "去掉", onClick = {
                        collab.removeKnownFact(fact.id)
                        state.statusMessage = "小队 · 已从已知情报去掉「${fact.label}」"
                    })
                }
                Spacer(Modifier.height(4.dp))
            }
            if (compact && collab.knownFacts.size > 4) {
                LabelRaw(text = "…另 ${collab.knownFacts.size - 4} 条 · 打开小队可编辑", style = RlType.label(10.sp, RlColors.Faint))
            }
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value = collab.knownDraft,
                onValueChange = { collab.knownDraft = it },
                textStyle = RlType.mono.copy(fontSize = if (compact) 11.sp else 12.sp, color = RlColors.InkSoft),
                cursorBrush = SolidColor(RlColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .wash(RlColors.PaperDeep)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(6.dp))
            GhostButton(
                text = "记入已知",
                onClick = {
                    val q = collab.knownDraft.trim()
                    if (q.isEmpty()) return@GhostButton
                    collab.addKnownFact(q)
                    state.statusMessage = "小队 · 已知情报 +「$q」"
                },
            )
        }
    }
}

/**
 * 提案拍板：同意 / 重来 / 意见 / 取消。
 * 点「意见」展开输入框。
 */
@Composable
fun ProposalActions(
    onAgree: () -> Unit,
    onRetry: () -> Unit,
    onOpinion: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var opinionOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(text = "同意", onClick = {
                opinionOpen = false
                draft = ""
                onAgree()
            })
            GhostButton(text = "重来", onClick = {
                opinionOpen = false
                draft = ""
                onRetry()
            })
            GhostButton(text = "意见", onClick = { opinionOpen = !opinionOpen })
            GhostButton(text = "取消", onClick = {
                opinionOpen = false
                draft = ""
                onCancel()
            })
            trailing()
        }
        AnimatedVisibility(
            visible = opinionOpen,
            enter = fadeIn(RlMotion.enter()) + expandVertically(RlMotion.enter()),
            exit = fadeOut(RlMotion.exit()) + shrinkVertically(RlMotion.exit()),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                LabelRaw(text = "写下意见，同伴会按此改写", style = RlType.label(10.sp, RlColors.Faint))
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = RlType.mono.copy(fontSize = 12.sp, color = RlColors.InkSoft),
                    cursorBrush = SolidColor(RlColors.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wash(RlColors.FieldDeep)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhostButton(
                        text = "送出意见",
                        onClick = {
                            val q = draft.trim()
                            if (q.isEmpty()) return@GhostButton
                            onOpinion(q)
                            draft = ""
                            opinionOpen = false
                        },
                    )
                    GhostButton(text = "收起", onClick = { opinionOpen = false })
                }
            }
        }
    }
}
