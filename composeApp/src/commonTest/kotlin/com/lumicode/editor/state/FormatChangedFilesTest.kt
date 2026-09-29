package com.lumicode.editor.state

import com.lumicode.editor.workspace.WorkspaceFileChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatChangedFilesTest {
    @Test
    fun empty() {
        assertEquals("无", formatChangedFiles(emptyList()))
    }

    @Test
    fun formatsKinds() {
        val text = formatChangedFiles(
            listOf(
                WorkspaceFileChange("A.kt", "added"),
                WorkspaceFileChange("B.kt", "modified"),
                WorkspaceFileChange("C.kt", "deleted"),
            ),
        )
        assertTrue(text.contains("+A.kt"))
        assertTrue(text.contains("~B.kt"))
        assertTrue(text.contains("−C.kt"))
    }

    @Test
    fun truncatesWithTotal() {
        val many = (1..10).map { WorkspaceFileChange("f$it.kt", "modified") }
        val text = formatChangedFiles(many, limit = 3)
        assertTrue(text.contains("共10个"))
        assertTrue(text.contains("~f1.kt"))
    }
}
