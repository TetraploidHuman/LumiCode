package com.lumicode.editor.platform

private val osName: String = System.getProperty("os.name").orEmpty()

actual fun platformLabel(): String = when {
    osName.contains("win", ignoreCase = true) -> "Windows"
    osName.contains("linux", ignoreCase = true) -> "Linux"
    osName.contains("mac", ignoreCase = true) -> "macOS"
    else -> osName.ifEmpty { "Desktop" }
}

actual fun platformTag(): String = when {
    osName.contains("win", ignoreCase = true) -> "WIN · JVM"
    osName.contains("mac", ignoreCase = true) -> "MAC · JVM"
    else -> "LINUX · JVM"
}
