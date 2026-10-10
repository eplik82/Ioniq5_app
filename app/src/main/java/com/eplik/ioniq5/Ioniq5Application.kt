package com.eplik.ioniq5

import android.app.Application
import com.eplik.ioniq5.connection.ConnectionManager
import kotlinx.coroutines.MainScope

class Ioniq5Application : Application() {
    /** Üks ühendus kogu rakenduse kohta (hiljem kasutab sama ka taustteenus ja Android Auto). */
    val connection: ConnectionManager by lazy { ConnectionManager(this, MainScope()) }
}
