package com.eplik.ioniq5.core.uds

sealed interface UdsResponse {

    /** Positiivne vastus; [data] sisaldab ka vastuse SID-i (nt 0x62) ja DID-i. */
    class Positive(val data: ByteArray) : UdsResponse

    class Negative(val requestSid: Int, val nrc: Int) : UdsResponse

    companion object {
        private const val NEGATIVE_RESPONSE = 0x7F

        fun parse(data: ByteArray, requestSid: Int): UdsResponse {
            require(data.isNotEmpty()) { "Tühi vastus" }
            val sid = data[0].toInt() and 0xFF
            return when {
                sid == NEGATIVE_RESPONSE && data.size >= 3 ->
                    Negative(data[1].toInt() and 0xFF, data[2].toInt() and 0xFF)
                sid == requestSid + 0x40 -> Positive(data)
                else -> throw IllegalArgumentException("Ootamatu vastus SID %02X päringule %02X".format(sid, requestSid))
            }
        }
    }
}
