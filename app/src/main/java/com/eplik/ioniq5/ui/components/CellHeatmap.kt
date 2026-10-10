package com.eplik.ioniq5.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.eplik.ioniq5.ui.theme.Accent
import com.eplik.ioniq5.ui.theme.Warning

private val Low = Color(0xFF3D6FD8)

/**
 * Elementide pinged pikslitena: madalaim sinine, keskmine türkiis, kõrgeim oranž.
 * Kui kõik on võrdsed, on kõik türkiissed.
 */
@Composable
fun CellHeatmap(cells: List<Double>, modifier: Modifier = Modifier, columns: Int = 12) {
    if (cells.isEmpty()) return
    val rows = (cells.size + columns - 1) / columns
    val min = cells.min()
    val max = cells.max()
    Canvas(modifier.aspectRatio(columns.toFloat() / rows)) {
        val cell = size.width / columns
        val gap = cell * 0.15f
        cells.forEachIndexed { index, v ->
            val t = if (max > min) ((v - min) / (max - min)).toFloat() else 0.5f
            val color = if (t < 0.5f) lerp(Low, Accent, t * 2) else lerp(Accent, Warning, (t - 0.5f) * 2)
            drawRoundRect(
                color = color,
                topLeft = Offset((index % columns) * cell + gap / 2, (index / columns) * cell + gap / 2),
                size = Size(cell - gap, cell - gap),
                cornerRadius = CornerRadius(gap / 2),
            )
        }
    }
}
