package com.eplik.ioniq5.core.pid

/** 11-bitised CAN-aadressid. Hyundai vastab tavaliselt aadressilt päring + 8 (nt 7E4 → 7EC). */
data class EcuAddress(val request: Int, val response: Int = request + 8) {
    val requestHex: String get() = "%03X".format(request)
    val responseHex: String get() = "%03X".format(response)
}
