package com.eplik.ioniq5.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min

/** Soojenduste olek: null = teadmata (baiti pole veel leitud), 0 = väljas, 1…3 = tase. */
data class CabinState(
    val driverSeat: Int? = null,
    val passengerSeat: Int? = null,
    val rearLeftSeat: Int? = null,
    val rearRightSeat: Int? = null,
    val driverVent: Int? = null,
    val passengerVent: Int? = null,
    val steering: Int? = null,
    val windshield: Int? = null,
    val rearWindow: Int? = null,
    val mirrors: Int? = null,
)

/**
 * Ioniq 5 pealtvaates ilma katuseta: esiots vasakul, juht all (vasakpoolne rool). Joonis on tehtud
 * 1000 × 500 ühikus ja skaleeritakse antud alasse. Kasutavad nii telefoni kliima vaade kui ka Android Auto.
 * Soojendus sees = oranž koos soojuslainetega, väljas = türkiisi toon, teadmata = tavaline värv.
 */
class CabinDrawing(dark: Boolean) {

    private val body = if (dark) 0xFF2E353B.toInt() else 0xFFF7F8F9.toInt()
    private val outline = if (dark) 0xFF6B757C.toInt() else 0xFFB4BCC2.toInt()
    private val glass = if (dark) 0xFF1B2226.toInt() else 0xFF56615F.toInt()
    private val floor = 0xFF0E1114.toInt()
    private val seat = 0xFFD9DEE1.toInt()
    private val seatBack = 0xFFC3C9CD.toInt()
    private val dash = 0xFF7C868D.toInt()
    private val accent = 0xFF2FD1C5.toInt()
    private val warning = 0xFFFFB547.toInt()
    private val ventBlue = 0xFF4FA3FF.toInt()
    private val waveDark = 0xFF3A2A12.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Joonistab auto [area] keskele, külgede suhtega 2 : 1. */
    fun draw(c: Canvas, area: RectF, s: CabinState) {
        val k = min(area.width() / W, area.height() / H)
        c.save()
        c.translate(area.centerX() - W * k / 2, area.centerY() - H * k / 2)
        c.scale(k, k)

        mirrors(c, s.mirrors)

        // Kere: ümar nina vasakul, kandiline tagaots
        val bodyPath = Path().apply {
            moveTo(125f, 62f)
            lineTo(868f, 62f)
            quadTo(915f, 62f, 915f, 108f)
            lineTo(915f, 392f)
            quadTo(915f, 438f, 868f, 438f)
            lineTo(125f, 438f)
            cubicTo(62f, 438f, 45f, 395f, 45f, 250f)
            cubicTo(45f, 105f, 62f, 62f, 125f, 62f)
            close()
        }
        fill.color = body
        c.drawPath(bodyPath, fill)
        stroke.color = outline
        stroke.strokeWidth = 3f
        c.drawPath(bodyPath, stroke)
        // Kapoti jooned
        stroke.strokeWidth = 2f
        c.drawLine(95f, 110f, 250f, 100f, stroke)
        c.drawLine(95f, 390f, 250f, 400f, stroke)

        // Külgaknad ja B-piller
        fill.color = glass
        c.drawRoundRect(RectF(300f, 67f, 745f, 90f), 8f, 8f, fill)
        c.drawRoundRect(RectF(300f, 410f, 745f, 433f), 8f, 8f, fill)
        fill.color = body
        c.drawRect(553f, 66f, 566f, 91f, fill)
        c.drawRect(553f, 409f, 566f, 434f, fill)

        // Esiklaas
        val windshield = Path().apply {
            moveTo(345f, 94f)
            lineTo(345f, 406f)
            lineTo(282f, 398f)
            quadTo(242f, 250f, 282f, 102f)
            close()
        }
        glassPart(c, windshield, s.windshield)
        if ((s.windshield ?: 0) > 0) heatWaves(c, 305f, 250f, 60f, warning, 3)

        // Salongi põrand (klaaskatus eemaldatud)
        fill.color = floor
        c.drawRoundRect(RectF(345f, 93f, 746f, 407f), 10f, 10f, fill)

        // Armatuurlaud, ekraanid ja rool
        fill.color = dash
        c.drawRoundRect(RectF(345f, 100f, 382f, 400f), 8f, 8f, fill)
        fill.color = floor
        c.drawRoundRect(RectF(352f, 170f, 362f, 360f), 3f, 3f, fill)
        steeringWheel(c, s.steering)

        // Keskkonsool (Universal Island)
        fill.color = seat
        c.drawRoundRect(RectF(432f, 233f, 540f, 267f), 14f, 14f, fill)
        fill.color = dash
        c.drawCircle(452f, 250f, 7f, fill)
        c.drawCircle(472f, 250f, 7f, fill)

        // Esiistmed: kõrvalistuja üleval, juht all
        frontSeat(c, 176f, s.passengerSeat, s.passengerVent)
        frontSeat(c, 324f, s.driverSeat, s.driverVent)

        // Tagapink: paremal pool üleval, vasakul pool (juhi taga) all
        fill.color = seat
        c.drawRect(585f, 236f, 668f, 264f, fill)
        fill.color = seatBack
        c.drawRect(664f, 236f, 726f, 264f, fill)
        rearSeat(c, 180f, s.rearRightSeat)
        rearSeat(c, 320f, s.rearLeftSeat)

        // Pagasiruum ja tagaklaas
        val rear = Path().apply { addRoundRect(RectF(800f, 100f, 896f, 400f), 30f, 30f, Path.Direction.CW) }
        glassPart(c, rear, s.rearWindow)
        if ((s.rearWindow ?: 0) > 0) heatWaves(c, 848f, 250f, 70f, warning, 3)

        c.restore()
    }

