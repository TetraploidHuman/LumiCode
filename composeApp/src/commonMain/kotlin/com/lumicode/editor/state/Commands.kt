package com.lumicode.editor.state

import com.lumicode.editor.model.Language

/** One entry of the COMMAND INDEX (Ctrl/Cmd+K). */
data class IdeCommand(
    val id: String,
    val title: String,
    val chinese: String,
    val shortcut: String,
    val group: String,
    val action: () -> Unit,
)

fun defaultCommands(
    state: IdeState,
    onRun: () -> Unit,
): List<IdeCommand> = listOf(
    IdeCommand("save", "Save archive", "写入档案", "Ctrl S", "01 / FILE") { state.save() },
    IdeCommand("new", "New archive entry", "新建文件", "Ctrl N", "01 / FILE") { state.newFile() },
    IdeCommand("close", "Close active document", "关闭当前文件", "Ctrl W", "01 / FILE") {
        state.activePath?.let { state.close(it) }
    },
    IdeCommand("quick", "Quick open document", "快速打开", "Ctrl P", "01 / FILE") {
        state.toggleOverlay(OverlayMode.QUICK_OPEN)
    },
    IdeCommand("find", "Find in document", "文档内查找", "Ctrl F", "02 / SEARCH") {
        state.findVisible = !state.findVisible
        state.findActiveMatch = 0
    },
    IdeCommand("explorer", "Toggle explorer", "切换资源管理器", "Ctrl B", "03 / LAYOUT") {
        state.explorerVisible = !state.explorerVisible
    },
    IdeCommand("reference", "Toggle reference area", "切换参考区", "Ctrl R", "03 / LAYOUT") {
        state.referenceVisible = !state.referenceVisible
    },
    IdeCommand("console", "Toggle analysis console", "切换控制台", "Ctrl J", "03 / LAYOUT") {
        state.outputVisible = !state.outputVisible
    },
    IdeCommand("run", "Run analysis pass", "运行分析", "F5", "04 / RUN") { onRun() },
    IdeCommand("export", "Export archive bundle", "导出档案", "Ctrl E", "04 / RUN") {
        state.appendTerminal("[EXPORT] packaging 6 entries → archive-16.bundle", LineKind.INFO)
        state.appendTerminal("[EXPORT] signature 0xB4·19·F2 ........ ok", LineKind.OK)
        state.statusMessage = "ARCHIVE EXPORTED"
        state.appendLog("EXPORT BUNDLE")
    },
    IdeCommand("next", "Next document", "下一个文件", "Ctrl Tab", "05 / NAVIGATE") { state.cycleTab(1) },
    IdeCommand("prev", "Previous document", "上一个文件", "Ctrl ⇧ Tab", "05 / NAVIGATE") { state.cycleTab(-1) },
    IdeCommand("language", "Re-classify language", "重新识别语言", "-", "05 / NAVIGATE") {
        val file = state.activeFile
        if (file != null) {
            val lang: Language = file.language
            state.appendTerminal("[LANG] ${file.name} → ${lang.label}", LineKind.INFO)
            state.appendLog("LANG ${lang.short}")
        }
    },
    IdeCommand("clear", "Clear console", "清空控制台", "-", "06 / SYSTEM") {
        state.terminal.clear()
        state.appendTerminal("[00] console cleared", LineKind.MUTED)
    },
    IdeCommand("diagnostics", "Re-run diagnostics", "重新诊断", "-", "06 / SYSTEM") {
        state.rescanProblems()
        state.statusMessage = "DIAGNOSTICS ${state.problems.size} ITEMS"
        state.appendTerminal("[DIAG] ${state.problems.size} finding(s) in active document", LineKind.WARN)
    },
)
