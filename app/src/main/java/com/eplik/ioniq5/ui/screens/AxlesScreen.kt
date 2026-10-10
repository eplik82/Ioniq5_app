package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow
import com.eplik.ioniq5.ui.components.scaleColor
import com.eplik.ioniq5.ui.theme.Accent
import com.eplik.ioniq5.ui.theme.PixelOff
import com.eplik.ioniq5.ui.theme.TextPrimary
import com.eplik.ioniq5.ui.theme.TextSecondary
import com.eplik.ioniq5.ui.theme.Warning
import kotlin.math.abs

/** Rooliratta ja esirataste nurga suhe (ligikaudne). */
private const val STEERING_RATIO = 14f

/** Rehvirõhu hoiatuse piir, bar. */
private const val LOW_PRESSURE_BAR = 2.2

/**
 * Rehvid ja sillad: auto pealtvaade (esiots üleval). Ratastel rõhk ja temperatuur, esirattad
 * pööratud roolinurga järgi, sildade juures mootorite andmed, keskel kiirendus.
 */
@Composable
fun AxlesScreen(readings: Readings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val measurer = rememberTextMeasurer()
    fun v(id: String) = readings[id]?.takeIf { it.isFresh(now) }?.value

    val steering = v(P.STEERING_ANGLE)
    val frontTorque = v(P.FRONT_MOTOR_TORQUE)
    val rearTorque = v(P.REAR_MOTOR_TORQUE)
    val split = if (frontTorque != null && rearTorque != null && abs(frontTorque) + abs(rearTorque) > 1) {
        abs(frontTorque) / (abs(frontTorque) + abs(rearTorque))
    } else {
        null
    }
    val labels = AxleLabels(
        front = stringResource(R.string.front_axle),
        rear = stringResource(R.string.rear_axle),
    )

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tires_axles_title), style = MaterialTheme.typography.displayMedium)
        Canvas(Modifier.fillMaxWidth().aspectRatio(0.75f)) {
            drawCar(
                measurer = measurer,
                steeringDeg = steering?.toFloat() ?: 0f,
                wheels = listOf(
                    Wheel(v(P.TIRE_PRESSURE_FL), v(P.TIRE_TEMP_FL), front = true, left = true),
                    Wheel(v(P.TIRE_PRESSURE_FR), v(P.TIRE_TEMP_FR), front = true, left = false),
                    Wheel(v(P.TIRE_PRESSURE_RL), v(P.TIRE_TEMP_RL), front = false, left = true),
                    Wheel(v(P.TIRE_PRESSURE_RR), v(P.TIRE_TEMP_RR), front = false, left = false),
                ),
                front = Motor(labels.front, frontTorque, v(P.MOTOR2_RPM)),
                rear = Motor(labels.rear, rearTorque, v(P.MOTOR_RPM)),
                gLat = v(P.LATERAL_ACCEL),
                gLon = v(P.LONGITUDINAL_ACCEL),
            )
        }
        Text(
            stringResource(R.string.steering_angle) + ": " + formatValue(steering, 1) + "°",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.torque_split) + ": " +
                (split?.let { "${formatValue(it * 100, 0)} / ${formatValue((1 - it) * 100, 0)} %" } ?: "—"),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.axles_data_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class AxleLabels(val front: String, val rear: String)

private data class Wheel(val pressureBar: Double?, val tempC: Double?, val front: Boolean, val left: Boolean)

private data class Motor(val label: String, val torqueNm: Double?, val rpm: Double?)

