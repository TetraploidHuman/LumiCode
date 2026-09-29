package com.lumicode.editor.workspace

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.suspendCancellableCoroutine

@JsFun(
    """
    (url, method, body) => fetch(url, {
        method: method,
        headers: body == null ? undefined : { 'Content-Type': 'application/json' },
        body: body ?? undefined
    }).then(r => r.text())
    """,
)
private external fun fetchTextJs(url: String, method: String, body: String?): Promise<JsString>

@JsFun("(s) => encodeURIComponent(s)")
private external fun encodeURIComponentJs(s: String): String

private suspend fun Promise<JsString>.awaitText(): String =
    suspendCancellableCoroutine { cont ->
        then(
            onFulfilled = { value ->
                cont.resume(value.toString())
                null
            },
            onRejected = { err ->
                cont.resumeWithException(IllegalStateException(err?.toString() ?: "fetch failed"))
                null
            },
        )
    }

class WasmWorkspaceBackend : WorkspaceBackend {
    override suspend fun status(): WorkspaceStatus =
        parseWorkspaceStatus(fetchTextJs("$BASE/status", "GET", null).awaitText())

    override suspend fun openRoot(path: String): WorkspaceStatus {
        val body = """{"path":${jsonString(path)}}"""
        return parseWorkspaceStatus(fetchTextJs("$BASE/open", "POST", body).awaitText())
    }

    override suspend fun browse(absPath: String): DirListing {
        val q = if (absPath.isEmpty()) "" else "?path=${encodeURIComponentJs(absPath)}"
        return parseDirListing(fetchTextJs("$BASE/browse$q", "GET", null).awaitText())
    }

    override suspend fun listDirs(subPath: String): DirListing {
        val q = if (subPath.isEmpty()) "" else "?path=${encodeURIComponentJs(subPath)}"
        return parseDirListing(fetchTextJs("$BASE/list-dirs$q", "GET", null).awaitText())
    }

    override suspend fun loadTree(): WorkspaceTree =
        parseWorkspaceTree(fetchTextJs("$BASE/tree", "GET", null).awaitText())

    override suspend fun readFile(relPath: String): WorkspaceFile =
        parseWorkspaceFile(fetchTextJs("$BASE/read?path=${encodeURIComponentJs(relPath)}", "GET", null).awaitText())

    override suspend fun writeFile(relPath: String, content: String): WorkspaceOp {
        val body = buildString {
            append("{\"path\":")
            append(jsonString(relPath))
            append(",\"content\":")
            append(jsonString(content))
            append('}')
        }
        return parseWorkspaceOp(fetchTextJs("$BASE/write", "PUT", body).awaitText())
    }

    override suspend fun mkdir(relPath: String): WorkspaceOp {
        val body = """{"path":${jsonString(relPath)}}"""
        return parseWorkspaceOp(fetchTextJs("$BASE/mkdir", "POST", body).awaitText())
    }

    override suspend fun mkdirAbs(parent: String, name: String): WorkspaceOp {
        val body = buildString {
            append("{\"parent\":")
            append(jsonString(parent))
            append(",\"name\":")
            append(jsonString(name))
            append('}')
        }
        return parseWorkspaceOp(fetchTextJs("$BASE/mkdir-abs", "POST", body).awaitText())
    }

    override suspend fun rename(from: String, to: String): WorkspaceOp {
        val body = """{"from":${jsonString(from)},"to":${jsonString(to)}}"""
        return parseWorkspaceOp(fetchTextJs("$BASE/rename", "POST", body).awaitText())
    }

    override suspend fun delete(relPath: String): WorkspaceOp =
        parseWorkspaceOp(fetchTextJs("$BASE/delete?path=${encodeURIComponentJs(relPath)}", "DELETE", null).awaitText())

    override suspend fun exec(command: String, cwd: String): ShellExecResult {
        val body = buildString {
            append("{\"command\":")
            append(jsonString(command))
            append(",\"cwd\":")
            append(jsonString(cwd))
            append('}')
        }
        return parseShellExec(fetchTextJs("$BASE/exec", "POST", body).awaitText())
    }

    override suspend fun createSnapshot(): WorkspaceSnapshotResult =
        parseWorkspaceSnapshot(fetchTextJs("$BASE/snapshot/create", "POST", "{}").awaitText())

    override suspend fun diffSnapshot(snapshotId: String): WorkspaceDiffResult =
        parseWorkspaceDiff(
            fetchTextJs(
                "$BASE/snapshot/diff?id=${encodeURIComponentJs(snapshotId)}",
                "GET",
                null,
            ).awaitText(),
        )

    override suspend fun restoreSnapshot(snapshotId: String): WorkspaceRestoreResult {
        val body = """{"id":${jsonString(snapshotId)}}"""
        return parseWorkspaceRestore(fetchTextJs("$BASE/snapshot/restore", "POST", body).awaitText())
    }

    override suspend fun forgetSnapshot(snapshotId: String): WorkspaceOp {
        val body = """{"id":${jsonString(snapshotId)}}"""
        return parseWorkspaceOp(fetchTextJs("$BASE/snapshot/forget", "POST", body).awaitText())
    }

    override suspend fun fileDiff(snapshotId: String, relPath: String): WorkspaceFileDiffResult =
        parseWorkspaceFileDiff(
            fetchTextJs(
                "$BASE/snapshot/file-diff?id=${encodeURIComponentJs(snapshotId)}&path=${encodeURIComponentJs(relPath)}",
                "GET",
                null,
            ).awaitText(),
        )

    override suspend fun gitStatus(): GitStatusResult =
        parseGitStatus(fetchTextJs("$BASE/git/status", "GET", null).awaitText())

    override suspend fun gitDiff(relPath: String): GitDiffResult {
        val q = if (relPath.isBlank()) "" else "?path=${encodeURIComponentJs(relPath)}"
        return parseGitDiff(fetchTextJs("$BASE/git/diff$q", "GET", null).awaitText())
    }

    override suspend fun gitCommit(message: String): GitCommitResult {
        val body = """{"message":${jsonString(message)}}"""
        return parseGitCommit(fetchTextJs("$BASE/git/commit", "POST", body).awaitText())
    }

    private companion object {
        val BASE: String get() = "${appBasePath()}/api/workspace"
    }
}

fun installWasmWorkspaceBackend() {
    WorkspaceApi.backend = WasmWorkspaceBackend()
}
