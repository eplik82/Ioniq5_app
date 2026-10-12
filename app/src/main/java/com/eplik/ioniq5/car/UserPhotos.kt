package com.eplik.ioniq5.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Kasutaja enda fotod rakenduse failikaustas (`Android/data/com.eplik.ioniq5/files/`), pannakse sinna adb-ga.
 * Fotosid ei ole repos ega APK-s (Hyundai pildid, repo on avalik). Ilma fotota kasutatakse vektorjoonist.
 */
object UserPhotos {
    /** Salong pealtvaates, 994 × 496, esiots vasakul, juht all. */
    const val CABIN = "cabin.png"

    /** E-GMP platvorm altvaates, 1361 × 718, esiots vasakul, juhi pool all. */
    const val CHASSIS = "chassis.png"

    private class Entry(val stamp: Long, val bitmap: Bitmap?)

    private val cache = mutableMapOf<String, Entry>()

    /** Laadib foto; uuesti ainult siis, kui fail muutub. */
    @Synchronized
    fun load(dir: File?, name: String): Bitmap? {
        val file = dir?.let { File(it, name) } ?: return null
        if (!file.isFile) {
            cache.remove(name)
            return null
        }
        val stamp = file.lastModified() xor file.length()
        cache[name]?.takeIf { it.stamp == stamp }?.let { return it.bitmap }
        val bitmap = BitmapFactory.decodeFile(file.path)
        cache[name] = Entry(stamp, bitmap)
        return bitmap
    }
}
