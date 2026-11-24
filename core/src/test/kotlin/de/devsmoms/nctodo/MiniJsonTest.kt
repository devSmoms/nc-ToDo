package de.devsmoms.nctodo

import de.devsmoms.nctodo.data.MiniJson
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class MiniJsonTest {

    @Test
    fun readsLoginFlowAnswers() {
        val start = MiniJson.parseObject(
            """{"poll":{"token":"abc\/123","endpoint":"https:\/\/cloud.example.de\/index.php\/login\/v2\/poll"},
               "login":"https:\/\/cloud.example.de\/index.php\/login\/v2\/flow\/xyz"}""",
        )!!
        val poll = start["poll"] as Map<*, *>
        assertEquals("abc/123", MiniJson.string(poll, "token"))
        assertEquals("https://cloud.example.de/index.php/login/v2/poll", MiniJson.string(poll, "endpoint"))
        assertEquals("https://cloud.example.de/index.php/login/v2/flow/xyz", MiniJson.string(start, "login"))

        val done = MiniJson.parseObject("""{"server":"https://cloud.example.de","loginName":"Jörg ä","appPassword":"p\"w\\x"}""")!!
        assertEquals("Jörg ä", MiniJson.string(done, "loginName"))
        assertEquals("p\"w\\x", MiniJson.string(done, "appPassword"))
    }

    @Test
    fun otherValueTypes() {
        val o = MiniJson.parseObject(""" { "a": [1, 2.5, -3e2], "b": true, "c": null, "d": {}, "e": [] } """)!!
        assertEquals(listOf(1.0, 2.5, -300.0), o["a"])
        assertEquals(true, o["b"])
        assertNull(o["c"])
        assertEquals("", MiniJson.string(o, "b"))
        assertEquals("", MiniJson.string(o, "missing"))
    }

    @Test
    fun rejectsInvalidInput() {
        listOf("", "[]", "{", "{\"a\":}", "{\"a\":1,}", "{\"a\":1} x", "<html>", "{\"a\":\"\u0001\"}")
            .forEach { assertNull(MiniJson.parseObject(it), it) }
    }
}
