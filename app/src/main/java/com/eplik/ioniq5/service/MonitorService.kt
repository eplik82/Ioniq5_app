package com.eplik.ioniq5.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eplik.ioniq5.Ioniq5Application
import com.eplik.ioniq5.MainActivity
import com.eplik.ioniq5.R
import com.eplik.ioniq5.connection.ConnectionStatus
import com.eplik.ioniq5.core.pid.Ioniq5Pids
import com.eplik.ioniq5.core.poll.batteryPowerKw
import com.eplik.ioniq5.ui.components.formatValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hoiab rakenduse protsessi elus, kui ekraan on kustus või rakendus taustal, et ühendus, pollimine
 * ja salvestus jätkuksid. Teavitusel on laetus ja võimsus. Lõpetab ise, kui ühendus suletakse.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(getString(R.string.app_name)), type)
        val connection = (application as Ioniq5Application).connection
        scope.launch {
            while (true) {
                val status = connection.status.value
                if (status is ConnectionStatus.Disconnected || (status is ConnectionStatus.Failed && !connection.isReconnecting)) {
                    stopSelf()
                    break
                }
                val r = connection.readings.value
                val soc = r[Ioniq5Pids.SOC_DISPLAY]?.value ?: r[Ioniq5Pids.SOC_BMS]?.value
                val text = "${formatValue(soc, 0)} % · ${formatValue(r.batteryPowerKw(), 1)} kW" +
                    if (connection.sleeping.value) " · ${getString(R.string.sleeping_short)}" else ""
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text))
                delay(UPDATE_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_MS = 5_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }
    }
}