    private fun mirrors(c: Canvas, level: Int?) {
        val color = heaterColor(level, body)
        listOf(false, true).forEach { bottom ->
            fun y(v: Float) = if (bottom) H - v else v
            val p = Path().apply {
                moveTo(328f, y(64f))
                lineTo(352f, y(28f))
                quadTo(370f, y(24f), 374f, y(36f))
                lineTo(364f, y(66f))
                close()
            }
            fill.color = color
            c.drawPath(p, fill)
            stroke.color = outline
            stroke.strokeWidth = 2.5f
            c.drawPath(p, stroke)
            if ((level ?: 0) > 0) heatWaves(c, 395f, y(30f), 26f, warning, 2)
        }
    }

    private fun steeringWheel(c: Canvas, level: Int?) {
        val cx = 388f
        val cy = 324f
        stroke.color = when {
            level == null -> 0xFF2A3036.toInt()
            level > 0 -> warning
            else -> accent
        }
        stroke.strokeWidth = 8f
        c.drawOval(RectF(cx - 14f, cy - 44f, cx + 14f, cy + 44f), stroke)
        stroke.strokeWidth = 6f
        c.drawLine(cx - 10f, cy, cx + 10f, cy, stroke)
        if ((level ?: 0) > 0) heatWaves(c, cx - 30f, cy, 34f, warning, 2)
    }

    private fun frontSeat(c: Canvas, cy: Float, heat: Int?, vent: Int?) {
        val color = heaterColor(heat, seat)
        fill.color = color
        c.drawRoundRect(RectF(408f, cy - 46f, 492f, cy + 46f), 20f, 20f, fill)
        fill.color = if ((heat ?: 0) > 0) darken(color) else heaterColor(heat, seatBack)
        c.drawRoundRect(RectF(484f, cy - 50f, 548f, cy + 50f), 24f, 24f, fill)
        fill.color = seatBack
        c.drawRoundRect(RectF(545f, cy - 25f, 561f, cy + 25f), 7f, 7f, fill)
        if ((heat ?: 0) > 0) heatWaves(c, 450f, cy, 56f, waveDark, heat!!.coerceIn(1, 3))
        if ((vent ?: 0) > 0) {
            fill.color = ventBlue
            repeat(vent!!.coerceIn(1, 3)) { i -> c.drawCircle(510f + 14f * i, cy, 5f, fill) }
        }
    }

    private fun rearSeat(c: Canvas, cy: Float, heat: Int?) {
        val color = heaterColor(heat, seat)
        fill.color = color
        c.drawRoundRect(RectF(585f, cy - 56f, 670f, cy + 56f), 18f, 18f, fill)
        fill.color = if ((heat ?: 0) > 0) darken(color) else heaterColor(heat, seatBack)
        c.drawRoundRect(RectF(662f, cy - 58f, 728f, cy + 58f), 22f, 22f, fill)
        fill.color = seatBack
        c.drawRoundRect(RectF(725f, cy - 24f, 740f, cy + 24f), 6f, 6f, fill)
        if ((heat ?: 0) > 0) heatWaves(c, 628f, cy, 56f, waveDark, heat!!.coerceIn(1, 3))
    }

    private fun glassPart(c: Canvas, path: Path, level: Int?) {
        fill.color = glass
        c.drawPath(path, fill)
        if ((level ?: 0) > 0) {
            stroke.color = warning
            stroke.strokeWidth = 5f
            c.drawPath(path, stroke)
        } else if (level == 0) {
            stroke.color = accent
            stroke.strokeWidth = 2.5f
            c.drawPath(path, stroke)
        }
    }

    /** Soojuslained: [count] lainelist püstjoont laiusega [width]. */
    private fun heatWaves(c: Canvas, x0: Float, y0: Float, width: Float, color: Int, count: Int) {
        val height = width * 0.9f
        val gap = width / 4
        stroke.color = color
        stroke.strokeWidth = width * 0.08f
        for (i in 0 until count) {
            val x = x0 + (i - (count - 1) / 2f) * gap
            c.drawPath(Path().apply {
                moveTo(x, y0 + height / 2)
                cubicTo(x - gap * 0.45f, y0 + height / 6, x + gap * 0.45f, y0 - height / 6, x, y0 - height / 2)
            }, stroke)
        }
    }

    private fun heaterColor(level: Int?, normal: Int): Int = when {
        level == null -> normal
        level > 0 -> warning
        else -> blend(normal, accent, 0.45f)
    }

    private fun darken(color: Int): Int = blend(color, Color.BLACK, 0.18f)

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private companion object {
        const val W = 1000f
        const val H = 500f
    }
}
