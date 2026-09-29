package com.lumicode.editor.workspace

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.isRegularFile

private val SKIP_DIRS = setOf(
    ".git", ".gradle", ".idea", ".kotlin", "node_modules", "build", "dist", "out", ".cursor",
)

private val TEXT_EXT = setOf(
    ".kt", ".kts", ".java", ".gradle", ".json", ".md", ".txt", ".xml", ".yaml", ".yml",
    ".toml", ".properties", ".html", ".css", ".js", ".mjs", ".ts", ".tsx", ".jsx", ".py",
    ".sh", ".rs", ".go", ".c", ".h", ".cpp", ".hpp", ".sql", ".gitignore", ".env",
)

private const val MAX_BYTES = 512 * 1024

class DesktopWorkspaceBackend : WorkspaceBackend {
    private var root: File? = loadSavedRoot()

    override suspend fun status(): WorkspaceStatus {
        val r = root?.takeIf { it.isDirectory } ?: return WorkspaceStatus(ok = false)
        return WorkspaceStatus(ok = true, root = r.absolutePath, fileCount = listFiles(r).size)
    }

    override suspend fun openRoot(path: String): WorkspaceStatus {
        val dir = File(path.trim()).canonicalFile
        if (!dir.isDirectory) return WorkspaceStatus(ok = false, error = "不是文件夹：${dir.path}")
        root = dir
        saveRoot(dir)
        return WorkspaceStatus(ok = true, root = dir.absolutePath, fileCount = listFiles(dir).size)
    }

    override suspend fun browse(absPath: String): DirListing {
        val target = when {
            absPath.isBlank() -> File(System.getProperty("user.home"))
            else -> File(absPath.trim()).canonicalFile
        }
        if (!target.isDirectory) return DirListing(ok = false, error = "不是文件夹")
        val entries = target.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.map { DirEntry(it.name, it.absolutePath) }
            .orEmpty()
        val parent = target.parentFile?.absolutePath.orEmpty()
        return DirListing(ok = true, current = target.absolutePath, parent = parent, entries = entries)
    }

    override suspend fun listDirs(subPath: String): DirListing {
        val base = root?.takeIf { it.isDirectory } ?: return DirListing(ok = false, error = "未打开工作区")
        val target = safeJoin(base, subPath)
        if (!target.isDirectory) return DirListing(ok = false, error = "不是文件夹")
        val entries = target.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.map { DirEntry(it.name, rel(base, it)) }
            .orEmpty()
        val parent = if (target == base) "" else rel(base, target.parentFile ?: base)
        return DirListing(ok = true, current = rel(base, target), parent = parent, entries = entries)
    }

    override suspend fun loadTree(): WorkspaceTree {
        val base = root?.takeIf { it.isDirectory } ?: return WorkspaceTree(ok = false, error = "未打开工作区")
        val files = listFiles(base)
        val folders = mutableSetOf<String>()
        files.forEach { rel ->
            var folder = File(rel).parent?.replace('\\', '/') ?: ""
            while (folder.isNotEmpty()) {
                folders += folder
                folder = File(folder).parent?.replace('\\', '/') ?: ""
            }
        }
        return WorkspaceTree(ok = true, root = base.absolutePath, files = files, folders = folders.sorted())
    }

    override suspend fun readFile(relPath: String): WorkspaceFile {
        val base = root ?: return WorkspaceFile(ok = false, error = "未打开工作区")
        val file = safeJoin(base, relPath)
        if (!file.isFile) return WorkspaceFile(ok = false, error = "不是文件")
        if (file.length() > MAX_BYTES) return WorkspaceFile(ok = false, error = "文件过大")
        val content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
        return WorkspaceFile(ok = true, path = relPath, content = content)
    }

