package com.lumicode.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumicode.editor.model.CodeFile
import com.lumicode.editor.model.buildTree
import com.lumicode.editor.model.defaultMetaForPath
import com.lumicode.editor.workspace.AgentFileEdit
import com.lumicode.editor.workspace.WorkspaceApi
import com.lumicode.editor.workspace.applyLineEdit
import com.lumicode.editor.platform.LocalPrefs
import com.lumicode.editor.platform.clockLabel
import com.lumicode.editor.platform.platformLabel
import com.lumicode.editor.syntax.SyntaxHighlighter

enum class OverlayMode {
    NONE,
    COMMAND_INDEX,
    QUICK_OPEN,
    SETTINGS,
    OVERVIEW,
    GOTO_LINE,
    WORKSPACE_SEARCH,
    SHORTCUTS,
    WORKSPACE_OPEN,
}

/** 顶层工作台：与代码编辑平级。 */
enum class ShellPage {
    CODE,
    SQUAD,
}

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
class IdeState(initialFiles: List<CodeFile> = emptyList()) {

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

    /** 代码工作区 / 小队工作台（平级）。 */
    var shellPage by mutableStateOf(ShellPage.CODE)

    fun openSquadPage() {
        shellPage = ShellPage.SQUAD
        collab.focusCollab()
        statusMessage = "小队 · ${collab.briefing}"
    }

    fun openCodePage() {
        shellPage = ShellPage.CODE
        statusMessage = "代码 · ${activePath ?: "工作区"}"
    }

    var findVisible by mutableStateOf(false)
    /** Ctrl+H 时展开替换行；Ctrl+F 仅查找。 */
    var findReplaceVisible by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var replaceQuery by mutableStateOf("")
    var findActiveMatch by mutableStateOf(0)
    var findCaseSensitive by mutableStateOf(false)
    var findRegex by mutableStateOf(false)
    /** 仅在冻结的选区内查找 / 替换。 */
    var findInSelection by mutableStateOf(false)
    var findScopeStart by mutableStateOf(0)
    var findScopeEnd by mutableStateOf(0)
    /** Markdown 默认预览；源码模式可编辑。 */
    var markdownPreview by mutableStateOf(true)

    val terminal = mutableStateListOf<TerminalLine>()
    val log = mutableStateListOf<LogEntry>()
    var problems by mutableStateOf<List<Problem>>(emptyList())

    var cursorLine by mutableStateOf(1)
    var cursorColumn by mutableStateOf(1)
    var selectionLength by mutableStateOf(0)
    var selectionStart by mutableStateOf(0)
    var selectionEnd by mutableStateOf(0)

    var savedCount by mutableStateOf(0)
    var runToken by mutableStateOf(0)
    var statusMessage by mutableStateOf("会话已授权")

    /** 终端：工作区内真实 shell（cwd 相对工作区根）。 */
    var shellCwd by mutableStateOf("")
    var shellDraft by mutableStateOf("")
    var shellBusy by mutableStateOf(false)
    var shellFocusToken by mutableStateOf(0)
        private set
    private val shellHistory = mutableListOf<String>()
    private var shellHistoryIndex = -1

    /** 交互式 PTY 终端缓冲（远程开发风格）。 */
    var ptyText by mutableStateOf("")
    var ptyReady by mutableStateOf(false)
    var ptyError by mutableStateOf<String?>(null)
    var ptyAttached by mutableStateOf(false)
        private set

