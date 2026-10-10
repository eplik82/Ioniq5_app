package com.eplik.ioniq5.core.pid

/** Üks päring ECU-le ja sellest vastusest dekodeeritavad signaalid. */
data class PidDefinition(
    val id: String,
    val ecu: EcuAddress,
    /** Päring heksana, nt "220101". */
    val request: String,
    val pollIntervalMs: Long,
    val signals: List<SignalDefinition>,
)

/**
 * Signaali asukoht positiivses vastuses. [startByte] loetakse vastuse algusest,
 * kaasa arvatud vastuse SID (nt 0x62) ja DID.
 */
data class SignalDefinition(
    val id: String,
    val nameEt: String,
    val nameEn: String,
    val unit: String,
    val group: ParameterGroup,
    val startByte: Int,
    val length: Int = 1,
    val signed: Boolean = false,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    /** Kui määratud, loetakse ainult üht bitti (0 = madalaim) ja väärtus on 0 või 1. */
    val bit: Int? = null,
)

enum class ParameterGroup { BATTERY, CELLS, CHARGING, DRIVE, TIRES, CLIMATE, SOLAR, AUX_BATTERY, VEHICLE }
