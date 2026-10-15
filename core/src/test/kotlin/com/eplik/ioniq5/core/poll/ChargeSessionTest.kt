package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeSessionTest {

    private fun readings(currentA: Double, chargedKwh: Double, soc: Double = 50.0): Readings = mapOf(
        P.BATTERY_VOLTAGE to Reading(680.0, 0),
        P.BATTERY_CURRENT to Reading(currentA, 0),
        P.CUMULATIVE_CHARGED_KWH to Reading(chargedKwh, 0),
        P.SOC_BMS to Reading(soc, 0),
    )

    @Test
    fun shortRegenIsNotACharge() {
        val t = ChargeSessionTracker()
        t.update(readings(-50.0, 100.0), 0)
        t.update(readings(-50.0, 100.1), 10_000)
        assertNull(t.update(readings(20.0, 100.1), 20_000))
    }

    @Test
    fun longRegenWhileDrivingIsNotACharge() {
        // Pikk laskumine: taaste üle 30 s, auto liigub (tagamootor ~3000 p/min ≈ 39 km/h)
        val t = ChargeSessionTracker()
        val moving = { r: Readings -> r + (P.MOTOR_RPM to Reading(3000.0, 0)) }
        t.update(moving(readings(-50.0, 100.0)), 0)
        assertNull(t.update(moving(readings(-50.0, 100.5)), 60_000))
        assertFalse(moving(readings(-50.0, 100.5)).isCharging())
        assertTrue(readings(-50.0, 100.5).isCharging())
    }

    @Test
    fun chargingSessionCountsEnergyAndEnds() {
        val t = ChargeSessionTracker()
        t.update(readings(-90.0, 100.0, soc = 40.0), 0)
        val s1 = t.update(readings(-90.0, 101.0), 40_000)!!
        assertTrue(s1.active)
        assertEquals(40.0, s1.startSoc!!, 1e-9)
        assertEquals(1.0, s1.chargedKwh, 1e-9)
        val s2 = t.update(readings(-90.0, 110.5), 600_000)!!
        assertEquals(10.5, s2.chargedKwh, 1e-9)
        assertFalse(t.update(readings(0.5, 110.5), 610_000)!!.active)
    }

    @Test
    fun speedAndEta() {
        val r = readings(-100.0, 0.0, soc = 40.0) + mapOf(
            P.MOTOR_RPM to Reading(8766.0, 0),
            P.SOH to Reading(100.0, 0),
        )
        assertEquals(114.4, r.speedKmh()!!, 0.1)
        // 40 % → 80 % = 29,04 kWh, 68 kW juures ~25,6 min
        assertEquals(29.04 / 68.0 * 60, r.minutesToSoc(80.0)!!, 0.01)
    }
}
