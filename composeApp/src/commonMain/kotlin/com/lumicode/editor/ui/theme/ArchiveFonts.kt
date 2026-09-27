package com.lumicode.editor.ui.theme

import androidx.compose.runtime.Composable

/**
 * 平台相关的归档风字体装载。
 * JVM / Android / Wasm：compose.resources 内置 Noto + JetBrains Mono。
 * Kotlin/Native（ComposeKN）：系统字体回退（暂无 resources 管线）。
 */
@Composable
expect fun InstallArchiveFonts()
