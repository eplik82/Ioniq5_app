package com.eplik.ioniq5.core.poll

import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.ElmException
import com.eplik.ioniq5.core.elm.hexToBytes
import com.eplik.ioniq5.core.pid.PidDefinition
import com.eplik.ioniq5.core.pid.SignalDecoder
import com.eplik.ioniq5.core.uds.UdsResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Pollib PID-e nende sageduse järgi ja hoiab viimaseid näite. [paused] peatab pollimise
 * (nt diagnostika skaneerimise ajaks).
 */
class Poller(
    private val elm: Elm327,
    private val pids: List<PidDefinition>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _readings = MutableStateFlow<Readings>(emptyMap())
    val readings: StateFlow<Readings> = _readings.asStateFlow()

    val paused = MutableStateFlow(false)

    private val lastPolled = mutableMapOf<String, Long>()

    /** Töötab, kuni korutiin tühistatakse. */
    suspend fun run() {
        while (true) {
            paused.first { !it }
            val now = clock()
            val due = pids.filter { now - (lastPolled[it.id] ?: Long.MIN_VALUE / 2) >= it.pollIntervalMs }
            if (due.isEmpty()) {
                delay(IDLE_DELAY_MS)
                continue
            }
            for (pid in due) {
                if (paused.value) break
                lastPolled[pid.id] = clock()
                pollOnce(pid)
            }
        }
    }

    /** Üks päring; vead (ECU magab, NO DATA) jäetakse vahele ja vana väärtus jääb alles. */
    suspend fun pollOnce(pid: PidDefinition) {
        val response = try {
            elm.request(pid.ecu, pid.request.hexToBytes())
        } catch (e: ElmException) {
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        if (response !is UdsResponse.Positive) return
        val time = clock()
        val decoded = pid.signals.mapNotNull { signal ->
            SignalDecoder.decode(signal, response.data)?.let { signal.id to Reading(it, time) }
        }
        _readings.update { it + decoded }
    }

    private companion object {
        const val IDLE_DELAY_MS = 100L
    }
}
