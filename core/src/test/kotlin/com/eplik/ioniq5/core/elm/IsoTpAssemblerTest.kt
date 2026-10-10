package com.eplik.ioniq5.core.elm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IsoTpAssemblerTest {

    @Test
    fun singleFrame() {
        val result = IsoTpAssembler.assemble(listOf("7EC0462F19000AAAAAA"))
        assertArrayEquals("62F19000".hexToBytes(), result.getValue(0x7EC))
    }

    @Test
    fun multiFrameWithSpacesAndPadding() {
        val lines = listOf(
            "7EC 10 0D 62 01 01 01 02",
            "7EC 21 03 04 05 06 07 08 09",
            "7EC 22 0A AA AA AA AA AA AA",
        )
        val result = IsoTpAssembler.assemble(lines)
        assertArrayEquals("6201010102030405060708090A".hexToBytes(), result.getValue(0x7EC))
    }

    @Test
    fun separatesEcusAndIgnoresGarbage() {
        val lines = listOf("SEARCHING...", "7E80641000000000000", "7EB0341000000000000", "")
        val result = IsoTpAssembler.assemble(lines)
        assertEquals(setOf(0x7E8, 0x7EB), result.keys)
        assertEquals(6, result.getValue(0x7E8).size)
        assertEquals(3, result.getValue(0x7EB).size)
    }
}
