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
 *
 * Une režiim: kui auto on välja lülitatud (12 V pinge alla [auxActiveVolts] kauem kui [sleepAfterMs])
 * või ükski ECU ei vasta [sleepAfterMs] jooksul, küsitakse ainult [probePidId] iga [probeIntervalMs]
 * järel, et mitte hoida auto juhtplokke ärkvel ega tühjendada 12 V akut.
 */
class Poller(
    private val elm: Elm327,
    private val pids: List<PidDefinition>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val probePidId: String = "bms_0101",
    private val auxSignalId: String = "aux_voltage",
    private val auxActiveVolts: Double = 13.2,
    private val sleepAfterMs: Long = 120_000,
    private val probeIntervalMs: Long = 300_000,
) {
    private val _readings = MutableStateFlow<Readings>(emptyMap())
    val readings: StateFlow<Readings> = _readings.asStateFlow()

    val paused = MutableStateFlow(false)

    /** Kasutaja seade: false = une režiimi ei kasutata, kõike pollitakse alati täiskiirusel (tühjendab 12 V akut). */
    val sleepEnabled = MutableStateFlow(true)

    private val lastPolled = mutableMapOf<String, Long>()

    private val _sleeping = MutableStateFlow(false)
    val sleeping: StateFlow<Boolean> = _sleeping.asStateFlow()

    private var lastSuccessMs: Long? = null
    private var auxLowSinceMs: Long? = null

    /** Kas auto paistab olevat välja lülitatud; uuendab [sleeping]. */
    fun updateSleepState(now: Long = clock()): Boolean {
        val lastSuccess = lastSuccessMs ?: now.also { lastSuccessMs = it }
        val noAnswers = now - lastSuccess > sleepAfterMs
        val auxLow = auxLowSinceMs?.let { now - it > sleepAfterMs } ?: false
        _sleeping.value = sleepEnabled.value && (noAnswers || auxLow)
        return _sleeping.value
    }

    /** Kasutaja soovil: ärata kohe (järgmine ring pollib kõike). */
    fun wake() {
        lastSuccessMs = clock()
        auxLowSinceMs = null
        _sleeping.value = false
    }

    /** Töötab, kuni korutiin tühistatakse. */
    suspend fun run() {
        while (true) {
            paused.first { !it }
            val now = clock()
            val sleepingNow = updateSleepState(now)
            val due = pids.filter {
                val interval = if (sleepingNow) {
                    if (it.id == probePidId) probeIntervalMs else return@filter false
                } else {
                    it.pollIntervalMs
                }
                now - (lastPolled[it.id] ?: Long.MIN_VALUE / 2) >= interval
            }
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
        lastSuccessMs = time
        decoded.firstOrNull { it.first == auxSignalId }?.second?.value?.let { aux ->
            auxLowSinceMs = if (aux < auxActiveVolts) auxLowSinceMs ?: time else null
        }
        _readings.update { it + decoded }
    }

    private companion object {
        const val IDLE_DELAY_MS = 100L
    }
}
