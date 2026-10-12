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
     * OBDb (E-GMP): 7E3 22E001 baidid 24–25 / 10 Nm pöördemoment, 28–29 / 100 °C mootor, 30–31 / 100 °C inverter.
     * Kiirenduste ja rataste kiiruste leidmiseks tuleb skaneerida sõidu ajal (kaasreisija!).
     */
    val STAGE3: List<ScanTask> = listOf(
        ScanTask(EcuAddress(0x7E3), 0x22, 0xE000..0xE0FF, "Tagamootor (MCU)"),
        ScanTask(EcuAddress(0x7E3), 0x22, 0x2000..0x20FF, "Tagamootor (MCU)"),
        ScanTask(EcuAddress(0x7E6), 0x22, 0xE000..0xE0FF, "Esimootor? 7E6"),
        ScanTask(EcuAddress(0x7E6), 0x22, 0x2000..0x20FF, "Esimootor? 7E6"),
        ScanTask(EcuAddress(0x7E2), 0x22, 0xE000..0xE0FF, "VCU"),
        // OBDb: laadimispistiku temperatuurid 744 22E004 (01xx ei vastanud 2026-10-10)
        ScanTask(EcuAddress(0x744), 0x22, 0xE000..0xE00F, "Laadimisport (744)"),
        ScanTask(EcuAddress(0x7D1), 0x22, 0x0100..0x010F, "Pidurid (IEB/ESC)"),
        ScanTask(EcuAddress(0x7D4), 0x22, 0x0100..0x0112, "Roolivõimendi (MDPS)"),
        ScanTask(EcuAddress(0x7C4), 0x22, 0x0100..0x01FF, "Esikaamera"),
        ScanTask(EcuAddress(0x7D0), 0x22, 0x0100..0x01FF, "Esiradar"),
    )

    /**
     * Kliima, 2. ring: istmete soojendus ja ventilatsioon. 1. ring (2026-10-12) leidis 7B3 0100–0102,
     * 01A0–01A3, 7A0 B001–B012 ja 770 BC01–BC10; rool ja tagaklaas olid seal, istmed mitte. 706 ja 7A1 ei
     * vastanud vahemikus 0100–01FF, seega proovitakse teisi vahemikke ja KWP teenust 0x21.
     * Skaneerida süüde sees.
     */
    val CLIMATE: List<ScanTask> = listOf(
        ScanTask(EcuAddress(0x706), 0x22, 0xB000..0xB0FF, "Istmemoodul? 706"),
        ScanTask(EcuAddress(0x706), 0x22, 0xC000..0xC0FF, "Istmemoodul? 706"),
        ScanTask(EcuAddress(0x706), 0x21, 0x01..0xFF, "Istmemoodul? 706 (KWP)"),
        ScanTask(EcuAddress(0x7A6), 0x22, 0x0100..0x01FF, "Lüliti? 7A6"),
        ScanTask(EcuAddress(0x7A6), 0x22, 0xB000..0xB0FF, "Lüliti? 7A6"),
        ScanTask(EcuAddress(0x7A3), 0x22, 0xB000..0xB0FF, "7A3"),
        ScanTask(EcuAddress(0x7A0), 0x22, 0xC000..0xC0FF, "IBU/BCM 7A0"),
        ScanTask(EcuAddress(0x770), 0x22, 0xC000..0xC0FF, "IBU 770"),
    )
}
