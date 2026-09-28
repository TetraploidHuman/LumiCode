package com.lumicode.editor.platform

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "lumicode_settings"

/** 由 [com.lumicode.editor.MainActivity] 在启动时注入。 */
object AndroidPrefsContext {
    @Volatile
    var app: Context? = null
}

actual object LocalPrefs {
    private fun prefs(): SharedPreferences? {
        val ctx = AndroidPrefsContext.app ?: return null
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? =
        prefs()?.let { if (it.contains(key)) it.getString(key, null) else null }

    actual fun set(key: String, value: String) {
        prefs()?.edit()?.putString(key, value)?.apply()
    }
}
