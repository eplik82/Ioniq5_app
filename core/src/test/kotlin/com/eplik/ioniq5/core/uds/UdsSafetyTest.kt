package com.eplik.ioniq5.core.uds

import com.eplik.ioniq5.core.elm.hexToBytes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsSafetyTest {

    @Test
    fun readServicesAreAllowed() {
        listOf("220101", "2101", "3E00", "0100", "0902", "1902FF").forEach {
            assertTrue(it, UdsSafety.isAllowed(it.hexToBytes()))
        }
    }

    @Test
    fun writingAndControlServicesAreBlocked() {
        // WriteDataByIdentifier, IO control, routine, clear DTC, session, security access, reset
        listOf("2E0101FF", "2F010103", "310101", "14FFFFFF", "1003", "2701", "1101", "").forEach {
            assertFalse(it, UdsSafety.isAllowed(it.hexToBytes()))
        }
    }
}
