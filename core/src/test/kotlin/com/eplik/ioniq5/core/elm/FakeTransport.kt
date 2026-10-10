package com.eplik.ioniq5.core.elm

import com.eplik.ioniq5.core.transport.Transport
import com.eplik.ioniq5.core.transport.TransportState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Vastab etteantud käskudele nagu ELM327; teadmata käsule vastab "?". */
class FakeTransport(private val replies: Map<String, String>) : Transport {
    val sent = mutableListOf<String>()

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<ByteArray> = _incoming

    override suspend fun connect() {
        _state.value = TransportState.CONNECTED
    }

    override suspend fun disconnect() {
        _state.value = TransportState.DISCONNECTED
    }

    override suspend fun write(data: ByteArray) {
        val cmd = data.decodeToString().trimEnd('\r')
        sent += cmd
        val reply = replies[cmd] ?: "?"
        // Vastus tuleb kahes tükis nagu päris BLE-adapterist
        val text = "$reply\r\r>"
        _incoming.emit(text.substring(0, text.length / 2).encodeToByteArray())
        _incoming.emit(text.substring(text.length / 2).encodeToByteArray())
    }
}
