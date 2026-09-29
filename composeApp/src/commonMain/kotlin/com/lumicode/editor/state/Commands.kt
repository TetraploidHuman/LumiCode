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
    IdeCommand("open-workspace", "打开工作区文件夹", "OPEN WORKSPACE", "-", "01 / 文件") {
        state.openWorkspacePicker()
    },
    IdeCommand("new", "新建文件", "NEW FILE", "Ctrl N", "01 / 文件") { state.newFile() },
    IdeCommand("new-folder", "新建文件夹", "NEW FOLDER", "-", "01 / 文件") { state.beginNewFolder() },
    IdeCommand("rename", "重命名", "RENAME", "-", "01 / 文件") { state.beginRename() },
    IdeCommand("delete", "删除选中项", "DELETE", "-", "01 / 文件") { state.deleteSelection() },
    IdeCommand("close", "关闭当前文档", "CLOSE DOCUMENT", "Ctrl W", "01 / 文件") {
        state.activePath?.let { state.close(it) }
    },
    IdeCommand("quick", "快速打开文档", "QUICK OPEN", "Ctrl P", "01 / 文件") {
        state.toggleOverlay(OverlayMode.QUICK_OPEN)
    },
    IdeCommand("find", "文档内查找", "FIND IN DOCUMENT", "Ctrl F", "02 / 查找") {
        state.openFind(replace = false)
    },
    IdeCommand("replace", "查找并替换", "FIND AND REPLACE", "Ctrl H", "02 / 查找") {
        state.openFind(replace = true)
    },
    IdeCommand("find-workspace", "工作区搜索", "SEARCH WORKSPACE", "Ctrl ⇧ F", "02 / 查找") {
        state.toggleOverlay(OverlayMode.WORKSPACE_SEARCH)
    },
    IdeCommand("goto-line", "跳转到行", "GO TO LINE", "Ctrl G", "02 / 查找") {
        state.openOverlay(OverlayMode.GOTO_LINE)
    },
    IdeCommand("explorer", "显示/隐藏资源管理器", "TOGGLE EXPLORER", "Ctrl B", "03 / 版面") {
        state.explorerVisible = !state.explorerVisible
        state.persistPanelPrefs()
    },
    IdeCommand("reference", "显示/隐藏参考区", "TOGGLE REFERENCE", "Ctrl R", "03 / 版面") {
        state.referenceVisible = !state.referenceVisible
        state.persistPanelPrefs()
    },
    IdeCommand("collab", "打开小队工作台", "OPEN SQUAD", "Ctrl ⇧ A", "03 / 版面") {
        state.openSquadPage()
    },
    IdeCommand("console", "显示/隐藏终端面板", "TOGGLE TERMINAL", "Ctrl J", "03 / 版面") {
        state.outputVisible = !state.outputVisible
        state.persistPanelPrefs()
    },
    IdeCommand("collab-open", "新开一路同伴", "OPEN LANE", "-", "07 / 小队") {
        state.openSquadPage()
        state.collab.openLane()
    },
    IdeCommand("collab-stop", "关掉全部同伴", "CLOSE ALL LANES", "-", "07 / 小队") {
        state.collab.stop()
        state.statusMessage = "小队 · ${state.collab.phaseLabel}"
    },
    IdeCommand("run", "在终端运行", "RUN IN TERMINAL", "F5", "04 / 运行") { onRun() },
    IdeCommand("export", "导出档案包", "EXPORT BUNDLE", "Ctrl E", "04 / 运行") {
        state.exportBundle()
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
    IdeCommand("clear", "清空终端", "CLEAR TERMINAL", "-", "06 / 系统") {
        state.terminal.clear()
        state.appendTerminal("[00] 终端已清空", LineKind.MUTED)
    },
    IdeCommand("settings", "打开设置", "SETTINGS", "-", "06 / 系统") {
        state.openOverlay(OverlayMode.SETTINGS)
    },
    IdeCommand("shortcuts", "快捷键一览", "KEYBOARD MAP", "Ctrl /", "06 / 系统") {
        state.openOverlay(OverlayMode.SHORTCUTS)
    },
    IdeCommand("diagnostics", "重新运行诊断", "DIAGNOSTICS", "-", "06 / 系统") {
        state.rescanProblems()
        state.statusMessage = "诊断完成 · ${state.problems.size} 项"
        state.appendTerminal("[诊断] 当前文档发现 ${state.problems.size} 处问题", LineKind.WARN)
    },
)
