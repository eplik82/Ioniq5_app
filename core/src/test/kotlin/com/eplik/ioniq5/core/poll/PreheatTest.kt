package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import org.junit.Assert.assertEquals
import org.junit.Test

class PreheatTest {

    private fun readings(heater: Double?, max: Double?) = buildMap {
        heater?.let { put(P.BATTERY_HEATER_TEMP, Reading(it, 0)) }
        max?.let { put(P.BATTERY_TEMP_MAX, Reading(it, 0)) }
    }

    @Test
    fun heaterWarmerThanModulesMeansOn() = assertEquals(true, readings(30.0, 17.0).batteryPreheating())

    @Test
    fun heaterAtModuleTemperatureMeansOff() = assertEquals(false, readings(13.0, 22.0).batteryPreheating())

    @Test
    fun missingDataIsUnknown() {
        assertEquals(null, readings(null, 17.0).batteryPreheating())
        assertEquals(null, readings(30.0, null).batteryPreheating())
    }
}
