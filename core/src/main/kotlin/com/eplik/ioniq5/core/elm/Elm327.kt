package com.eplik.ioniq5.core.elm

import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.transport.Transport
import com.eplik.ioniq5.core.uds.UdsResponse
import com.eplik.ioniq5.core.uds.UdsSafety
import kotlinx.coroutines.CoroutineStart
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
            val text = withTimeout(timeoutMs) { response.await() }
            text.substringBefore(PROMPT)
                .split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != cmd && !it.startsWith("SEARCHING") }
        }
    }

    suspend fun initialize() {
        command("ATZ", timeoutMs = 5_000)
        for (cmd in INIT_COMMANDS) {
            val reply = command(cmd)
            if (reply.none { it.endsWith("OK") }) throw ElmException("$cmd: ${reply.joinToString(" ")}")
        }
        currentHeader = null
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
