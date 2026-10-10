package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.pid.Ioniq5Pids

data class Reading(val value: Double, val timeMs: Long)

/** Kõik viimased näidud signaali ID järgi. */
typealias Readings = Map<String, Reading>

fun Readings.value(id: String): Double? = this[id]?.value

/**
 * Aku võimsus kW: positiivne = tühjeneb (sõit, kütmine), negatiivne = laeb.
 * Arvutatakse aku pingest ja voolust.
 */
fun Readings.batteryPowerKw(): Double? {
    val v = value(Ioniq5Pids.BATTERY_VOLTAGE) ?: return null
    val i = value(Ioniq5Pids.BATTERY_CURRENT) ?: return null
    return v * i / 1000
}

/** Elementide pinged järjekorras; elemendid, mille pinge on ~0 (pole olemas), jäetakse välja. */
fun Readings.cellVoltages(): List<Double> =
    (1..Ioniq5Pids.CELL_COUNT_MAX)
        .map { value(Ioniq5Pids.cellId(it)) }
        .takeWhile { it != null && it > 1.0 }
        .map { it!! }
