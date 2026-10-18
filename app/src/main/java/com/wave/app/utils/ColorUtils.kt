package com.wave.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ColorUtils {

    suspend fun extractDominantColor(imageUrl: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpsURLConnection
                connection.doInput = true
                connection.connect()

                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                getDominantColor(bitmap)
            } catch (e: Exception) {
                // Fallback на твой фиолетовый цвет
                Color.parseColor("#6A00FF")
            }
        }
    }

    private fun getDominantColor(bitmap: Bitmap): Int {
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false)

        val pixels = IntArray(smallBitmap.width * smallBitmap.height)
        smallBitmap.getPixels(pixels, 0, smallBitmap.width, 0, 0, smallBitmap.width, smallBitmap.height)

        val colorCount = mutableMapOf<Int, Int>()
        for (pixel in pixels) {
            if (Color.alpha(pixel) < 50) continue
            val color = pixel and 0x00FFFFFF
            colorCount[color] = colorCount.getOrDefault(color, 0) + 1
        }

        return colorCount.maxByOrNull { it.value }?.key ?: Color.parseColor("#6A00FF")
    }

    fun makeMutedColor(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        // Делаем цвет тусклым и монотонным
        hsl[1] = hsl[1] * 0.2f // Сильно уменьшаем насыщенность
        hsl[2] = hsl[2] * 0.3f // Делаем очень темным

        return ColorUtils.HSLToColor(hsl)
    }
}