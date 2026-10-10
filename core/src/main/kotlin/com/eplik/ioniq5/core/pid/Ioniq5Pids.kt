package com.eplik.ioniq5.core.pid

/**
 * Ioniq 5 (E-GMP, 72,6 kWh) PID-id. Baitide asukohad on kontrollitud päris auto vastustega
 * (testdata/raw/), vt Ioniq5PidsTest. Märkega „kandidaat” väärtused vajavad veel kinnitust.
 */
object Ioniq5Pids {

    val BMS = EcuAddress(0x7E4)
    val ICCU = EcuAddress(0x7E5)
    val CLUSTER = EcuAddress(0x7C6)

    // Signaalide ID-d, mida UI kasutab
    const val SOC_BMS = "soc_bms"
    const val SOC_DISPLAY = "soc_display"
    const val SOH = "soh"
    const val BATTERY_CURRENT = "battery_current"
    const val BATTERY_VOLTAGE = "battery_voltage"
    const val BATTERY_TEMP_MAX = "battery_temp_max"
    const val BATTERY_TEMP_MIN = "battery_temp_min"
    const val CELL_V_MAX = "cell_v_max"
    const val CELL_V_MAX_NO = "cell_v_max_no"
    const val CELL_V_MIN = "cell_v_min"
    const val CELL_V_MIN_NO = "cell_v_min_no"
    const val AUX_VOLTAGE = "aux_voltage"
    const val CUMULATIVE_CHARGED_KWH = "cumulative_charged_kwh"
    const val CUMULATIVE_DISCHARGED_KWH = "cumulative_discharged_kwh"
    const val OPERATING_HOURS = "operating_hours"
    const val MOTOR_RPM = "motor_rpm"
    const val MOTOR2_RPM = "motor2_rpm"

    // 3. etapi signaalid: PID-id leitakse avastusskaneeringuga, seni on väärtused tühjad
    const val TIRE_PRESSURE_FL = "tire_pressure_fl"
    const val TIRE_PRESSURE_FR = "tire_pressure_fr"
    const val TIRE_PRESSURE_RL = "tire_pressure_rl"
    const val TIRE_PRESSURE_RR = "tire_pressure_rr"
    const val TIRE_TEMP_FL = "tire_temp_fl"
    const val TIRE_TEMP_FR = "tire_temp_fr"
    const val TIRE_TEMP_RL = "tire_temp_rl"
    const val TIRE_TEMP_RR = "tire_temp_rr"
    const val STEERING_ANGLE = "steering_angle"
    const val FRONT_MOTOR_TORQUE = "front_motor_torque"
    const val REAR_MOTOR_TORQUE = "rear_motor_torque"
    const val LATERAL_ACCEL = "lateral_accel"
    const val LONGITUDINAL_ACCEL = "longitudinal_accel"
    const val ODOMETER = "odometer"
    const val ICCU_HV_VOLTAGE = "iccu_hv_voltage"
    const val SOLAR_PANEL_VOLTAGE = "solar_panel_voltage"

    const val CELL_COUNT_MAX = 192
    const val MODULE_TEMP_COUNT = 16

    fun cellId(index: Int) = "cell_%03d".format(index)

    fun moduleTempId(index: Int) = "module_temp_%02d".format(index)

    private fun moduleTemp(index: Int, start: Int) = SignalDefinition(
        moduleTempId(index), "Mooduli $index temperatuur", "Module $index temperature", "°C",
        ParameterGroup.BATTERY, start, signed = true,
    )

    private fun s(
        id: String,
        et: String,
        en: String,
        unit: String,
        group: ParameterGroup,
        start: Int,
        length: Int = 1,
        signed: Boolean = false,
        scale: Double = 1.0,
    ) = SignalDefinition(id, et, en, unit, group, start, length, signed, scale)

