package com.eplik.ioniq5.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lihtne joongraafik: punktid (x, y), x ja y skaala andmete järgi. Puuduvad väärtused katkestavad joone.
 * [zeroLine] joonistab y = 0 joone (nt võimsus: laadimine alla nulli).
 */
@Composable
fun LineChart(
    title: String,
    points: List<Pair<Double, Double?>>,
    yUnit: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    zeroLine: Boolean = false,
    xLabel: (Double) -> String = { formatValue(it, 0) },
) {
    val values = points.mapNotNull { it.second }
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (values.size < 2) {
            Text("—", style = MaterialTheme.typography.labelMedium)
            return@Column
        }
        val measurer = rememberTextMeasurer()
        val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
        val labelStyle = TextStyle(color = axisColor, fontSize = 10.sp)
        val yMin = minOf(values.min(), if (zeroLine) 0.0 else values.min())
        val yMax = maxOf(values.max(), if (zeroLine) 0.0 else values.max()).let { if (it == yMin) yMin + 1 else it }
        val xMin = points.first().first
        val xMax = points.last().first.let { if (it == xMin) xMin + 1 else it }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val left = 44.dp.toPx()
            val bottom = size.height - 14.dp.toPx()
            val w = size.width - left
            fun px(x: Double) = left + ((x - xMin) / (xMax - xMin)).toFloat() * w
            fun py(y: Double) = bottom - ((y - yMin) / (yMax - yMin)).toFloat() * bottom

            drawLine(axisColor, Offset(left, 0f), Offset(left, bottom), strokeWidth = 1f)
            drawLine(axisColor, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1f)
            if (zeroLine && yMin < 0 && yMax > 0) {
                drawLine(axisColor.copy(alpha = 0.5f), Offset(left, py(0.0)), Offset(size.width, py(0.0)), strokeWidth = 1f)
            }
            val path = Path()
            var penDown = false
            points.forEach { (x, y) ->
                if (y == null) {
                    penDown = false
                } else if (!penDown) {
                    path.moveTo(px(x), py(y)); penDown = true
                } else {
                    path.lineTo(px(x), py(y))
                }
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))

            drawText(measurer.measure("${formatValue(yMax, 1)} $yUnit", labelStyle), topLeft = Offset(0f, 0f))
            val minLabel = measurer.measure("${formatValue(yMin, 1)}", labelStyle)
            drawText(minLabel, topLeft = Offset(0f, bottom - minLabel.size.height))
            drawText(measurer.measure(xLabel(xMin), labelStyle), topLeft = Offset(left, bottom + 2))
            val endLabel = measurer.measure(xLabel(xMax), labelStyle)
            drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, bottom + 2))
        }
    }
}
