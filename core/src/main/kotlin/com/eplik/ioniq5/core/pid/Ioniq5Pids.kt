package com.eplik.ioniq5.core.pid

/**
 * Ioniq 5 (E-GMP, 72,6 kWh) PID-id. Baitide asukohad on kontrollitud päris auto vastustega
 * (testdata/raw/), vt Ioniq5PidsTest. Märkega „kandidaat” väärtused vajavad veel kinnitust.
 */
object Ioniq5Pids {

    val BMS = EcuAddress(0x7E4)
    val ICCU = EcuAddress(0x7E5)
    val CLUSTER = EcuAddress(0x7C6)
    val TPMS = EcuAddress(0x7A0)
    val MDPS = EcuAddress(0x7D4)
    val HVAC = EcuAddress(0x7B3)
    val ESC = EcuAddress(0x7D1)
    val IBU = EcuAddress(0x770)
    val REAR_MCU = EcuAddress(0x7E3)
    val CHARGE_PORT = EcuAddress(0x744)

    /** TPMS rõhk: bait × 0,2 psi, teisendatud baarideks. */
    private const val PSI_02_TO_BAR = 0.2 * 0.0689476

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
    const val BATTERY_HEATER_TEMP = "battery_heater_temp"
    const val BATTERY_REMAINING_KWH = "battery_remaining_kwh"
    const val BATTERY_INLET_TEMP = "battery_inlet_temp"
    const val BATTERY_COOLANT_TEMP = "battery_coolant_temp"
    const val AC_COMPRESSOR_RPM = "ac_compressor_rpm"
    const val HVAC_COOLANT_TEMP = "hvac_coolant_temp"
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
    const val REAR_MOTOR_TEMP = "rear_motor_temp"
    const val REAR_INVERTER_TEMP = "rear_inverter_temp"
    const val CHARGE_PORT_DC1_TEMP = "charge_port_dc1_temp"
    const val CHARGE_PORT_DC2_TEMP = "charge_port_dc2_temp"
    const val CHARGE_PORT_AC_TEMP = "charge_port_ac_temp"
    const val LATERAL_ACCEL = "lateral_accel"
    const val LONGITUDINAL_ACCEL = "longitudinal_accel"
    const val ODOMETER = "odometer"
    const val ICCU_HV_VOLTAGE = "iccu_hv_voltage"
    const val SOLAR_PANEL_VOLTAGE = "solar_panel_voltage"
    const val SOLAR_POWER = "solar_power"
    const val OBC_AC_VOLTAGE = "obc_ac_voltage"
    const val OBC_AC_CURRENT_1 = "obc_ac_current_1"
    const val OBC_AC_CURRENT_2 = "obc_ac_current_2"
    const val OBC_AC_CURRENT_3 = "obc_ac_current_3"
    const val OBC_DC_VOLTAGE = "obc_dc_voltage"
    const val OBC_DC_CURRENT = "obc_dc_current"
    const val OBC_AC_VOLTAGE_2 = "obc_ac_voltage_2"
    const val OBC_AC_FREQUENCY = "obc_ac_frequency"
    const val OBC_TEMP_1 = "obc_temp_1"
    const val OBC_TEMP_2 = "obc_temp_2"
    const val CABIN_TEMP = "cabin_temp"
    const val OUTDOOR_TEMP = "outdoor_temp"
    const val EVAPORATOR_TEMP = "evaporator_temp"
    const val HVAC_VEHICLE_SPEED = "hvac_vehicle_speed"
    const val CABIN_HUMIDITY = "cabin_humidity"
    const val VENT_TEMP_1 = "vent_temp_1"
    const val VENT_TEMP_2 = "vent_temp_2"

