package com.eplik.ioniq5.core.elm

import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.transport.Transport
import com.eplik.ioniq5.core.uds.UdsResponse
import com.eplik.ioniq5.core.uds.UdsSafety
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * ELM327-ühilduva adapteri (LELink) juhtimine: AT-käsud ja UDS-päringud ECU-dele.
 * Korraga on käimas ainult üks käsk; vastus loetakse kuni `>` viibani.
 */
class Elm327(
    private val transport: Transport,
    private val defaultTimeoutMs: Long = 2_000,
) {
    private val mutex = Mutex()
    private val requestMutex = Mutex()
    private var currentHeader: EcuAddress? = null

    /** Kutsutakse iga käsu järel (ka aegumisel); diagnostikalogi ja seansi salvestamise jaoks. */
    var trafficListener: ((TrafficEntry) -> Unit)? = null

    /** Saadab käsu ja tagastab vastuse read ilma kaja, viiba ja tühjade ridadeta. */
    suspend fun command(cmd: String, timeoutMs: Long = defaultTimeoutMs): List<String> = mutex.withLock {
        coroutineScope {
            val response = async(start = CoroutineStart.UNDISPATCHED) {
                val text = StringBuilder()
                transport.incoming
                    .takeWhile { chunk ->
                        text.append(chunk.decodeToString())
                        PROMPT !in text
                    }
                    .collect()
                text.toString()
            }
            transport.write("$cmd\r".encodeToByteArray())
            val text = try {
                withTimeout(timeoutMs) { response.await() }
            } catch (e: TimeoutCancellationException) {
                response.cancel()
                trafficListener?.invoke(TrafficEntry(System.currentTimeMillis(), cmd, listOf(TIMEOUT)))
                throw ElmException("$TIMEOUT: $cmd")
            }
            val lines = text.substringBefore(PROMPT)
                .split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != cmd && !it.startsWith("SEARCHING") }
            trafficListener?.invoke(TrafficEntry(System.currentTimeMillis(), cmd, lines))
            lines
        }
    }

    /** Lähtestab adapteri ja seadistab CAN-i. Tagastab adapteri versiooni (ATZ vastus). */
    suspend fun initialize(): String {
        val version = command("ATZ", timeoutMs = 5_000).lastOrNull().orEmpty()
        for (cmd in INIT_COMMANDS) expectOk(cmd)
        currentHeader = null
        return version
    }

    /**
     * Diagnostikakonsooli käsk: AT-käsk adapterile või hex-päring praegusele päisele.
     * Hex-päringud läbivad sama ohutuskontrolli mis [request].
     */
    suspend fun console(input: String): List<String> = requestMutex.withLock {
        val cmd = input.uppercase().filterNot { it.isWhitespace() }
        if (cmd.startsWith("AT")) {
            if (BLOCKED_AT.any { cmd.startsWith(it) }) throw SecurityException("$cmd ei ole konsoolis lubatud")
            // Kasutaja võib muuta päist või seadeid, seega järgmine request() seab päise uuesti
            currentHeader = null
            command(cmd, timeoutMs = if (cmd == "ATZ") 5_000 else defaultTimeoutMs)
        } else {
            if (cmd.isEmpty() || cmd.length % 2 != 0 || !cmd.all { it in '0'..'9' || it in 'A'..'F' }) {
                throw IllegalArgumentException("Vigane hex-päring: $input")
            }
            UdsSafety.requireAllowed(cmd.hexToBytes())
            command(cmd)
        }
    }

    /** UDS/OBD päring ühele ECU-le. Lubatud on ainult lugemisteenused (vt [UdsSafety]). */
    suspend fun request(
        ecu: EcuAddress,
        payload: ByteArray,
        timeoutMs: Long = defaultTimeoutMs,
    ): UdsResponse = requestMutex.withLock {
        UdsSafety.requireAllowed(payload)
        if (currentHeader != ecu) {
            expectOk("ATSH${ecu.requestHex}")
            expectOk("ATCRA${ecu.responseHex}")
            currentHeader = ecu
        }
        val lines = command(payload.toHex(), timeoutMs)
        lines.firstOrNull { line -> ERRORS.any { line.contains(it) } }?.let { throw ElmException(it) }
        val data = IsoTpAssembler.assemble(lines)[ecu.response]
            ?: throw ElmException("${ecu.responseHex} ei vastanud: ${lines.joinToString(" ")}")
        UdsResponse.parse(data, requestSid = payload[0].toInt() and 0xFF)
    }

    private suspend fun expectOk(cmd: String) {
        val reply = command(cmd)
        if (reply.none { it.endsWith("OK") }) throw ElmException("$cmd: ${reply.joinToString(" ")}")
    }

    companion object {
        private const val PROMPT = ">"
        const val TIMEOUT = "TIMEOUT"

        /** Monitooringurežiimid ujutavad adapteri üle, ATPP kirjutab adapteri püsimällu. */
        private val BLOCKED_AT = listOf("ATMA", "ATMR", "ATMT", "ATPP")

        val INIT_COMMANDS = listOf(
            "ATE0", // kaja välja
            "ATL0", // reavahetused välja
            "ATS0", // tühikud välja
            "ATH1", // CAN päised sisse (vajalik mitme ECU ja ISO-TP jaoks)
            "ATCAF1", // automaatne ISO-TP vormindus
            "ATSP6", // ISO 15765-4 CAN, 11 bit, 500 kbit/s
        )

        private val ERRORS = listOf("NO DATA", "CAN ERROR", "BUFFER FULL", "STOPPED", "UNABLE TO CONNECT", "BUS INIT", "?")
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

fun String.hexToBytes(): ByteArray = replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
