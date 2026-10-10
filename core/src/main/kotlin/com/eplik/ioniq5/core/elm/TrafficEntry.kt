package com.eplik.ioniq5.core.elm

/** Üks käsk adapterile ja selle vastuse read. */
data class TrafficEntry(val timeMs: Long, val command: String, val response: List<String>) {

    /** Sama vorming mis failides testdata/raw/: käsk rea alguses `>` järel, siis vastuse read. */
    fun format(): String = buildString {
        append("> ").append(command).append('\n')
        response.forEach { append(it).append('\n') }
    }
}
