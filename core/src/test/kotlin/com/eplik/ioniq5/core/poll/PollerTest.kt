package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.FakeTransport
import com.eplik.ioniq5.core.pid.Ioniq5Pids
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PollerTest {

    // 220101 päris vastus ISO-TP kaadritena (ATH1, ATS0)
    private val bms0101 = listOf(
        "7EC103E620101EFFBE7",
        "7EC21EF3B0000000000",
        "7EC2201A618BF170F0E",
        "7EC23150E14120031B0",
        "7EC2436AE0100009000",
        "7EC2502325200021CD5",
        "7EC26000182DB000167",
        "7EC27AC021B81910002",
        "7EC287B223E000005E7",
    ).joinToString("\r")

    private fun transport() = FakeTransport(mapOf("220101" to bms0101)) {
        if (it.startsWith("AT")) "OK" else "NO DATA"
    }

    @Test
    fun pollOnceDecodesAndDerivesPower() = runTest {
        val poller = Poller(Elm327(transport()), Ioniq5Pids.ALL, clock = { 1_000L })
        Ioniq5Pids.ALL.forEach { poller.pollOnce(it) }
        val r = poller.readings.value

        assertEquals(29.5, r.value(Ioniq5Pids.SOC_BMS)!!, 1e-9)
        assertEquals(633.5 * 42.2 / 1000, r.batteryPowerKw()!!, 1e-9)
        assertEquals(1_000L, r.getValue(Ioniq5Pids.SOC_BMS).timeMs)
        // NO DATA vastusega PID-id jäävad tühjaks, pollimine ei katke
        assertNull(r.value(Ioniq5Pids.ODOMETER))
    }

    @Test
    fun cellVoltagesStopAtMissingCells() {
        val readings = (1..192).associate { cell ->
            Ioniq5Pids.cellId(cell) to Reading(if (cell <= 180) 3.5 else 0.02, 0)
        }
        assertEquals(180, readings.cellVoltages().size)
    }
}
