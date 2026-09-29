package com.lumicode.editor.workspace

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.isRegularFile

private val SKIP_DIRS = setOf(
    ".git", ".gradle", ".idea", ".kotlin", "node_modules", "build", "dist", "out", ".cursor",
    ".lumicode",
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

    override suspend fun createSnapshot(): WorkspaceSnapshotResult {
        val base = root?.takeIf { it.isDirectory }
            ?: return WorkspaceSnapshotResult(ok = false, error = "未打开工作区")
        val snapId = "snap-" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        val snapRoot = snapDir(base, snapId)
        val filesDir = File(snapRoot, "files")
        filesDir.mkdirs()
        val files = listFiles(base)
        var copied = 0
        for (rel in files) {
            val src = safeJoin(base, rel)
            val dst = File(filesDir, rel)
            try {
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
                copied++
            } catch (_: Exception) {
            }
        }
        val manifest = buildString {
            append("{\"id\":")
            append(jsonString(snapId))
            append(",\"root\":")
            append(jsonString(base.absolutePath))
            append(",\"files\":[")
            files.forEachIndexed { i, f ->
                if (i > 0) append(',')
                append(jsonString(f))
            }
            append("]}")
        }
        File(snapRoot, "manifest.json").writeText(manifest, Charsets.UTF_8)
        return WorkspaceSnapshotResult(ok = true, snapshotId = snapId, fileCount = copied)
    }

    override suspend fun diffSnapshot(snapshotId: String): WorkspaceDiffResult {
        val base = root?.takeIf { it.isDirectory }
            ?: return WorkspaceDiffResult(ok = false, error = "未打开工作区")
        val snapRoot = try {
            snapDir(base, snapshotId)
        } catch (e: IllegalArgumentException) {
            return WorkspaceDiffResult(ok = false, error = e.message)
        }
        val manifestFile = File(snapRoot, "manifest.json")
        if (!manifestFile.isFile) return WorkspaceDiffResult(ok = false, error = "snapshot not found")
        val raw = manifestFile.readText(Charsets.UTF_8)
        val before = raw.stringListField("files").toSet()
        val after = listFiles(base).toSet()
        val filesDir = File(snapRoot, "files")
        val changes = mutableListOf<WorkspaceFileChange>()
        for (rel in (after - before).sorted()) {
            changes += WorkspaceFileChange(rel, "added")
        }
        for (rel in (before - after).sorted()) {
            changes += WorkspaceFileChange(rel, "deleted")
        }
        for (rel in (before intersect after).sorted()) {
            val snapFile = File(filesDir, rel)
            val cur = safeJoin(base, rel)
            val changed = try {
                !snapFile.isFile || snapFile.readBytes().contentEquals(cur.readBytes()).not()
            } catch (_: Exception) {
                true
            }
            if (changed) changes += WorkspaceFileChange(rel, "modified")
        }
        return WorkspaceDiffResult(ok = true, snapshotId = snapshotId, changes = changes)
    }

    override suspend fun restoreSnapshot(snapshotId: String): WorkspaceRestoreResult {
        val base = root?.takeIf { it.isDirectory }
            ?: return WorkspaceRestoreResult(ok = false, error = "未打开工作区")
        val snapRoot = try {
            snapDir(base, snapshotId)
        } catch (e: IllegalArgumentException) {
            return WorkspaceRestoreResult(ok = false, error = e.message)
        }
        val manifestFile = File(snapRoot, "manifest.json")
        if (!manifestFile.isFile) return WorkspaceRestoreResult(ok = false, error = "snapshot not found")
        val before = manifestFile.readText(Charsets.UTF_8).stringListField("files")
        val beforeSet = before.toSet()
        val after = listFiles(base).toSet()
        val filesDir = File(snapRoot, "files")
        var restored = 0
        var deleted = 0
        for (rel in (after - beforeSet).sorted()) {
            val target = safeJoin(base, rel)
            if (target.isFile && target.delete()) {
                deleted++
                var parent = target.parentFile
                while (parent != null && parent != base && parent.list()?.isEmpty() == true) {
                    if (!parent.delete()) break
                    parent = parent.parentFile
                }
            }
        }
        for (rel in before) {
            val src = File(filesDir, rel)
            if (!src.isFile) continue
            val dst = safeJoin(base, rel)
            try {
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
                restored++
            } catch (_: Exception) {
            }
        }
        return WorkspaceRestoreResult(
            ok = true,
            snapshotId = snapshotId,
            restored = restored,
            deleted = deleted,
        )
    }

    override suspend fun forgetSnapshot(snapshotId: String): WorkspaceOp {
        val base = root?.takeIf { it.isDirectory }
            ?: return WorkspaceOp(ok = false, error = "未打开工作区")
        val snapRoot = try {
            snapDir(base, snapshotId)
        } catch (e: IllegalArgumentException) {
            return WorkspaceOp(ok = false, error = e.message)
        }
        if (!snapRoot.exists()) return WorkspaceOp(ok = true)
        return if (snapRoot.deleteRecursively()) WorkspaceOp(ok = true)
        else WorkspaceOp(ok = false, error = "forget failed")
    }

    override suspend fun fileDiff(snapshotId: String, relPath: String): WorkspaceFileDiffResult {
        val base = root?.takeIf { it.isDirectory }
            ?: return WorkspaceFileDiffResult(ok = false, error = "未打开工作区")
        val snapRoot = try {
            snapDir(base, snapshotId)
        } catch (e: IllegalArgumentException) {
            return WorkspaceFileDiffResult(ok = false, error = e.message)
        }
        if (!File(snapRoot, "manifest.json").isFile) {
            return WorkspaceFileDiffResult(ok = false, error = "snapshot not found")
        }
        val rel = relPath.replace('\\', '/').trimStart('/')
        val beforePath = File(File(snapRoot, "files"), rel)
        val afterPath = try {
            safeJoin(base, rel)
        } catch (e: IllegalArgumentException) {
            return WorkspaceFileDiffResult(ok = false, error = e.message)
        }
        val before = try {
            if (beforePath.isFile) beforePath.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
        val after = try {
            if (afterPath.isFile) afterPath.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
        val kind = when {
            before == null && after != null -> "added"
            before != null && after == null -> "deleted"
            before == after -> "same"
            else -> "modified"
        }
        return WorkspaceFileDiffResult(
            ok = true,
            snapshotId = snapshotId,
            path = rel,
            kind = kind,
            before = before,
            after = after,
        )
    }

    override suspend fun gitStatus(): GitStatusResult {
        val base = root?.takeIf { it.isDirectory }
            ?: return GitStatusResult(ok = false, error = "未打开工作区")
        if (!File(base, ".git").exists()) {
            return GitStatusResult(ok = false, error = "not a git repository")
        }
        val result = exec(
            "git status --porcelain=v1 -uall && echo '---' && git rev-parse --abbrev-ref HEAD && git log -1 --oneline",
        )
        val out = result.stdout + result.stderr
        val parts = out.split("---", limit = 2)
        val porcelain = parts[0]
        val meta = if (parts.size > 1) parts[1].trim().lines() else emptyList()
        val branch = meta.getOrNull(0)?.trim().orEmpty()
        val head = meta.getOrNull(1)?.trim().orEmpty()
        val files = mutableListOf<GitFileEntry>()
        for (line in porcelain.lines()) {
            if (line.length < 4) continue
            val code = line.take(2)
            var path = line.drop(3).trim()
            if (" -> " in path) path = path.substringAfter(" -> ")
            val kind = when {
                code.trim() == "??" -> "untracked"
                code[0] == 'A' || code.getOrNull(1) == 'A' -> "added"
                code[0] == 'D' || code.getOrNull(1) == 'D' -> "deleted"
                'R' in code -> "renamed"
                else -> "modified"
            }
            files += GitFileEntry(path = path.replace('\\', '/'), kind = kind, code = code)
        }
        return GitStatusResult(
            ok = true,
            branch = branch,
            head = head,
            files = files,
            clean = files.isEmpty(),
        )
    }

    override suspend fun gitDiff(relPath: String): GitDiffResult {
        val base = root?.takeIf { it.isDirectory }
            ?: return GitDiffResult(ok = false, error = "未打开工作区")
        if (!File(base, ".git").exists()) {
            return GitDiffResult(ok = false, error = "not a git repository")
        }
        val path = relPath.replace('\\', '/').trimStart('/')
        if (path.isNotEmpty()) {
            try {
                safeJoin(base, path)
            } catch (e: IllegalArgumentException) {
                return GitDiffResult(ok = false, error = e.message)
            }
        }
        val cmd = if (path.isEmpty()) {
            "git diff --no-color ; git diff --no-color --cached"
        } else {
            "git diff --no-color -- $path ; git diff --no-color --cached -- $path"
        }
        val result = exec(cmd)
        return GitDiffResult(
            ok = true,
            path = path,
            diff = (result.stdout + result.stderr).take(400_000),
        )
    }

    override suspend fun gitCommit(message: String): GitCommitResult {
        val msg = message.trim()
        if (msg.isEmpty()) return GitCommitResult(ok = false, error = "empty commit message")
        val base = root?.takeIf { it.isDirectory }
            ?: return GitCommitResult(ok = false, error = "未打开工作区")
        if (!File(base, ".git").exists()) {
            return GitCommitResult(ok = false, error = "not a git repository")
        }
        val add = exec("git add -A")
        if (!add.ok) {
            return GitCommitResult(ok = false, error = add.stderr.ifBlank { add.error ?: "git add failed" })
        }
        val escaped = msg.replace("'", "'\\''")
        val commit = exec("git commit -m '$escaped'")
        return GitCommitResult(
            ok = commit.ok,
            message = msg,
            stdout = commit.stdout,
            error = if (commit.ok) null else commit.stderr.ifBlank { commit.error },
        )
    }

    private fun snapDir(workspaceRoot: File, snapId: String): File {
        val sid = snapId.trim()
        require(sid.matches(Regex("""snap-[a-f0-9]{12}"""))) { "invalid snapshot id" }
        val key = java.security.MessageDigest.getInstance("SHA-1")
            .digest(workspaceRoot.canonicalPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(b) }
            .take(16)
        val cache = File(System.getProperty("user.home"), ".cache/lumicode/snapshots/$key/$sid")
        return cache
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
