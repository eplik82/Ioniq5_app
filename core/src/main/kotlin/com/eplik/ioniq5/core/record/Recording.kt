package com.eplik.ioniq5.core.record

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.core.poll.acInputPowerKw
import com.eplik.ioniq5.core.poll.batteryPowerKw
import com.eplik.ioniq5.core.poll.speedKmh
import java.util.Locale

/** Salvestuse CSV-vorming: üks rida iga paari sekundi järel. Tühi väli = väärtus puudub või aegunud. */
object RecordingFormat {

    enum class Column(val key: String) {
        TIME("time_ms"),
        SOC("soc"),
        POWER("power_kw"),
        VOLTAGE("battery_v"),
        CURRENT("battery_a"),
        TEMP_MIN("temp_min_c"),
        TEMP_MAX("temp_max_c"),
        SPEED("speed_kmh"),
        ODOMETER("odometer_km"),
        AUX("aux_v"),
        SOLAR("solar_w"),
        CELL_MIN("cell_min_v"),
        CELL_MAX("cell_max_v"),
        CHARGED_TOTAL("charged_kwh_total"),
        DISCHARGED_TOTAL("discharged_kwh_total"),
        AC_POWER("ac_power_kw"),
        STEERING("steering_deg"),
    }

    val header: String = Column.entries.joinToString(",") { it.key }

    /** Näit loetakse ainult siis, kui see on viimase [maxAgeMs] jooksul uuendatud. */
    fun row(readings: Readings, timeMs: Long, maxAgeMs: Long = 15_000): String {
        fun fresh(id: String) = readings[id]?.takeIf { timeMs - it.timeMs <= maxAgeMs }?.value
        val powerFresh = fresh(P.BATTERY_VOLTAGE) != null && fresh(P.BATTERY_CURRENT) != null
        val values: Map<Column, Double?> = mapOf(
            Column.SOC to (fresh(P.SOC_DISPLAY) ?: fresh(P.SOC_BMS)),
            Column.POWER to readings.batteryPowerKw()?.takeIf { powerFresh },
            Column.VOLTAGE to fresh(P.BATTERY_VOLTAGE),
            Column.CURRENT to fresh(P.BATTERY_CURRENT),
            Column.TEMP_MIN to fresh(P.BATTERY_TEMP_MIN),
            Column.TEMP_MAX to fresh(P.BATTERY_TEMP_MAX),
            Column.SPEED to readings.speedKmh()?.takeIf { fresh(P.MOTOR_RPM) != null },
            Column.ODOMETER to readings[P.ODOMETER]?.value,
            Column.AUX to fresh(P.AUX_VOLTAGE),
            Column.SOLAR to fresh(P.SOLAR_POWER),
            Column.CELL_MIN to fresh(P.CELL_V_MIN),
            Column.CELL_MAX to fresh(P.CELL_V_MAX),
            Column.CHARGED_TOTAL to fresh(P.CUMULATIVE_CHARGED_KWH),
            Column.DISCHARGED_TOTAL to fresh(P.CUMULATIVE_DISCHARGED_KWH),
            Column.AC_POWER to readings.acInputPowerKw()?.takeIf { fresh(P.OBC_AC_VOLTAGE) != null },
            Column.STEERING to fresh(P.STEERING_ANGLE),
        )
        return Column.entries.joinToString(",") { col ->
            if (col == Column.TIME) timeMs.toString() else values[col]?.let { String.format(Locale.US, "%.3f", it) } ?: ""
        }
    }

    /** Loeb CSV read; tundmatud või vigased read jäetakse vahele. */
    fun parse(lines: Sequence<String>): List<Row> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()
        val keys = iterator.next().split(",")
        val index = Column.entries.associateWith { keys.indexOf(it.key) }
        val rows = mutableListOf<Row>()
        while (iterator.hasNext()) {
            val parts = iterator.next().split(",")
            val time = index[Column.TIME]?.let { parts.getOrNull(it)?.toLongOrNull() } ?: continue
            rows += Row(
                time,
                Column.entries.associateWith { col -> index[col]?.takeIf { it >= 0 }?.let { parts.getOrNull(it)?.toDoubleOrNull() } },
            )
        }
        return rows
    }

    data class Row(val timeMs: Long, val values: Map<Column, Double?>) {
        operator fun get(column: Column): Double? = values[column]
    }
}

enum class RecordingType { DRIVE, CHARGE, PARKED }

data class RecordingSummary(
    val startMs: Long,
    val endMs: Long,
    val type: RecordingType,
    val distanceKm: Double?,
    val dischargedKwh: Double?,
    val chargedKwh: Double?,
    val socStart: Double?,
    val socEnd: Double?,
    val maxSpeedKmh: Double?,
    val maxChargePowerKw: Double?,
    val solarWh: Double,
) {
    val durationMs: Long get() = endMs - startMs

    /** Netokulu kWh/100 km (tühjendatud − laetud, sh regeneratsioon). */
    val consumptionKwhPer100Km: Double?
        get() {
            val d = distanceKm ?: return null
            if (d < 1) return null
            val used = (dischargedKwh ?: return null) - (chargedKwh ?: 0.0)
            return used / d * 100
        }
}

object RecordingSummarizer {

    fun summarize(rows: List<RecordingFormat.Row>): RecordingSummary? {
        if (rows.isEmpty()) return null
        fun first(c: RecordingFormat.Column) = rows.firstNotNullOfOrNull { it[c] }
        fun last(c: RecordingFormat.Column) = rows.asReversed().firstNotNullOfOrNull { it[c] }
        fun delta(c: RecordingFormat.Column): Double? {
            val a = first(c) ?: return null
            val b = last(c) ?: return null
            return (b - a).coerceAtLeast(0.0)
        }

        val distance = delta(RecordingFormat.Column.ODOMETER)
        val charged = delta(RecordingFormat.Column.CHARGED_TOTAL)
        val discharged = delta(RecordingFormat.Column.DISCHARGED_TOTAL)
        val maxSpeed = rows.mapNotNull { it[RecordingFormat.Column.SPEED] }.maxOrNull()
        val maxCharge = rows.mapNotNull { it[RecordingFormat.Column.POWER] }.minOrNull()?.takeIf { it < 0 }?.let { -it }

        var solarWh = 0.0
        rows.zipWithNext().forEach { (a, b) ->
            val pa = a[RecordingFormat.Column.SOLAR]
            val pb = b[RecordingFormat.Column.SOLAR]
            val dt = b.timeMs - a.timeMs
            if (pa != null && pb != null && dt in 1..60_000) solarWh += (pa + pb) / 2 * dt / 3_600_000.0
        }

        val moved = (distance ?: 0.0) >= 0.5 || (maxSpeed ?: 0.0) >= 10
        val type = when {
            moved -> RecordingType.DRIVE
            (charged ?: 0.0) >= 0.2 -> RecordingType.CHARGE
            else -> RecordingType.PARKED
        }
        return RecordingSummary(
            startMs = rows.first().timeMs,
            endMs = rows.last().timeMs,
            type = type,
            distanceKm = distance,
            dischargedKwh = discharged,
            chargedKwh = charged,
            socStart = first(RecordingFormat.Column.SOC),
            socEnd = last(RecordingFormat.Column.SOC),
            maxSpeedKmh = maxSpeed,
            maxChargePowerKw = maxCharge,
            solarWh = solarWh,
        )
    }
}
