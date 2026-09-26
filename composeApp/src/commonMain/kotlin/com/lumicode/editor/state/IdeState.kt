package com.lumicode.editor.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumicode.editor.model.CodeFile
import com.lumicode.editor.model.FileNode
import com.lumicode.editor.model.buildTree
import com.lumicode.editor.platform.clockLabel
import com.lumicode.editor.platform.platformLabel

enum class OverlayMode { NONE, COMMAND_INDEX, QUICK_OPEN, SETTINGS, OVERVIEW }

enum class LineKind { INFO, OK, WARN, ERROR, MUTED }

/** id 是单调递增的序号：列表动画靠它认人，不靠下标（下标会随 takeLast 整体平移）。 */
data class TerminalLine(val id: Long, val time: String, val text: String, val kind: LineKind)

data class Problem(val path: String, val line: Int, val column: Int, val message: String, val severity: LineKind)

data class LogEntry(val id: Long, val time: String, val text: String)

/**
 * The whole editor state tree. Plain Compose state — no platform dependencies,
 * so the same instance drives Android, desktop and wasm.
 */
class IdeState(initialFiles: List<CodeFile>) {

    private val initialCatalogue: Map<String, CodeFile> = initialFiles.associateBy { it.path }
    private val catalogue = mutableStateMapOf<String, CodeFile>().apply { putAll(initialCatalogue) }
    private val contents = mutableStateMapOf<String, String>()
    private val savedSnapshot = mutableStateMapOf<String, String>()

    var tree by mutableStateOf(buildTree(initialFiles))
        private set

    val openTabs = mutableStateListOf<String>()
    var activePath by mutableStateOf<String?>(null)

    var explorerVisible by mutableStateOf(true)
    var outputVisible by mutableStateOf(true)
    var referenceVisible by mutableStateOf(true)

    /** Set once the shell detects a narrow (phone) viewport. */
    var compactApplied by mutableStateOf(false)

    var overlay by mutableStateOf(OverlayMode.NONE)
    var overlayQuery by mutableStateOf("")

    var findVisible by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var findActiveMatch by mutableStateOf(0)

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

    private val expanded = mutableStateMapOf<String, Boolean>()
    private var untitledCounter = 0

    /** 终端行 / 日志条目的单调序号，供列表入场动画做稳定的 key。 */
    private var entrySeq = 0L

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
    }

    fun open(path: String, revealLine: Int? = null) {
        if (!contents.containsKey(path)) return
        if (!openTabs.contains(path)) openTabs.add(path)
        activePath = path
        pendingRevealLine = revealLine
        findActiveMatch = 0
        cursorLine = revealLine ?: 1
        cursorColumn = 1
        appendLog("打开 ${path.substringAfterLast('/')}")
        if (overlay != OverlayMode.NONE) overlay = OverlayMode.NONE
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

    /** Cursor offset of the active document, owned by the editor composable. */
    var cursorOffset by mutableStateOf(0)

    fun newFile() {
        untitledCounter++
        val name = "untitled-${untitledCounter.toString().padStart(2, '0')}.kt"
        var path = "src/$name"
        while (catalogue.containsKey(path)) {
            untitledCounter++
            path = "src/untitled-${untitledCounter.toString().padStart(2, '0')}.kt"
        }
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
        tree = buildTree(catalogue.values.toList())
        open(path)
        appendTerminal("[++] 已创建 $path", LineKind.INFO)
        appendLog("新建 ${path.substringAfterLast('/')}")
    }

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
        terminal.add(TerminalLine(entrySeq++, clockLabel(), text, kind))
        while (terminal.size > 200) terminal.removeAt(0)
    }

    fun appendLog(text: String) {
        log.add(LogEntry(entrySeq++, clockLabel(), text))
        while (log.size > 60) log.removeAt(0)
    }

    /** Restores the pristine archive — wired to REINITIALIZE in the status bar. */
    fun softReset() {
        openTabs.clear()
        catalogue.clear()
        catalogue.putAll(initialCatalogue)
        contents.clear()
        savedSnapshot.clear()
        initialCatalogue.forEach { (path, file) ->
            contents[path] = file.content
            savedSnapshot[path] = file.content
        }
        tree = buildTree(initialCatalogue.values.toList())
        terminal.clear()
        log.clear()
        problems = emptyList()
        savedCount = 0
        untitledCounter = 0
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

    /** ESC 的分级行为：关浮层 → 关查找 → 关控制台 → 打开工作区总览。 */
    fun handleEscape() {
        when {
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
