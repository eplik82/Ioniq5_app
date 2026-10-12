package com.eplik.ioniq5.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/** Ühe ratta näidud valmis tekstina; [low] = null teadmata, false korras, true madal rõhk. */
data class ChassisWheel(
    val pressure: String,
    val temp: String,
    val speed: String,
    val low: Boolean?,
    val front: Boolean,
    val left: Boolean,
)

/**
 * Rehvid kasutaja E-GMP platvormi foto peal ([UserPhotos.CHASSIS], 1361 × 718, esiots vasakul, juhi pool all).
 * Rehvi peal värv (türkiis korras, oranž madal rõhk), näidud ratta kõrval aku kohal ja all.
 * Kasutavad nii telefoni rataste vaade kui ka Android Auto.
 */
class ChassisDrawing {

    private val accent = 0xFF2FD1C5.toInt()
    private val warning = 0xFFFFB547.toInt()
    // Foto taust on hele, seega tekst on alati tume
    private val textPrimary = 0xFF15191D.toInt()
    private val textSecondary = 0xFF4A545C.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }

    /** Joonistab foto [area] keskele (külgede suhe säilib). */
    fun draw(c: Canvas, area: RectF, wheels: List<ChassisWheel>, photo: Bitmap) {
        val k = min(area.width() / PW, area.height() / PH)
        c.save()
        c.translate(area.centerX() - PW * k / 2, area.centerY() - PH * k / 2)
        c.scale(k, k)
        fill.isFilterBitmap = true
        c.drawBitmap(photo, null, RectF(0f, 0f, PW, PH), fill)
        wheels.forEach { wheel(c, it) }
        c.restore()
    }

    private fun wheel(c: Canvas, w: ChassisWheel) {
        // Juhi pool (vasak) on foto all, esiots vasakul
        val top = !w.left
        val tire = RectF(
            if (w.front) 105f else 1030f,
            if (top) 68f else 570f,
            if (w.front) 333f else 1258f,
            if (top) 150f else 652f,
        )
        w.low?.let { low ->
            fill.color = if (low) warning else accent
            fill.alpha = if (low) 150 else 85
            c.drawRoundRect(tire, 18f, 18f, fill)
            fill.alpha = 255
        }
        // Näidud aku kohal (ülemised rattad) või all (alumised), esirataste omad vasakult, tagumised paremalt
        text.textAlign = if (w.front) Paint.Align.LEFT else Paint.Align.RIGHT
        val x = if (w.front) 352f else 1012f
        val y0 = if (top) 62f else 604f
        text.color = if (w.low == true) warning else textPrimary
        text.textSize = 46f
        c.drawText(w.pressure, x, y0, text)
        text.color = textSecondary
        text.textSize = 34f
        c.drawText(w.temp, x, y0 + 44f, text)
        c.drawText(w.speed, x, y0 + 84f, text)
        text.textAlign = Paint.Align.LEFT
    }

    companion object {
        const val PW = 1361f
        const val PH = 718f
    }
}
