package com.eplik.ioniq5.core.alert

import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import kotlin.math.abs

enum class AlertType {
    TIRE_LOW,
    TIRE_IMBALANCE,
    AUX_LOW,
    BATTERY_HOT,
    BATTERY_COLD,
    CELL_IMBALANCE,
    SOC_LOW,
}

enum class Severity { INFO, WARNING, CRITICAL }

/** Ratta asukoht rehvihoiatustel (sama järjekord nagu TPMS-i vastuses). */
enum class Wheel { FL, FR, RL, RR }

/**
 * Aktiivne hoiatus. [value] on hoiatuse põhjustanud väärtus (bar, V, °C, %), [wheel] ainult
 * rehvihoiatustel.
 */
data class Alert(val type: AlertType, val severity: Severity, val value: Double, val wheel: Wheel? = null) {
    val key: Pair<AlertType, Wheel?> get() = type to wheel
}

/** Hoiatuspiirid. Rõhk ja madal laetus tulevad seadetest, ülejäänud on fikseeritud. */
data class AlertLimits(
    val lowTirePressureBar: Double = 2.2,
    val lowSocPercent: Double = 10.0,
    val tireImbalanceBar: Double = 0.3,
    val lowAuxVolts: Double = 12.0,
    val criticalAuxVolts: Double = 11.8,
    val batteryHotC: Double = 45.0,
    val batteryCriticalC: Double = 50.0,
    val batteryColdC: Double = 0.0,
    val cellImbalanceV: Double = 0.08,
)

/**
 * Hindab näite ja hoiab aktiivseid hoiatusi. Igal hoiatusel on hüsterees: see lülitub sisse piiril
 * ja välja alles siis, kui väärtus on piirist piisavalt kaugel, et hoiatus ei vilguks.
 * Väärtused, mis on vanemad kui [maxAgeMs], jäetakse arvestamata (hoiatuse olek jääb samaks).
 */
class AlertMonitor(var limits: AlertLimits = AlertLimits(), private val maxAgeMs: Long = MAX_AGE_MS) {

    private val active = linkedMapOf<Pair<AlertType, Wheel?>, Alert>()

    val alerts: List<Alert> get() = active.values.sortedByDescending { it.severity }

    /** Uuendab olekut ja tagastab kõik aktiivsed hoiatused, raskemad eespool. */
    fun update(readings: Readings, nowMs: Long): List<Alert> {
        fun v(id: String): Double? = readings[id]?.takeIf { nowMs - it.timeMs <= maxAgeMs }?.value
        val l = limits

        // Rehvid
        val pressures = TIRES.mapNotNull { (wheel, id) -> v(id)?.let { wheel to it } }
        pressures.forEach { (wheel, bar) ->
            evaluate(
                AlertType.TIRE_LOW, wheel, bar,
                on = bar < l.lowTirePressureBar,
                off = bar >= l.lowTirePressureBar + 0.05,
                severity = if (bar < l.lowTirePressureBar - 0.3) Severity.CRITICAL else Severity.WARNING,
            )
        }
        if (pressures.size == TIRES.size) {
            val spread = pressures.maxOf { it.second } - pressures.minOf { it.second }
            evaluate(AlertType.TIRE_IMBALANCE, null, spread, spread > l.tireImbalanceBar, spread < l.tireImbalanceBar - 0.05, Severity.INFO)
        }

        // 12 V aku
        v(P.AUX_VOLTAGE)?.let { volts ->
            evaluate(
                AlertType.AUX_LOW, null, volts,
                on = volts < l.lowAuxVolts,
                off = volts >= l.lowAuxVolts + 0.2,
                severity = if (volts < l.criticalAuxVolts) Severity.CRITICAL else Severity.WARNING,
            )
        }

        // Kõrgepinge aku temperatuur
        v(P.BATTERY_TEMP_MAX)?.let { t ->
            evaluate(
                AlertType.BATTERY_HOT, null, t,
                on = t >= l.batteryHotC,
                off = t < l.batteryHotC - 2,
                severity = if (t >= l.batteryCriticalC) Severity.CRITICAL else Severity.WARNING,
            )
        }
        v(P.BATTERY_TEMP_MIN)?.let { t ->
            evaluate(AlertType.BATTERY_COLD, null, t, t <= l.batteryColdC, t > l.batteryColdC + 2, Severity.INFO)
        }

        // Elementide pingevahe: ainult väikese vooluga, sest koormuse all kasvab vahe ajutiselt
        val current = v(P.BATTERY_CURRENT)
        val maxCell = v(P.CELL_V_MAX)
        val minCell = v(P.CELL_V_MIN)
        if (current != null && abs(current) < QUIET_CURRENT_A && maxCell != null && minCell != null) {
            val gap = maxCell - minCell
            evaluate(AlertType.CELL_IMBALANCE, null, gap, gap >= l.cellImbalanceV, gap < l.cellImbalanceV - 0.02, Severity.WARNING)
        }

        // Madal laetus
        (v(P.SOC_DISPLAY) ?: v(P.SOC_BMS))?.let { soc ->
            evaluate(
                AlertType.SOC_LOW, null, soc,
                on = soc <= l.lowSocPercent,
                off = soc > l.lowSocPercent + 2,
                severity = if (soc <= l.lowSocPercent / 2) Severity.CRITICAL else Severity.WARNING,
            )
        }
        return alerts
    }

    private fun evaluate(type: AlertType, wheel: Wheel?, value: Double, on: Boolean, off: Boolean, severity: Severity) {
        val key = type to wheel
        val isActive = key in active
        when {
            on || (isActive && !off) -> active[key] = Alert(type, severity, value, wheel)
            else -> active.remove(key)
        }
    }

    /** Kõik hoiatused maha (nt seadete muutmisel hinnatakse uuesti). */
    fun reset() = active.clear()

    companion object {
        /** Une režiimis loetakse BMS-i iga 5 min järel, rehve harvem; 10 min jooksul on väärtus kehtiv. */
        const val MAX_AGE_MS = 10 * 60_000L
        const val QUIET_CURRENT_A = 30.0

        val TIRES = listOf(
            Wheel.FL to P.TIRE_PRESSURE_FL,
            Wheel.FR to P.TIRE_PRESSURE_FR,
            Wheel.RL to P.TIRE_PRESSURE_RL,
            Wheel.RR to P.TIRE_PRESSURE_RR,
        )
    }
}