    // Soojendused: tase 0–3 (istmed) või 0/1. Rool, tagaklaas, peeglid ja esiklaas on leitud kliima
    // arenduslogist 2026-10-12; istmete soojendus ja ventilatsioon on veel leidmata (vaade näitab teadmata).
    const val SEAT_HEAT_DRIVER = "seat_heat_driver"
    const val SEAT_HEAT_PASSENGER = "seat_heat_passenger"
    const val SEAT_HEAT_REAR_LEFT = "seat_heat_rear_left"
    const val SEAT_HEAT_REAR_RIGHT = "seat_heat_rear_right"
    const val SEAT_VENT_DRIVER = "seat_vent_driver"
    const val SEAT_VENT_PASSENGER = "seat_vent_passenger"
    const val STEERING_HEAT = "steering_heat"
    const val WINDSHIELD_HEAT = "windshield_heat"
    const val REAR_WINDOW_HEAT = "rear_window_heat"
    const val MIRROR_HEAT = "mirror_heat"
    const val SET_TEMP_DRIVER = "set_temp_driver"
    const val SET_TEMP_PASSENGER = "set_temp_passenger"
    const val VEHICLE_SPEED = "vehicle_speed"
    const val WHEEL_SPEED_FL = "wheel_speed_fl"
    const val WHEEL_SPEED_FR = "wheel_speed_fr"
    const val WHEEL_SPEED_RL = "wheel_speed_rl"
    const val WHEEL_SPEED_RR = "wheel_speed_rr"

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
            // OBDb Hyundai-IONIQ-5: aku sisendi (jahutusvedeliku) temperatuur, bait 25 − 40; sõidulogides 12–13 °C
            SignalDefinition(BATTERY_INLET_TEMP, "Aku sisendi temperatuur", "Battery inlet temperature", "°C", ParameterGroup.BATTERY, 25, offset = -40.0),
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
            // OBDb ja Torque: järelejäänud energia baidid 31–32 × 2 Wh; sõidulogis 57,4 kWh
            s(BATTERY_REMAINING_KWH, "Aku järelejäänud energia", "Battery remaining energy", "kWh", ParameterGroup.BATTERY, 31, 2, scale = 0.002),
            // Aku soojendi temperatuur: Torque/Car Scanneri Ioniq 5 PID-fail „Battery Heater 1 Temperature” =
            // Signed(X) = bait 26. Sõidulogides 11–13 °C (soojendi väljas), DC-laadimisel 24–33 °C.
            s(BATTERY_HEATER_TEMP, "Aku soojendi temperatuur", "Battery heater temperature", "°C", ParameterGroup.BATTERY, 26, signed = true),
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
            // M–N: tähendus teadmata (pimedas 275,8 V, päeval 164–197 V), seega mitte paneeli pinge
            s(SOLAR_PANEL_VOLTAGE, "ICCU M–N", "ICCU M–N", "V", ParameterGroup.SOLAR, 15, 2, scale = 0.1),
            // Kandidaat: päikesekatuse võimsus = bait 25 × 0,5 W. Päeval pilves 37–40 W (klapib auto
            // kogutoodangu kasvuga ~37,5 W), pimedas 3 W.
            s(SOLAR_POWER, "Päikese võimsus", "Solar power", "W", ParameterGroup.SOLAR, 25, scale = 0.5),
        ),
    )

    /** Rehvid: 4 × 5 baiti alates baidist 7 (FL, FR, RL, RR): rõhk × 0,2 psi, temperatuur − 50 °C. */
    private val tires = PidDefinition(
        id = "tpms_c00b",
        ecu = TPMS,
        request = "22C00B",
        pollIntervalMs = 10_000,
        signals = listOf(
            TIRE_PRESSURE_FL to TIRE_TEMP_FL,
            TIRE_PRESSURE_FR to TIRE_TEMP_FR,
            TIRE_PRESSURE_RL to TIRE_TEMP_RL,
            TIRE_PRESSURE_RR to TIRE_TEMP_RR,
        ).flatMapIndexed { wheel, (pressure, temp) ->
            val start = 7 + wheel * 5
            listOf(
                SignalDefinition(pressure, "Rehvirõhk", "Tire pressure", "bar", ParameterGroup.TIRES, start, scale = PSI_02_TO_BAR),
                SignalDefinition(temp, "Rehvi temperatuur", "Tire temperature", "°C", ParameterGroup.TIRES, start + 1, offset = -50.0),
            )
        },
    )

    /** Roolinurk × 0,1°, positiivne = vasakule (kinnitatud autos); sama väärtus on ka 7D1 220104 baitides 34–35. */
    private val steering = PidDefinition(
        id = "mdps_0101",
        ecu = MDPS,
        request = "220101",
        pollIntervalMs = 500,
        signals = listOf(
            s(STEERING_ANGLE, "Roolinurk", "Steering angle", "°", ParameterGroup.DRIVE, 10, 2, signed = true, scale = 0.1),
        ),
    )

    /**
     * Pardalaadija (OBC, ICCU sees) AC-laadimisel. Kontrollitud 10.10 AC-laadimisega: DC pinge ja vool
     * klapivad BMS-iga, faasivoolud ~15,8 A, AC pinge 228,6 V. Ilma laadimiseta on väljad nullid.
     */
    private val obc = PidDefinition(
        id = "iccu_e002",
        ecu = ICCU,
        request = "22E002",
        pollIntervalMs = 5_000,
        signals = listOf(
            s(OBC_AC_CURRENT_1, "AC vool L1", "AC current L1", "A", ParameterGroup.CHARGING, 14, 2, scale = 0.01),
            s(OBC_AC_CURRENT_2, "AC vool L2", "AC current L2", "A", ParameterGroup.CHARGING, 16, 2, scale = 0.01),
            s(OBC_AC_CURRENT_3, "AC vool L3", "AC current L3", "A", ParameterGroup.CHARGING, 18, 2, scale = 0.01),
            s(OBC_DC_VOLTAGE, "OBC DC pinge", "OBC DC voltage", "V", ParameterGroup.CHARGING, 22, 2, scale = 0.1),
            s(OBC_DC_CURRENT, "OBC DC vool", "OBC DC current", "A", ParameterGroup.CHARGING, 24, 2, scale = 0.01),
            // OBDb + kaks AC-laadimise logi (10.10, 16.10): baidid 7–8 ja 9–10 / 10 = liinipinge (384 V,
            // 3-faasiline 400 V võrk), bait 13 sagedus, baidid 30 ja 31 − 40 laadija temperatuurid.
            // Varem loeti baite 30–31 ekslikult AC pingeks (228,6 V), mis andis kasuteguriks ~100 %.
            s(OBC_AC_VOLTAGE, "AC pinge", "AC voltage", "V", ParameterGroup.CHARGING, 7, 2, scale = 0.1),
            s(OBC_AC_VOLTAGE_2, "AC pinge 2", "AC voltage 2", "V", ParameterGroup.CHARGING, 9, 2, scale = 0.1),
            s(OBC_AC_FREQUENCY, "AC sagedus", "AC frequency", "Hz", ParameterGroup.CHARGING, 13),
            SignalDefinition(OBC_TEMP_1, "Laadija temperatuur 1", "Charger temperature 1", "°C", ParameterGroup.CHARGING, 30, offset = -40.0),
            SignalDefinition(OBC_TEMP_2, "Laadija temperatuur 2", "Charger temperature 2", "°C", ParameterGroup.CHARGING, 31, offset = -40.0),
        ),
    )

    /**
     * Kliima (7B3, vastab ainult süüte ajal). Temperatuurid: bait / 2 − 40 °C. Sõidulogis 2026-10-11
     * oli salong 23,5 °C, väljas 12,5 °C (kinnitatud auto näiduga), aurusti 5 °C; bait 32 järgis täpselt sõidukiirust.
     */
    private val hvac = PidDefinition(
        id = "hvac_0100",
        ecu = HVAC,
        request = "220100",
        pollIntervalMs = 5_000,
        signals = listOf(
            SignalDefinition(CABIN_TEMP, "Salongi temperatuur", "Cabin temperature", "°C", ParameterGroup.CLIMATE, 8, scale = 0.5, offset = -40.0),
            SignalDefinition(OUTDOOR_TEMP, "Välistemperatuur", "Outdoor temperature", "°C", ParameterGroup.CLIMATE, 9, scale = 0.5, offset = -40.0),
            SignalDefinition(EVAPORATOR_TEMP, "Aurusti temperatuur", "Evaporator temperature", "°C", ParameterGroup.CLIMATE, 10, scale = 0.5, offset = -40.0),
            s(HVAC_VEHICLE_SPEED, "Kiirus (kliimamoodul)", "Speed (climate module)", "km/h", ParameterGroup.DRIVE, 32),
            // Kandidaadid: niiskus 48…54 % (Car Scanner „Humidity sensor for automatic defogger”),
            // puhurid 25…40 °C (Car Scanner „Interior temperature: driver vent / legs vent”)
            s(CABIN_HUMIDITY, "Salongi õhuniiskus", "Cabin humidity", "%", ParameterGroup.CLIMATE, 28),
            SignalDefinition(VENT_TEMP_1, "Puhuri temperatuur 1", "Vent temperature 1", "°C", ParameterGroup.CLIMATE, 30, scale = 0.5, offset = -40.0),
            SignalDefinition(VENT_TEMP_2, "Puhuri temperatuur 2", "Vent temperature 2", "°C", ParameterGroup.CLIMATE, 31, scale = 0.5, offset = -40.0),
        ),
    )

    /**
     * Pidurisüsteem (IEB/ESC). Sõidulogid 2026-10-11: baidid 13–16 on rataste kiirused km/h
     * (pöördes on välimised rattad kiiremad), baidid 9–10 / 256 on sõiduki kiirus km/h
     * (võrdne rataste ja kliimamooduli kiirusega).
     */
    private val esc = PidDefinition(
        id = "esc_0104",
        ecu = ESC,
        request = "220104",
        pollIntervalMs = 1_000,
        signals = listOf(
            s(VEHICLE_SPEED, "Kiirus", "Speed", "km/h", ParameterGroup.DRIVE, 9, 2, scale = 1.0 / 256),
            s(WHEEL_SPEED_FL, "Ratta kiirus VE", "Wheel speed FL", "km/h", ParameterGroup.TIRES, 13),
            s(WHEEL_SPEED_FR, "Ratta kiirus PE", "Wheel speed FR", "km/h", ParameterGroup.TIRES, 14),
            s(WHEEL_SPEED_RL, "Ratta kiirus VT", "Wheel speed RL", "km/h", ParameterGroup.TIRES, 15),
            s(WHEEL_SPEED_RR, "Ratta kiirus PT", "Wheel speed RR", "km/h", ParameterGroup.TIRES, 16),
        ),
    )

    /**
     * Kliima seaded (7B3 2201A0). Arenduslogi 2026-10-12: seatud temperatuur 20 / 22 / 23,5 °C andis
     * baitides 59–60 väärtused 0C / 10 / 13 → 14 + bait × 0,5 °C. Bait 5 oli 1 esiklaasi soojenduse
     * (defrosteri) ajal (kandidaat).
     */
    private val hvacSettings = PidDefinition(
        id = "hvac_01A0",
        ecu = HVAC,
        request = "2201A0",
        pollIntervalMs = 5_000,
        signals = listOf(
            SignalDefinition(SET_TEMP_DRIVER, "Seatud temp. (juht)", "Set temperature (driver)", "°C", ParameterGroup.CLIMATE, 59, scale = 0.5, offset = 14.0),
            SignalDefinition(SET_TEMP_PASSENGER, "Seatud temp. (kõrvalistuja)", "Set temperature (passenger)", "°C", ParameterGroup.CLIMATE, 60, scale = 0.5, offset = 14.0),
            SignalDefinition(WINDSHIELD_HEAT, "Esiklaasi soojendus", "Windshield defrost", "", ParameterGroup.CLIMATE, 5, bit = 0),
        ),
    )

    /** IBU 770 BC03 bait 10 bit 1: tagaklaasi soojendus (lülitab ka peeglid). Arenduslogi 2026-10-12. */
    private val ibuRearWindow = PidDefinition(
        id = "ibu_bc03",
        ecu = IBU,
        request = "22BC03",
        pollIntervalMs = 5_000,
        signals = listOf(
            SignalDefinition(REAR_WINDOW_HEAT, "Tagaklaasi soojendus", "Rear window heater", "", ParameterGroup.CLIMATE, 10, bit = 1),
            SignalDefinition(MIRROR_HEAT, "Peeglite soojendus", "Mirror heaters", "", ParameterGroup.CLIMATE, 10, bit = 1),
        ),
    )

    /** IBU 770 BC08 bait 8 bit 6: roolisoojendus. Arenduslogi 2026-10-12. */
    private val ibuSteering = PidDefinition(
        id = "ibu_bc08",
        ecu = IBU,
        request = "22BC08",
        pollIntervalMs = 5_000,
        signals = listOf(
            SignalDefinition(STEERING_HEAT, "Roolisoojendus", "Steering wheel heater", "", ParameterGroup.CLIMATE, 8, bit = 6),
        ),
    )

    /**
     * BMS 220106 (OBDb, E-GMP): bait 7 aku jahutusvedelik WCS sisendis (sõidulogides 11–12 °C),
     * bait 15 × 20 kliimakompressori pöörded (700–800 p/min kliima töötamisel).
     */
    private val bmsThermal = PidDefinition(
        id = "bms_0106",
        ecu = BMS,
        request = "220106",
        pollIntervalMs = 5_000,
        signals = listOf(
            s(BATTERY_COOLANT_TEMP, "Aku jahutusvedelik", "Battery coolant temperature", "°C", ParameterGroup.BATTERY, 7, signed = true),
            s(AC_COMPRESSOR_RPM, "Kliimakompressor", "A/C compressor speed", "rpm", ParameterGroup.CLIMATE, 15, scale = 20.0),
        ),
    )

    /** Kliima 7B3 220102 (OBDb): bait 7 / 2 − 40 jahutusvedeliku temperatuur (33–47 °C). */
    private val hvacCoolant = PidDefinition(
        id = "hvac_0102",
        ecu = HVAC,
        request = "220102",
        pollIntervalMs = 10_000,
        signals = listOf(
            SignalDefinition(HVAC_COOLANT_TEMP, "Jahutusvedelik (kliima)", "Coolant temperature (HVAC)", "°C", ParameterGroup.CLIMATE, 7, scale = 0.5, offset = -40.0),
        ),
    )

    /**
     * Tagamootori kontroller (7E3 22E001, vastus ~200 baiti, little-endian). OBDb E-GMP baidid; kontrollitud
     * skaneeringuga 2026-10-13 77 km/h juures: −80,1 Nm, mootor 15,1 °C, inverter 12,1 °C (hommik 8 °C).
     */
    private val rearMcu = PidDefinition(
        id = "mcu_rear_E001",
        ecu = REAR_MCU,
        request = "22E001",
        pollIntervalMs = 2_000,
        signals = listOf(
            SignalDefinition(REAR_MOTOR_TORQUE, "Tagamootori pöördemoment", "Rear motor torque", "Nm", ParameterGroup.DRIVE, 24, 2, signed = true, scale = 0.1, littleEndian = true),
            SignalDefinition(REAR_MOTOR_TEMP, "Tagamootori temperatuur", "Rear motor temperature", "°C", ParameterGroup.DRIVE, 28, 2, signed = true, scale = 0.01, littleEndian = true),
            SignalDefinition(REAR_INVERTER_TEMP, "Tagainverteri temperatuur", "Rear inverter temperature", "°C", ParameterGroup.DRIVE, 30, 2, signed = true, scale = 0.01, littleEndian = true),
        ),
    )

    /** Laadimispesa (744 22E004): DC1, DC2 ja AC kontakti temperatuur − 50 °C (OBDb; 10 °C hommikul 8 °C juures). */
    private val chargePort = PidDefinition(
        id = "charge_port_E004",
        ecu = CHARGE_PORT,
        request = "22E004",
        pollIntervalMs = 10_000,
        signals = listOf(
            SignalDefinition(CHARGE_PORT_DC1_TEMP, "Laadimispesa DC1", "Charge port DC1", "°C", ParameterGroup.CHARGING, 11, offset = -50.0),
            SignalDefinition(CHARGE_PORT_DC2_TEMP, "Laadimispesa DC2", "Charge port DC2", "°C", ParameterGroup.CHARGING, 12, offset = -50.0),
            SignalDefinition(CHARGE_PORT_AC_TEMP, "Laadimispesa AC", "Charge port AC", "°C", ParameterGroup.CHARGING, 13, offset = -50.0),
        ),
    )

    val ALL: List<PidDefinition> = listOf(bmsMain, bmsHealth) + cellPids + listOf(rearMcu, chargePort) +
        listOf(bmsThermal, odometer, iccuSolar, obc, tires, steering, esc, hvac, hvacCoolant, hvacSettings, ibuRearWindow, ibuSteering)
}
