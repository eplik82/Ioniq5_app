package com.eplik.ioniq5.core.record

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Reading
import com.eplik.ioniq5.core.record.RecordingFormat.Column
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingTest {

    private fun readings(t: Long, soc: Double, odo: Double, charged: Double, discharged: Double, rpm: Double, current: Double = 30.0) = mapOf(
        P.SOC_BMS to Reading(soc, t),
        P.BATTERY_VOLTAGE to Reading(650.0, t),
        P.BATTERY_CURRENT to Reading(current, t),
        P.ODOMETER to Reading(odo, t),
        P.CUMULATIVE_CHARGED_KWH to Reading(charged, t),
        P.CUMULATIVE_DISCHARGED_KWH to Reading(discharged, t),
        P.MOTOR_RPM to Reading(rpm, t),
        P.SOLAR_POWER to Reading(40.0, t),
    )

    @Test
    fun rowsRoundTripAndStaleValuesAreEmpty() {
        val r = readings(1_000, 50.0, 1000.0, 10.0, 20.0, 0.0)
        val row = RecordingFormat.row(r, 1_000)
        val parsed = RecordingFormat.parse(sequenceOf(RecordingFormat.header, row)).single()
        assertEquals(50.0, parsed[Column.SOC]!!, 1e-9)
        assertEquals(650.0 * 30.0 / 1000, parsed[Column.POWER]!!, 1e-9)
        // 60 s hiljem on kõik aegunud peale odomeetri
        val stale = RecordingFormat.parse(sequenceOf(RecordingFormat.header, RecordingFormat.row(r, 61_000))).single()
        assertNull(stale[Column.SOC])
        assertEquals(1000.0, stale[Column.ODOMETER]!!, 1e-9)
    }

    @Test
    fun driveSummary() {
        val lines = listOf(RecordingFormat.header) + (0..360).map { i ->
            val t = i * 10_000L // 1 h
            RecordingFormat.row(readings(t, 80.0 - i * 0.05, 1000.0 + i * 0.25, 10.0 + i * 0.001, 20.0 + i * 0.06, 6000.0), t)
        }
        val s = RecordingSummarizer.summarize(RecordingFormat.parse(lines.asSequence()))!!
        assertEquals(RecordingType.DRIVE, s.type)
        assertEquals(90.0, s.distanceKm!!, 1e-6)
        assertEquals(21.6, s.dischargedKwh!!, 1e-6)
        assertEquals((21.6 - 0.36) / 90 * 100, s.consumptionKwhPer100Km!!, 1e-6)
        assertEquals(40.0, s.solarWh, 1e-6)
        assertEquals(80.0, s.socStart!!, 1e-9)
    }

    @Test
    fun chargeSummary() {
        val lines = listOf(RecordingFormat.header) + (0..60).map { i ->
            val t = i * 60_000L
            RecordingFormat.row(readings(t, 40.0 + i, 1000.0, 10.0 + i * 0.18, 20.0, 0.0, current = -15.0), t)
        }
        val s = RecordingSummarizer.summarize(RecordingFormat.parse(lines.asSequence()))!!
        assertEquals(RecordingType.CHARGE, s.type)
        assertEquals(10.8, s.chargedKwh!!, 1e-6)
        assertEquals(650.0 * 15 / 1000, s.maxChargePowerKw!!, 1e-9)
        assertNull(s.consumptionKwhPer100Km)
    }
}
