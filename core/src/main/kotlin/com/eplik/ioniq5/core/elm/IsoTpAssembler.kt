package com.eplik.ioniq5.core.elm

/**
 * Pannakse kokku ISO-TP (ISO 15765-2) kaadrid ELM327 väljundist, kui päised on sees (`ATH1`).
 *
 * Iga rida on üks CAN-kaader 11-bitise ID-ga, nt `7EC 10 3E 62 01 01 FF F7 E7` või tühikuteta
 * `7EC103E620101FFF7E7`. Tagastab iga vastanud ECU kohta kokkupandud andmed ilma PCI-baitideta.
 */
object IsoTpAssembler {

    fun assemble(lines: List<String>): Map<Int, ByteArray> {
        val buffers = linkedMapOf<Int, Message>()
        for (raw in lines) {
            val line = raw.replace(" ", "")
            if (line.length < 5 || !line.all { it.isHexDigit() }) continue
            val id = line.substring(0, 3).toInt(16)
            val frame = line.substring(3).chunked(2).filter { it.length == 2 }.map { it.toInt(16) }
            if (frame.isEmpty()) continue
            val pci = frame[0]
            when (pci shr 4) {
                0x0 -> {
                    val length = pci and 0x0F
                    buffers[id] = Message(length).apply { append(frame.drop(1)) }
                }
                0x1 -> {
                    if (frame.size < 2) continue
                    val length = ((pci and 0x0F) shl 8) or frame[1]
                    buffers[id] = Message(length).apply { append(frame.drop(2)) }
                }
                0x2 -> buffers[id]?.append(frame.drop(1))
                // 0x3 = flow control, seda ELM327 tavaliselt ei näita
            }
        }
        return buffers.mapValues { (_, message) -> message.toByteArray() }
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'

    private class Message(val length: Int) {
        private val bytes = ArrayList<Int>(length)

        fun append(data: List<Int>) {
            bytes.addAll(data)
        }

        fun toByteArray(): ByteArray = bytes.take(length).map { it.toByte() }.toByteArray()
    }
}
