package com.eplik.ioniq5.core.scan

import com.eplik.ioniq5.core.pid.EcuAddress

/** Üks avastusskaneeringu samm: ECU, teenus (0x22 või 0x21) ja ID-vahemik. */
data class ScanTask(val ecu: EcuAddress, val service: Int, val ids: IntRange, val label: String) {
    val size: Int get() = ids.last - ids.first + 1

    fun describe(): String =
        if (service == 0x22) "%s 22 %04X–%04X".format(ecu.requestHex, ids.first, ids.last)
        else "%s 21 %02X–%02X".format(ecu.requestHex, ids.first, ids.last)
}

object DiscoveryPlan {

    /**
     * 3. etapp: rehvid, mootorid, rattad, rool. Aadressid on ECU-skaneeringust (2026-10-10),
     * vahemikud on Hyundai tavapärased. Kõik päringud on ainult lugemiseks.
     */
    /**
     * 3. etapp, 2. ring (pärast 2026-10-10 skaneeringuid): TPMS ja roolinurk on leitud.
     * Otsida mootorite pöördemoment ja temperatuurid ning rataste kiirused ja kiirendused.
     * Kiirenduste ja rataste kiiruste leidmiseks tuleb skaneerida sõidu ajal (kaasreisija!).
     */
    val STAGE3: List<ScanTask> = listOf(
        ScanTask(EcuAddress(0x7E3), 0x22, 0xE000..0xE0FF, "Tagamootor (MCU)"),
        ScanTask(EcuAddress(0x7E3), 0x22, 0x2000..0x20FF, "Tagamootor (MCU)"),
        ScanTask(EcuAddress(0x7E6), 0x22, 0xE000..0xE0FF, "Esimootor? 7E6"),
        ScanTask(EcuAddress(0x7E6), 0x22, 0x2000..0x20FF, "Esimootor? 7E6"),
        ScanTask(EcuAddress(0x7E2), 0x22, 0xE000..0xE0FF, "VCU"),
        ScanTask(EcuAddress(0x7D1), 0x22, 0x0100..0x010F, "Pidurid (IEB/ESC)"),
        ScanTask(EcuAddress(0x7D4), 0x22, 0x0100..0x0112, "Roolivõimendi (MDPS)"),
        ScanTask(EcuAddress(0x7C4), 0x22, 0x0100..0x01FF, "Esikaamera"),
        ScanTask(EcuAddress(0x7D0), 0x22, 0x0100..0x01FF, "Esiradar"),
    )
}
