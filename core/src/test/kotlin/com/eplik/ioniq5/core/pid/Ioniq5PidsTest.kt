package com.eplik.ioniq5.core.pid

import com.eplik.ioniq5.core.elm.hexToBytes
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Päris auto vastused 2026-10-10 (testdata/raw/did-scans_2026-10-10_1346.txt ja
 * iccu_7E5_did-scan_E000-E0FF_2026-10-10_1320.txt). Oodatud väärtused on võrreldud
 * Car Scanneri ja auto näidikutega.
 */
class Ioniq5PidsTest {

    private val responses = mapOf(
        "220101" to "620101EFFBE7EF3B000000000001A618BF170F0E150E14120031B036AE010000900002325200021CD5000182DB000167AC021B819100027B223E000005E7",
        "220105" to "620105FFFB740F012C01012C150F160F15121662D458F20100500D0003E4851FB40036000000000000000F150F14",
        "220102" to "620102FFFFFFFFAEAFAFAFAFAFAFAFB0AFAFAFAFAFAFAFB0B0B0AFB0AFB0AFAFAFAFAFAFAFAFAF",
        "22010C" to "62010CFFFFF000AFAFAFAFAFAFAFAFAFAFAFAFAFAFAFAFAFAFAFAF020000000001000100080801",
        "22B002" to "62B002E0000000FFB60107E9000000",
        "22E011" to "62E011FFFFFFF80101000000753A5507B4195A39847FCD512251006708010101000A000009000700000000000000000000",
    )

    private fun decode(id: String): Double? {
        val pid = P.ALL.first { p -> p.signals.any { it.id == id } }
        val signal = pid.signals.first { it.id == id }
        return SignalDecoder.decode(signal, responses.getValue(pid.request).hexToBytes())
    }

    private fun check(expected: Double, id: String) = assertEquals(id, expected, decode(id)!!, 1e-6)

    @Test
    fun bmsMain() {
        check(29.5, P.SOC_BMS)
        check(42.2, P.BATTERY_CURRENT)
        check(633.5, P.BATTERY_VOLTAGE)
        check(23.0, P.BATTERY_TEMP_MAX)
        check(15.0, P.BATTERY_TEMP_MIN)
        check(3.52, P.CELL_V_MAX)
        check(54.0, P.CELL_V_MAX_NO)
        check(3.48, P.CELL_V_MIN)
        check(1.0, P.CELL_V_MIN_NO)
        check(14.4, P.AUX_VOLTAGE)
        check(9903.5, P.CUMULATIVE_CHARGED_KWH)
        check(9207.6, P.CUMULATIVE_DISCHARGED_KWH)
        check(8766.0, P.MOTOR_RPM)
    }

    @Test
    fun bmsHealth() {
        check(99.6, P.SOH)
        check(27.0, P.SOC_DISPLAY)
    }

    @Test
    fun cells() {
        check(3.48, P.cellId(1))
        check(3.50, P.cellId(2))
        // 72,6 kWh akul on viimases plokis ainult 20 elementi (kokku 180)
        check(3.50, P.cellId(180))
        check(0.04, P.cellId(181))
    }

    @Test
    fun odometerAndIccu() {
        check(67561.0, P.ODOMETER)
        check(649.0, P.ICCU_HV_VOLTAGE)
        check(197.2, P.SOLAR_PANEL_VOLTAGE)
    }

    @Test
    fun moduleTemperatures() {
        // 0101: 14, 21, 14, 20, 18; 0105: 21, 15, 22, 15, 21, 18, 22, 15, 21, 15, 20
        val expected = listOf(14, 21, 14, 20, 18, 21, 15, 22, 15, 21, 18, 22, 15, 21, 15, 20)
        expected.forEachIndexed { i, t -> check(t.toDouble(), P.moduleTempId(i + 1)) }
    }

    @Test
    fun signalIdsAreUnique() {
        val ids = P.ALL.flatMap { it.signals }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
