package com.lumicode.editor.platform

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

actual fun platformLabel(): String = "Android ${Build.VERSION.RELEASE}"

actual fun platformTag(): String = "ANDROID · ${Build.SUPPORTED_ABIS.firstOrNull()?.uppercase() ?: "ARM"}"

actual fun clockLabel(): String = clockFormat.format(Date())
