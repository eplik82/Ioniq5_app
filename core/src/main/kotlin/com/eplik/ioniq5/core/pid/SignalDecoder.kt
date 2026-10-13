package com.eplik.ioniq5.core.pid

object SignalDecoder {

    /** Tagastab väärtuse või nulli, kui vastus on liiga lühike. Mitmebaidised väärtused on big-endian, kui pole märgitud teisiti. */
    fun decode(signal: SignalDefinition, data: ByteArray): Double? {
        val end = signal.startByte + signal.length
        if (signal.startByte < 0 || end > data.size) return null

        var raw = 0L
        val indices = if (signal.littleEndian) (end - 1 downTo signal.startByte) else (signal.startByte until end)
        for (i in indices) raw = (raw shl 8) or (data[i].toLong() and 0xFF)

        signal.bit?.let { return ((raw shr it) and 1L).toDouble() }

        if (signal.signed) {
            val bits = signal.length * 8
            if (raw and (1L shl (bits - 1)) != 0L) raw -= 1L shl bits
        }
        return raw * signal.scale + signal.offset
    }
}
