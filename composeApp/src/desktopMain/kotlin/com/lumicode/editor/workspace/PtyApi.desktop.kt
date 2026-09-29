package com.lumicode.editor.workspace

/** Desktop：暂无本机 PTY（优先走 Web 服务的 /api/pty）；保留空实现避免崩溃。 */
class DesktopPtyBackend : PtyBackend {
    override var listener: ((PtyEvent) -> Unit)? = null

    override fun connect(cwd: String, cols: Int, rows: Int) {
        listener?.invoke(PtyEvent.Error("Desktop 请使用 Web 版交互终端，或后续接入本机 PTY"))
    }

    override fun sendInput(data: String) = Unit
    override fun resize(cols: Int, rows: Int) = Unit
    override fun disconnect() = Unit
}

fun installDesktopPtyBackend() {
    PtyApi.backend = DesktopPtyBackend()
}
