package com.lumicode.editor.dsh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DshApiParseTest {
    @Test
    fun parseHealth() {
        val h = parseDshHealth("""{"ok":true,"provider":"qwen35-250","model":"qwen35-9b","error":null}""")
        assertTrue(h.ok)
        assertEquals("qwen35-250", h.provider)
        assertEquals("qwen35-9b", h.model)
        assertNull(h.error)
    }

    @Test
    fun parseChatWithSteps() {
        val raw = """
            {
              "ok": true,
              "sessionId": "session-abc",
              "reply": "done",
              "provider": "qwen35-250",
              "model": "qwen35-9b",
              "error": null,
              "steps": [
                {"kind":"think","text":"plan","name":null,"seq":10},
                {"kind":"tool","text":"{\"path\":\"A.kt\"}","name":"read","seq":11},
                {"kind":"tool_result","text":"ok","name":null,"seq":12},
                {"kind":"say","text":"done","name":null,"seq":13}
              ]
            }
        """.trimIndent()
        val chat = parseDshChat(raw)
        assertTrue(chat.ok)
        assertEquals("session-abc", chat.sessionId)
        assertEquals("done", chat.reply)
        assertEquals(4, chat.steps.size)
        assertEquals("think", chat.steps[0].kind)
        assertEquals("plan", chat.steps[0].text)
        assertEquals("tool", chat.steps[1].kind)
        assertEquals("read", chat.steps[1].name)
        assertEquals("say", chat.steps[3].kind)
    }

    @Test
    fun parseProgress() {
        val raw = """{"jobId":"job-1","done":false,"steps":[{"kind":"tool","text":"x","name":"bash","seq":1}]}"""
        val p = parseDshProgress(raw)
        assertEquals("job-1", p.jobId)
        assertFalse(p.done)
        assertEquals(1, p.steps.size)
        assertEquals("bash", p.steps[0].name)
    }

    @Test
    fun parseEmptySteps() {
        assertTrue(parseTraceSteps("""{"ok":true,"steps":[]}""").isEmpty())
        assertTrue(parseTraceSteps("""{"ok":true}""").isEmpty())
    }

    @Test
    fun jobIdShape() {
        val id = newDshJobId()
        assertTrue(id.startsWith("job-"))
        assertEquals(20, id.length)
    }
}
