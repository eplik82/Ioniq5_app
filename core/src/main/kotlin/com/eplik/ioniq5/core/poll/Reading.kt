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

/** Mooduli temperatuurid 1..16 (puuduvad jäetakse null-iks). */
fun Readings.moduleTemperatures(): List<Double?> =
    (1..Ioniq5Pids.MODULE_TEMP_COUNT).map { value(Ioniq5Pids.moduleTempId(it)) }

/** Soojendi peab olema moodulitest vähemalt nii palju soojem, et lugeda eelsoojendus sisselülitatuks. */
const val PREHEAT_DELTA_C = 5.0

/**
 * Aku eelsoojendus: sees, kui soojendi temperatuur on kõige soojemast moodulist vähemalt [PREHEAT_DELTA_C]
 * võrra kõrgem (Car Scanneri DC-laadimise logis soojendi 24–33 °C, moodulid 11–17 °C). Otsest olekubitti
 * pole leitud, seega on see hinnang. null = andmed puuduvad.
 */
fun Readings.batteryPreheating(): Boolean? {
    val heater = value(Ioniq5Pids.BATTERY_HEATER_TEMP) ?: return null
    val warmest = value(Ioniq5Pids.BATTERY_TEMP_MAX) ?: moduleTemperatures().filterNotNull().maxOrNull() ?: return null
    return heater >= warmest + PREHEAT_DELTA_C
}

/** Ioniq 5 AWD tagasilla ülekandearv ja 255/45 R20 rehvi ümbermõõt (m). */
private const val REAR_GEAR_RATIO = 10.65
private const val TIRE_CIRCUMFERENCE_M = 2.316

/**
 * Kiirus km/h: pidurisüsteemi (ESC) kiirus, selle puudumisel tagamootori pööretest. Pööretest arvutatud
 * kiirus on ~3 % suurem kui rataste kiirus (73,6 vs 71 km/h); esimootor võib olla lahti ühendatud,
 * seega tagamootor on õige allikas.
 */
fun Readings.speedKmh(): Double? = value(Ioniq5Pids.VEHICLE_SPEED)
    ?: value(Ioniq5Pids.MOTOR_RPM)?.let { kotlin.math.abs(it) / REAR_GEAR_RATIO * TIRE_CIRCUMFERENCE_M * 60 / 1000 }

/** Aku kasutatav mahutavus kWh (72,6 kWh × SOH). */
fun Readings.usableCapacityKwh(nominalKwh: Double = 72.6): Double =
    nominalKwh * (value(Ioniq5Pids.SOH) ?: 100.0) / 100

/** Hinnanguline laadimisaeg minutites sihtlaetuseni praeguse võimsusega; null, kui ei lae. */
fun Readings.minutesToSoc(targetPercent: Double, nominalKwh: Double = 72.6): Double? {
    val power = batteryPowerKw() ?: return null
    if (power > -0.5) return null
    val soc = value(Ioniq5Pids.SOC_DISPLAY) ?: value(Ioniq5Pids.SOC_BMS) ?: return null
    if (soc >= targetPercent) return 0.0
    val energyKwh = (targetPercent - soc) / 100 * usableCapacityKwh(nominalKwh)
    return energyKwh / -power * 60
}

/**
 * Pardalaadija AC sisendvõimsus kW. Laadija annab liinipinge (3-faasilises 400 V võrgus ~384 V) ja
 * faasivoolud; faasipinge = liinipinge / √3. Alla 300 V pinge loetakse juba faasipingeks (1-faasiline,
 * kontrollimata). P = faasipinge × faasivoolude summa.
 */
fun Readings.acInputPowerKw(): Double? {
    val v = value(Ioniq5Pids.OBC_AC_VOLTAGE) ?: return null
    val currents = listOf(Ioniq5Pids.OBC_AC_CURRENT_1, Ioniq5Pids.OBC_AC_CURRENT_2, Ioniq5Pids.OBC_AC_CURRENT_3)
        .mapNotNull { value(it) }
    if (v < 50 || currents.sum() < 0.5) return null
    val phaseVolts = if (v > 300) v / kotlin.math.sqrt(3.0) else v
    return phaseVolts * currents.sum() / 1000
}

/** Pardalaadija töötab (DC väljundvool üle 1 A) ehk käib AC-laadimine. null = andmed puuduvad. */
fun Readings.obcActive(): Boolean? = value(Ioniq5Pids.OBC_DC_CURRENT)?.let { it > 1.0 }

/** Pardalaadija DC väljundvõimsus kW. */
fun Readings.obcOutputPowerKw(): Double? {
    val v = value(Ioniq5Pids.OBC_DC_VOLTAGE) ?: return null
    val i = value(Ioniq5Pids.OBC_DC_CURRENT) ?: return null
    if (i < 0.1) return null
    return v * i / 1000
}
