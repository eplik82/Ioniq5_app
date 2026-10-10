package com.eplik.ioniq5.core.scan

import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.FakeTransport
import com.eplik.ioniq5.core.elm.hexToBytes
import com.eplik.ioniq5.core.pid.EcuAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerTest {

    // Kõik AT-käsud õnnestuvad, tundmatud päringud: NO DATA
    private fun transport(replies: Map<String, String>) =
        FakeTransport(replies) { if (it.startsWith("AT")) "OK" else "NO DATA" }

    @Test
    fun findsRespondingEcusAndReadsIdentifiers() = runTest {
        // Fake ei arvesta päist: 3E00 vastus sisaldab kahte ECU-d, Elm327 valib ATCRA aadressi järgi.
        // 7E5 annab osanumbri "AB12", teised identifikaatorid puuduvad.
        val elm = Elm327(
            transport(
                mapOf(
                    "3E00" to "7EC027E00\r7ED027E00",
                    "22F187" to "7ED0762F18741423132",
                ),
            ),
        )
        val found = EcuScanner(elm).scan(0x7E3..0x7E5)

        assertEquals(listOf(EcuAddress(0x7E4), EcuAddress(0x7E5)), found.map { it.address })
        assertEquals(emptyMap<Int, String>(), found[0].identifiers)
        assertEquals(mapOf(0xF187 to "AB12"), found[1].identifiers)
    }

    @Test
    fun didScanReturnsOnlyPositiveResponses() = runTest {
        val elm = Elm327(
            transport(
                mapOf(
                    "22E010" to "7ED037F2231",
                    "22E011" to "7ED0562E0110102",
                ),
            ),
        )
        val found = DidScanner(elm).scan(EcuAddress(0x7E5), 0xE010..0xE012)
        assertEquals(listOf(0xE011), found.map { it.did })
        assertEquals(5, found[0].data.size)
    }

    @Test
    fun kwpScanUsesOneByteIds() = runTest {
        val transport = transport(mapOf("2101" to "7EB0461010203"))
        val found = DidScanner(Elm327(transport)).scan(EcuAddress(0x7E3), 0x01..0x02, service = 0x21)
        assertEquals(listOf(0x01), found.map { it.did })
        assertEquals(listOf("ATSH7E3", "ATCRA7EB", "2101", "2102"), transport.sent)
    }

    @Test
    fun discoveryPlanIsReadOnly() {
        (DiscoveryPlan.STAGE3 + DiscoveryPlan.CLIMATE).forEach { assert(it.service == 0x22 || it.service == 0x21) }
        assertEquals("7E3 22 E000–E0FF", DiscoveryPlan.STAGE3[0].describe())
    }

    @Test
    fun devCaptureIsReadOnly() {
        DevCapture.REQUESTS.forEach {
            assert(com.eplik.ioniq5.core.uds.UdsSafety.isAllowed(it.request.hexToBytes()))
        }
    }
}
