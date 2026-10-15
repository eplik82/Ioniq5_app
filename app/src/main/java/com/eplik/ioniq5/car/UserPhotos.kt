package com.eplik.ioniq5.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.eplik.ioniq5.R
import java.io.File

/**
 * Salongi ja platvormi fotod. Vaikimisi rakendusega kaasas olevad pildid (logod ja tekstid eemaldatud);
 * kasutaja võib need asendada oma failiga rakenduse failikaustas (`Android/data/com.eplik.ioniq5/files/`).
 */
object UserPhotos {
    /** Salong pealtvaates, 994 × 496, esiots vasakul, juht all. */
    const val CABIN = "cabin.png"

    /** E-GMP platvorm altvaates, 1361 × 718, esiots vasakul, juhi pool all. */
    const val CHASSIS = "chassis.png"

    private val bundled = mapOf(CABIN to R.drawable.cabin_photo, CHASSIS to R.drawable.chassis_photo)

    private class Entry(val stamp: Long, val bitmap: Bitmap?)

    private val cache = mutableMapOf<String, Entry>()

    /** Kasutaja fail, kui see on olemas, muidu kaasasolev pilt. Laaditakse uuesti ainult faili muutumisel. */
    @Synchronized
    fun load(context: Context, name: String): Bitmap? {
        val file = context.getExternalFilesDir(null)?.let { File(it, name) }
        val stamp = if (file?.isFile == true) file.lastModified() xor file.length() else 0L
        cache[name]?.takeIf { it.stamp == stamp }?.let { return it.bitmap }
        val bitmap = if (stamp != 0L) {
            BitmapFactory.decodeFile(file!!.path)
        } else {
            bundled[name]?.let { BitmapFactory.decodeResource(context.resources, it) }
        }
        cache[name] = Entry(stamp, bitmap)
        return bitmap
    }
}
