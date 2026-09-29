package com.lumicode.editor.workspace

data class WorkspaceStatus(
    val ok: Boolean,
    val root: String? = null,
    val fileCount: Int = 0,
    val error: String? = null,
)

data class WorkspaceTree(
    val ok: Boolean,
    val root: String? = null,
    val files: List<String> = emptyList(),
    val folders: List<String> = emptyList(),
    val error: String? = null,
)

data class WorkspaceFile(
    val ok: Boolean,
    val path: String? = null,
    val content: String? = null,
    val error: String? = null,
)

data class WorkspaceOp(
    val ok: Boolean,
    val error: String? = null,
)

data class DirEntry(
    val name: String,
    val path: String,
)

data class DirListing(
    val ok: Boolean,
    val current: String = "",
    val parent: String = "",
    val entries: List<DirEntry> = emptyList(),
    val error: String? = null,
)

data class ShellExecResult(
    val ok: Boolean,
    val exitCode: Int = -1,
    val stdout: String = "",
    val stderr: String = "",
    val cwd: String = "",
    val error: String? = null,
)

/** One file delta vs a pre-task workspace snapshot. */
data class WorkspaceFileChange(
    val path: String,
    /** added | modified | deleted */
    val kind: String,
)

data class WorkspaceSnapshotResult(
    val ok: Boolean,
    val snapshotId: String? = null,
    val fileCount: Int = 0,
    val error: String? = null,
)

data class WorkspaceDiffResult(
    val ok: Boolean,
    val snapshotId: String? = null,
    val changes: List<WorkspaceFileChange> = emptyList(),
    val error: String? = null,
)

data class WorkspaceRestoreResult(
    val ok: Boolean,
    val snapshotId: String? = null,
    val restored: Int = 0,
    val deleted: Int = 0,
    val error: String? = null,
)

interface WorkspaceBackend {
    suspend fun status(): WorkspaceStatus
    suspend fun openRoot(path: String): WorkspaceStatus
    suspend fun browse(absPath: String = ""): DirListing
    suspend fun listDirs(subPath: String = ""): DirListing
    suspend fun loadTree(): WorkspaceTree
    suspend fun readFile(relPath: String): WorkspaceFile
    suspend fun writeFile(relPath: String, content: String): WorkspaceOp
    suspend fun mkdir(relPath: String): WorkspaceOp
    /** 浏览态：在绝对路径父目录下新建文件夹。 */
    suspend fun mkdirAbs(parent: String, name: String): WorkspaceOp
    suspend fun rename(from: String, to: String): WorkspaceOp
    suspend fun delete(relPath: String): WorkspaceOp
    suspend fun exec(command: String, cwd: String = ""): ShellExecResult
    suspend fun createSnapshot(): WorkspaceSnapshotResult
    suspend fun diffSnapshot(snapshotId: String): WorkspaceDiffResult
    suspend fun restoreSnapshot(snapshotId: String): WorkspaceRestoreResult
    suspend fun forgetSnapshot(snapshotId: String): WorkspaceOp
}

object WorkspaceApi {
    var backend: WorkspaceBackend? = null

    suspend fun status(): WorkspaceStatus =
        backend?.status() ?: WorkspaceStatus(ok = false, error = "工作区后端未安装")

    suspend fun openRoot(path: String): WorkspaceStatus =
        backend?.openRoot(path) ?: WorkspaceStatus(ok = false, error = "工作区后端未安装")

    suspend fun browse(absPath: String = ""): DirListing =
        backend?.browse(absPath) ?: DirListing(ok = false, error = "工作区后端未安装")

    suspend fun listDirs(subPath: String = ""): DirListing =
        backend?.listDirs(subPath) ?: DirListing(ok = false, error = "工作区后端未安装")

    suspend fun loadTree(): WorkspaceTree =
        backend?.loadTree() ?: WorkspaceTree(ok = false, error = "工作区后端未安装")

    suspend fun readFile(relPath: String): WorkspaceFile =
        backend?.readFile(relPath) ?: WorkspaceFile(ok = false, error = "工作区后端未安装")

    suspend fun writeFile(relPath: String, content: String): WorkspaceOp =
        backend?.writeFile(relPath, content) ?: WorkspaceOp(ok = false, error = "工作区后端未安装")

    suspend fun mkdir(relPath: String): WorkspaceOp =
        backend?.mkdir(relPath) ?: WorkspaceOp(ok = false, error = "工作区后端未安装")

    suspend fun mkdirAbs(parent: String, name: String): WorkspaceOp =
        backend?.mkdirAbs(parent, name) ?: WorkspaceOp(ok = false, error = "工作区后端未安装")

    suspend fun rename(from: String, to: String): WorkspaceOp =
        backend?.rename(from, to) ?: WorkspaceOp(ok = false, error = "工作区后端未安装")

