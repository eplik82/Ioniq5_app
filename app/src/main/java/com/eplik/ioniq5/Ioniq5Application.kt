package com.eplik.ioniq5

import android.app.Application
import com.eplik.ioniq5.alert.AlertManager
import com.eplik.ioniq5.connection.ConnectionManager
import com.eplik.ioniq5.record.RecordingManager
import com.eplik.ioniq5.settings.AppSettings
import kotlinx.coroutines.MainScope

class Ioniq5Application : Application() {
    /** Üks ühendus kogu rakenduse kohta (hiljem kasutab sama ka taustteenus ja Android Auto). */
    val connection: ConnectionManager by lazy { ConnectionManager(this, MainScope()) }

    val settings: AppSettings by lazy { AppSettings(this) }

    val recordings: RecordingManager by lazy { RecordingManager(this, connection, MainScope()) }

    val alerts: AlertManager by lazy { AlertManager(this, connection, settings, MainScope()) }

    override fun onCreate() {
        super.onCreate()
        recordings.start()
        alerts.start()
    }
}
