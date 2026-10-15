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

/** Ühe silla mootor: pöörded, pöördemoment (Nm), võimsus (kW) ja temperatuurid; null = teadmata. */
data class ChassisAxle(
    val label: String,
    val rpm: Double?,
    val torqueNm: Double?,
    val temps: String? = null,
) {
    /** Mehaaniline võimsus kW = Nm × rpm / 9549; negatiivne = taaste. */
    val powerKw: Double? get() = if (rpm != null && torqueNm != null) torqueNm * rpm / 9549 else null
    val active: Boolean get() = kotlin.math.abs(rpm ?: 0.0) > 50 || kotlin.math.abs(torqueNm ?: 0.0) > 5
}

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

    private val regen = 0xFF7CD957.toInt()
    private val cardBg = 0xFF14181C.toInt()
    private val cardText = 0xFFF2F4F5.toInt()
    private val cardDim = 0xFF9AA4AD.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }

    /** Joonistab foto [area] keskele (külgede suhe säilib). */
    fun draw(
        c: Canvas,
        area: RectF,
        wheels: List<ChassisWheel>,
        photo: Bitmap,
        front: ChassisAxle? = null,
        rear: ChassisAxle? = null,
        splitLabel: String? = null,
    ) {
        val k = min(area.width() / PW, area.height() / PH)
        c.save()
        c.translate(area.centerX() - PW * k / 2, area.centerY() - PH * k / 2)
        c.scale(k, k)
        fill.isFilterBitmap = true
        c.drawBitmap(photo, null, RectF(0f, 0f, PW, PH), fill)
        wheels.forEach { wheel(c, it) }
        // Mootorite kaardid sildade kohal (esimootor vasakul, tagamootor paremal)
        front?.let { axle(c, it, RectF(70f, 252f, 340f, 466f)) }
        rear?.let { axle(c, it, RectF(1020f, 252f, 1290f, 466f)) }
        // Veojaotus aku keskel
        if (front != null && rear != null) split(c, front, rear, splitLabel)
        c.restore()
    }

    private fun axle(c: Canvas, a: ChassisAxle, box: RectF) {
        fill.color = cardBg
        fill.alpha = 215
        c.drawRoundRect(box, 18f, 18f, fill)
        fill.alpha = 255
        if (a.active) {
            stroke.color = if ((a.powerKw ?: 0.0) < -0.5) regen else accent
            stroke.strokeWidth = 5f
            c.drawRoundRect(box, 18f, 18f, stroke)
        }
        val x = box.left + 18f
        val maxW = box.width() - 36f
        text.textAlign = Paint.Align.LEFT
        fun line(s: String, size: Float, color: Int, baseline: Float) {
            text.color = color
            text.textSize = size
            val w = text.measureText(s)
            if (w > maxW) text.textSize = size * maxW / w
            c.drawText(s, x, box.top + baseline, text)
        }
        line(a.label.uppercase(), 28f, cardDim, 38f)
        line("${fmt(a.rpm, 0)} rpm", 40f, cardText, 84f)
        line("${fmt(a.torqueNm, 0)} Nm · ${fmt(a.powerKw, 1)} kW", 32f, cardText, 126f)
        // Temperatuurid kahel real (" · " eraldab mootori ja inverteri)
        a.temps?.split(" · ")?.take(2)?.forEachIndexed { i, t -> line(t, 30f, cardDim, 166f + i * 36f) }
    }

    private fun split(c: Canvas, front: ChassisAxle, rear: ChassisAxle, label: String?) {
        val f = kotlin.math.abs(front.torqueNm ?: return)
        val r = kotlin.math.abs(rear.torqueNm ?: return)
        if (f + r < 5) return
        val share = (f / (f + r)).toFloat()
        val bar = RectF(470f, 345f, 890f, 373f)
        fill.color = cardDim
        c.drawRoundRect(bar, 14f, 14f, fill)
        fill.color = accent
        c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * share, bar.bottom), 14f, 14f, fill)
        text.textAlign = Paint.Align.CENTER
        text.color = cardText
        text.textSize = 34f
        c.drawText("${label ?: ""} ${fmt(share * 100.0, 0)} / ${fmt((1 - share) * 100.0, 0)} %".trim(), bar.centerX(), bar.top - 16f, text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun fmt(v: Double?, decimals: Int) = v?.let { "%.${decimals}f".format(it) } ?: "—"

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
