package com.eplik.ioniq5.core.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Baitide kanal adapterini (BLE, klassikaline Bluetooth või simulaator).
 * Ülemised kihid ei tea, mis transport all on.
 */
interface Transport {
    val state: StateFlow<TransportState>

    /** Adapterist saabuvad baidid tükkidena, nagu need tulevad. */
    val incoming: Flow<ByteArray>

    suspend fun connect()

    suspend fun disconnect()

    suspend fun write(data: ByteArray)
}

enum class TransportState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }
