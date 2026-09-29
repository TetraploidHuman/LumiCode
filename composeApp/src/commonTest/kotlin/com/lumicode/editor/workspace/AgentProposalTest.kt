package com.lumicode.editor.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentProposalTest {
    @Test
    fun parseSummaryOnly() {
        val parsed = parseAgentReply("改好了 Main.kt，风险低。")
        assertEquals("改好了 Main.kt，风险低。", parsed.summary)
        assertTrue(parsed.edits.isEmpty())
    }

    @Test
    fun parseLegacyEditsMarker() {
        val raw = """
            摘要一句。
            ---LUMICODE_EDIT---
            {"edits":[{"path":"Main.kt","startLine":1,"endLine":2,"content":"fun main() {\n}\n"}]}
        """.trimIndent()
        val parsed = parseAgentReply(raw)
        assertEquals("摘要一句。", parsed.summary)
        assertEquals(1, parsed.edits.size)
        assertEquals("Main.kt", parsed.edits[0].path)
        assertEquals(1, parsed.edits[0].startLine)
        assertEquals(2, parsed.edits[0].endLine)
        assertTrue(parsed.edits[0].content.contains("fun main()"))
    }

    @Test
    fun applyLineEditReplacesRange() {
        val source = "a\nb\nc\n"
        val out = applyLineEdit(source, startLine = 2, endLine = 2, replacement = "B")
        assertEquals("a\nB\nc\n", out)
    }

    @Test
    fun applyLineEditIgnoresInvalidRange() {
        val source = "a\nb\n"
        assertEquals(source, applyLineEdit(source, 0, 1, "x"))
    }

    @Test
    fun parseWorkspaceDiffChanges() {
        val raw = """
            {"ok":true,"snapshotId":"snap-aaaaaaaaaaaa","changes":[
              {"path":"A.kt","kind":"modified"},
              {"path":"B.kt","kind":"added"},
              {"path":"C.kt","kind":"deleted"}
            ],"changeCount":3}
        """.trimIndent()
        val diff = parseWorkspaceDiff(raw)
        assertTrue(diff.ok)
        assertEquals("snap-aaaaaaaaaaaa", diff.snapshotId)
        assertEquals(3, diff.changes.size)
        assertEquals("modified", diff.changes[0].kind)
        assertEquals("B.kt", diff.changes[1].path)
        assertEquals("deleted", diff.changes[2].kind)
    }

    @Test
    fun parseSnapshotAndRestore() {
        val snap = parseWorkspaceSnapshot("""{"ok":true,"snapshotId":"snap-bbbbbbbbbbbb","fileCount":4}""")
        assertTrue(snap.ok)
        assertEquals(4, snap.fileCount)
        val restore = parseWorkspaceRestore(
            """{"ok":true,"snapshotId":"snap-bbbbbbbbbbbb","restored":3,"deleted":1}""",
        )
        assertTrue(restore.ok)
        assertEquals(3, restore.restored)
        assertEquals(1, restore.deleted)
    }
}
