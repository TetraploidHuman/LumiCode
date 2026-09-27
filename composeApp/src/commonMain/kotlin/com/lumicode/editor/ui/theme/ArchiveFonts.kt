package com.lumicode.editor.ui.theme

import androidx.compose.runtime.Composable

/**
 * 平台相关的归档风字体装载。
 * JVM / Android / Wasm 用内置 Noto + JetBrains Mono；
 * ComposeKN Kotlin/Native 目标回退系统字体（无 compose.resources）。
 */
@Composable
expect fun InstallArchiveFonts()
