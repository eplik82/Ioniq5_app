package com.eplik.ioniq5.core.elm

import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.uds.UdsResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327Test {

    private val bms = EcuAddress(0x7E4)

    private fun okReplies() = mapOf("ATZ" to "ELM327 v1.5") + Elm327.INIT_COMMANDS.associateWith { "OK" } +
        mapOf("ATSH7E4" to "OK", "ATCRA7EC" to "OK")

    @Test
    fun initializeSendsSetupCommands() = runTest {
        val transport = FakeTransport(okReplies())
        Elm327(transport).initialize()
        assertEquals(listOf("ATZ") + Elm327.INIT_COMMANDS, transport.sent)
    }

    @Test
    fun requestSetsHeaderOnceAndAssemblesResponse() = runTest {
        val transport = FakeTransport(
            okReplies() + mapOf("220101" to "7EC1009620101010203\r7EC21040506AAAAAAAA"),
        )
        val elm = Elm327(transport)
        val first = elm.request(bms, "220101".hexToBytes())
        elm.request(bms, "220101".hexToBytes())

        assertTrue(first is UdsResponse.Positive)
        assertArrayEquals("620101010203040506".hexToBytes(), (first as UdsResponse.Positive).data)
        assertEquals(listOf("ATSH7E4", "ATCRA7EC", "220101", "220101"), transport.sent)
    }

    @Test
    fun negativeResponse() = runTest {
        val transport = FakeTransport(okReplies() + mapOf("22F190" to "7EC037F2231"))
        val response = Elm327(transport).request(bms, "22F190".hexToBytes())
        assertTrue(response is UdsResponse.Negative)
        assertEquals(0x31, (response as UdsResponse.Negative).nrc)
    }

    @Test(expected = ElmException::class)
    fun noDataThrows() = runTest {
        val transport = FakeTransport(okReplies() + mapOf("220101" to "NO DATA"))
        Elm327(transport).request(bms, "220101".hexToBytes())
    }

    @Test
    fun forbiddenServiceIsNeverSent() = runTest {
        val transport = FakeTransport(okReplies())
        val result = runCatching { Elm327(transport).request(bms, "2E0101FF".hexToBytes()) }
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(transport.sent.isEmpty())
    }
}
