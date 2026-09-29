package com.lumicode.editor.platform

/** Wasm Firefox needs a native &lt;textarea&gt; for CJK IME; other targets use Compose. */
expect fun preferDomImeForEditor(): Boolean
