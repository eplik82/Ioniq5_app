package com.eplik.ioniq5.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.eplik.ioniq5.MainActivity
import com.eplik.ioniq5.R
import com.eplik.ioniq5.connection.ConnectionManager
import com.eplik.ioniq5.core.alert.Alert
import com.eplik.ioniq5.core.alert.AlertLimits
import com.eplik.ioniq5.core.alert.AlertMonitor
import com.eplik.ioniq5.core.alert.AlertType
import com.eplik.ioniq5.core.alert.Severity
import com.eplik.ioniq5.core.alert.Wheel
import com.eplik.ioniq5.settings.AppSettings
import com.eplik.ioniq5.settings.PressureUnit
import com.eplik.ioniq5.ui.components.formatValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Jälgib näite ([AlertMonitor]) ja annab aktiivsed hoiatused UI-le ja Android Autole.
 * Uue hoiatuse (hoiatus või kriitiline) kohta tehakse telefoni teavitus, kui see on seadetes lubatud.
 */
class AlertManager(
    private val context: Context,
    private val connection: ConnectionManager,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private val monitor = AlertMonitor()
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private var notified = emptySet<Pair<AlertType, Wheel?>>()

    fun start() {
        createChannel()
        scope.launch {
            combine(connection.readings, settings.settings) { readings, s -> readings to s }.collect { (readings, s) ->
                val limits = AlertLimits(lowTirePressureBar = s.lowPressureBar, lowSocPercent = s.lowSocPercent)
                if (limits != monitor.limits) {
                    monitor.limits = limits
                    monitor.reset()
                }
                val list = monitor.update(readings, System.currentTimeMillis())
                _alerts.value = list
                notify(list, s.alertNotifications, s.pressureUnit)
            }
        }
    }

    private fun notify(list: List<Alert>, enabled: Boolean, unit: PressureUnit) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val current = list.filter { it.severity != Severity.INFO }
        val keys = current.map { it.key }.toSet()
        (notified - keys).forEach { manager.cancel(notificationId(it)) }
        if (enabled) {
            current.filter { it.key !in notified }.forEach { alert ->
                runCatching { manager.notify(notificationId(alert.key), build(alert, unit)) }
            }
        }
        notified = keys
    }

    private fun build(alert: Alert, unit: PressureUnit) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(context.getString(R.string.alert_notification_title))
        .setContentText(alert.text(context, unit))
        .setPriority(if (alert.severity == Severity.CRITICAL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun notificationId(key: Pair<AlertType, Wheel?>) = 100 + key.first.ordinal * 10 + (key.second?.ordinal ?: 9)

    private companion object {
        const val CHANNEL_ID = "alerts"
    }
}

/** Hoiatuse tekst kasutaja keeles (telefon, teavitus ja Android Auto). */
fun Alert.text(context: Context, unit: PressureUnit): String = when (type) {
    AlertType.TIRE_LOW -> context.getString(
        R.string.alert_tire_low,
        context.getString(wheel.label()),
        "${formatValue(value * unit.fromBar, unit.decimals)} ${unit.label}",
    )
    AlertType.TIRE_IMBALANCE -> context.getString(R.string.alert_tire_imbalance, "${formatValue(value * unit.fromBar, unit.decimals)} ${unit.label}")
    AlertType.AUX_LOW -> context.getString(R.string.alert_aux_low, formatValue(value, 1))
    AlertType.BATTERY_HOT -> context.getString(R.string.alert_battery_hot, formatValue(value, 0))
    AlertType.BATTERY_COLD -> context.getString(R.string.alert_battery_cold, formatValue(value, 0))
    AlertType.CELL_IMBALANCE -> context.getString(R.string.alert_cell_imbalance, formatValue(value * 1000, 0))
    AlertType.SOC_LOW -> context.getString(R.string.alert_soc_low, formatValue(value, 0))
}

private fun Wheel?.label(): Int = when (this) {
    Wheel.FL -> R.string.wheel_fl
    Wheel.FR -> R.string.wheel_fr
    Wheel.RL -> R.string.wheel_rl
    Wheel.RR -> R.string.wheel_rr
    null -> R.string.no_value
}
