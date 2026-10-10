package com.eplik.ioniq5.core.scan

import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.ElmException
import com.eplik.ioniq5.core.elm.hexToBytes
import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.uds.UdsResponse

/**
 * Otsib, millised ECU-d vastavad. Igale aadressile saadetakse TesterPresent (`3E 00`);
 * vastanutelt loetakse identifikaatorid. Kõik päringud on ainult lugemiseks.
 */
class EcuScanner(private val elm: Elm327) {

    data class FoundEcu(val address: EcuAddress, val identifiers: Map<Int, String>)

    suspend fun scan(
        requestIds: IntRange = 0x700..0x7F7,
        timeoutMs: Long = 1_000,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<FoundEcu> {
        val ids = requestIds.filter { it != OBD_BROADCAST }
        val found = mutableListOf<FoundEcu>()
        ids.forEachIndexed { index, id ->
            val ecu = EcuAddress(id)
            if (responds(ecu, timeoutMs)) found += FoundEcu(ecu, readIdentifiers(ecu, timeoutMs))
            onProgress(index + 1, ids.size)
        }
        return found
    }

    private suspend fun responds(ecu: EcuAddress, timeoutMs: Long): Boolean = try {
        // Ka eitav vastus (7F 3E ..) tähendab, et ECU on olemas
        elm.request(ecu, TESTER_PRESENT, timeoutMs)
        true
    } catch (e: ElmException) {
        false
    } catch (e: IllegalArgumentException) {
        // Vastus tuli, aga ootamatu kujuga: ECU on siiski olemas
        true
    }

    private suspend fun readIdentifiers(ecu: EcuAddress, timeoutMs: Long): Map<Int, String> =
        IDENTIFIERS.mapNotNull { did ->
            val response = try {
                elm.request(ecu, "22%04X".format(did).hexToBytes(), timeoutMs)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            (response as? UdsResponse.Positive)?.let { did to it.data.drop(3).toByteArray().toPrintable() }
        }.toMap()

    companion object {
        private const val OBD_BROADCAST = 0x7DF
        private val TESTER_PRESENT = byteArrayOf(0x3E, 0x00)

        /** F187 osanumber, F191 riistvara number, F195 tarkvara versioon, F18C seerianumber. */
        val IDENTIFIERS = listOf(0xF187, 0xF191, 0xF195, 0xF18C)
    }
}

/** ASCII-tekst, kui baidid on prinditavad, muidu hex. */
internal fun ByteArray.toPrintable(): String {
    val trimmed = dropLastWhile { it == 0.toByte() || it == 0x20.toByte() || it == 0xFF.toByte() }
    return if (trimmed.isNotEmpty() && trimmed.all { it in 0x20..0x7E }) {
        trimmed.toByteArray().decodeToString()
    } else {
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }
}