    private val bmsMain = PidDefinition(
        id = "bms_0101",
        ecu = BMS,
        request = "220101",
        pollIntervalMs = 1_000,
        signals = listOf(
            s(SOC_BMS, "Laetus (BMS)", "State of charge (BMS)", "%", ParameterGroup.BATTERY, 7, scale = 0.5),
            s(BATTERY_CURRENT, "Aku vool", "Battery current", "A", ParameterGroup.BATTERY, 13, 2, signed = true, scale = 0.1),
            s(BATTERY_VOLTAGE, "Aku pinge", "Battery voltage", "V", ParameterGroup.BATTERY, 15, 2, scale = 0.1),
            s(BATTERY_TEMP_MAX, "Aku max temperatuur", "Battery max temperature", "°C", ParameterGroup.BATTERY, 17, signed = true),
            s(BATTERY_TEMP_MIN, "Aku min temperatuur", "Battery min temperature", "°C", ParameterGroup.BATTERY, 18, signed = true),
            s(CELL_V_MAX, "Elemendi max pinge", "Max cell voltage", "V", ParameterGroup.CELLS, 26, scale = 0.02),
            s(CELL_V_MAX_NO, "Max pingega element", "Max cell number", "", ParameterGroup.CELLS, 27),
            s(CELL_V_MIN, "Elemendi min pinge", "Min cell voltage", "V", ParameterGroup.CELLS, 28, scale = 0.02),
            s(CELL_V_MIN_NO, "Min pingega element", "Min cell number", "", ParameterGroup.CELLS, 29),
            s(AUX_VOLTAGE, "12 V aku", "12 V battery", "V", ParameterGroup.AUX_BATTERY, 32, scale = 0.1),
            s(CUMULATIVE_CHARGED_KWH, "Laetud kokku", "Total charged", "kWh", ParameterGroup.BATTERY, 41, 4, scale = 0.1),
            s(CUMULATIVE_DISCHARGED_KWH, "Tühjendatud kokku", "Total discharged", "kWh", ParameterGroup.BATTERY, 45, 4, scale = 0.1),
            s(OPERATING_HOURS, "BMS tööaeg", "BMS operating time", "h", ParameterGroup.BATTERY, 49, 4, scale = 1.0 / 3600),
            s(MOTOR_RPM, "Mootori pöörded", "Motor speed", "rpm", ParameterGroup.DRIVE, 56, 2, signed = true),
            // Kandidaat: teine (eesmine) mootor; ~115 km/h juures 0 (lahti ühendatud?)
            s(MOTOR2_RPM, "Mootori 2 pöörded", "Motor 2 speed", "rpm", ParameterGroup.DRIVE, 58, 2, signed = true),
        ) + (1..5).map { moduleTemp(it, 18 + it) }, // moodulid 1–5: baidid 19–23
    )

    private val bmsHealth = PidDefinition(
        id = "bms_0105",
        ecu = BMS,
        request = "220105",
        pollIntervalMs = 10_000,
        signals = listOf(
            s(SOH, "Aku tervis (SOH)", "State of health", "%", ParameterGroup.BATTERY, 28, 2, scale = 0.1),
            // Kandidaat: kontrollida auto näiduga
            s(SOC_DISPLAY, "Laetus (näidik)", "State of charge (display)", "%", ParameterGroup.BATTERY, 34, scale = 0.5),
        ) +
            // Moodulid 6–12: baidid 12–18, moodulid 13–16: baidid 42–45 (Car Scanneri B06–B16 järgi)
            (6..12).map { moduleTemp(it, it + 6) } +
            (13..16).map { moduleTemp(it, it + 29) },
    )

    /** Elementide pinged: 6 päringut × kuni 32 elementi, igaüks 1 bait × 0,02 V alates baidist 7. */
    private val cellPids: List<PidDefinition> =
        listOf("220102", "220103", "220104", "22010A", "22010B", "22010C").mapIndexed { block, request ->
            PidDefinition(
                id = "bms_cells_$block",
                ecu = BMS,
                request = request,
                pollIntervalMs = 10_000,
                signals = (0 until 32).map { i ->
                    val cell = block * 32 + i + 1
                    s(cellId(cell), "Element $cell", "Cell $cell", "V", ParameterGroup.CELLS, 7 + i, scale = 0.02)
                },
            )
        }

    private val odometer = PidDefinition(
        id = "cluster_b002",
        ecu = CLUSTER,
        request = "22B002",
        pollIntervalMs = 30_000,
        signals = listOf(s(ODOMETER, "Läbisõit", "Odometer", "km", ParameterGroup.VEHICLE, 9, 3)),
    )

    private val iccuSolar = PidDefinition(
        id = "iccu_e011",
        ecu = ICCU,
        request = "22E011",
        pollIntervalMs = 5_000,
        signals = listOf(
            s(ICCU_HV_VOLTAGE, "Kõrgepinge (ICCU)", "High voltage (ICCU)", "V", ParameterGroup.SOLAR, 17, 2, scale = 0.1),
            // Kandidaat: tõenäoliselt päikesepaneeli pinge
            s(SOLAR_PANEL_VOLTAGE, "Päikesepaneeli pinge", "Solar panel voltage", "V", ParameterGroup.SOLAR, 15, 2, scale = 0.1),
        ),
    )

    val ALL: List<PidDefinition> = listOf(bmsMain, bmsHealth) + cellPids + listOf(odometer, iccuSolar)
}
