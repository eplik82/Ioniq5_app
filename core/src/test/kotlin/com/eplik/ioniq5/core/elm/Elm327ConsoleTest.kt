package com.eplik.ioniq5.core.elm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ConsoleTest {

    private fun transport() = FakeTransport(
        mapOf("ATSH7E5" to "OK", "22E011" to "7ED0462E01101"),
    )

    @Test
    fun atAndHexCommandsAreSentAndLogged() = runTest {
        val transport = transport()
        val elm = Elm327(transport)
        val log = mutableListOf<TrafficEntry>()
        elm.trafficListener = { log += it }

        assertEquals(listOf("OK"), elm.console("atsh 7e5"))
        assertEquals(listOf("7ED0462E01101"), elm.console("22 E0 11"))
        assertEquals(listOf("ATSH7E5", "22E011"), log.map { it.command })
        assertEquals("> 22E011\n7ED0462E01101\n", log[1].format())
    }

    @Test
    fun forbiddenHexAndAtCommandsAreRejected() = runTest {
        val transport = transport()
        val elm = Elm327(transport)
        listOf("2E E0 11 00", "1003", "ATMA", "ATPP 0C SV 23").forEach {
            assertTrue(it, runCatching { elm.console(it) }.exceptionOrNull() is SecurityException)
        }
        assertTrue(runCatching { elm.console("22E01") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(transport.sent.isEmpty())
    }
}
