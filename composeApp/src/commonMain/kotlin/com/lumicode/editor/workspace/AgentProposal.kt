package com.lumicode.editor.workspace

/** Agent 提案里对单个文件的行段替换。行号 1-based，闭区间。 */
data class AgentFileEdit(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
)

data class ParsedAgentReply(
    val summary: String,
    val edits: List<AgentFileEdit>,
)

private val MARKER = "---LUMICODE_EDIT---"

fun parseAgentReply(raw: String): ParsedAgentReply {
    val trimmed = raw.trim()
    val markerIdx = trimmed.indexOf(MARKER)
    val summary = if (markerIdx >= 0) {
        trimmed.substring(0, markerIdx).trim()
    } else {
        trimmed
    }
    val jsonPart = if (markerIdx >= 0) {
        trimmed.substring(markerIdx + MARKER.length).trim()
    } else {
        ""
    }
    val edits = if (jsonPart.isNotEmpty()) parseEditsJson(jsonPart) else emptyList()
    return ParsedAgentReply(summary = summary.ifEmpty { trimmed }, edits = edits)
}

private fun parseEditsJson(json: String): List<AgentFileEdit> {
    val editsKey = """"edits""""
    val at = json.indexOf(editsKey)
    if (at < 0) return emptyList()
    var j = at + editsKey.length
    while (j < json.length && json[j].isWhitespace()) j++
    if (j >= json.length || json[j] != ':') return emptyList()
    j++
    while (j < json.length && json[j].isWhitespace()) j++
    if (j >= json.length || json[j] != '[') return emptyList()
    // 取 edits 数组切片，再用括号感知切对象（content 里常有 `{`/`}`）
    val arrayBody = json.substring(j)
    val out = mutableListOf<AgentFileEdit>()
    for (obj in arrayBody.jsonObjectSlices()) {
        val path = obj.stringField("path") ?: continue
        val start = obj.intField("startLine") ?: obj.intField("start") ?: continue
        val end = obj.intField("endLine") ?: obj.intField("end") ?: start
        val content = obj.stringField("content") ?: ""
        out += AgentFileEdit(path = path, startLine = start, endLine = end, content = content)
    }
    return out
}

fun applyLineEdit(source: String, startLine: Int, endLine: Int, replacement: String): String {
    if (startLine <= 0 || endLine <= 0) return source
    val lines = source.split('\n').toMutableList()
    val startIdx = (startLine - 1).coerceIn(0, lines.size)
    val endIdx = endLine.coerceAtLeast(startLine).coerceIn(0, lines.size)
    val newLines = replacement.split('\n')
    for (i in endIdx - 1 downTo startIdx) {
        if (i in lines.indices) lines.removeAt(i)
    }
    lines.addAll(startIdx, newLines)
    return lines.joinToString("\n")
}

fun buildAgentPromptFooter(): String = buildString {
    appendLine()
    appendLine("回复格式要求：")
    appendLine("1) 先用中文写 3–6 句给人看的提案摘要。")
    appendLine("2) 然后单独一行写 $MARKER")
    appendLine("3) 紧跟 JSON（不要 markdown 围栏）：")
    appendLine("""{"edits":[{"path":"相对路径","startLine":1,"endLine":1,"content":"替换后的完整行段内容"}]}""")
    append("若仅建议、无需改文件，edits 可为空数组 []。")
}
