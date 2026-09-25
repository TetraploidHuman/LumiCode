package com.lumicode.editor.platform

import kotlin.js.js

/**
 * Wall clock supplied by the browser through a small JavaScript snippet, so the wasm
 * build needs no extra dependency.
 */
private val browserClock: () -> String = js(
    "(function () { return function () {" +
        " var d = new Date();" +
        " var p = function (n) { return String(n).padStart(2, '0'); };" +
        " return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());" +
        " }; })()",
)

actual fun platformLabel(): String = "WebAssembly"

actual fun platformTag(): String = "WASM · WEB"

actual fun clockLabel(): String = browserClock()
