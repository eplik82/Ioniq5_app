package com.eplik.ioniq5.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.eplik.ioniq5.Ioniq5Application

/**
 * Android Auto sisenemispunkt. Rakendus on isiklikuks kasutuseks ja paigaldatakse ise
 * (Android Auto arendajarežiim → „Unknown sources”), seega lubatakse kõik hostid.
 */
class MonitorCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = MonitorSession()
}

class MonitorSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val app = carContext.applicationContext as Ioniq5Application
        // Kui telefonis pole ühendust, ühenda viimase adapteriga
        if (app.settings.settings.value.autoConnect) app.connection.connectToLast()
        return DashboardScreen(carContext)
    }
}
