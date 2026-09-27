package com.lumicode.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumicode.editor.model.CodeFile
import com.lumicode.editor.model.buildTree
import com.lumicode.editor.platform.clockLabel
import com.lumicode.editor.platform.platformLabel
import com.lumicode.editor.syntax.SyntaxHighlighter

enum class OverlayMode { NONE, COMMAND_INDEX, QUICK_OPEN, SETTINGS, OVERVIEW, GOTO_LINE, WORKSPACE_SEARCH }

enum class LineKind { INFO, OK, WARN, ERROR, MUTED }

/** 文件树弹层：新建 / 重命名。 */
sealed class TreeDialog {
    data class NewFile(val parentFolder: String) : TreeDialog()
    data class NewFolder(val parentFolder: String) : TreeDialog()
    data class Rename(val path: String, val isFolder: Boolean) : TreeDialog()
}

data class TerminalLine(val time: String, val text: String, val kind: LineKind)

data class Problem(val path: String, val line: Int, val column: Int, val message: String, val severity: LineKind)

data class LogEntry(val time: String, val text: String)

/** 工作区搜索的一条命中。 */
data class WorkspaceHit(
    val path: String,
    val line: Int,
    val column: Int,
    val preview: String,
)

/**
 * The whole editor state tree. Plain Compose state — no platform dependencies,
 * so the same instance drives Android, desktop and wasm.
 */
class IdeState(initialFiles: List<CodeFile>) {

    private val initialCatalogue: Map<String, CodeFile> = initialFiles.associateBy { it.path }
    private val catalogue = mutableStateMapOf<String, CodeFile>().apply { putAll(initialCatalogue) }
    private val contents = mutableStateMapOf<String, String>()
    private val savedSnapshot = mutableStateMapOf<String, String>()
    /** 空文件夹（无文件时仍要显示在树上）。 */
    private val emptyFolders = mutableStateMapOf<String, Boolean>()

    var tree by mutableStateOf(buildTree(initialFiles))
        private set

    val openTabs = mutableStateListOf<String>()
    var activePath by mutableStateOf<String?>(null)

    /** 文件树当前选中的节点（文件或文件夹），供新建/重命名/删除定位。 */
    var treeSelection by mutableStateOf<String?>(null)
    var treeDialog by mutableStateOf<TreeDialog?>(null)
    var treeDialogDraft by mutableStateOf("")
    var treeDialogError by mutableStateOf<String?>(null)

    var explorerVisible by mutableStateOf(true)
    var outputVisible by mutableStateOf(true)
    var referenceVisible by mutableStateOf(true)

    /** Set once the shell detects a narrow (phone) viewport. */
    var compactApplied by mutableStateOf(false)

    var overlay by mutableStateOf(OverlayMode.NONE)
    var overlayQuery by mutableStateOf("")

    var findVisible by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var replaceQuery by mutableStateOf("")
    var findActiveMatch by mutableStateOf(0)
    var findCaseSensitive by mutableStateOf(false)
    var findRegex by mutableStateOf(false)
    /** Markdown 默认预览；源码模式可编辑。 */
    var markdownPreview by mutableStateOf(true)

    val terminal = mutableStateListOf<TerminalLine>()
    val log = mutableStateListOf<LogEntry>()
    var problems by mutableStateOf<List<Problem>>(emptyList())

    var cursorLine by mutableStateOf(1)
    var cursorColumn by mutableStateOf(1)
    var selectionLength by mutableStateOf(0)

    var savedCount by mutableStateOf(0)
    var runToken by mutableStateOf(0)
    var statusMessage by mutableStateOf("会话已授权")

    var pendingRevealLine by mutableStateOf<Int?>(null)
    /** 递增令牌，保证同号行也能再次跳转。 */
    var pendingRevealSeq by mutableStateOf(0)

    private val expanded = mutableStateMapOf<String, Boolean>()
    private var untitledCounter = 0

    init {
        initialFiles.forEach {
            contents[it.path] = it.content
            savedSnapshot[it.path] = it.content
        }
        appendTerminal("[00] 挂载 /dev/archive ................. 通过", LineKind.OK)
        appendTerminal("[01] 签名校验 ......................... 通过", LineKind.OK)
        appendTerminal("[02] 会话已授权 · ${platformLabel()}", LineKind.INFO)
        appendLog("工作区已挂载")
        open("src/Main.kt")
        rescanProblems()
    }

