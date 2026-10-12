package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.ui.components.ValueTile
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow
import com.eplik.ioniq5.ui.theme.Accent
import com.eplik.ioniq5.ui.theme.Warning

/** Soojenduse olek joonisel: null = teadmata (baiti pole veel leitud), 0 = väljas, 1…3 = tase. */
private data class Heaters(
    val driverSeat: Int?,
    val passengerSeat: Int?,
    val rearLeftSeat: Int?,
    val rearRightSeat: Int?,
    val driverVent: Int?,
    val passengerVent: Int?,
    val steering: Int?,
    val windshield: Int?,
    val rearWindow: Int?,
    val mirrors: Int?,
)

private data class ClimateColors(val outline: Color, val glass: Color, val seat: Color, val text: Color, val dim: Color)

private val VentBlue = Color(0xFF4FA3FF)

/**
 * Kliima: Ioniq 5 salong pealtvaates (rool vasakul, esiklaas üleval). Istmetel, roolil, klaasidel ja
 * peeglitel on soojenduse ikoonid; keskel salongi temperatuur ja õhuniiskus, üleval välistemperatuur.
 */
@Composable
fun ClimateScreen(readings: Readings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    fun v(id: String) = readings[id]?.takeIf { it.isFresh(now) }?.value
    fun level(id: String) = v(id)?.toInt()
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val colors = ClimateColors(
        outline = scheme.onSurfaceVariant,
        glass = scheme.surfaceVariant,
        seat = scheme.surface,
        text = scheme.onBackground,
        dim = scheme.onSurfaceVariant,
    )
    val heaters = Heaters(
        driverSeat = level(P.SEAT_HEAT_DRIVER),
        passengerSeat = level(P.SEAT_HEAT_PASSENGER),
        rearLeftSeat = level(P.SEAT_HEAT_REAR_LEFT),
        rearRightSeat = level(P.SEAT_HEAT_REAR_RIGHT),
        driverVent = level(P.SEAT_VENT_DRIVER),
        passengerVent = level(P.SEAT_VENT_PASSENGER),
        steering = level(P.STEERING_HEAT),
        windshield = level(P.WINDSHIELD_HEAT),
        rearWindow = level(P.REAR_WINDOW_HEAT),
        mirrors = level(P.MIRROR_HEAT),
    )
    val cabin = v(P.CABIN_TEMP)
    val humidity = v(P.CABIN_HUMIDITY)
    val outdoor = v(P.OUTDOOR_TEMP)
    val labels = listOf(
        stringResource(R.string.cabin_temp),
        stringResource(R.string.outdoor_temp),
    )

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.climate_title), style = MaterialTheme.typography.displayMedium)
        Canvas(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
            drawInterior(
                measurer = measurer,
                colors = colors,
                heaters = heaters,
                cabinText = "${labels[0]}\n${formatValue(cabin, 1)} °C\n${formatValue(humidity, 0)} %",
                outdoorText = "${labels[1]}  ${formatValue(outdoor, 1)} °C",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(stringResource(R.string.cabin_temp), formatValue(cabin, 1), "°C", cabin != null, Modifier.weight(1f))
            ValueTile(
                stringResource(R.string.cabin_humidity),
                formatValue(humidity, 0),
                "%",
                humidity != null,
                Modifier.weight(1f),
                detail = stringResource(R.string.candidate),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(stringResource(R.string.outdoor_temp), formatValue(outdoor, 1), "°C", outdoor != null, Modifier.weight(1f))
            ValueTile(
                stringResource(R.string.outdoor_humidity),
                "—",
                "%",
                false,
                Modifier.weight(1f),
                detail = stringResource(R.string.outdoor_humidity_none),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(
                stringResource(R.string.set_temp),
                "${formatValue(v(P.SET_TEMP_DRIVER), 1)} / ${formatValue(v(P.SET_TEMP_PASSENGER), 1)}",
                "°C",
                v(P.SET_TEMP_DRIVER) != null,
                Modifier.weight(1f),
            )
            ValueTile(
                stringResource(R.string.evaporator_temp),
                formatValue(v(P.EVAPORATOR_TEMP), 1),
                "°C",
                v(P.EVAPORATOR_TEMP) != null,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(
                stringResource(R.string.vent_temp),
                "${formatValue(v(P.VENT_TEMP_1), 0)} / ${formatValue(v(P.VENT_TEMP_2), 0)}",
                "°C",
                v(P.VENT_TEMP_1) != null,
                Modifier.weight(1f),
                detail = stringResource(R.string.candidate),
            )
            ValueTile(
                stringResource(R.string.steering_heat),
                stringResource(if ((heaters.steering ?: 0) > 0) R.string.on else R.string.off),
                "",
                heaters.steering != null,
                Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.climate_heaters_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawInterior(
    measurer: TextMeasurer,
    colors: ClimateColors,
    heaters: Heaters,
    cabinText: String,
    outdoorText: String,
) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.008f

    // Välistemperatuur auto kohal
    drawCentered(measurer, outdoorText, Offset(w / 2, h * 0.035f), TextStyle(color = colors.text, fontSize = (h * 0.028f).toSp(), textAlign = TextAlign.Center))

    // Kere (Ioniq 5 on nurgeline: väikese raadiusega nurgad)
    val bodyW = w * 0.56f
    val bodyH = h * 0.84f
    val left = (w - bodyW) / 2
    val top = h * 0.09f
    val right = left + bodyW
    val bottom = top + bodyH
    drawRoundRect(colors.outline, Offset(left, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.14f), style = Stroke(stroke))

    // Peeglid esiuste juures
    val mirrorY = top + bodyH * 0.28f
    val mirrorColor = if (heaters.mirrors == null) colors.glass else heaterColor(heaters.mirrors, colors)
    listOf(left - w * 0.07f, right).forEach { x ->
        drawRoundRect(mirrorColor, Offset(x, mirrorY), Size(w * 0.07f, h * 0.035f), CornerRadius(w * 0.012f))
    }
    if ((heaters.mirrors ?: 0) > 0) {
        heatWaves(Offset(left - w * 0.035f, mirrorY - h * 0.01f), w * 0.03f, Warning, 2)
        heatWaves(Offset(right + w * 0.035f, mirrorY - h * 0.01f), w * 0.03f, Warning, 2)
    }

    // Esiklaas (trapets) ja tagaklaas
    val windshield = Path().apply {
        moveTo(left + bodyW * 0.10f, top + bodyH * 0.21f)
        lineTo(right - bodyW * 0.10f, top + bodyH * 0.21f)
        lineTo(right - bodyW * 0.16f, top + bodyH * 0.11f)
        lineTo(left + bodyW * 0.16f, top + bodyH * 0.11f)
        close()
    }
    drawGlass(windshield, heaters.windshield, colors, stroke)
    val rearWindow = Path().apply {
        moveTo(left + bodyW * 0.14f, top + bodyH * 0.84f)
        lineTo(right - bodyW * 0.14f, top + bodyH * 0.84f)
        lineTo(right - bodyW * 0.18f, top + bodyH * 0.92f)
        lineTo(left + bodyW * 0.18f, top + bodyH * 0.92f)
        close()
    }
    drawGlass(rearWindow, heaters.rearWindow, colors, stroke)
    if ((heaters.windshield ?: 0) > 0) heatWaves(Offset(w / 2, top + bodyH * 0.16f), bodyW * 0.22f, Warning, 3)
    if ((heaters.rearWindow ?: 0) > 0) heatWaves(Offset(w / 2, top + bodyH * 0.88f), bodyW * 0.18f, Warning, 3)

    // Küljeaknad (neli ust)
    val windowW = bodyW * 0.035f
    listOf(0.30f to 0.50f, 0.54f to 0.78f).forEach { (from, to) ->
        listOf(left + stroke * 1.5f, right - stroke * 1.5f - windowW).forEach { x ->
            drawRoundRect(colors.glass, Offset(x, top + bodyH * from), Size(windowW, bodyH * (to - from)), CornerRadius(windowW / 2))
        }
    }

    // Istmed: ees kaks, taga pink kahe soojendatava kohaga
    val seatW = bodyW * 0.30f
    val seatH = bodyH * 0.17f
    val frontY = top + bodyH * 0.33f
    val rearY = top + bodyH * 0.60f
    val driverX = left + bodyW * 0.12f
    val passengerX = right - bodyW * 0.12f - seatW
    drawSeat(Offset(driverX, frontY), Size(seatW, seatH), heaters.driverSeat, heaters.driverVent, colors)
    drawSeat(Offset(passengerX, frontY), Size(seatW, seatH), heaters.passengerSeat, heaters.passengerVent, colors)
    drawSeat(Offset(driverX, rearY), Size(seatW, seatH), heaters.rearLeftSeat, null, colors)
    drawSeat(Offset(passengerX, rearY), Size(seatW, seatH), heaters.rearRightSeat, null, colors)

    // Rool juhi ees (vasakpoolne rool)
    val wheelCenter = Offset(driverX + seatW / 2, top + bodyH * 0.265f)
    val wheelR = seatW * 0.30f
    val steeringColor = if (heaters.steering == null) colors.outline else heaterColor(heaters.steering, colors)
    drawCircle(steeringColor, wheelR, wheelCenter, style = Stroke(stroke * 1.6f))
    drawLine(steeringColor, Offset(wheelCenter.x - wheelR, wheelCenter.y + wheelR * 0.2f), Offset(wheelCenter.x + wheelR, wheelCenter.y + wheelR * 0.2f), strokeWidth = stroke * 1.2f)
    if ((heaters.steering ?: 0) > 0) heatWaves(Offset(wheelCenter.x, wheelCenter.y - wheelR * 0.35f), wheelR * 0.9f, Warning, 3)

    // Salongi temperatuur ja niiskus keskel (Ioniq 5 lame põrand / Universal Island)
    drawCentered(
        measurer,
        cabinText,
        Offset(w / 2, top + bodyH * 0.535f),
        TextStyle(color = colors.text, fontSize = (h * 0.026f).toSp(), textAlign = TextAlign.Center),
    )
}

/** Iste: põhi ja seljatugi; soojendus oranžid lained (tase 1–3), ventilatsioon sinised punktid. */
private fun DrawScope.drawSeat(topLeft: Offset, size: Size, heat: Int?, vent: Int?, colors: ClimateColors) {
    val color = heaterColor(heat, colors)
    val corner = CornerRadius(size.width * 0.18f)
    drawRoundRect(color, topLeft, size, corner)
    // Seljatugi istme tagaosas
    drawRoundRect(
        colors.outline.copy(alpha = 0.6f),
        Offset(topLeft.x, topLeft.y + size.height * 0.80f),
        Size(size.width, size.height * 0.20f),
        corner,
    )
    val center = Offset(topLeft.x + size.width / 2, topLeft.y + size.height * 0.40f)
    if ((heat ?: 0) > 0) heatWaves(center, size.width * 0.55f, Color(0xFF0B0E11), heat!!.coerceIn(1, 3))
    if ((vent ?: 0) > 0) {
        repeat(vent!!.coerceIn(1, 3)) { i ->
            drawCircle(VentBlue, size.width * 0.05f, Offset(topLeft.x + size.width * (0.3f + 0.2f * i), topLeft.y + size.height * 0.68f))
        }
    }
}

/** Soojuse lained: [count] vertikaalset lainelist joont, laiusega [width]. */
private fun DrawScope.heatWaves(center: Offset, width: Float, color: Color, count: Int) {
    val height = width * 0.9f
    val gap = width / 4
    for (i in 0 until count) {
        val x = center.x + (i - (count - 1) / 2f) * gap
        val path = Path().apply {
            moveTo(x, center.y + height / 2)
            cubicTo(x - gap * 0.4f, center.y + height / 6, x + gap * 0.4f, center.y - height / 6, x, center.y - height / 2)
        }
        drawPath(path, color, style = Stroke(width * 0.07f, cap = StrokeCap.Round))
    }
}

/** Klaas: klaasivärv; soojendus sees → oranž serv (lained joonistatakse peale). */
private fun DrawScope.drawGlass(path: Path, level: Int?, colors: ClimateColors, stroke: Float) {
    drawPath(path, colors.glass)
    if ((level ?: 0) > 0) drawPath(path, Warning, style = Stroke(stroke * 1.5f))
}

private fun heaterColor(level: Int?, colors: ClimateColors): Color = when {
    level == null -> colors.seat
    level > 0 -> Warning
    else -> Accent.copy(alpha = 0.35f)
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, center.y - layout.size.height / 2))
}