    suspend fun delete(relPath: String): WorkspaceOp =
        backend?.delete(relPath) ?: WorkspaceOp(ok = false, error = "工作区后端未安装")

    suspend fun exec(command: String, cwd: String = ""): ShellExecResult =
        backend?.exec(command, cwd)
            ?: ShellExecResult(ok = false, error = "工作区后端未安装")

    suspend fun createSnapshot(): WorkspaceSnapshotResult =
        backend?.createSnapshot()
            ?: WorkspaceSnapshotResult(ok = false, error = "工作区后端未安装")

    suspend fun diffSnapshot(snapshotId: String): WorkspaceDiffResult =
        backend?.diffSnapshot(snapshotId)
            ?: WorkspaceDiffResult(ok = false, error = "工作区后端未安装")

    suspend fun restoreSnapshot(snapshotId: String): WorkspaceRestoreResult =
        backend?.restoreSnapshot(snapshotId)
            ?: WorkspaceRestoreResult(ok = false, error = "工作区后端未安装")

    suspend fun forgetSnapshot(snapshotId: String): WorkspaceOp =
        backend?.forgetSnapshot(snapshotId) ?: WorkspaceOp(ok = false, error = "工作区后端未安装")
}

fun parseWorkspaceStatus(raw: String): WorkspaceStatus =
    WorkspaceStatus(
        ok = raw.boolField("ok"),
        root = raw.stringField("root"),
        fileCount = raw.intField("fileCount") ?: 0,
        error = raw.stringField("error"),
    )

fun parseWorkspaceTree(raw: String): WorkspaceTree =
    WorkspaceTree(
        ok = raw.boolField("ok"),
        root = raw.stringField("root"),
        files = raw.stringListField("files"),
        folders = raw.stringListField("folders"),
        error = raw.stringField("error"),
    )

fun parseWorkspaceFile(raw: String): WorkspaceFile =
    WorkspaceFile(
        ok = raw.boolField("ok"),
        path = raw.stringField("path"),
        content = raw.stringField("content"),
        error = raw.stringField("error"),
    )

fun parseWorkspaceOp(raw: String): WorkspaceOp =
    WorkspaceOp(
        ok = raw.boolField("ok"),
        error = raw.stringField("error"),
    )

fun parseDirListing(raw: String): DirListing {
    if (!raw.boolField("ok")) {
        return DirListing(ok = false, error = raw.stringField("error"))
    }
    val entries = mutableListOf<DirEntry>()
    val block = Regex(""""entries"\s*:\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL)
        .find(raw)?.groupValues?.getOrNull(1).orEmpty()
    Regex("""\{[^{}]*}""").findAll(block).forEach { m ->
        val obj = m.value
        val name = obj.stringField("name") ?: return@forEach
        val path = obj.stringField("path") ?: name
        entries += DirEntry(name, path)
    }
    return DirListing(
        ok = true,
        current = raw.stringField("current").orEmpty(),
        parent = raw.stringField("parent").orEmpty(),
        entries = entries,
    )
}

fun parseShellExec(raw: String): ShellExecResult =
    ShellExecResult(
        ok = raw.boolField("ok"),
        exitCode = raw.intField("exitCode") ?: -1,
        stdout = raw.stringField("stdout").orEmpty(),
        stderr = raw.stringField("stderr").orEmpty(),
        cwd = raw.stringField("cwd").orEmpty(),
        error = raw.stringField("error"),
    )

fun parseWorkspaceSnapshot(raw: String): WorkspaceSnapshotResult =
    WorkspaceSnapshotResult(
        ok = raw.boolField("ok"),
        snapshotId = raw.stringField("snapshotId"),
        fileCount = raw.intField("fileCount") ?: 0,
        error = raw.stringField("error"),
    )

fun parseWorkspaceDiff(raw: String): WorkspaceDiffResult {
    if (!raw.boolField("ok")) {
        return WorkspaceDiffResult(ok = false, error = raw.stringField("error"))
    }
    val changes = mutableListOf<WorkspaceFileChange>()
    val block = Regex(""""changes"\s*:\s*\[(.*)]""", RegexOption.DOT_MATCHES_ALL)
        .find(raw)?.groupValues?.getOrNull(1).orEmpty()
    for (obj in block.jsonObjectSlices()) {
        val path = obj.stringField("path") ?: continue
        val kind = obj.stringField("kind") ?: continue
        changes += WorkspaceFileChange(path = path, kind = kind)
    }
    return WorkspaceDiffResult(
        ok = true,
        snapshotId = raw.stringField("snapshotId"),
        changes = changes,
    )
}

fun parseWorkspaceRestore(raw: String): WorkspaceRestoreResult =
    WorkspaceRestoreResult(
        ok = raw.boolField("ok"),
        snapshotId = raw.stringField("snapshotId"),
        restored = raw.intField("restored") ?: 0,
        deleted = raw.intField("deleted") ?: 0,
        error = raw.stringField("error"),
    )
