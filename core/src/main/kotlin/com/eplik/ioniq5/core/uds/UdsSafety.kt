package com.eplik.ioniq5.core.uds

/**
 * Rakendus ainult loeb autost andmeid. Kõik teised teenused (kirjutamine, rutiinid,
 * veakoodide kustutamine, sessioonid, turvaligipääs) on siin keelatud.
 */
object UdsSafety {

    val ALLOWED_SERVICES: Map<Int, String> = mapOf(
        0x01 to "OBD-II hetkeandmed",
        0x09 to "OBD-II sõiduki info",
        0x19 to "ReadDTCInformation",
        0x1A to "KWP ReadEcuIdentification",
        0x21 to "KWP ReadDataByLocalIdentifier",
        0x22 to "ReadDataByIdentifier",
        0x3E to "TesterPresent",
    )

    fun isAllowed(payload: ByteArray): Boolean =
        payload.isNotEmpty() && (payload[0].toInt() and 0xFF) in ALLOWED_SERVICES

    fun requireAllowed(payload: ByteArray) {
        if (!isAllowed(payload)) {
            val sid = payload.firstOrNull()?.let { "%02X".format(it.toInt() and 0xFF) } ?: "tühi"
            throw SecurityException("Teenus $sid ei ole lubatud: rakendus ainult loeb andmeid")
        }
    }
}
