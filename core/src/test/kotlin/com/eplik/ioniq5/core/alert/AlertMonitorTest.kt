package com.eplik.ioniq5.core.alert

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertMonitorTest {

    private val now = 1_000_000L

    private fun r(vararg values: Pair<String, Double>, at: Long = now) = values.associate { (id, v) -> id to Reading(v, at) }

    private val goodTires = arrayOf(
        P.TIRE_PRESSURE_FL to 2.9,
        P.TIRE_PRESSURE_FR to 2.9,
        P.TIRE_PRESSURE_RL to 2.9,
        P.TIRE_PRESSURE_RR to 2.9,
    )

    @Test
    fun noAlertsForNormalValues() {
        val m = AlertMonitor()
        val alerts = m.update(
            r(
                *goodTires,
                P.AUX_VOLTAGE to 12.6,
                P.BATTERY_TEMP_MAX to 25.0,
                P.BATTERY_TEMP_MIN to 20.0,
                P.BATTERY_CURRENT to 1.0,
                P.CELL_V_MAX to 3.82,
                P.CELL_V_MIN to 3.80,
                P.SOC_BMS to 70.0,
            ),
            now,
        )
        assertEquals(emptyList<Alert>(), alerts)
    }

    @Test
    fun lowTireWithHysteresis() {
        val m = AlertMonitor(AlertLimits(lowTirePressureBar = 2.2))
        val low = goodTires.toMutableList().apply { this[3] = P.TIRE_PRESSURE_RR to 2.1 }
        val a = m.update(r(*low.toTypedArray()), now)
        assertEquals(listOf(AlertType.TIRE_LOW, AlertType.TIRE_IMBALANCE), a.map { it.type }.sortedBy { it.ordinal })
        assertEquals(Wheel.RR, a.first { it.type == AlertType.TIRE_LOW }.wheel)

        // 2,22 on üle piiri, aga hüstereesi sees: hoiatus jääb
        low[3] = P.TIRE_PRESSURE_RR to 2.22
        assertTrue(m.update(r(*low.toTypedArray()), now).any { it.type == AlertType.TIRE_LOW })
        low[3] = P.TIRE_PRESSURE_RR to 2.3
        assertTrue(m.update(r(*low.toTypedArray()), now).none { it.type == AlertType.TIRE_LOW })
    }

    @Test
    fun auxSeverity() {
        val m = AlertMonitor()
        assertEquals(Severity.WARNING, m.update(r(P.AUX_VOLTAGE to 11.9), now).single().severity)
        assertEquals(Severity.CRITICAL, m.update(r(P.AUX_VOLTAGE to 11.5), now).single().severity)
        assertEquals(1, m.update(r(P.AUX_VOLTAGE to 12.1), now).size)
        assertEquals(0, m.update(r(P.AUX_VOLTAGE to 12.3), now).size)
    }

    @Test
    fun staleReadingsDoNotChangeState() {
        val m = AlertMonitor()
        m.update(r(P.AUX_VOLTAGE to 11.9), now)
        // Vana hea väärtus ei kustuta hoiatust ega tekita uut
        val later = now + AlertMonitor.MAX_AGE_MS + 1
        assertEquals(1, m.update(r(P.AUX_VOLTAGE to 12.8, at = now), later).size)
        assertEquals(0, AlertMonitor().update(r(P.AUX_VOLTAGE to 11.0, at = now), later).size)
    }

    @Test
    fun cellImbalanceOnlyAtLowCurrent() {
        val m = AlertMonitor()
        val wide = arrayOf(P.CELL_V_MAX to 3.90, P.CELL_V_MIN to 3.80)
        assertEquals(0, m.update(r(*wide, P.BATTERY_CURRENT to -150.0), now).size)
        assertEquals(AlertType.CELL_IMBALANCE, m.update(r(*wide, P.BATTERY_CURRENT to 2.0), now).single().type)
    }

    @Test
    fun socAndTemperature() {
        val m = AlertMonitor(AlertLimits(lowSocPercent = 10.0))
        val a = m.update(r(P.SOC_BMS to 4.5, P.BATTERY_TEMP_MAX to 51.0, P.BATTERY_TEMP_MIN to -3.0), now)
        assertEquals(
            listOf(AlertType.BATTERY_HOT to Severity.CRITICAL, AlertType.SOC_LOW to Severity.CRITICAL, AlertType.BATTERY_COLD to Severity.INFO),
            a.map { it.type to it.severity }.sortedWith(compareByDescending<Pair<AlertType, Severity>> { it.second }.thenBy { it.first.ordinal }),
        )
        // Raskemad eespool
        assertEquals(Severity.INFO, a.last().severity)
    }
}
