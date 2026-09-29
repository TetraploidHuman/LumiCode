package com.lumicode.editor.workspace

import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop interactive shell via util-linux `script` (allocates a PTY without JNI).
 * Event protocol matches [PtyApi] / Wasm WebSocket backend.
 */
class DesktopPtyBackend : PtyBackend {
    override var listener: ((PtyEvent) -> Unit)? = null

    private val lock = Any()
    private var process: Process? = null
    private var stdin: OutputStreamWriter? = null
    private val alive = AtomicBoolean(false)

    override fun connect(cwd: String, cols: Int, rows: Int) {
        disconnectInternal(emitClosed = false)
        val work = resolveWorkDir(cwd)
        if (work == null) {
            listener?.invoke(PtyEvent.Error("请先打开工作区"))
            return
        }
        val shell = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/bash"
        val scriptBin = findExecutable("script")
        if (scriptBin == null) {
            listener?.invoke(PtyEvent.Error("未找到 script（util-linux），无法分配伪终端"))
            return
        }
        try {
            val pb = ProcessBuilder(scriptBin, "-qfc", "$shell -i", "/dev/null")
                .directory(work)
                .redirectErrorStream(true)
            val env = pb.environment()
            env["TERM"] = "xterm-256color"
            env["COLORTERM"] = "truecolor"
            env["COLUMNS"] = cols.coerceIn(20, 300).toString()
            env["LINES"] = rows.coerceIn(5, 120).toString()
            val proc = pb.start()
            synchronized(lock) {
                process = proc
                stdin = OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8)
                alive.set(true)
            }
            listener?.invoke(PtyEvent.Ready(cwd = work.absolutePath, pid = proc.pid().toInt()))
            Thread({ pumpOutput(proc) }, "lumicode-pty-pump").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            cleanup()
            listener?.invoke(PtyEvent.Error(e.message ?: "pty start failed"))
        }
    }

    private fun pumpOutput(proc: Process) {
        val buf = ByteArray(4096)
        try {
            val input = proc.inputStream
            while (alive.get()) {
                val n = try {
                    input.read(buf)
                } catch (_: Exception) {
                    -1
                }
                if (n < 0) break
                if (n == 0) continue
                val text = String(buf, 0, n, StandardCharsets.UTF_8)
                listener?.invoke(PtyEvent.Out(text))
            }
        } finally {
            val code = try {
                if (proc.isAlive) proc.waitFor()
                proc.exitValue()
            } catch (_: Exception) {
                -1
            }
            if (alive.getAndSet(false)) {
                listener?.invoke(PtyEvent.Exit(code))
            }
            synchronized(lock) {
                if (process === proc) {
                    process = null
                    try {
                        stdin?.close()
                    } catch (_: Exception) {
                    }
                    stdin = null
                }
            }
        }
    }

    override fun sendInput(data: String) {
        if (data.isEmpty()) return
        synchronized(lock) {
            val w = stdin ?: return
            try {
                w.write(data)
                w.flush()
            } catch (_: Exception) {
            }
        }
    }

    override fun resize(cols: Int, rows: Int) {
        // Best-effort: Java has no master-fd ioctl; SIGWINCH without TIOCSWINSZ is often a no-op.
        val pid = synchronized(lock) { process?.pid() } ?: return
        try {
            ProcessBuilder("kill", "-WINCH", pid.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor(200, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
    }

    override fun disconnect() {
        disconnectInternal(emitClosed = true)
    }

    private fun disconnectInternal(emitClosed: Boolean) {
        alive.set(false)
        val proc = synchronized(lock) {
            val p = process
            process = null
            try {
                stdin?.close()
            } catch (_: Exception) {
            }
            stdin = null
            p
        }
        if (proc != null) {
            proc.destroy()
            try {
                if (!proc.waitFor(500, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
                proc.destroyForcibly()
            }
        }
        if (emitClosed) {
            listener?.invoke(PtyEvent.Closed)
        }
    }

    private fun cleanup() {
        alive.set(false)
        synchronized(lock) {
            process = null
            try {
                stdin?.close()
            } catch (_: Exception) {
            }
            stdin = null
        }
    }

    /** Resolve [cwd] relative to the mounted workspace root (same config as DesktopWorkspaceBackend). */
    private fun resolveWorkDir(cwdRel: String): File? {
        val rootPath = File(System.getProperty("user.home"), ".config/lumicode/workspace-root.txt")
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.trim()
            .orEmpty()
        val root = File(rootPath).takeIf { it.isDirectory }?.canonicalFile
        if (root == null) {
            val abs = cwdRel.trim()
            return File(abs).takeIf { abs.isNotEmpty() && it.isDirectory }?.canonicalFile
        }
        val cleaned = cwdRel.trim().trimStart('/').replace('\\', '/')
        if (cleaned.isEmpty()) return root
        if (".." in cleaned.split('/')) return null
        val target = File(root, cleaned).canonicalFile
        val base = root.path
        if (target != root && !target.path.startsWith(base + File.separator)) return null
        return if (target.isDirectory) target else root
    }

    private fun findExecutable(name: String): String? {
        val path = System.getenv("PATH").orEmpty()
        for (dir in path.split(File.pathSeparatorChar)) {
            if (dir.isBlank()) continue
            val f = File(dir, name)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        for (candidate in listOf(
            "/usr/bin/script",
            "/bin/script",
            "/run/current-system/sw/bin/script",
        )) {
            val f = File(candidate)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
        return null
    }
}

fun installDesktopPtyBackend() {
    PtyApi.backend = DesktopPtyBackend()
}
