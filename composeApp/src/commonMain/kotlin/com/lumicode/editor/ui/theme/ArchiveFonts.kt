package com.lumicode.editor.ui.theme

import androidx.compose.runtime.Composable

/**
 * 平台相关的归档风字体装载。
 * JVM / Android / Wasm：compose.resources 内置 Noto + JetBrains Mono。
 * Kotlin/Native（ComposeKN）：`com.composekn.resources` + 生成的 `Res.font.*`
 *（与 Desktop 同一批 TTF/OTF）。
 */
@Composable
expect fun InstallArchiveFonts()
