package com.lumicode.editor.platform

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 宿主身份（名称 / 短标签）。OS / ABI 没有官方 common API，保留薄 expect；
 * 墙钟用 [kotlinx-datetime]，全部目标共用 [clockLabel]。
 */
expect fun platformLabel(): String

expect fun platformTag(): String

/** 墙钟 `HH:mm:ss`（系统时区）；Android / Desktop / Wasm / Native 同一实现。 */
fun clockLabel(): String {
    val t = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time
    return "${t.hour.toString().padStart(2, '0')}:" +
        "${t.minute.toString().padStart(2, '0')}:" +
        "${t.second.toString().padStart(2, '0')}"
}
