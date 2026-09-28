package com.lumicode.editor.platform

import kotlinx.browser.localStorage

private const val PREFIX = "lumicode."

actual object LocalPrefs {
    actual fun get(key: String): String? =
        localStorage.getItem(PREFIX + key)

    actual fun set(key: String, value: String) {
        localStorage.setItem(PREFIX + key, value)
    }
}
