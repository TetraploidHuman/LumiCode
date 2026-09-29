package com.lumicode.editor.workspace

/** Events from an interactive PTY session. */
sealed class PtyEvent {
    data class Ready(val cwd: String, val pid: Int) : PtyEvent()
    data class Out(val data: String) : PtyEvent()
    data class Exit(val code: Int) : PtyEvent()
    data class Error(val message: String) : PtyEvent()
    data object Closed : PtyEvent()
}

interface PtyBackend {
    fun connect(cwd: String = "", cols: Int = 80, rows: Int = 24)
    fun sendInput(data: String)
    fun resize(cols: Int, rows: Int)
    fun disconnect()
    var listener: ((PtyEvent) -> Unit)?
}

object PtyApi {
    var backend: PtyBackend? = null

    fun connect(cwd: String = "", cols: Int = 80, rows: Int = 24) {
        backend?.connect(cwd, cols, rows)
    }

    fun sendInput(data: String) {
        backend?.sendInput(data)
    }

    fun resize(cols: Int, rows: Int) {
        backend?.resize(cols, rows)
    }

    fun disconnect() {
        backend?.disconnect()
    }
}
