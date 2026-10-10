package com.eplik.ioniq5.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.eplik.ioniq5.ui.theme.Accent
import com.eplik.ioniq5.ui.theme.Background
import com.eplik.ioniq5.ui.theme.TextPrimary
import com.eplik.ioniq5.ui.theme.Warning
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val Cold = Color(0xFF3D6FD8)

enum class CellColorMode { VOLTAGE, DEVIATION }

/** Värv skaalal 0..1: sinine → türkiis → oranž. */
fun scaleColor(t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return if (c < 0.5f) lerp(Cold, Accent, c * 2) else lerp(Accent, Warning, (c - 0.5f) * 2)
}

/**
 * Aku elemendid ruudustikuna, väärtus iga ruudu sees. Puudutus valib elemendi ([onSelect] saab
 * indeksi 0-st või null). Pinge režiimis on värv min…max järgi, erinevuse režiimis keskmise suhtes.
 */
@Composable
fun BatteryCellGrid(
    cells: List<Double>,
    mode: CellColorMode,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 10,
    selectedColor: Color = TextPrimary,
) {
    if (cells.isEmpty()) return
    val rows = (cells.size + columns - 1) / columns
    val min = cells.min()
    val maxV = cells.max()
    val avg = cells.average()
    val maxDev = max(cells.maxOf { abs(it - avg) }, 0.005)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(columns.toFloat() / rows)
            .pointerInput(cells.size, columns) {
                detectTapGestures { pos ->
                    val cell = size.width.toFloat() / columns
                    val index = (pos.y / cell).toInt() * columns + (pos.x / cell).toInt()
                    onSelect(if (index in cells.indices && index != selected) index else null)
                }
            },
    ) {
        val cell = size.width / columns
        val gap = cell * 0.08f
        val style = TextStyle(color = Background, fontSize = (cell * 0.27f).toSp(), fontWeight = FontWeight.Medium)
        cells.forEachIndexed { index, v ->
            val t = when (mode) {
                CellColorMode.VOLTAGE -> if (maxV > min) ((v - min) / (maxV - min)).toFloat() else 0.5f
                CellColorMode.DEVIATION -> (0.5 + (v - avg) / maxDev / 2).toFloat()
            }
            val topLeft = Offset((index % columns) * cell + gap / 2, (index / columns) * cell + gap / 2)
            val box = Size(cell - gap, cell - gap)
            drawRoundRect(scaleColor(t), topLeft, box, CornerRadius(gap))
            if (index == selected) {
                drawRoundRect(selectedColor, topLeft, box, CornerRadius(gap), style = Stroke(width = gap * 1.5f))
            }
            val text = when (mode) {
                CellColorMode.VOLTAGE -> String.format(Locale.getDefault(), "%.2f", v)
                CellColorMode.DEVIATION -> String.format(Locale.getDefault(), "%+.0f", (v - avg) * 1000)
            }
            val layout = measurer.measure(text, style)
            drawText(
                layout,
                topLeft = Offset(
                    topLeft.x + (box.width - layout.size.width) / 2,
                    topLeft.y + (box.height - layout.size.height) / 2,
                ),
            )
        }
    }
}

/** Moodulite temperatuuriandurid (16) kahes reas, temperatuur ruudu sees. */
@Composable
fun ModuleTemperatureGrid(temps: List<Double?>, modifier: Modifier = Modifier, columns: Int = 8) {
    if (temps.all { it == null }) return
    val rows = (temps.size + columns - 1) / columns
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(columns.toFloat() / rows)) {
        val cell = size.width / columns
        val gap = cell * 0.08f
        val style = TextStyle(color = Background, fontSize = (cell * 0.26f).toSp(), fontWeight = FontWeight.Medium)
        val labelStyle = TextStyle(color = Background.copy(alpha = 0.7f), fontSize = (cell * 0.16f).toSp())
        temps.forEachIndexed { index, temp ->
            val topLeft = Offset((index % columns) * cell + gap / 2, (index / columns) * cell + gap / 2)
            val box = Size(cell - gap, cell - gap)
            // Skaala 5…40 °C
            val color = temp?.let { scaleColor(((it - 5) / 35).toFloat()) } ?: Color.Gray
            drawRoundRect(color, topLeft, box, CornerRadius(gap))
            val text = temp?.let { String.format(Locale.getDefault(), "%.0f°", it) } ?: "—"
            val layout = measurer.measure(text, style)
            drawText(layout, topLeft = Offset(topLeft.x + (box.width - layout.size.width) / 2, topLeft.y + box.height * 0.42f - layout.size.height / 2))
            val label = measurer.measure("M${index + 1}", labelStyle)
            drawText(label, topLeft = Offset(topLeft.x + (box.width - label.size.width) / 2, topLeft.y + box.height * 0.72f))
        }
    }
}