    // ---------------------------------------------------------------- files

    fun contentOf(path: String): String = contents[path] ?: ""

    fun metaOf(path: String): CodeFile? = catalogue[path]

    val activeFile: CodeFile? get() = activePath?.let { catalogue[it] }

    val activeContent: String get() = activePath?.let { contentOf(it) } ?: ""

    fun isDirty(path: String): Boolean = contents[path] != savedSnapshot[path]

    val dirtyCount: Int get() = openTabs.count { isDirty(it) }

    fun isFolderOpen(path: String): Boolean = expanded[path] == true

    fun toggleFolder(path: String) {
        expanded[path] = !isFolderOpen(path)
        treeSelection = path
    }

    fun selectTreeNode(path: String) {
        treeSelection = path
    }

    private fun rebuildTree() {
        tree = buildTree(catalogue.values.toList(), emptyFolders.keys)
    }

    private fun isFolderOnly(path: String): Boolean =
        !contents.containsKey(path) &&
            (emptyFolders.containsKey(path) || catalogue.keys.any { it.startsWith("$path/") })

    private fun parentFolderOfSelection(): String {
        val sel = treeSelection ?: return "src"
        return when {
            isFolderOnly(sel) -> sel
            else -> sel.substringBeforeLast('/', missingDelimiterValue = "src").ifEmpty { "src" }
        }
    }

    fun beginNewFile() {
        treeDialog = TreeDialog.NewFile(parentFolderOfSelection())
        treeDialogDraft = "untitled.kt"
        treeDialogError = null
    }

    fun beginNewFolder() {
        treeDialog = TreeDialog.NewFolder(parentFolderOfSelection())
        treeDialogDraft = "new-folder"
        treeDialogError = null
    }

    fun beginRename() {
        val sel = treeSelection ?: return
        treeDialog = TreeDialog.Rename(sel, isFolderOnly(sel))
        treeDialogDraft = sel.substringAfterLast('/')
        treeDialogError = null
    }

    fun cancelTreeDialog() {
        treeDialog = null
        treeDialogDraft = ""
        treeDialogError = null
    }

    fun confirmTreeDialog() {
        val dialog = treeDialog ?: return
        val name = treeDialogDraft.trim()
        if (name.isEmpty()) {
            treeDialogError = "名称不能为空"
            return
        }
        if (name.contains('/') || name.contains('\\')) {
            treeDialogError = "名称不能包含路径分隔符"
            return
        }
        when (dialog) {
            is TreeDialog.NewFile -> {
                val path = joinPath(dialog.parentFolder, name)
                if (pathTaken(path)) {
                    treeDialogError = "已存在同名项"
                    return
                }
                createFileAt(path)
                cancelTreeDialog()
            }
            is TreeDialog.NewFolder -> {
                val path = joinPath(dialog.parentFolder, name)
                if (pathTaken(path)) {
                    treeDialogError = "已存在同名项"
                    return
                }
                emptyFolders[path] = true
                if (dialog.parentFolder.isNotEmpty()) expanded[dialog.parentFolder] = true
                expanded[path] = true
                rebuildTree()
                treeSelection = path
                appendTerminal("[++] 已创建文件夹 $path", LineKind.INFO)
                appendLog("新建文件夹 ${path.substringAfterLast('/')}")
                cancelTreeDialog()
            }
            is TreeDialog.Rename -> {
                val parent = dialog.path.substringBeforeLast('/', missingDelimiterValue = "")
                val newPath = joinPath(parent, name)
                if (newPath == dialog.path) {
                    cancelTreeDialog()
                    return
                }
                if (pathTaken(newPath)) {
                    treeDialogError = "已存在同名项"
                    return
                }
                if (!renamePath(dialog.path, newPath, dialog.isFolder)) {
                    treeDialogError = "无法重命名"
                    return
                }
                cancelTreeDialog()
            }
        }
    }

    fun deleteSelection() {
        val sel = treeSelection ?: return
        deletePath(sel)
    }

    private fun pathTaken(path: String): Boolean =
        catalogue.containsKey(path) ||
            emptyFolders.containsKey(path) ||
            catalogue.keys.any { it.startsWith("$path/") }

    private fun joinPath(parent: String, name: String): String =
        if (parent.isEmpty()) name else "$parent/$name"

