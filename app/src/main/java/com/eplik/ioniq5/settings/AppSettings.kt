package com.eplik.ioniq5.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PressureUnit(val label: String, val fromBar: Double, val decimals: Int) {
    BAR("bar", 1.0, 2),
    PSI("psi", 14.5038, 1),
    KPA("kPa", 100.0, 0),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val pressureUnit: PressureUnit = PressureUnit.BAR,
    val lowPressureBar: Double = 2.2,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Aku nimimahutavus kWh; null = automaatne elementide arvu järgi. */
    val batteryCapacityKwh: Double? = null,
    val autoConnect: Boolean = true,
    /** Hoiatus, kui laetus on sellest madalam (%). */
    val lowSocPercent: Double = 10.0,
    /** Hoiatused ka telefoni teavitustena. */
    val alertNotifications: Boolean = true,
    /** Esmakäivituse ohutushoiatus on kinnitatud. */
    val safetyAccepted: Boolean = false,
)

/** Kasutaja seaded, salvestatud SharedPreferences'isse. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val new = transform(_settings.value)
        _settings.value = new
        prefs.edit()
            .putString(KEY_UNIT, new.pressureUnit.name)
            .putFloat(KEY_LOW, new.lowPressureBar.toFloat())
            .putString(KEY_THEME, new.themeMode.name)
            .putFloat(KEY_CAPACITY, new.batteryCapacityKwh?.toFloat() ?: 0f)
            .putBoolean(KEY_AUTO, new.autoConnect)
            .putFloat(KEY_LOW_SOC, new.lowSocPercent.toFloat())
            .putBoolean(KEY_ALERT_NOTIFY, new.alertNotifications)
            .putBoolean(KEY_SAFETY, new.safetyAccepted)
            .apply()
    }

    private fun load() = Settings(
        pressureUnit = runCatching { PressureUnit.valueOf(prefs.getString(KEY_UNIT, null)!!) }.getOrDefault(PressureUnit.BAR),
        lowPressureBar = prefs.getFloat(KEY_LOW, 2.2f).toDouble(),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        batteryCapacityKwh = prefs.getFloat(KEY_CAPACITY, 0f).toDouble().takeIf { it > 0 },
        autoConnect = prefs.getBoolean(KEY_AUTO, true),
        lowSocPercent = prefs.getFloat(KEY_LOW_SOC, 10f).toDouble(),
        alertNotifications = prefs.getBoolean(KEY_ALERT_NOTIFY, true),
        safetyAccepted = prefs.getBoolean(KEY_SAFETY, false),
    )

    private companion object {
        const val KEY_UNIT = "pressure_unit"
        const val KEY_LOW = "low_pressure_bar"
        const val KEY_THEME = "theme"
        const val KEY_CAPACITY = "battery_capacity"
        const val KEY_AUTO = "auto_connect"
        const val KEY_LOW_SOC = "low_soc"
        const val KEY_ALERT_NOTIFY = "alert_notifications"
        const val KEY_SAFETY = "safety_accepted"
    }
}

/** Aku nimimahutavus: seadetest või elementide arvu järgi (180 → 72,6; 192 → 77,4; 144 → 58). */
fun Settings.capacityKwh(cellCount: Int): Double = batteryCapacityKwh ?: when {
    cellCount >= 192 -> 77.4
    cellCount in 1..144 -> 58.0
    else -> 72.6
}
