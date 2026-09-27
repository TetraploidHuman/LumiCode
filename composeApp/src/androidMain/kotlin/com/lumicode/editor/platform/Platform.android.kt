package com.lumicode.editor.platform

import android.os.Build

actual fun platformLabel(): String = "Android ${Build.VERSION.RELEASE}"

actual fun platformTag(): String =
    "ANDROID · ${Build.SUPPORTED_ABIS.firstOrNull()?.uppercase() ?: "ARM"}"