    private fun createFileAt(path: String) {
        val template = """
            package lumicode.scratch

            fun main() {
                println("new archive entry")
            }
        """.trimIndent()
        val meta = initialCatalogue.values.first().meta.copy(
            archiveNo = "X-${(catalogue.size + 1).toString().padStart(3, '0')}",
            department = "SCRATCH",
            departmentCn = "草稿区",
            collection = "UNCLASSIFIED",
            collectionCn = "未分类",
            related = "Operator",
            abstract = "新建的未分类档案条目。写入后会被归档到工作区索引中，编号按创建顺序递增。",
        )
        catalogue[path] = CodeFile(path = path, content = template, meta = meta)
        contents[path] = template
        savedSnapshot[path] = ""
        emptyFolders.remove(path)
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parent.isNotEmpty()) {
            emptyFolders.remove(parent)
            expanded[parent] = true
        }
        rebuildTree()
        open(path)
        appendTerminal("[++] 已创建 $path", LineKind.INFO)
        appendLog("新建 ${path.substringAfterLast('/')}")
    }

    private fun renamePath(oldPath: String, newPath: String, isFolder: Boolean): Boolean {
        if (isFolder) {
            val prefix = "$oldPath/"
            val fileMoves = catalogue.filterKeys { it.startsWith(prefix) }.toList()
            for ((from, meta) in fileMoves) {
                val to = newPath + from.removePrefix(oldPath)
                remapFile(from, to, meta)
            }
            val folderMoves = emptyFolders.keys
                .filter { it == oldPath || it.startsWith(prefix) }
                .toList()
            for (from in folderMoves) {
                val to = if (from == oldPath) newPath else newPath + from.removePrefix(oldPath)
                emptyFolders.remove(from)
                emptyFolders[to] = true
                if (expanded.remove(from) == true) expanded[to] = true
            }
            if (emptyFolders.containsKey(oldPath)) {
                emptyFolders.remove(oldPath)
                emptyFolders[newPath] = true
            }
            // 即使没有 empty 标记，文件夹也可能仅由子文件撑起——子文件已 remap
            if (expanded.remove(oldPath) == true) expanded[newPath] = true
            rebuildTree()
            treeSelection = newPath
            appendTerminal("[↔] 文件夹 $oldPath → $newPath", LineKind.INFO)
            appendLog("重命名文件夹 ${newPath.substringAfterLast('/')}")
            return true
        }
        val meta = catalogue[oldPath] ?: return false
        remapFile(oldPath, newPath, meta)
        rebuildTree()
        treeSelection = newPath
        appendTerminal("[↔] $oldPath → $newPath", LineKind.INFO)
        appendLog("重命名 ${newPath.substringAfterLast('/')}")
        return true
    }

    private fun remapFile(from: String, to: String, meta: CodeFile) {
        catalogue.remove(from)
        catalogue[to] = meta.copy(path = to)
        contents[to] = contents.remove(from) ?: meta.content
        savedSnapshot[to] = savedSnapshot.remove(from) ?: ""
        val tabIndex = openTabs.indexOf(from)
        if (tabIndex >= 0) openTabs[tabIndex] = to
        if (activePath == from) activePath = to
    }

    private fun deletePath(path: String) {
        if (isFolderOnly(path)) {
            val prefix = "$path/"
            catalogue.keys.filter { it.startsWith(prefix) }.toList().forEach { removeFile(it) }
            emptyFolders.keys.filter { it == path || it.startsWith(prefix) }.toList()
                .forEach {
                    emptyFolders.remove(it)
                    expanded.remove(it)
                }
            expanded.remove(path)
            rebuildTree()
            if (treeSelection == path || treeSelection?.startsWith(prefix) == true) {
                treeSelection = null
            }
            appendTerminal("[--] 已删除文件夹 $path", LineKind.WARN)
            appendLog("删除文件夹 ${path.substringAfterLast('/')}")
        } else if (contents.containsKey(path)) {
            removeFile(path)
            rebuildTree()
            if (treeSelection == path) treeSelection = null
            appendTerminal("[--] 已删除 $path", LineKind.WARN)
            appendLog("删除 ${path.substringAfterLast('/')}")
        }
    }

    private fun removeFile(path: String) {
        close(path)
        catalogue.remove(path)
        contents.remove(path)
        savedSnapshot.remove(path)
    }

    fun newFile() {
        untitledCounter++
        val name = "untitled-${untitledCounter.toString().padStart(2, '0')}.kt"
        var path = "src/$name"
        while (catalogue.containsKey(path)) {
            untitledCounter++
            path = "src/untitled-${untitledCounter.toString().padStart(2, '0')}.kt"
        }
        createFileAt(path)
    }

    fun open(path: String, revealLine: Int? = null) {
        if (!contents.containsKey(path)) return
        if (!openTabs.contains(path)) openTabs.add(path)
        activePath = path
        treeSelection = path
        if (revealLine != null) requestRevealLine(revealLine)
        else {
            pendingRevealLine = null
            findActiveMatch = 0
            cursorLine = 1
            cursorColumn = 1
        }
        appendLog("打开 ${path.substringAfterLast('/')}")
        if (overlay != OverlayMode.NONE) overlay = OverlayMode.NONE
    }

    /** 跳到当前文档指定行（1-based）；行号越界会夹紧。 */
    fun requestRevealLine(line: Int) {
        val text = activeContent
        val maxLine = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        val clamped = line.coerceIn(1, maxLine)
        pendingRevealLine = clamped
        pendingRevealSeq++
        findActiveMatch = 0
        cursorLine = clamped
        cursorColumn = 1
    }

    fun close(path: String) {
        val index = openTabs.indexOf(path)
        if (index < 0) return
        openTabs.removeAt(index)
        if (activePath == path) {
            activePath = openTabs.getOrNull((index - 1).coerceAtLeast(0))
        }
        appendLog("关闭 ${path.substringAfterLast('/')}")
    }

    fun cycleTab(step: Int) {
        if (openTabs.size < 2) return
        val current = openTabs.indexOf(activePath)
        val next = ((current + step) % openTabs.size + openTabs.size) % openTabs.size
        open(path = openTabs[next])
    }

    fun updateContent(path: String, text: String) {
        contents[path] = text
        if (activePath == path && !isDirty(path)) statusMessage = "已修改"
    }

    /** 替换当前查找命中；成功后停在「下一个」同序号命中上。 */
    fun replaceCurrentMatch(): Boolean {
        val path = activePath ?: return false
        val query = findQuery
        if (query.isEmpty()) return false
        val text = contentOf(path)
        val match = SyntaxHighlighter.matchAt(text, query, findActiveMatch, findCaseSensitive, findRegex)
            ?: return false
        val replacement = replaceQuery
        val next = text.substring(0, match.start) + replacement + text.substring(match.end)
        contents[path] = next
        statusMessage = "已替换 1 处"
        val matches = SyntaxHighlighter.countMatches(next, query, findCaseSensitive, findRegex)
        findActiveMatch = when {
            matches <= 0 -> 0
            findActiveMatch >= matches -> 0
            else -> findActiveMatch
        }
        appendLog("替换 ${path.substringAfterLast('/')}")
        return true
    }

    /** 替换当前文档内全部命中。 */
    fun replaceAllMatches(): Int {
        val path = activePath ?: return 0
        val query = findQuery
        if (query.isEmpty()) return 0
        val text = contentOf(path)
        val matches = SyntaxHighlighter.findMatches(text, query, findCaseSensitive, findRegex)
        if (matches.isEmpty()) return 0
        val replacement = replaceQuery
        val out = StringBuilder()
        var from = 0
        for (match in matches) {
            out.append(text, from, match.start)
            out.append(replacement)
            from = match.end
        }
        out.append(text, from, text.length)
        contents[path] = out.toString()
        findActiveMatch = 0
        statusMessage = "已替换 ${matches.size} 处"
        appendLog("全部替换 ${path.substringAfterLast('/')} · ${matches.size}")
        return matches.size
    }

    /** 跨文件搜索；最多 [limit] 条。 */
    fun searchWorkspace(query: String, limit: Int = 80): List<WorkspaceHit> {
        if (query.isEmpty()) return emptyList()
        val hits = mutableListOf<WorkspaceHit>()
        for (path in contents.keys.sorted()) {
            val text = contentOf(path)
            val matches = SyntaxHighlighter.findMatches(text, query, findCaseSensitive, findRegex)
            for (match in matches) {
                val lineIdx = SyntaxHighlighter.lineOf(text, match.start)
                val col = SyntaxHighlighter.columnOf(text, match.start) + 1
                val lineStart = if (match.start == 0) {
                    0
                } else {
                    val i = text.lastIndexOf('\n', match.start - 1)
                    if (i < 0) 0 else i + 1
                }
                val lineEnd = text.indexOf('\n', match.start).let { if (it < 0) text.length else it }
                val preview = text.substring(lineStart, lineEnd).trim().take(72)
                hits += WorkspaceHit(path, lineIdx + 1, col, preview)
                if (hits.size >= limit) return hits
            }
        }
        return hits
    }

    fun exportBundle() {
        appendTerminal("[导出] 打包 ${catalogue.size} 个条目 → archive-16.bundle", LineKind.INFO)
        appendTerminal("[导出] 签名 0xB4·19·F2 ................ 通过", LineKind.OK)
        statusMessage = "档案已导出"
        appendLog("导出档案包")
    }

    /** Cursor offset of the active document, owned by the editor composable. */
    var cursorOffset by mutableStateOf(0)

    fun save(path: String? = activePath) {
        val target = path ?: return
        savedSnapshot[target] = contentOf(target)
        savedCount++
        statusMessage = "档案已写入"
        appendTerminal("[${savedCount.toString().padStart(2, '0')}] 写入 ${target.substringAfterLast('/')} ......... 通过", LineKind.OK)
        appendLog("保存 ${target.substringAfterLast('/')}")
    }

    fun requestRun() {
        runToken++
    }

    // ---------------------------------------------------------- diagnostics

    fun rescanProblems() {
        val path = activePath ?: run {
            problems = emptyList()
            return
        }
        val text = contentOf(path)
        val found = mutableListOf<Problem>()
        text.split('\n').forEachIndexed { index, line ->
            val todo = TODO_REGEX.find(line)
            if (todo != null) {
                found += Problem(path, index + 1, todo.range.first + 1, "${todo.groupValues[1]} 标记待处理", LineKind.WARN)
            }
            if (line.length > 100) {
                found += Problem(path, index + 1, 101, "该行超过 100 列", LineKind.INFO)
            }
        }
        val balance = text.count { it == '{' } - text.count { it == '}' }
        if (balance != 0) {
            found += Problem(path, 1, 1, "花括号不匹配：${if (balance > 0) "缺 $balance 个 }" else "多 ${-balance} 个 }"}", LineKind.ERROR)
        }
        problems = found
    }

    // -------------------------------------------------------------- console

    fun appendTerminal(text: String, kind: LineKind = LineKind.INFO) {
        terminal.add(TerminalLine(clockLabel(), text, kind))
        while (terminal.size > 200) terminal.removeAt(0)
    }

    fun appendLog(text: String) {
        log.add(LogEntry(clockLabel(), text))
        while (log.size > 60) log.removeAt(0)
    }

    /** Restores the pristine archive — wired to REINITIALIZE in the status bar. */
    fun softReset() {
        openTabs.clear()
        catalogue.clear()
        catalogue.putAll(initialCatalogue)
        contents.clear()
        savedSnapshot.clear()
        emptyFolders.clear()
        initialCatalogue.forEach { (path, file) ->
            contents[path] = file.content
            savedSnapshot[path] = file.content
        }
        rebuildTree()
        terminal.clear()
        log.clear()
        problems = emptyList()
        savedCount = 0
        untitledCounter = 0
        treeSelection = null
        cancelTreeDialog()
        statusMessage = "会话已授权"
        appendTerminal("[00] 重置会话 ......................... 通过", LineKind.OK)
        appendLog("重置会话")
        open("src/Main.kt")
    }

    fun toggleOverlay(mode: OverlayMode) {
        overlay = if (overlay == mode) OverlayMode.NONE else mode
        overlayQuery = ""
    }

    fun openOverlay(mode: OverlayMode) {
        overlay = mode
        overlayQuery = ""
    }

    /** ESC 的分级行为：关树弹层 → 关浮层 → 关查找 → 打开工作区总览。 */
    fun handleEscape() {
        when {
            treeDialog != null -> cancelTreeDialog()
            overlay != OverlayMode.NONE -> overlay = OverlayMode.NONE
            findVisible -> findVisible = false
            else -> openOverlay(OverlayMode.OVERVIEW)
        }
    }

    fun quickOpenTargets(): List<String> = contents.keys.sorted()

    private companion object {
        val TODO_REGEX = Regex("\\b(TODO|FIXME|XXX|HACK)\\b")
    }
}
