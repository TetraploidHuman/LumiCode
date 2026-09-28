package com.lumicode.editor.platform

import java.util.prefs.Preferences

actual object LocalPrefs {
    private val node: Preferences =
        Preferences.userRoot().node("com/lumicode/editor")

    actual fun get(key: String): String? =
        node.get(key, null)

    actual fun set(key: String, value: String) {
        node.put(key, value)
        runCatching { node.flush() }
    }
}
