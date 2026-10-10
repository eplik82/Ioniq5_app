package com.eplik.ioniq5.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.eplik.ioniq5.ui.theme.Accent
import com.eplik.ioniq5.ui.theme.PixelOff

/**
 * „Parametric pixel” näidik: ruutude võrgustik, mis täitub alt üles ja vasakult paremale.
 * [fraction] null tähendab, et väärtust pole (kõik ruudud tuhmid).
 */
@Composable
fun PixelGauge(
    fraction: Float?,
    modifier: Modifier = Modifier,
    columns: Int = 10,
    rows: Int = 5,
    onColor: Color = Accent,
    offColor: Color = PixelOff,
) {
    val total = columns * rows
    val lit = ((fraction ?: 0f).coerceIn(0f, 1f) * total).toInt()
    Canvas(modifier.aspectRatio(columns.toFloat() / rows)) {
        val cell = size.width / columns
        val gap = cell * 0.18f
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                drawRoundRect(
                    color = if (fraction != null && index < lit) onColor else offColor,
                    topLeft = Offset(col * cell + gap / 2, (rows - 1 - row) * cell + gap / 2),
                    size = Size(cell - gap, cell - gap),
                    cornerRadius = CornerRadius(gap / 2),
                )
            }
        }
    }
}
