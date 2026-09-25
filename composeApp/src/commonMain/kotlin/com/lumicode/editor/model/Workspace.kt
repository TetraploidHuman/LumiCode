package com.lumicode.editor.model

/** Languages the demo editor can highlight. */
enum class Language(val label: String, val short: String, val extension: String) {
    KOTLIN("Kotlin", "KT", "kt"),
    GRADLE("Gradle Kotlin DSL", "GRADLE", "kts"),
    JSON("JSON", "JSON", "json"),
    MARKDOWN("Markdown", "MD", "md"),
    TEXT("Plain Text", "TXT", "txt"),
    ;

    companion object {
        fun fromName(name: String): Language = when {
            name.endsWith(".kts") -> GRADLE
            name.endsWith(".kt") -> KOTLIN
            name.endsWith(".json") -> JSON
            name.endsWith(".md") -> MARKDOWN
            else -> TEXT
        }
    }
}

/** Archival metadata shown in the right-hand REFERENCE AREA, mirroring the poster layout. */
data class FileMeta(
    val archiveNo: String,
    val department: String,
    val departmentCn: String,
    val collection: String,
    val collectionCn: String,
    val related: String,
    val status: String,
    val statusCn: String,
    val abstract: String,
)

/** A single file inside the virtual workspace. */
data class CodeFile(
    val path: String,
    val content: String,
    val meta: FileMeta,
) {
    val name: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/', "")
    val language: Language get() = Language.fromName(name)
}

/** Tree node used by the explorer; folders are synthesised from file paths. */
data class FileNode(
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val children: List<FileNode> = emptyList(),
) {
    val fileCount: Int get() = if (isFolder) children.sumOf { if (it.isFolder) it.fileCount else 1 } else 1
}

fun buildTree(files: List<CodeFile>): List<FileNode> {
    val root = LinkedHashMap<String, MutableList<CodeFile>>()
    for (file in files) {
        val folder = file.folder
        root.getOrPut(folder) { mutableListOf() }.add(file)
    }

    fun nodesFor(prefix: String): List<FileNode> {
        val out = mutableListOf<FileNode>()
        val direct = root[prefix].orEmpty()
        out += direct.sortedBy { it.name }.map { FileNode(it.name, it.path, false) }

        val childFolders = root.keys
            .filter { it.isNotEmpty() && it.startsWith(if (prefix.isEmpty()) "" else "$prefix/") && it != prefix }
            .map { rest ->
                val trimmed = if (prefix.isEmpty()) rest else rest.removePrefix("$prefix/")
                trimmed.substringBefore('/')
            }
            .distinct()
            .sorted()

        for (child in childFolders) {
            val childPath = if (prefix.isEmpty()) child else "$prefix/$child"
            out += FileNode(child, childPath, true, nodesFor(childPath))
        }
        // folders first, then files — archive order
        return out.sortedWith(compareByDescending<FileNode> { it.isFolder }.thenBy { it.name })
    }

    return nodesFor("")
}