    fun ensurePty() {
        if (!workspaceMounted) {
            ptyError = "请先打开工作区"
            openWorkspacePicker()
            return
        }
        if (ptyAttached && ptyReady) return
        ptyError = null
        ptyAttached = true
        val backend = com.lumicode.editor.workspace.PtyApi.backend ?: run {
            ptyError = "PTY 后端未安装"
            return
        }
        backend.listener = { event ->
            when (event) {
                is com.lumicode.editor.workspace.PtyEvent.Ready -> {
                    ptyReady = true
                    ptyError = null
                    appendPty("\r\n[pty ready · ${event.cwd} · pid ${event.pid}]\r\n")
                }
                is com.lumicode.editor.workspace.PtyEvent.Out -> appendPty(event.data)
                is com.lumicode.editor.workspace.PtyEvent.Exit -> {
                    ptyReady = false
                    appendPty("\r\n[exit ${event.code}]\r\n")
                }
                is com.lumicode.editor.workspace.PtyEvent.Error -> {
                    ptyReady = false
                    ptyError = event.message
                    appendPty("\r\n[pty error · ${event.message}]\r\n")
                }
                com.lumicode.editor.workspace.PtyEvent.Closed -> {
                    ptyReady = false
                    ptyAttached = false
                }
            }
        }
        com.lumicode.editor.workspace.PtyApi.connect(cwd = shellCwd, cols = 100, rows = 28)
        outputVisible = true
    }

    fun appendPty(chunk: String) {
        val next = ptyText + chunk
        ptyText = if (next.length > 120_000) next.takeLast(100_000) else next
    }

    fun ptyInput(data: String) {
        if (!ptyReady) {
            ensurePty()
        }
        com.lumicode.editor.workspace.PtyApi.sendInput(data)
    }

    fun ptySubmitLine(line: String) {
        val text = if (line.endsWith("\n")) line else "$line\n"
        // 本地回显一行到旧日志（便于滚动查看历史命令）
        appendTerminal("$ ${line.trimEnd()}", LineKind.INFO)
        ptyInput(text)
        shellDraft = ""
    }

    /** 人机共作台：UI 状态在本地，推理经 /api/dsh → lumicode-dsh-bridge → 本机 dsh-web。 */
    val collab = CollabState()

    /** 已挂载的磁盘工作区根路径；空表示尚未选择文件夹。 */
    var workspaceRoot by mutableStateOf<String?>(null)
    var workspaceMounted by mutableStateOf(false)
    var workspaceBusy by mutableStateOf(false)
    var persistTick by mutableStateOf(0)
        private set
    private val persistQueue = mutableStateListOf<String>()
    var deleteTick by mutableStateOf(0)
        private set
    private val deleteQueue = mutableStateListOf<String>()

    /** 已挂载但内容尚未从磁盘拉取的路径（懒加载，避免启动白屏）。 */
    private val unloadedPaths = mutableSetOf<String>()
    var loadTick by mutableStateOf(0)
        private set
    private val loadQueue = mutableStateListOf<String>()

    var pendingRevealLine by mutableStateOf<Int?>(null)
    /** 递增令牌，保证同号行也能再次跳转。 */
    var pendingRevealSeq by mutableStateOf(0)

    /**
     * 非编辑器路径改写当前文档内容时递增（替换 / 外部写入）。
     * CodeEditor 只在此令牌变化时回同步 [text]，避免长按输入时滞后的父状态盖掉本地值。
     */
    var documentEpoch by mutableStateOf(0)
        private set

    private val expanded = mutableStateMapOf<String, Boolean>()
    private var untitledCounter = 0

