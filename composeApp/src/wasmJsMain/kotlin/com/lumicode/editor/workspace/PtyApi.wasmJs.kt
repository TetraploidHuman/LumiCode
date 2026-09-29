package com.lumicode.editor.workspace

import kotlin.js.JsString

@JsFun(
    """
    () => {
      const p = location.pathname || '';
      if (p === '/code' || p.startsWith('/code/')) return '/code';
      return '';
    }
    """,
)
internal external fun appBasePathJs(): JsString

fun appBasePath(): String = appBasePathJs().toString()

@JsFun(
    """
    (url, onOpen, onMsg, onClose, onError) => {
      const ws = new WebSocket(url);
      ws.binaryType = 'arraybuffer';
      ws.onopen = () => onOpen();
      ws.onmessage = (ev) => {
        if (typeof ev.data === 'string') onMsg(ev.data);
        else onMsg(new TextDecoder().decode(ev.data));
      };
      ws.onclose = () => onClose();
      ws.onerror = () => onError('websocket error');
      return ws;
    }
    """,
)
private external fun openWsJs(
    url: String,
    onOpen: () -> Unit,
    onMsg: (String) -> Unit,
    onClose: () -> Unit,
    onError: (String) -> Unit,
): JsAny

@JsFun("(ws, text) => { if (ws && ws.readyState === 1) ws.send(text); }")
private external fun wsSendJs(ws: JsAny, text: String)

@JsFun("(ws) => { try { ws.close(); } catch (e) {} }")
private external fun wsCloseJs(ws: JsAny)

@JsFun("() => (location.protocol === 'https:' ? 'wss:' : 'ws:')")
private external fun jsLocationProtocolWs(): String

@JsFun("() => location.host")
private external fun jsLocationHost(): String

class WasmPtyBackend : PtyBackend {
    override var listener: ((PtyEvent) -> Unit)? = null
    private var socket: JsAny? = null
    private var openPayload: String? = null

    override fun connect(cwd: String, cols: Int, rows: Int) {
        disconnect()
        val base = appBasePath()
        val url = "${jsLocationProtocolWs()}//${jsLocationHost()}$base/api/pty"
        openPayload = """{"type":"open","cols":$cols,"rows":$rows,"cwd":${jsonString(cwd)}}"""
        socket = openWsJs(
            url,
            onOpen = {
                val payload = openPayload
                val ws = socket
                if (payload != null && ws != null) {
                    wsSendJs(ws, payload)
                    openPayload = null
                }
            },
            onMsg = { text -> handleMessage(text) },
            onClose = { listener?.invoke(PtyEvent.Closed) },
            onError = { msg -> listener?.invoke(PtyEvent.Error(msg)) },
        )
    }

    private fun handleMessage(text: String) {
        val type = text.stringField("type")
        if (type == null) {
            listener?.invoke(PtyEvent.Out(text))
            return
        }
        when (type) {
            "ready" -> listener?.invoke(
                PtyEvent.Ready(
                    cwd = text.stringField("cwd").orEmpty(),
                    pid = text.intField("pid") ?: 0,
                ),
            )
            "out" -> listener?.invoke(PtyEvent.Out(text.stringField("data").orEmpty()))
            "exit" -> listener?.invoke(PtyEvent.Exit(text.intField("code") ?: -1))
            "error" -> listener?.invoke(PtyEvent.Error(text.stringField("message") ?: "pty error"))
            else -> listener?.invoke(PtyEvent.Out(text))
        }
    }

    override fun sendInput(data: String) {
        val ws = socket ?: return
        wsSendJs(ws, """{"type":"input","data":${jsonString(data)}}""")
    }

    override fun resize(cols: Int, rows: Int) {
        val ws = socket ?: return
        wsSendJs(ws, """{"type":"resize","cols":$cols,"rows":$rows}""")
    }

    override fun disconnect() {
        socket?.let { wsCloseJs(it) }
        socket = null
        openPayload = null
    }
}

fun installWasmPtyBackend() {
    PtyApi.backend = WasmPtyBackend()
}
