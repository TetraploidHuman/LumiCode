package com.lumicode.editor.platform

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 墙钟 `HH:mm:ss`（系统时区）。
 * 用标准库 [Clock] + kotlinx-datetime 的时区换算，全平台 common，无需 expect/actual。
 */
@OptIn(ExperimentalTime::class)
fun clockLabel(): String {
    val t = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time
    return "${t.hour.toString().padStart(2, '0')}:" +
        "${t.minute.toString().padStart(2, '0')}:" +
        "${t.second.toString().padStart(2, '0')}"
}
