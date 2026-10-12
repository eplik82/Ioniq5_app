package com.eplik.ioniq5.core.scan

import com.eplik.ioniq5.core.pid.EcuAddress

/**
 * Arenduslogi: päringud, mida korratakse tsüklina, et koguda toorandmeid eri olukordades
 * (päike, sõit, laadimine). Sisaldab kinnitatud PID-e ja veel uuritavaid kandidaate.
 */
object DevCapture {

    data class Request(val ecu: EcuAddress, val request: String, val label: String)

    private val BMS = EcuAddress(0x7E4)
    private val ICCU = EcuAddress(0x7E5)
    private val CLUSTER = EcuAddress(0x7C6)
    private val TPMS = EcuAddress(0x7A0)
    private val MDPS = EcuAddress(0x7D4)
    private val ESC = EcuAddress(0x7D1)
    private val VCU = EcuAddress(0x7E2)
    private val HVAC = EcuAddress(0x7B3)
    private val BCM = EcuAddress(0x7A0)
    private val IBU = EcuAddress(0x770)

    val REQUESTS: List<Request> = listOf(
        Request(BMS, "220101", "BMS põhiandmed"),
        Request(BMS, "220105", "BMS SOH, temperatuurid"),
        Request(BMS, "220106", "BMS jahutus"),
        Request(BMS, "220111", "BMS"),
        Request(BMS, "220114", "BMS"),
        Request(ICCU, "22E001", "ICCU"),
        Request(ICCU, "22E002", "ICCU pardalaadija"),
        Request(ICCU, "22E003", "ICCU"),
        Request(ICCU, "22E011", "ICCU päikesekatus"),
        Request(ICCU, "22E021", "ICCU"),
        Request(VCU, "22E004", "VCU"),
        Request(VCU, "22E005", "VCU"),
        Request(ESC, "220104", "Pidurid: roolinurk, rattad?"),
        Request(ESC, "220106", "Pidurid"),
        Request(MDPS, "220101", "Roolivõimendi: roolinurk"),
        Request(MDPS, "220102", "Roolivõimendi"),
        Request(MDPS, "220112", "Roolivõimendi"),
        Request(TPMS, "22C00B", "Rehvid"),
        Request(CLUSTER, "22B002", "Odomeeter"),
        Request(HVAC, "220100", "Kliima: temperatuurid"),
        Request(HVAC, "220102", "Kliima"),
        // Elemendid harvem pole vajalik: ühe tsükli kohta 6 päringut
        Request(BMS, "220102", "Elemendid 1–32"),
        Request(BMS, "220103", "Elemendid 33–64"),
        Request(BMS, "220104", "Elemendid 65–96"),
        Request(BMS, "22010A", "Elemendid 97–128"),
        Request(BMS, "22010B", "Elemendid 129–160"),
        Request(BMS, "22010C", "Elemendid 161–180"),
    )

    /**
     * Kliima ja soojendused: kõik DID-id, mis kliima avastusskaneering 2026-10-12 leidis
     * (7B3: 7, 7A0: 18, 770: 9). Lühike tsükkel, et iga lüliti mõju oleks logis selgelt näha.
     */
    val CLIMATE: List<Request> =
        listOf("220100", "220101", "220102", "2201A0", "2201A1", "2201A2", "2201A3").map { Request(HVAC, it, "Kliima") } +
            (0x01..0x12).map { Request(BCM, "22B0%02X".format(it), "Kere 7A0") } +
            listOf("22BC01", "22BC03", "22BC04", "22BC05", "22BC06", "22BC07", "22BC08", "22BC09", "22BC10")
                .map { Request(IBU, it, "Kere 770") }
}
