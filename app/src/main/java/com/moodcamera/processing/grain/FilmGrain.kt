package com.moodcamera.processing.grain

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.ln
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

object FilmGrain {

    private var lastRandom = Random.nextLong()

    fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        if (intensity <= 0f) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val grainStrength = intensity * 35f
        val random = Random(lastRandom)
        var seed = lastRandom

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)

            seed = seed * 6364136223846793005L + 1442695040888963407L
            val u1 = ((seed ushr 11) and 0x1FFFFFFF).toFloat() / 0x20000000fL
            val u2 = ((seed ushr 27) and 0x1FFFFFFF).toFloat() / 0x20000000fL
            val noise = if (u1 > 0f) {
                (sqrt(-2f * ln(u1)) * cos(2f * Math.PI.toFloat() * u2) * grainStrength).toInt()
            } else 0

            val luma = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
            val lumaFactor = 1f - kotlin.math.abs(luma - 0.5f) * 2f
            val grainNoise = (noise * (0.5f + lumaFactor * 0.5f)).toInt()

            r = (r + grainNoise).coerceIn(0, 255)
            g = (g + grainNoise).coerceIn(0, 255)
            b = (b + grainNoise).coerceIn(0, 255)

            pixels[i] = Color.argb(Color.alpha(pixel), r, g, b)
        }

        lastRandom = seed
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
