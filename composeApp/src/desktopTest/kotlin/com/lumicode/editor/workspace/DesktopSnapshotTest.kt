package com.lumicode.editor.workspace

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Desktop FS backend: snapshot / diff / restore against a temp workspace.
 */
class DesktopSnapshotTest {
    private lateinit var dir: File
    private lateinit var backend: DesktopWorkspaceBackend

    @BeforeTest
    fun setUp() {
        dir = File.createTempFile("lumicode-desk-", "").also {
            it.delete()
            it.mkdirs()
        }
        File(dir, "Main.kt").writeText("fun main()\n")
        File(dir, "README.md").writeText("# t\n")
        backend = DesktopWorkspaceBackend()
        runBlocking {
            val opened = backend.openRoot(dir.absolutePath)
            assertTrue(opened.ok, opened.error)
        }
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun snapshotDiffRestore() = runBlocking {
        val created = backend.createSnapshot()
        assertTrue(created.ok, created.error)
        val sid = created.snapshotId!!
        assertTrue(sid.startsWith("snap-"))

        File(dir, "Main.kt").writeText("changed\n")
        File(dir, "Extra.kt").writeText("extra\n")
        File(dir, "README.md").delete()

        val diff = backend.diffSnapshot(sid)
        assertTrue(diff.ok, diff.error)
        val byPath = diff.changes.associateBy { it.path }
        assertEquals("modified", byPath["Main.kt"]?.kind)
        assertEquals("added", byPath["Extra.kt"]?.kind)
        assertEquals("deleted", byPath["README.md"]?.kind)

        val restored = backend.restoreSnapshot(sid)
        assertTrue(restored.ok, restored.error)
        assertEquals("fun main()\n", File(dir, "Main.kt").readText())
        assertTrue(File(dir, "README.md").isFile)
        assertFalse(File(dir, "Extra.kt").exists())

        assertTrue(backend.forgetSnapshot(sid).ok)
        assertFalse(backend.diffSnapshot(sid).ok)
    }
}
