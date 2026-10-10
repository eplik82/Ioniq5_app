package com.eplik.ioniq5.core.poll

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyIntegratorTest {

    @Test
    fun constantPowerForOneHour() {
        val e = EnergyIntegrator()
        (0..720).forEach { e.add(40.0, it * 5_000L) } // 40 W, iga 5 s, 1 h
        assertEquals(40.0, e.energyWh, 1e-6)
    }

    @Test
    fun longGapsAndRepeatedTimestampsAreIgnored() {
        val e = EnergyIntegrator()
        e.add(40.0, 0)
        e.add(40.0, 0)
        e.add(40.0, 10 * 60_000) // 10 min vahe: ei loe
        e.add(40.0, 10 * 60_000 + 36_000)
        assertEquals(0.4, e.energyWh, 1e-9)
    }
}