private fun DrawScope.drawCar(
    measurer: TextMeasurer,
    steeringDeg: Float,
    wheels: List<Wheel>,
    front: Motor,
    rear: Motor,
    gLat: Double?,
    gLon: Double?,
) {
    val w = size.width
    val h = size.height
    val bodyW = w * 0.42f
    val bodyH = h * 0.86f
    val bodyLeft = (w - bodyW) / 2
    val bodyTop = (h - bodyH) / 2
    val stroke = Stroke(width = w * 0.006f)

    // Kere
    drawRoundRect(TextSecondary, Offset(bodyLeft, bodyTop), Size(bodyW, bodyH), CornerRadius(bodyW * 0.28f), style = stroke)
    // Klaasikatus (päikesekatus)
    drawRoundRect(
        PixelOff,
        Offset(bodyLeft + bodyW * 0.15f, bodyTop + bodyH * 0.32f),
        Size(bodyW * 0.7f, bodyH * 0.36f),
        CornerRadius(bodyW * 0.08f),
    )

    val frontY = bodyTop + bodyH * 0.18f
    val rearY = bodyTop + bodyH * 0.82f
    val wheelW = w * 0.07f
    val wheelH = h * 0.12f

    // Sillad
    listOf(frontY to front, rearY to rear).forEach { (y, motor) ->
        drawLine(TextSecondary, Offset(bodyLeft - wheelW / 2, y), Offset(bodyLeft + bodyW + wheelW / 2, y), strokeWidth = w * 0.008f)
        val active = (motor.torqueNm ?: 0.0) != 0.0 || (motor.rpm ?: 0.0) != 0.0
        val boxW = bodyW * 0.62f
        val boxH = h * 0.085f
        drawRoundRect(
            if (active) Accent else PixelOff,
            Offset(w / 2 - boxW / 2, y - boxH / 2),
            Size(boxW, boxH),
            CornerRadius(boxH * 0.25f),
        )
        val text = "${motor.label}\n${formatValue(motor.torqueNm, 0)} Nm · ${formatValue(motor.rpm, 0)} rpm"
        drawCentered(measurer, text, Offset(w / 2, y), TextStyle(color = Color(0xFF0B0E11), fontSize = (h * 0.024f).toSp(), textAlign = TextAlign.Center))
    }

    // Rattad
    wheels.forEach { wheel ->
        val cx = if (wheel.left) bodyLeft else bodyLeft + bodyW
        val cy = if (wheel.front) frontY else rearY
        val low = wheel.pressureBar != null && wheel.pressureBar < LOW_PRESSURE_BAR
        val color = when {
            wheel.pressureBar == null -> TextSecondary
            low -> Warning
            else -> Accent
        }
        val angle = if (wheel.front) steeringDeg / STEERING_RATIO else 0f
        rotate(angle, pivot = Offset(cx, cy)) {
            drawRoundRect(color, Offset(cx - wheelW / 2, cy - wheelH / 2), Size(wheelW, wheelH), CornerRadius(wheelW * 0.3f))
        }
        val label = "${formatValue(wheel.pressureBar, 1)} bar\n${formatValue(wheel.tempC, 0)} °C"
        val labelX = if (wheel.left) (cx - wheelW / 2) / 2 else (cx + wheelW / 2 + w) / 2
        drawCentered(measurer, label, Offset(labelX, cy), TextStyle(color = TextPrimary, fontSize = (h * 0.028f).toSp(), textAlign = TextAlign.Center))
    }

    // Kiirenduse punkt (±0,5 g ring)
    val center = Offset(w / 2, h / 2)
    val radius = bodyW * 0.22f
    drawCircle(TextSecondary, radius, center, style = stroke)
    drawLine(TextSecondary, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = stroke.width / 2)
    drawLine(TextSecondary, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = stroke.width / 2)
    if (gLat != null && gLon != null) {
        val g = kotlin.math.hypot(gLat, gLon)
        val dot = Offset(
            center.x + (gLat / 0.5).coerceIn(-1.0, 1.0).toFloat() * radius,
            center.y - (gLon / 0.5).coerceIn(-1.0, 1.0).toFloat() * radius,
        )
        drawCircle(scaleColor((g / 0.5).toFloat()), radius * 0.15f, dot)
    }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, center: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2, center.y - layout.size.height / 2))
}
