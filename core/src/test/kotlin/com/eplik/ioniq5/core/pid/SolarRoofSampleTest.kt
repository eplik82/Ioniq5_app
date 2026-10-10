package com.eplik.ioniq5.core.pid

import com.eplik.ioniq5.core.elm.IsoTpAssembler
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Päris vastus ICCU-lt (testdata/raw/iccu_7E5_22E011_2026-10-10_1303.txt), dekodeeritud
 * kasutaja Car Scanneri valemitega. Valemid on kontrollimata; test kinnitab ainult, et
 * kokkupanek ja baitide asukohad vastavad Car Scanneri tähtedele (A = bait 3).
 */
class SolarRoofSampleTest {

    private val lines = listOf(
        "7ED103162E011FFFFFF",
        "7ED21F8010100000078",
        "7ED223A4E066D199139",
        "7ED237B7FD251224D00",
        "7ED246808010101000A",
        "7ED2500000900070000",
        "7ED2600000000000000",
        "7ED2700AAAAAAAAAAAA",
    )

    private fun signal(start: Int, length: Int, scale: Double) =
        SignalDefinition("s", "s", "s", "", ParameterGroup.SOLAR, start, length, scale = scale)

    private val data = IsoTpAssembler.assemble(lines).getValue(0x7ED)

    @Test
    fun assemblesFullResponse() {
        assertEquals(0x31, data.size)
        assertEquals(0x62, data[0].toInt() and 0xFF)
    }

    @Test
    fun carScannerFormulas() {
        // ((K*256)+L)/100, ((M*256)+N)/10, Q*2
        assertEquals(149.26, SignalDecoder.decode(signal(13, 2, 0.01), data)!!, 1e-9)
        assertEquals(164.5, SignalDecoder.decode(signal(15, 2, 0.1), data)!!, 1e-9)
        assertEquals(114.0, SignalDecoder.decode(signal(19, 1, 2.0), data)!!, 1e-9)
    }
}
