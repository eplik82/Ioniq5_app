package com.eplik.ioniq5.core.poll

/**
 * Integreerib võimsust aja järgi (trapetsmeetod). Vahed, mis on pikemad kui [maxGapMs]
 * (ühendus katkes, ECU magas), jäetakse välja, et mitte arvata energiat juurde.
 */
class EnergyIntegrator(private val maxGapMs: Long = 60_000) {
    private var lastTimeMs: Long? = null
    private var lastPowerW: Double? = null

    var energyWh: Double = 0.0
        private set

    fun add(powerW: Double, timeMs: Long) {
        val prevTime = lastTimeMs
        val prevPower = lastPowerW
        if (prevTime != null && prevPower != null && timeMs > prevTime && timeMs - prevTime <= maxGapMs) {
            energyWh += (prevPower + powerW) / 2 * (timeMs - prevTime) / 3_600_000.0
        }
        if (prevTime == null || timeMs > prevTime) {
            lastTimeMs = timeMs
            lastPowerW = powerW
        }
    }
}
