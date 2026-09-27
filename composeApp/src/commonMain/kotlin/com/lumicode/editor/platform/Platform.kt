package com.lumicode.editor.platform

/**
 * 宿主身份：各目标 actual（Android 带版本/ABI，Desktop 看 OS 名，Wasm 固定文案）。
 * 墙钟见同包 [clockLabel]（common，不走 expect）。
 */
expect fun platformLabel(): String

expect fun platformTag(): String
