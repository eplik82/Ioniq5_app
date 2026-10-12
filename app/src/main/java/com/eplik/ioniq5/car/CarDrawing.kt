package com.eplik.ioniq5.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.eplik.ioniq5.R
import com.eplik.ioniq5.alert.text
import com.eplik.ioniq5.core.alert.Alert
import com.eplik.ioniq5.core.alert.Severity
import com.eplik.ioniq5.connection.ConnectionStatus
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.core.poll.acInputPowerKw
import com.eplik.ioniq5.core.poll.batteryPowerKw
import com.eplik.ioniq5.core.poll.cellVoltages
import com.eplik.ioniq5.core.poll.minutesToSoc
import com.eplik.ioniq5.core.poll.speedKmh
import com.eplik.ioniq5.settings.Settings
import com.eplik.ioniq5.settings.capacityKwh
import com.eplik.ioniq5.ui.components.formatValue
import kotlin.math.abs
import kotlin.math.min

/**
 * Android Auto näidikud Ioniq 5 stiilis: tume taust, õhukesed numbrid, türkiissinine aktsent,
 * pikslinäidikud. Joonistab otse Canvas'ile nähtavasse alasse.
 */
class CarDrawing(private val context: Context, dark: Boolean) {

    private val background = if (dark) 0xFF0B0E11.toInt() else 0xFFF4F6F7.toInt()
    private val surface = if (dark) 0xFF14181C.toInt() else 0xFFFFFFFF.toInt()
    private val textPrimary = if (dark) 0xFFF2F4F5.toInt() else 0xFF15191D.toInt()
    private val textSecondary = if (dark) 0xFF9AA4AD.toInt() else 0xFF5A646D.toInt()
    private val pixelOff = if (dark) 0xFF2A3036.toInt() else 0xFFDDE2E6.toInt()
    private val accent = 0xFF2FD1C5.toInt()
    private val warning = 0xFFFFB547.toInt()
    private val critical = 0xFFFF6B6B.toInt()
    private val cold = 0xFF3D6FD8.toInt()
    private val regen = 0xFF7CD957.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }

    private var now = System.currentTimeMillis()
    private lateinit var r: Readings

    fun draw(
        canvas: Canvas,
        area: Rect,
        page: CarPage,
        readings: Readings,
        status: ConnectionStatus,
        sleeping: Boolean,
        settings: Settings,
        alerts: List<Alert> = emptyList(),
        solarEnergyWh: Double = 0.0,
    ) {
        r = readings
        now = System.currentTimeMillis()
        canvas.drawColor(background)
        val pad = area.height() * 0.05f
        val box = RectF(area.left + pad, area.top + pad, area.right - pad, area.bottom - pad)
        when (page) {
            CarPage.OVERVIEW -> overview(canvas, box)
            CarPage.BATTERY -> battery(canvas, box)
            CarPage.WHEELS -> wheels(canvas, box, settings)
            CarPage.CHARGING -> charging(canvas, box, settings)
            CarPage.SOLAR -> solar(canvas, box, solarEnergyWh)
            CarPage.CLIMATE -> climate(canvas, box)
        }
        // Olekurida all: ühenduse olek või kõige raskem hoiatus
        val alert = alerts.firstOrNull { it.severity != Severity.INFO }
        val statusText = when {
            status !is ConnectionStatus.Connected -> context.getString(R.string.connection_disconnected)
            alert != null -> "⚠ " + alert.text(context, settings.pressureUnit)
            sleeping -> context.getString(R.string.sleep_banner)
            else -> null
        }
        val statusColor = if (alert?.severity == Severity.CRITICAL && status is ConnectionStatus.Connected) critical else warning
        statusText?.let { label(canvas, it, box.left, box.bottom, area.height() * 0.035f, statusColor) }
    }

    // ---------- Lehed ----------

    private fun overview(c: Canvas, b: RectF) {
        val leftW = b.width() * 0.38f
        // Näidiku laetus, kui see on värske, muidu BMS-i oma (aegunud näidikuväärtus andis siin „—”)
        val socId = if (fresh(P.SOC_DISPLAY)) P.SOC_DISPLAY else P.SOC_BMS
        val soc = v(socId)
        val h = b.height()
        label(c, str(R.string.state_of_charge).uppercase(), b.left, b.top + h * 0.08f, h * 0.045f)
        big(c, soc?.let { formatValue(it, 0) } ?: "—", " %", b.left, b.top + h * 0.38f, h * 0.30f, fresh(socId))
        pixelGauge(c, RectF(b.left, b.top + h * 0.48f, b.left + leftW * 0.92f, b.bottom - h * 0.08f), soc?.div(100))

        val power = r.batteryPowerKw()
        val tiles = listOf(
            Tile(
                str(R.string.battery_power),
                formatValue(power, 1),
                "kW",
                fresh(P.BATTERY_CURRENT),
                if (power != null && power < -0.5) regen else null,
            ),
            Tile(str(R.string.speed_calc), formatValue(r.speedKmh(), 0), "km/h", fresh(P.VEHICLE_SPEED) || fresh(P.MOTOR_RPM)),
            Tile(
                str(R.string.battery_temp),
                "${formatValue(v(P.BATTERY_TEMP_MIN), 0)}…${formatValue(v(P.BATTERY_TEMP_MAX), 0)}",
                "°C",
                fresh(P.BATTERY_TEMP_MAX),
            ),
            Tile(str(R.string.aux_battery), formatValue(v(P.AUX_VOLTAGE), 1), "V", fresh(P.AUX_VOLTAGE)),
            Tile(str(R.string.solar_power), formatValue(v(P.SOLAR_POWER), 0), "W", fresh(P.SOLAR_POWER)),
            Tile(str(R.string.odometer), formatValue(r[P.ODOMETER]?.value, 0), "km", r[P.ODOMETER] != null),
            Tile(str(R.string.cabin_temp), formatValue(v(P.CABIN_TEMP), 1), "°C", fresh(P.CABIN_TEMP)),
            Tile(str(R.string.outdoor_temp), formatValue(v(P.OUTDOOR_TEMP), 1), "°C", fresh(P.OUTDOOR_TEMP)),
        )
        tileGrid(c, RectF(b.left + leftW, b.top, b.right, b.bottom - h * 0.06f), tiles, columns = 2)
    }

    private fun battery(c: Canvas, b: RectF) {
        val cells = r.cellVoltages()
        val gridW = b.width() * 0.62f
        val h = b.height()
        if (cells.isNotEmpty()) {
            val columns = 18
            val rows = (cells.size + columns - 1) / columns
            val cell = min(gridW / columns, (h * 0.9f) / rows)
            val minV = cells.min()
            val maxV = cells.max()
            cells.forEachIndexed { i, volts ->
                val t = if (maxV > minV) ((volts - minV) / (maxV - minV)).toFloat() else 0.5f
                fill.color = scale(t)
                val x = b.left + (i % columns) * cell
                val y = b.top + (i / columns) * cell
                c.drawRoundRect(RectF(x + cell * 0.08f, y + cell * 0.08f, x + cell * 0.92f, y + cell * 0.92f), cell * 0.12f, cell * 0.12f, fill)
            }
            label(c, "${cells.size} · ${formatValue(minV, 2)}…${formatValue(maxV, 2)} V", b.left, b.top + rows * cell + h * 0.07f, h * 0.045f)
        } else {
            label(c, "—", b.left, b.top + h * 0.1f, h * 0.05f)
        }
        val maxCell = v(P.CELL_V_MAX)
        val minCell = v(P.CELL_V_MIN)
        tileGrid(
            c,
            RectF(b.left + gridW + b.width() * 0.03f, b.top, b.right, b.bottom - h * 0.06f),
            listOf(
                Tile(str(R.string.soc_bms), formatValue(v(P.SOC_BMS), 1), "%", fresh(P.SOC_BMS)),
                Tile(str(R.string.soh), formatValue(v(P.SOH), 1), "%", fresh(P.SOH)),
                Tile(
                    str(R.string.cell_deviation),
                    formatValue(if (maxCell != null && minCell != null) (maxCell - minCell) * 1000 else null, 0),
                    "mV",
                    fresh(P.CELL_V_MAX),
                ),
                Tile(
                    str(R.string.battery_temp),
                    "${formatValue(v(P.BATTERY_TEMP_MIN), 0)}…${formatValue(v(P.BATTERY_TEMP_MAX), 0)}",
                    "°C",
                    fresh(P.BATTERY_TEMP_MAX),
                ),
            ),
            columns = 1,
        )
    }

    private fun wheels(c: Canvas, b: RectF, settings: Settings) {
        val h = b.height()
        val carW = h * 0.42f
        val carH = h * 0.9f
        val cx = b.left + b.width() * 0.32f
        val body = RectF(cx - carW / 2, b.top + (h - carH) / 2, cx + carW / 2, b.top + (h + carH) / 2)
        stroke.color = textSecondary
        stroke.strokeWidth = h * 0.008f
        c.drawRoundRect(body, carW * 0.3f, carW * 0.3f, stroke)
        val frontY = body.top + carH * 0.2f
        val rearY = body.top + carH * 0.8f
        val wheelW = h * 0.07f
        val wheelH = h * 0.15f
        val steering = v(P.STEERING_ANGLE)
        val unit = settings.pressureUnit
        listOf(
            WheelIds(P.TIRE_PRESSURE_FL, P.TIRE_TEMP_FL, P.WHEEL_SPEED_FL, front = true, left = true),
            WheelIds(P.TIRE_PRESSURE_FR, P.TIRE_TEMP_FR, P.WHEEL_SPEED_FR, front = true, left = false),
            WheelIds(P.TIRE_PRESSURE_RL, P.TIRE_TEMP_RL, P.WHEEL_SPEED_RL, front = false, left = true),
            WheelIds(P.TIRE_PRESSURE_RR, P.TIRE_TEMP_RR, P.WHEEL_SPEED_RR, front = false, left = false),
        ).forEach { (pId, tId, speedId, front, left) ->
            val x = if (left) body.left else body.right
            val y = if (front) frontY else rearY
            val bar = v(pId)
            fill.color = when {
                bar == null -> pixelOff
                bar < settings.lowPressureBar -> warning
                else -> accent
            }
            c.save()
            // Positiivne roolinurk = vasakule; Canvas pöörab päripäeva
            if (front && steering != null) c.rotate(-(steering / 14).toFloat(), x, y)
            c.drawRoundRect(RectF(x - wheelW / 2, y - wheelH / 2, x + wheelW / 2, y + wheelH / 2), wheelW * 0.3f, wheelW * 0.3f, fill)
            c.restore()
            val tx = if (left) x - wheelW * 0.8f else x + wheelW * 0.8f
            text.textAlign = if (left) Paint.Align.RIGHT else Paint.Align.LEFT
            text.color = textPrimary
            text.textSize = h * 0.055f
            c.drawText("${formatValue(bar?.times(unit.fromBar), unit.decimals)} ${unit.label}", tx, y, text)
            text.color = textSecondary
            text.textSize = h * 0.045f
            c.drawText("${formatValue(v(tId), 0)} °C", tx, y + h * 0.06f, text)
            // Ratta kiirus pidurisüsteemist (ainult süüte ajal)
            c.drawText("${formatValue(v(speedId), 0)} km/h", tx, y + h * 0.115f, text)
            text.textAlign = Paint.Align.LEFT
        }
        val right = b.left + b.width() * 0.62f
        tileGrid(
            c,
            RectF(right, b.top, b.right, b.bottom - h * 0.06f),
            listOf(
                Tile(
                    str(R.string.steering_angle),
                    formatValue(steering?.let { abs(it) }, 0) + when {
                        steering == null -> ""
                        abs(steering) < 0.5 -> "°"
                        steering > 0 -> "° ←"
                        else -> "° →"
                    },
                    "",
                    fresh(P.STEERING_ANGLE),
                ),
                Tile(str(R.string.speed_calc), formatValue(r.speedKmh(), 0), "km/h", fresh(P.VEHICLE_SPEED) || fresh(P.MOTOR_RPM)),
                Tile(str(R.string.front_axle) + " rpm", formatValue(v(P.MOTOR2_RPM), 0), "", fresh(P.MOTOR2_RPM)),
                Tile(str(R.string.rear_axle) + " rpm", formatValue(v(P.MOTOR_RPM), 0), "", fresh(P.MOTOR_RPM)),
            ),
            columns = 1,
        )
    }

    private fun charging(c: Canvas, b: RectF, settings: Settings) {
        val h = b.height()
        val power = r.batteryPowerKw()
        val charging = fresh(P.BATTERY_CURRENT) && power != null && power < -0.5
        val soc = v(P.SOC_DISPLAY) ?: v(P.SOC_BMS)
        val leftW = b.width() * 0.45f
        label(
            c,
            (if (charging) str(R.string.charging_now) else str(R.string.not_charging)).uppercase(),
            b.left,
            b.top + h * 0.08f,
            h * 0.045f,
            if (charging) accent else textSecondary,
        )
        big(c, if (charging) formatValue(-power!!, 1) else formatValue(soc, 0), if (charging) " kW" else " %", b.left, b.top + h * 0.38f, h * 0.28f, true)
        pixelGauge(c, RectF(b.left, b.top + h * 0.48f, b.left + leftW * 0.9f, b.bottom - h * 0.08f), soc?.div(100))
        val capacity = settings.capacityKwh(r.cellVoltages().size)
        fun eta(target: Double) = r.minutesToSoc(target, capacity)?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) } ?: "—"
        tileGrid(
            c,
            RectF(b.left + leftW, b.top, b.right, b.bottom - h * 0.06f),
            listOf(
                Tile(str(R.string.state_of_charge), formatValue(soc, 1), "%", fresh(P.SOC_BMS)),
                Tile(str(R.string.time_to_80), eta(80.0), "h:min", charging),
                Tile(str(R.string.ac_input_power), formatValue(r.acInputPowerKw(), 1), "kW", fresh(P.OBC_AC_VOLTAGE)),
                Tile(
                    str(R.string.battery_temp),
                    "${formatValue(v(P.BATTERY_TEMP_MIN), 0)}…${formatValue(v(P.BATTERY_TEMP_MAX), 0)}",
                    "°C",
                    fresh(P.BATTERY_TEMP_MAX),
                ),
            ),
            columns = 2,
        )
    }

    private fun solar(c: Canvas, b: RectF, energyWh: Double) {
        val h = b.height()
        val leftW = b.width() * 0.45f
        val power = v(P.SOLAR_POWER)
        label(c, str(R.string.solar_power).uppercase(), b.left, b.top + h * 0.08f, h * 0.045f)
        big(c, formatValue(power, 0), " W", b.left, b.top + h * 0.38f, h * 0.28f, fresh(P.SOLAR_POWER))
        // Katuse tippvõimsus on umbes 200 W
        pixelGauge(c, RectF(b.left, b.top + h * 0.48f, b.left + leftW * 0.9f, b.bottom - h * 0.08f), power?.div(SOLAR_PEAK_W))
        tileGrid(
            c,
            RectF(b.left + leftW, b.top, b.right, b.bottom - h * 0.06f),
            listOf(
                Tile(str(R.string.solar_session_energy), formatValue(energyWh, 1), "Wh", true),
                Tile(str(R.string.iccu_hv_voltage), formatValue(v(P.ICCU_HV_VOLTAGE), 0), "V", fresh(P.ICCU_HV_VOLTAGE)),
            ),
            columns = 1,
        )
    }

    private fun climate(c: Canvas, b: RectF) {
        val h = b.height()
        // Salongi joonis on püstine nagu telefonis (esiklaas üleval), paremal näidud
        val interior = RectF(b.left + b.width() * 0.02f, b.top, b.left + b.width() * 0.02f + h * 0.72f, b.bottom)
        drawInterior(c, interior)
        tileGrid(
            c,
            RectF(interior.right + b.width() * 0.04f, b.top, b.right, b.bottom - h * 0.06f),
            listOf(
                Tile(str(R.string.cabin_temp), formatValue(v(P.CABIN_TEMP), 1), "°C", fresh(P.CABIN_TEMP)),
                Tile(str(R.string.outdoor_temp), formatValue(v(P.OUTDOOR_TEMP), 1), "°C", fresh(P.OUTDOOR_TEMP)),
                Tile(str(R.string.cabin_humidity), formatValue(v(P.CABIN_HUMIDITY), 0), "%", fresh(P.CABIN_HUMIDITY)),
                Tile(
                    str(R.string.set_temp),
                    "${formatValue(v(P.SET_TEMP_DRIVER), 1)} / ${formatValue(v(P.SET_TEMP_PASSENGER), 1)}",
                    "°C",
                    fresh(P.SET_TEMP_DRIVER),
                ),
                Tile(str(R.string.evaporator_temp), formatValue(v(P.EVAPORATOR_TEMP), 1), "°C", fresh(P.EVAPORATOR_TEMP)),
                Tile(
                    str(R.string.vent_temp),
                    "${formatValue(v(P.VENT_TEMP_1), 0)} / ${formatValue(v(P.VENT_TEMP_2), 0)}",
                    "°C",
                    fresh(P.VENT_TEMP_1),
                ),
            ),
            columns = 2,
        )
    }

    /**
     * Ioniq 5 salong pealtvaates, sama geomeetria nagu telefoni kliima vaates. Soojendus sees = oranž
     * koos lainetega, väljas = türkiis, teadmata (baiti pole leitud) = hall.
     */
    private fun drawInterior(c: Canvas, a: RectF) {
        val w = a.width()
        val h = a.height()
        val sw = w * 0.008f
        fun level(id: String) = v(id)?.toInt()
        val outdoor = v(P.OUTDOOR_TEMP)
        val cabin = v(P.CABIN_TEMP)

        text.textAlign = Paint.Align.CENTER
        text.color = textPrimary
        text.textSize = h * 0.045f
        c.drawText("${str(R.string.outdoor_temp)}  ${formatValue(outdoor, 1)} °C", a.left + w / 2, a.top + h * 0.055f, text)

        val bodyW = w * 0.56f
        val bodyH = h * 0.84f
        val left = a.left + (w - bodyW) / 2
        val top = a.top + h * 0.09f
        val right = left + bodyW
        stroke.color = textSecondary
        stroke.strokeWidth = sw
        c.drawRoundRect(RectF(left, top, right, top + bodyH), bodyW * 0.14f, bodyW * 0.14f, stroke)

        // Peeglid
        val mirrors = level(P.MIRROR_HEAT)
        val mirrorY = top + bodyH * 0.28f
        fill.color = if (mirrors == null) pixelOff else heaterColor(mirrors)
        listOf(left - w * 0.07f, right).forEach { x ->
            c.drawRoundRect(RectF(x, mirrorY, x + w * 0.07f, mirrorY + h * 0.035f), w * 0.012f, w * 0.012f, fill)
        }
        if ((mirrors ?: 0) > 0) {
            heatWaves(c, left - w * 0.035f, mirrorY - h * 0.01f, w * 0.03f, warning, 2)
            heatWaves(c, right + w * 0.035f, mirrorY - h * 0.01f, w * 0.03f, warning, 2)
        }

        // Esiklaas ja tagaklaas
        val windshield = level(P.WINDSHIELD_HEAT)
        val rearWindow = level(P.REAR_WINDOW_HEAT)
        glass(c, Path().apply {
            moveTo(left + bodyW * 0.10f, top + bodyH * 0.21f)
            lineTo(right - bodyW * 0.10f, top + bodyH * 0.21f)
            lineTo(right - bodyW * 0.16f, top + bodyH * 0.11f)
            lineTo(left + bodyW * 0.16f, top + bodyH * 0.11f)
            close()
        }, windshield, sw)
        glass(c, Path().apply {
            moveTo(left + bodyW * 0.14f, top + bodyH * 0.84f)
            lineTo(right - bodyW * 0.14f, top + bodyH * 0.84f)
            lineTo(right - bodyW * 0.18f, top + bodyH * 0.92f)
            lineTo(left + bodyW * 0.18f, top + bodyH * 0.92f)
            close()
        }, rearWindow, sw)
        if ((windshield ?: 0) > 0) heatWaves(c, a.left + w / 2, top + bodyH * 0.16f, bodyW * 0.22f, warning, 3)
        if ((rearWindow ?: 0) > 0) heatWaves(c, a.left + w / 2, top + bodyH * 0.88f, bodyW * 0.18f, warning, 3)

        // Küljeaknad
        val windowW = bodyW * 0.035f
        fill.color = pixelOff
        listOf(0.30f to 0.50f, 0.54f to 0.78f).forEach { (from, to) ->
            listOf(left + sw * 1.5f, right - sw * 1.5f - windowW).forEach { x ->
                c.drawRoundRect(RectF(x, top + bodyH * from, x + windowW, top + bodyH * to), windowW / 2, windowW / 2, fill)
            }
        }

        // Istmed
        val seatW = bodyW * 0.30f
        val seatH = bodyH * 0.17f
        val frontY = top + bodyH * 0.33f
        val rearY = top + bodyH * 0.60f
        val driverX = left + bodyW * 0.12f
        val passengerX = right - bodyW * 0.12f - seatW
        seat(c, RectF(driverX, frontY, driverX + seatW, frontY + seatH), level(P.SEAT_HEAT_DRIVER), level(P.SEAT_VENT_DRIVER))
        seat(c, RectF(passengerX, frontY, passengerX + seatW, frontY + seatH), level(P.SEAT_HEAT_PASSENGER), level(P.SEAT_VENT_PASSENGER))
        seat(c, RectF(driverX, rearY, driverX + seatW, rearY + seatH), level(P.SEAT_HEAT_REAR_LEFT), null)
        seat(c, RectF(passengerX, rearY, passengerX + seatW, rearY + seatH), level(P.SEAT_HEAT_REAR_RIGHT), null)

        // Rool juhi ees
        val steering = level(P.STEERING_HEAT)
        val cx = driverX + seatW / 2
        val cy = top + bodyH * 0.265f
        val wheelR = seatW * 0.30f
        stroke.color = if (steering == null) textSecondary else heaterColor(steering)
        stroke.strokeWidth = sw * 1.6f
        c.drawCircle(cx, cy, wheelR, stroke)
        stroke.strokeWidth = sw * 1.2f
        c.drawLine(cx - wheelR, cy + wheelR * 0.2f, cx + wheelR, cy + wheelR * 0.2f, stroke)
        if ((steering ?: 0) > 0) heatWaves(c, cx, cy - wheelR * 0.35f, wheelR * 0.9f, warning, 3)

        // Salongi temperatuur keskel
        text.textAlign = Paint.Align.CENTER
        text.color = textPrimary
        text.textSize = h * 0.04f
        c.drawText("${formatValue(cabin, 1)} °C", a.left + w / 2, top + bodyH * 0.565f, text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun seat(c: Canvas, s: RectF, heat: Int?, vent: Int?) {
        val corner = s.width() * 0.18f
        fill.color = heaterColor(heat)
        c.drawRoundRect(s, corner, corner, fill)
        fill.color = textSecondary
        fill.alpha = 150
        c.drawRoundRect(RectF(s.left, s.top + s.height() * 0.80f, s.right, s.bottom), corner, corner, fill)
        fill.alpha = 255
        if ((heat ?: 0) > 0) heatWaves(c, s.centerX(), s.top + s.height() * 0.40f, s.width() * 0.55f, 0xFF0B0E11.toInt(), heat!!.coerceIn(1, 3))
        if ((vent ?: 0) > 0) {
            fill.color = VENT_BLUE
            repeat(vent!!.coerceIn(1, 3)) { i ->
                c.drawCircle(s.left + s.width() * (0.3f + 0.2f * i), s.top + s.height() * 0.68f, s.width() * 0.05f, fill)
            }
        }
    }

    private fun glass(c: Canvas, path: Path, level: Int?, sw: Float) {
        fill.color = pixelOff
        c.drawPath(path, fill)
        if ((level ?: 0) > 0) {
            stroke.color = warning
            stroke.strokeWidth = sw * 1.5f
            c.drawPath(path, stroke)
        }
    }

    /** Soojuse lained: [count] lainelist püstjoont laiusega [width]. */
    private fun heatWaves(c: Canvas, x0: Float, y0: Float, width: Float, color: Int, count: Int) {
        val height = width * 0.9f
        val gap = width / 4
        stroke.color = color
        stroke.strokeWidth = width * 0.07f
        stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0 until count) {
            val x = x0 + (i - (count - 1) / 2f) * gap
            c.drawPath(Path().apply {
                moveTo(x, y0 + height / 2)
                cubicTo(x - gap * 0.4f, y0 + height / 6, x + gap * 0.4f, y0 - height / 6, x, y0 - height / 2)
            }, stroke)
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun heaterColor(level: Int?): Int = when {
        level == null -> pixelOff
        level > 0 -> warning
        else -> blend(background, accent, 0.35f)
    }

    // ---------- Abifunktsioonid ----------

    private data class WheelIds(val pressure: String, val temp: String, val speed: String, val front: Boolean, val left: Boolean)

    private data class Tile(val label: String, val value: String, val unit: String, val fresh: Boolean, val valueColor: Int? = null)

    private fun tileGrid(c: Canvas, area: RectF, tiles: List<Tile>, columns: Int) {
        val rows = (tiles.size + columns - 1) / columns
        val gap = area.height() * 0.03f
        val w = (area.width() - gap * (columns - 1)) / columns
        val h = (area.height() - gap * (rows - 1)) / rows
        tiles.forEachIndexed { i, t ->
            val x = area.left + (i % columns) * (w + gap)
            val y = area.top + (i / columns) * (h + gap)
            fill.color = surface
            c.drawRoundRect(RectF(x, y, x + w, y + h), h * 0.12f, h * 0.12f, fill)
            val alpha = if (t.fresh) 255 else 110
            text.textAlign = Paint.Align.LEFT
            text.color = textSecondary
            text.alpha = alpha
            fitTextSize(t.label.uppercase(), min(h * 0.2f, w * 0.09f), w * 0.88f)
            c.drawText(t.label.uppercase(), x + w * 0.06f, y + h * 0.3f, text)
            text.color = t.valueColor ?: textPrimary
            text.alpha = alpha
            fitTextSize(t.value, min(h * 0.42f, w * 0.2f), w * 0.7f)
            val valueX = x + w * 0.06f
            val valueY = y + h * 0.82f
            c.drawText(t.value, valueX, valueY, text)
            val vw = text.measureText(t.value)
            text.color = textSecondary
            text.alpha = alpha
            text.textSize = min(h * 0.2f, w * 0.09f)
            c.drawText(" " + t.unit, valueX + vw, valueY, text)
        }
        text.alpha = 255
    }

    /** Seab teksti suuruse nii, et [s] mahub laiusesse [maxWidth] (pikad sildid, eriti eesti keeles). */
    private fun fitTextSize(s: String, size: Float, maxWidth: Float) {
        text.textSize = size
        val width = text.measureText(s)
        if (width > maxWidth) text.textSize = size * maxWidth / width
    }

    private fun pixelGauge(c: Canvas, area: RectF, fraction: Double?) {
        val columns = 10
        val rows = 5
        // Ruudu suurus nii, et näidik mahub nii laiuses kui ka kõrguses (Ioniq 5 ekraan on väga lai)
        val cell = min(area.width() / columns, area.height() / rows)
        val lit = ((fraction ?: 0.0).coerceIn(0.0, 1.0) * columns * rows).toInt()
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                fill.color = if (fraction != null && index < lit) accent else pixelOff
                val x = area.left + col * cell
                val y = area.top + (rows - 1 - row) * cell
                c.drawRoundRect(RectF(x + cell * 0.09f, y + cell * 0.09f, x + cell * 0.91f, y + cell * 0.91f), cell * 0.1f, cell * 0.1f, fill)
            }
        }
    }

    private fun big(c: Canvas, value: String, unit: String, x: Float, baseline: Float, size: Float, fresh: Boolean) {
        text.textAlign = Paint.Align.LEFT
        text.color = textPrimary
        text.alpha = if (fresh) 255 else 110
        text.textSize = size
        c.drawText(value, x, baseline, text)
        val w = text.measureText(value)
        text.color = textSecondary
        text.alpha = if (fresh) 255 else 110
        text.textSize = size * 0.35f
        c.drawText(unit, x + w, baseline, text)
        text.alpha = 255
    }

    private fun label(c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int = textSecondary) {
        text.textAlign = Paint.Align.LEFT
        text.color = color
        text.textSize = size
        c.drawText(s, x, baseline, text)
    }

    private fun scale(t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return if (k < 0.5f) blend(cold, accent, k * 2) else blend(accent, warning, (k - 0.5f) * 2)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun v(id: String): Double? = r[id]?.takeIf { now - it.timeMs < STALE_MS }?.value

    private fun fresh(id: String): Boolean = r[id]?.let { now - it.timeMs < STALE_MS } == true

    private fun str(id: Int): String = context.getString(id)

    private companion object {
        const val STALE_MS = 15_000L
        const val SOLAR_PEAK_W = 200.0
        val VENT_BLUE = 0xFF4FA3FF.toInt()
    }
}
