package com.lumicode.editor.platform

/** Human readable target name shown in the status bar and console. */
expect fun platformLabel(): String

/** Short id used by the telemetry rail. */
expect fun platformTag(): String

/** Wall-clock label, e.g. 16:06:09. */
expect fun clockLabel(): String