    init {
        initialFiles.forEach {
            contents[it.path] = it.content
            savedSnapshot[it.path] = it.content
        }
        appendTerminal("[00] 会话已授权 · ${platformLabel()}", LineKind.OK)
        if (initialFiles.isNotEmpty()) {
            workspaceMounted = false
            appendLog("演示工作区（内存）")
            collab.appendAgent("[项目] 小队 · 按需开路 · 人当上级", LineKind.MUTED)
            open("src/Main.kt")
            rescanProblems()
        } else {
            appendLog("请选择工作区文件夹")
            statusMessage = "请选择工作区文件夹"
        }
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

    suspend fun confirmTreeDialog() {
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
                if (workspaceMounted) {
                    val written = WorkspaceApi.writeFile(path, contentOf(path))
                    if (!written.ok) {
                        treeDialogError = written.error ?: "写入磁盘失败"
                        return
                    }
                    savedSnapshot[path] = contentOf(path)
                }
                cancelTreeDialog()
            }
            is TreeDialog.NewFolder -> {
                val path = joinPath(dialog.parentFolder, name)
                if (pathTaken(path)) {
                    treeDialogError = "已存在同名项"
                    return
                }
                if (workspaceMounted) {
                    val created = WorkspaceApi.mkdir(path)
                    if (!created.ok) {
                        treeDialogError = created.error ?: "创建文件夹失败"
                        return
                    }
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
                if (workspaceMounted) {
                    val renamed = WorkspaceApi.rename(dialog.path, newPath)
                    if (!renamed.ok) {
                        treeDialogError = renamed.error ?: "重命名失败"
                        return
                    }
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
        if (workspaceMounted) {
            if (!deleteQueue.contains(sel)) deleteQueue.add(sel)
            deleteTick++
            statusMessage = "正在从磁盘删除…"
        } else {
            deletePath(sel)
        }
    }

    fun drainDeleteQueue(): List<String> {
        val batch = deleteQueue.toList()
        deleteQueue.clear()
        return batch
    }

    suspend fun flushDelete(path: String): Boolean {
        val removed = WorkspaceApi.delete(path)
        if (removed.ok) {
            deletePath(path)
            return true
        }
        statusMessage = removed.error ?: "删除失败"
        appendTerminal("[!!] 删除失败 · $path", LineKind.ERROR)
        return false
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
        val meta = defaultMetaForPath(path, catalogue.size + 1)
        catalogue[path] = CodeFile(path = path, content = template, meta = meta)
        contents[path] = template
        savedSnapshot[path] = ""
        unloadedPaths.remove(path)
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
        if (!catalogue.containsKey(path) && !contents.containsKey(path)) return
        if (path in unloadedPaths) queueLoad(path)
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

    fun queueLoad(path: String) {
        if (!loadQueue.contains(path)) loadQueue.add(path)
        loadTick++
    }

    fun drainLoadQueue(): List<String> {
        val batch = loadQueue.toList()
        loadQueue.clear()
        return batch
    }

    suspend fun flushLoad(path: String): Boolean {
        if (path !in unloadedPaths && contents.containsKey(path) && contentOf(path).isNotEmpty()) {
            return true
        }
        val file = WorkspaceApi.readFile(path)
        if (!file.ok || file.content == null) {
            appendTerminal("[!!] 读取失败 · $path · ${file.error}", LineKind.ERROR)
            unloadedPaths.remove(path)
            return false
        }
        contents[path] = file.content
        savedSnapshot[path] = file.content
        catalogue[path] = (catalogue[path] ?: CodeFile(path, file.content, defaultMetaForPath(path, catalogue.size + 1)))
            .copy(content = file.content)
        unloadedPaths.remove(path)
        bumpDocumentEpoch(path)
        if (activePath == path) rescanProblems()
        return true
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

    private fun bumpDocumentEpoch(path: String) {
        if (path == activePath) documentEpoch++
    }

    /** 替换当前查找命中；成功后停在「下一个」同序号命中上。 */
    fun replaceCurrentMatch(): Boolean {
        val path = activePath ?: return false
        val query = findQuery
        if (query.isEmpty()) return false
        val text = contentOf(path)
        val match = SyntaxHighlighter.matchAt(
            text,
            query,
            findActiveMatch,
            findCaseSensitive,
            findRegex,
            findRangeStart(),
            findRangeEnd(),
        ) ?: return false
        val replacement = replaceQuery
        val next = text.substring(0, match.start) + replacement + text.substring(match.end)
        contents[path] = next
        bumpDocumentEpoch(path)
        statusMessage = "已替换 1 处"
        val matches = SyntaxHighlighter.countMatches(
            next,
            query,
            findCaseSensitive,
            findRegex,
            findRangeStart(),
            findRangeEnd(next.length),
        )
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
        val matches = SyntaxHighlighter.findMatches(
            text,
            query,
            findCaseSensitive,
            findRegex,
            findRangeStart(),
            findRangeEnd(),
        )
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
        bumpDocumentEpoch(path)
        findActiveMatch = 0
        statusMessage = "已替换 ${matches.size} 处"
        appendLog("全部替换 ${path.substringAfterLast('/')} · ${matches.size}")
        return matches.size
    }

    /** Ctrl+F / Ctrl+H：打开查找；有单行选区时预填查询。 */
    fun openFind(replace: Boolean) {
        if (findVisible && findReplaceVisible == replace) {
            findVisible = false
            return
        }
        findVisible = true
        findReplaceVisible = replace
        findActiveMatch = 0
        seedFindQueryFromSelection()
    }

    /** 开关「仅选区」；开启时冻结当前选区范围。 */
    fun toggleFindInSelection() {
        if (findInSelection) {
            findInSelection = false
            findActiveMatch = 0
            return
        }
        val a = minOf(selectionStart, selectionEnd)
        val b = maxOf(selectionStart, selectionEnd)
        if (b <= a) {
            statusMessage = "需要先选中一段文本"
            return
        }
        findScopeStart = a
        findScopeEnd = b
        findInSelection = true
        findActiveMatch = 0
    }

    fun findRangeStart(): Int = if (findInSelection) findScopeStart else 0

    fun findRangeEnd(textLength: Int = activeContent.length): Int =
        if (findInSelection) findScopeEnd.coerceAtMost(textLength) else textLength

    private fun seedFindQueryFromSelection() {
        val text = activeContent
        val a = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val b = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        if (b <= a) return
        val selected = text.substring(a, b)
        if ('\n' in selected || selected.length > 200) return
        findQuery = selected
    }

    fun loadPanelPrefs() {
        LocalPrefs.get(KEY_EXPLORER)?.let { explorerVisible = it != "0" && it != "false" }
        LocalPrefs.get(KEY_REFERENCE)?.let { referenceVisible = it != "0" && it != "false" }
        LocalPrefs.get(KEY_OUTPUT)?.let { outputVisible = it != "0" && it != "false" }
    }

    fun persistPanelPrefs() {
        LocalPrefs.set(KEY_EXPLORER, if (explorerVisible) "1" else "0")
        LocalPrefs.set(KEY_REFERENCE, if (referenceVisible) "1" else "0")
        LocalPrefs.set(KEY_OUTPUT, if (outputVisible) "1" else "0")
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
        if (!workspaceMounted) {
            savedSnapshot[target] = contentOf(target)
            savedCount++
            statusMessage = "档案已写入（内存）"
            appendTerminal("[${savedCount.toString().padStart(2, '0')}] 写入 ${target.substringAfterLast('/')} ......... 通过", LineKind.OK)
            appendLog("保存 ${target.substringAfterLast('/')}")
            return
        }
        queuePersist(target)
        statusMessage = "正在写入磁盘…"
    }

    fun queuePersist(path: String) {
        if (!persistQueue.contains(path)) persistQueue.add(path)
        persistTick++
    }

    fun drainPersistQueue(): List<String> {
        val batch = persistQueue.toList()
        persistQueue.clear()
        return batch
    }

    suspend fun flushPersist(path: String): Boolean {
        val result = WorkspaceApi.writeFile(path, contentOf(path))
        if (result.ok) {
            savedSnapshot[path] = contentOf(path)
            savedCount++
            appendTerminal("[${savedCount.toString().padStart(2, '0')}] 落盘 ${path.substringAfterLast('/')} ......... 通过", LineKind.OK)
            appendLog("落盘 ${path.substringAfterLast('/')}")
            return true
        }
        appendTerminal("[!!] 落盘失败 · $path · ${result.error}", LineKind.ERROR)
        statusMessage = result.error ?: "落盘失败"
        return false
    }

    suspend fun mountWorkspace(path: String): Boolean {
        workspaceBusy = true
        val opened = WorkspaceApi.openRoot(path)
        if (!opened.ok) {
            workspaceBusy = false
            statusMessage = opened.error ?: "无法打开工作区"
            appendTerminal("[!!] 打开工作区失败 · ${opened.error}", LineKind.ERROR)
            return false
        }
        val tree = WorkspaceApi.loadTree()
        if (!tree.ok) {
            workspaceBusy = false
            statusMessage = tree.error ?: "读取工作区失败"
            return false
        }
        val seededReadme = replaceWorkspaceFromTree(tree, opened.root ?: path)
        workspaceRoot = opened.root ?: path
        workspaceMounted = true
        shellCwd = ""
        collab.clearDshSessions()
        LocalPrefs.set(KEY_WORKSPACE, workspaceRoot!!)
        if (seededReadme) {
            flushPersist("README.md")
        }
        workspaceBusy = false
        overlay = OverlayMode.NONE
        statusMessage = "工作区 · ${workspaceRoot!!.substringAfterLast('/')}"
        collab.appendAgent("[项目] 工作区已挂载 · 小队经 DSH 可改盘", LineKind.MUTED)
        appendTerminal("[00] 工作区 · $workspaceRoot · ${tree.files.size} 个文件", LineKind.OK)
        appendTerminal("终端就绪 · 输入命令回车连接交互 PTY", LineKind.MUTED)
        appendLog("挂载工作区")
        // 问题扫描推迟到文件真正打开并加载内容之后，避免首帧卡死。
        return true
    }

    suspend fun tryRestoreWorkspace(): Boolean {
        val saved = LocalPrefs.get(KEY_WORKSPACE)?.trim().orEmpty()
        if (saved.isEmpty()) return false
        return mountWorkspace(saved)
    }

    fun openWorkspacePicker() {
        overlay = OverlayMode.WORKSPACE_OPEN
        overlayQuery = workspaceRoot.orEmpty()
    }

    /** @return true 表示挂载的是空目录并已种入 README.md，调用方应落盘。 */
    private suspend fun replaceWorkspaceFromTree(
        tree: com.lumicode.editor.workspace.WorkspaceTree,
        root: String,
    ): Boolean {
        openTabs.clear()
        catalogue.clear()
        contents.clear()
        savedSnapshot.clear()
        emptyFolders.clear()
        expanded.clear()
        unloadedPaths.clear()
        loadQueue.clear()
        activePath = null
        treeSelection = null
        var idx = 0
        for (rel in tree.files) {
            idx++
            // 只登记路径，内容按打开时懒加载 —— 全量拉文件会卡死 Wasm 首帧（白屏）。
            catalogue[rel] = CodeFile(rel, "", defaultMetaForPath(rel, idx))
            contents[rel] = ""
            savedSnapshot[rel] = ""
            unloadedPaths += rel
        }
        for (folder in tree.folders) emptyFolders[folder] = true
        rebuildTree()
        val first = tree.files.firstOrNull()
        if (first != null) {
            open(first)
            return false
        }
        // 空工作区：种一个 README，避免编辑器空白、Agent 提案无处对照。
        val seed = "README.md"
        val body = "# ${root.substringAfterLast('/')}\n\n"
        catalogue[seed] = CodeFile(seed, body, defaultMetaForPath(seed, 1))
        contents[seed] = body
        savedSnapshot[seed] = ""
        rebuildTree()
        open(seed)
        return true
    }

    /** 供 Agent 的轻量目录（不塞文件正文；正文由 DSH 工具自己读）。 */
    fun workspaceListingBlock(maxEntries: Int = 80): String {
        if (!workspaceMounted) return ""
        val rootLine = workspaceRoot?.let { "工作区根：$it" }.orEmpty()
        val listing = contents.keys.sorted().take(maxEntries).joinToString("\n") { "- $it" }
        return buildString {
            appendLine(rootLine)
            appendLine("文件列表（相对路径，可能不全）：")
            appendLine(listing.ifEmpty { "（空目录或尚未索引）" })
            if (activePath != null) appendLine("当前打开：$activePath")
        }.trim()
    }

    /** 从磁盘重新拉树与已打开文件内容（DSH 工具改盘后同步编辑器）。 */
    suspend fun reloadWorkspaceFromDisk(): Boolean {
        if (!workspaceMounted) return false
        val tree = WorkspaceApi.loadTree()
        if (!tree.ok) {
            statusMessage = tree.error ?: "刷新工作区失败"
            return false
        }
        val keepOpen = openTabs.toList()
        val keepActive = activePath
        val seen = tree.files.toSet()
        for (rel in tree.files) {
            if (!catalogue.containsKey(rel)) {
                catalogue[rel] = CodeFile(rel, "", defaultMetaForPath(rel, catalogue.size + 1))
                contents[rel] = ""
                savedSnapshot[rel] = ""
            }
            unloadedPaths += rel
            contents[rel] = ""
        }
        emptyFolders.clear()
        for (folder in tree.folders) emptyFolders[folder] = true
        // 磁盘上已删的条目从目录拿掉（未打开的）
        catalogue.keys.filter { it !in seen && it !in keepOpen }.toList().forEach { path ->
            catalogue.remove(path)
            contents.remove(path)
            savedSnapshot.remove(path)
            unloadedPaths.remove(path)
        }
        rebuildTree()
        for (path in keepOpen) {
            if (path in seen) flushLoad(path)
        }
        if (keepActive != null && keepActive in seen) {
            activePath = keepActive
            if (keepActive in unloadedPaths) flushLoad(keepActive)
            bumpDocumentEpoch(keepActive)
        } else {
            tree.files.firstOrNull()?.let { open(it) }
        }
        appendLog("同步磁盘工作区")
        return true
    }

    /**
     * 上级点「顺着」：DSH 工具已改盘时只同步编辑器；若仍带旧式 edits 则兼容落盘。
     * 确认后丢掉回滚快照（磁盘保持 Agent 改动）。
     */
    suspend fun applyAgentProposal(taskId: String? = collab.selectedTaskId): Boolean {
        val id = taskId ?: return false
        val task = collab.tasks.firstOrNull { it.id == id } ?: return false
        var ok = reloadWorkspaceFromDisk()
        if (task.proposalEdits.isNotEmpty()) {
            for (edit in task.proposalEdits) {
                ok = applyAgentEdit(edit) && ok
            }
            rebuildTree()
        }
        task.snapshotId?.let { snap ->
            runCatching { WorkspaceApi.forgetSnapshot(snap) }
            collab.clearTaskSnapshot(id)
        }
        collab.goAlong(id)
        statusMessage = if (ok) {
            if (task.changedFiles.isNotEmpty()) {
                "已确认 · 保留 ${task.changedFiles.size} 处改动"
            } else {
                "已同步 DSH 磁盘变更"
            }
        } else {
            "已确认 · 同步不完整"
        }
        return ok
    }

    /** 取消任务：先按快照回滚磁盘，再停任务。 */
    suspend fun leaveItAndRestore(taskId: String? = collab.selectedTaskId): Boolean {
        val id = taskId ?: return false
        val task = collab.tasks.firstOrNull { it.id == id } ?: return false
        val snap = task.snapshotId
        var restored = false
        if (!snap.isNullOrBlank()) {
            val result = WorkspaceApi.restoreSnapshot(snap)
            if (result.ok) {
                restored = true
                collab.markRollbackDone(
                    id,
                    "恢复 ${result.restored} · 删除新增 ${result.deleted}",
                )
                runCatching { WorkspaceApi.forgetSnapshot(snap) }
                reloadWorkspaceFromDisk()
            } else {
                collab.appendAgent("回滚失败 · ${result.error ?: "unknown"}", LineKind.ERROR)
                statusMessage = "回滚失败：${result.error ?: "unknown"}"
            }
        }
        collab.leaveIt(id)
        statusMessage = if (restored) "已取消并回滚磁盘" else "已取消任务"
        return restored || snap.isNullOrBlank()
    }

    /** 重跑：先回滚到任务前快照，再派 DSH（新快照会在下次派发时拍）。 */
    suspend fun tryAgainWithRestore(taskId: String? = collab.selectedTaskId): Boolean {
        val id = taskId ?: return false
        val task = collab.tasks.firstOrNull { it.id == id } ?: return false
        val snap = task.snapshotId
        if (!snap.isNullOrBlank()) {
            val result = WorkspaceApi.restoreSnapshot(snap)
            if (result.ok) {
                collab.markRollbackDone(
                    id,
                    "恢复 ${result.restored} · 删除新增 ${result.deleted}",
                )
                runCatching { WorkspaceApi.forgetSnapshot(snap) }
                collab.clearTaskSnapshot(id)
                reloadWorkspaceFromDisk()
            } else {
                statusMessage = "回滚失败：${result.error ?: "unknown"}"
                return false
            }
        }
        collab.tryAgain(id)
        statusMessage = "已回滚并重跑"
        return true
    }

    private suspend fun applyAgentEdit(edit: AgentFileEdit): Boolean {
        val path = edit.path.trim().replace('\\', '/')
        if (!contents.containsKey(path)) {
            val meta = defaultMetaForPath(path, catalogue.size + 1)
            catalogue[path] = CodeFile(path, "", meta)
            contents[path] = ""
            savedSnapshot[path] = ""
        }
        val merged = applyLineEdit(contentOf(path), edit.startLine, edit.endLine, edit.content)
        contents[path] = merged
        if (activePath == path) bumpDocumentEpoch(path)
        if (!openTabs.contains(path)) openTabs.add(path)
        return if (workspaceMounted) flushPersist(path) else {
            savedSnapshot[path] = merged
            true
        }
    }

    fun requestRun() {
        outputVisible = true
        if (shellDraft.isBlank()) {
            shellDraft = defaultRunCommand()
        }
        runToken++
    }

    /** F5 / 运行：按当前文件推断真实命令。 */
    fun defaultRunCommand(): String {
        val path = activePath ?: return "pwd"
        val name = path.substringAfterLast('/')
        return when {
            name.endsWith(".sh") -> "bash ${shellQuote(path)}"
            name.endsWith(".py") -> "python3 ${shellQuote(path)}"
            name.endsWith(".js") || name.endsWith(".mjs") -> "node ${shellQuote(path)}"
            name == "gradlew" || name.endsWith(".gradle.kts") || name.endsWith(".gradle") ->
                if (contents.containsKey("gradlew")) "./gradlew tasks --quiet" else "ls"
            name.endsWith(".kt") || name.endsWith(".kts") ->
                "wc -l ${shellQuote(path)}"
            name.endsWith(".md") -> "wc -l ${shellQuote(path)}"
            else -> "ls -la ${shellQuote(path)}"
        }
    }

    private fun shellQuote(path: String): String =
        "'" + path.replace("'", "'\\''") + "'"

    fun submitShellDraft() {
        if (shellDraft.isBlank()) return
        if (shellBusy) return
        // Prefer interactive PTY when backend is available.
        if (com.lumicode.editor.workspace.PtyApi.backend != null) {
            ensurePty()
            ptySubmitLine(shellDraft)
            return
        }
        runToken++
    }

    fun shellHistoryUp() {
        if (shellHistory.isEmpty()) return
        if (shellHistoryIndex < 0) shellHistoryIndex = shellHistory.lastIndex
        else shellHistoryIndex = (shellHistoryIndex - 1).coerceAtLeast(0)
        shellDraft = shellHistory[shellHistoryIndex]
    }

    fun shellHistoryDown() {
        if (shellHistory.isEmpty() || shellHistoryIndex < 0) return
        shellHistoryIndex++
        if (shellHistoryIndex > shellHistory.lastIndex) {
            shellHistoryIndex = -1
            shellDraft = ""
        } else {
            shellDraft = shellHistory[shellHistoryIndex]
        }
    }

    suspend fun runShellCommand(command: String = shellDraft): Boolean {
        val line = command.trim()
        if (line.isEmpty()) return false
        if (!workspaceMounted) {
            appendTerminal("\$ $line", LineKind.MUTED)
            appendTerminal("请先打开工作区文件夹", LineKind.ERROR)
            statusMessage = "终端 · 未挂载工作区"
            openWorkspacePicker()
            return false
        }
        outputVisible = true
        shellBusy = true
        appendTerminal("$ ${promptPrefix()}$line", LineKind.INFO)
        if (shellHistory.lastOrNull() != line) shellHistory.add(line)
        shellHistoryIndex = -1

        val cdTarget = parseCd(line)
        if (cdTarget != null) {
            val ok = changeShellCwd(cdTarget)
            shellBusy = false
            if (ok) {
                shellDraft = ""
                appendTerminal("cwd → ${shellCwd.ifEmpty { "." }}", LineKind.OK)
                statusMessage = "终端 · ${shellCwd.ifEmpty { "/" }}"
            }
            shellFocusToken++
            return ok
        }

        val result = WorkspaceApi.exec(line, shellCwd)
        shellBusy = false
        if (result.stdout.isNotEmpty()) {
            result.stdout.split('\n').forEach { row ->
                appendTerminal(row, LineKind.MUTED)
            }
        }
        if (result.stderr.isNotEmpty()) {
            result.stderr.split('\n').forEach { row ->
                if (row.isNotEmpty()) appendTerminal(row, LineKind.WARN)
            }
        }
        if (!result.ok && !result.error.isNullOrBlank() && result.stdout.isEmpty() && result.stderr.isEmpty()) {
            appendTerminal(result.error!!, LineKind.ERROR)
        }
        appendTerminal(
            "exit ${result.exitCode}",
            if (result.ok) LineKind.OK else LineKind.ERROR,
        )
        shellDraft = ""
        statusMessage = if (result.ok) "终端 · 完成" else "终端 · 退出码 ${result.exitCode}"
        shellFocusToken++
        return result.ok
    }

    private fun promptPrefix(): String {
        val tip = shellCwd.ifEmpty { workspaceRoot?.substringAfterLast('/') ?: "." }
        return if (tip.length <= 24) "$tip " else "…${tip.takeLast(22)} "
    }

    private fun parseCd(line: String): String? {
        if (line == "cd") return ""
        if (!line.startsWith("cd ")) return null
        return line.removePrefix("cd ").trim().trim('"', '\'')
    }

    private suspend fun changeShellCwd(target: String): Boolean {
        when {
            target.isEmpty() || target == "~" -> {
                shellCwd = ""
                return true
            }
            target == "." -> return true
            target.startsWith("/") -> {
                appendTerminal("仅允许工作区内相对路径（用 cd 子目录 / cd ..）", LineKind.ERROR)
                return false
            }
            else -> {
                val next = joinShellPath(shellCwd, target)
                val verify = WorkspaceApi.exec("test -d ${shellQuote(next.ifEmpty { "." })}", "")
                if (!verify.ok) {
                    appendTerminal("无此目录：$target", LineKind.ERROR)
                    return false
                }
                shellCwd = next
                return true
            }
        }
    }

    private fun joinShellPath(base: String, rel: String): String {
        var parts = if (base.isEmpty()) emptyList() else base.split('/').filter { it.isNotEmpty() }
        for (seg in rel.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts = parts.dropLast(1)
                else -> parts = parts + seg
            }
        }
        return parts.joinToString("/")
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
        shellCwd = ""
        shellDraft = ""
        shellBusy = false
        ptyText = ""
        ptyReady = false
        ptyError = null
        ptyAttached = false
        com.lumicode.editor.workspace.PtyApi.disconnect()
        statusMessage = "会话已授权"
        collab.resetRound()
        appendTerminal("[00] 重置会话 ......................... 通过", LineKind.OK)
        appendLog("重置会话")
        if (workspaceMounted) {
            appendTerminal("工作区仍挂载 · ${workspaceRoot ?: "?"}", LineKind.MUTED)
            activePath?.let { open(it) } ?: contents.keys.firstOrNull()?.let { open(it) }
        } else {
            open("src/Main.kt")
        }
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
            shellPage == ShellPage.SQUAD && collab.detailAgentId != null -> collab.closeDetail()
            shellPage == ShellPage.SQUAD -> openCodePage()
            else -> openOverlay(OverlayMode.OVERVIEW)
        }
    }

    fun quickOpenTargets(): List<String> = contents.keys.sorted()

    private companion object {
        val TODO_REGEX = Regex("\\b(TODO|FIXME|XXX|HACK)\\b")
        const val KEY_EXPLORER = "panelExplorer"
        const val KEY_REFERENCE = "panelReference"
        const val KEY_OUTPUT = "panelOutput"
        const val KEY_WORKSPACE = "workspaceRoot"
    }
}
