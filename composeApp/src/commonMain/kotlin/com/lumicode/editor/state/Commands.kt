package com.lumicode.editor.state

import com.lumicode.editor.model.Language

/** 命令面板的一个条目：title 是中文主标题，chinese 字段保留英文副标题。 */
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
    IdeCommand("save", "保存档案", "SAVE ARCHIVE", "Ctrl S", "01 / 文件") { state.save() },
    IdeCommand("new", "新建文件", "NEW FILE", "Ctrl N", "01 / 文件") { state.newFile() },
    IdeCommand("close", "关闭当前文档", "CLOSE DOCUMENT", "Ctrl W", "01 / 文件") {
        state.activePath?.let { state.close(it) }
    },
    IdeCommand("quick", "快速打开文档", "QUICK OPEN", "Ctrl P", "01 / 文件") {
        state.toggleOverlay(OverlayMode.QUICK_OPEN)
    },
    IdeCommand("find", "文档内查找", "FIND IN DOCUMENT", "Ctrl F", "02 / 查找") {
        state.findVisible = !state.findVisible
        state.findActiveMatch = 0
    },
    IdeCommand("explorer", "显示/隐藏资源管理器", "TOGGLE EXPLORER", "Ctrl B", "03 / 版面") {
        state.explorerVisible = !state.explorerVisible
    },
    IdeCommand("reference", "显示/隐藏参考区", "TOGGLE REFERENCE", "Ctrl R", "03 / 版面") {
        state.referenceVisible = !state.referenceVisible
    },
    IdeCommand("console", "显示/隐藏分析控制台", "TOGGLE CONSOLE", "Ctrl J", "03 / 版面") {
        state.outputVisible = !state.outputVisible
    },
    IdeCommand("run", "运行分析", "RUN ANALYSIS", "F5", "04 / 运行") { onRun() },
    IdeCommand("export", "导出档案包", "EXPORT BUNDLE", "Ctrl E", "04 / 运行") {
        state.appendTerminal("[导出] 打包 6 个条目 → archive-16.bundle", LineKind.INFO)
        state.appendTerminal("[导出] 签名 0xB4·19·F2 ................ 通过", LineKind.OK)
        state.statusMessage = "档案已导出"
        state.appendLog("导出档案包")
    },
    IdeCommand("next", "下一个文档", "NEXT DOCUMENT", "Ctrl Tab", "05 / 导航") { state.cycleTab(1) },
    IdeCommand("prev", "上一个文档", "PREVIOUS DOCUMENT", "Ctrl ⇧ Tab", "05 / 导航") { state.cycleTab(-1) },
    IdeCommand("language", "重新识别语言", "RE-CLASSIFY", "-", "05 / 导航") {
        val file = state.activeFile
        if (file != null) {
            val lang: Language = file.language
            state.appendTerminal("[语言] ${file.name} → ${lang.label}", LineKind.INFO)
            state.appendLog("语言 ${lang.short}")
        }
    },
    IdeCommand("clear", "清空控制台", "CLEAR CONSOLE", "-", "06 / 系统") {
        state.terminal.clear()
        state.appendTerminal("[00] 控制台已清空", LineKind.MUTED)
    },
    IdeCommand("diagnostics", "重新运行诊断", "DIAGNOSTICS", "-", "06 / 系统") {
        state.rescanProblems()
        state.statusMessage = "诊断完成 · ${state.problems.size} 项"
        state.appendTerminal("[诊断] 当前文档发现 ${state.problems.size} 处问题", LineKind.WARN)
    },
)
