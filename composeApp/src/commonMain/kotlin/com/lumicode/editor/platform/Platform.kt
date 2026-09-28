package com.lumicode.editor.platform

/**
 * 宿主身份：各目标 actual（Android 带版本/ABI，Desktop 看 OS 名，Wasm 固定文案）。
 * 墙钟见同包 [clockLabel]（common，不走 expect）。
 */
expect fun platformLabel(): String

expect fun platformTag(): String

/**
 * 轻量本地键值：Android SharedPreferences / JVM Preferences / Wasm localStorage。
 * 只存设置类字符串，不做工作区序列化。
 */
expect object LocalPrefs {
    fun get(key: String): String?
    fun set(key: String, value: String)
}