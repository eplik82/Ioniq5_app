package com.eplik.ioniq5.core.pid

import com.eplik.ioniq5.core.elm.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalDecoderTest {

    private fun signal(start: Int, length: Int = 1, signed: Boolean = false, scale: Double = 1.0, bit: Int? = null) =
        SignalDefinition("s", "s", "s", "", ParameterGroup.BATTERY, start, length, signed, scale, bit = bit)

    private val data = "620101FF9C02B3A5".hexToBytes()

    @Test
    fun unsignedSingleByteWithScale() {
        assertEquals(1.0, SignalDecoder.decode(signal(5, scale = 0.5), data)!!, 1e-9)
    }

    @Test
    fun signedTwoBytesBigEndian() {
        // 0xFF9C = -100, skaala 0.1 → -10.0 (nt aku vool amprites)
        assertEquals(-10.0, SignalDecoder.decode(signal(3, 2, signed = true, scale = 0.1), data)!!, 1e-9)
    }

    @Test
    fun unsignedTwoBytes() {
        assertEquals(0x02B3.toDouble(), SignalDecoder.decode(signal(5, 2), data)!!, 1e-9)
    }

    @Test
    fun singleBit() {
        // 0xA5 = 1010 0101
        assertEquals(1.0, SignalDecoder.decode(signal(7, bit = 0), data)!!, 1e-9)
        assertEquals(0.0, SignalDecoder.decode(signal(7, bit = 1), data)!!, 1e-9)
    }

    @Test
    fun tooShortResponseGivesNull() {
        assertNull(SignalDecoder.decode(signal(7, 2), data))
    }
}
