package com.eplik.ioniq5.core.scan

import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.ElmException
import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.uds.UdsResponse

/**
 * Käib läbi ühe ECU DID-vahemiku teenusega 0x22 (ReadDataByIdentifier) ja tagastab positiivsed
 * vastused. Päikesekatuse baitide leidmiseks võrreldakse kahte skaneeringut (päike / vari).
 */
class DidScanner(private val elm: Elm327) {

    data class DidValue(val did: Int, val data: ByteArray)

    suspend fun scan(
        ecu: EcuAddress,
        dids: IntRange,
        timeoutMs: Long = 1_000,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<DidValue> {
        require(dids.first in 0..0xFFFF && dids.last in 0..0xFFFF) { "DID peab olema 0000–FFFF" }
        val total = dids.last - dids.first + 1
        val found = mutableListOf<DidValue>()
        dids.forEachIndexed { index, did ->
            val payload = byteArrayOf(0x22, (did shr 8).toByte(), did.toByte())
            val response = try {
                elm.request(ecu, payload, timeoutMs)
            } catch (e: ElmException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            if (response is UdsResponse.Positive) found += DidValue(did, response.data)
            onProgress(index + 1, total)
        }
        return found
    }
}
