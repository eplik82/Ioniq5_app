package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.pid.Ioniq5Pids

/** Käimasolev või viimane laadimine: algus, algne SOC ja laetud energia BMS-i koguloenduri järgi. */
data class ChargeSession(
    val startMs: Long,
    val startSoc: Double?,
    val startChargedKwh: Double,
    val chargedKwh: Double,
    val lastMs: Long,
    val active: Boolean,
)

/**
 * Tuvastab laadimise näitude järgi: aku võimsus alla −[thresholdKw] kW vähemalt [minDurationMs]
 * vältel = laadimine (regeneratsioon sõidu ajal on lühiajaline ja jääb välja).
 */
class ChargeSessionTracker(
    private val thresholdKw: Double = 1.0,
    private val minDurationMs: Long = 30_000,
) {
    private var candidateStart: Pair<Long, Readings>? = null
    var session: ChargeSession? = null
        private set

    fun update(readings: Readings, nowMs: Long): ChargeSession? {
        val power = readings.batteryPowerKw()
        val charged = readings.value(Ioniq5Pids.CUMULATIVE_CHARGED_KWH)
        val charging = power != null && power < -thresholdKw
        val current = session

        if (charging && charged != null) {
            if (current?.active == true) {
                session = current.copy(chargedKwh = charged - current.startChargedKwh, lastMs = nowMs)
            } else {
                val (start, startReadings) = candidateStart ?: (nowMs to readings).also { candidateStart = it }
                if (nowMs - start >= minDurationMs) {
                    val startCharged = startReadings.value(Ioniq5Pids.CUMULATIVE_CHARGED_KWH) ?: charged
                    session = ChargeSession(
                        startMs = start,
                        startSoc = startReadings.value(Ioniq5Pids.SOC_DISPLAY) ?: startReadings.value(Ioniq5Pids.SOC_BMS),
                        startChargedKwh = startCharged,
                        chargedKwh = charged - startCharged,
                        lastMs = nowMs,
                        active = true,
                    )
                }
            }
        } else if (power != null) {
            candidateStart = null
            if (current?.active == true) session = current.copy(active = false)
        }
        return session
    }
}