    override suspend fun writeFile(relPath: String, content: String): WorkspaceOp {
        val base = root ?: return WorkspaceOp(ok = false, error = "未打开工作区")
        val file = safeJoin(base, relPath)
        file.parentFile?.mkdirs()
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8)
        return WorkspaceOp(ok = true)
    }

    override suspend fun mkdir(relPath: String): WorkspaceOp {
        val base = root ?: return WorkspaceOp(ok = false, error = "未打开工作区")
        val dir = safeJoin(base, relPath)
        if (!dir.mkdirs() && !dir.isDirectory) return WorkspaceOp(ok = false, error = "创建失败")
        return WorkspaceOp(ok = true)
    }

    override suspend fun mkdirAbs(parent: String, name: String): WorkspaceOp {
        val folder = name.trim()
        if (folder.isEmpty() || folder == "." || folder == ".." ||
            folder.contains('/') || folder.contains('\\')
        ) {
            return WorkspaceOp(ok = false, error = "文件夹名无效")
        }
        val parentDir = File(parent.trim().ifEmpty { System.getProperty("user.home") }).canonicalFile
        if (!parentDir.isDirectory) return WorkspaceOp(ok = false, error = "父目录不存在")
        val target = File(parentDir, folder).canonicalFile
        if (!target.path.startsWith(parentDir.path + File.separator) && target != parentDir) {
            return WorkspaceOp(ok = false, error = "路径越界")
        }
        if (target.exists()) return WorkspaceOp(ok = false, error = "已存在同名项")
        return if (target.mkdir()) WorkspaceOp(ok = true) else WorkspaceOp(ok = false, error = "创建失败")
    }

    override suspend fun rename(from: String, to: String): WorkspaceOp {
        val base = root ?: return WorkspaceOp(ok = false, error = "未打开工作区")
        val src = safeJoin(base, from)
        val dst = safeJoin(base, to)
        if (!src.exists()) return WorkspaceOp(ok = false, error = "源不存在")
        if (dst.exists()) return WorkspaceOp(ok = false, error = "目标已存在")
        dst.parentFile?.mkdirs()
        return if (src.renameTo(dst)) WorkspaceOp(ok = true) else WorkspaceOp(ok = false, error = "重命名失败")
    }

    override suspend fun delete(relPath: String): WorkspaceOp {
        val base = root ?: return WorkspaceOp(ok = false, error = "未打开工作区")
        val target = safeJoin(base, relPath)
        if (!target.exists()) return WorkspaceOp(ok = false, error = "不存在")
        return when {
            target.isDirectory -> if (target.delete()) WorkspaceOp(ok = true) else WorkspaceOp(ok = false, error = "文件夹非空或无法删除")
            else -> if (target.delete()) WorkspaceOp(ok = true) else WorkspaceOp(ok = false, error = "删除失败")
        }
    }

    override suspend fun exec(command: String, cwd: String): ShellExecResult {
        val base = root ?: return ShellExecResult(ok = false, error = "未打开工作区")
        val cmd = command.trim()
        if (cmd.isEmpty()) return ShellExecResult(ok = false, error = "empty command")
        val work = try {
            if (cwd.isBlank()) base else safeJoin(base, cwd)
        } catch (e: IllegalArgumentException) {
            return ShellExecResult(ok = false, error = e.message)
        }
        if (!work.isDirectory) return ShellExecResult(ok = false, error = "cwd not a directory")
        val shell = System.getenv("SHELL") ?: "/bin/bash"
        return try {
            val process = ProcessBuilder(shell, "-lc", cmd)
                .directory(work)
                .redirectErrorStream(false)
                .start()
            val finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellExecResult(
                    ok = false,
                    exitCode = -1,
                    error = "timeout after 60s",
                    cwd = rel(base, work),
                )
            }
            val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).readText().take(200 * 1024)
            val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).readText().take(200 * 1024)
            val code = process.exitValue()
            ShellExecResult(
                ok = code == 0,
                exitCode = code,
                stdout = stdout,
                stderr = stderr,
                cwd = rel(base, work),
            )
        } catch (e: Exception) {
            ShellExecResult(ok = false, error = e.message ?: "exec failed", cwd = rel(base, work))
        }
    }

    private fun rel(base: File, file: File): String {
        val p = base.toPath().relativize(file.toPath()).toString().replace('\\', '/')
        return if (p == ".") "" else p
    }

    private fun safeJoin(base: File, rel: String): File {
        val cleaned = rel.trim().trimStart('/').replace('\\', '/')
        if (".." in cleaned.split('/')) throw IllegalArgumentException("path escape")
        val target = File(base, cleaned).canonicalFile
        val baseCanon = base.canonicalFile
        if (target != baseCanon && !target.path.startsWith(baseCanon.path + File.separator)) {
            throw IllegalArgumentException("path escape")
        }
        return target
    }

    private fun listFiles(base: File): List<String> {
        val out = mutableListOf<String>()
        base.walkTopDown()
            .onEnter { dir -> dir.name !in SKIP_DIRS && !dir.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                val ext = file.extension.lowercase().let { if (it.isEmpty()) "" else ".$it" }
                if (ext.isNotEmpty() && ext !in TEXT_EXT) return@forEach
                if (file.length() > MAX_BYTES) return@forEach
                out += rel(base, file)
            }
        return out.sorted()
    }

    private fun loadSavedRoot(): File? {
        val path = configFile().takeIf { it.isRegularFile() }?.let {
            Files.readString(it, StandardCharsets.UTF_8).trim()
        }.orEmpty()
        val dir = File(path).takeIf { it.isDirectory } ?: return null
        return dir.canonicalFile
    }

    private fun saveRoot(dir: File) {
        val cfg = configFile()
        cfg.parent?.toFile()?.mkdirs()
        Files.writeString(cfg, dir.absolutePath + "\n", StandardCharsets.UTF_8)
    }

    private fun configFile() =
        File(System.getProperty("user.home"), ".config/lumicode/workspace-root.txt").toPath()
}

fun installDesktopWorkspaceBackend() {
    WorkspaceApi.backend = DesktopWorkspaceBackend()
}
