package com.moodcamera.processing.grain

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Random

object FilmGrain {

    fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        if (intensity <= 0f) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val grainStrength = intensity * 40f
        val random = Random(System.nanoTime())

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)

            val noise = (random.nextGaussian() * grainStrength).toInt()

            val luma = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
            val lumaFactor = 1f - kotlin.math.abs(luma - 0.5f) * 2f

            val grainNoise = (noise * (0.5f + lumaFactor * 0.5f)).toInt()

            r = (r + grainNoise).coerceIn(0, 255)
            g = (g + grainNoise).coerceIn(0, 255)
            b = (b + grainNoise).coerceIn(0, 255)

            pixels[i] = Color.argb(Color.alpha(pixel), r, g, b)
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
