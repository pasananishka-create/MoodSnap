package com.moodcamera.processing.halation

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.min

object HalationEffect {

    fun applyHalation(bitmap: Bitmap, intensity: Float): Bitmap {
        if (intensity <= 0f) return bitmap

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find highlight threshold based on intensity
        val threshold = (255 * (0.85f - intensity * 0.3f)).toInt()

        // Create highlight mask
        val highlightMask = FloatArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val luma = (r * 0.299f + g * 0.587f + b * 0.114f)

            if (luma > threshold) {
                highlightMask[i] = ((luma - threshold) / (255f - threshold)).coerceIn(0f, 1f)
            }
        }

        // Apply halation glow (warm bloom around highlights)
        val glowRadius = (intensity * 15f).toInt().coerceIn(1, 20)
        val glowStrength = intensity * 0.6f

        for (i in pixels.indices) {
            if (highlightMask[i] > 0f) {
                val x = i % width
                val y = i / width
                val mask = highlightMask[i]

                // Add warm glow (reddish-orange)
                val pixel = pixels[i]
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)

                val glowR = (mask * glowStrength * 60f).toInt()
                val glowG = (mask * glowStrength * 20f).toInt()
                val glowB = (mask * glowStrength * 5f).toInt()

                r = (r + glowR).coerceIn(0, 255)
                g = (g + glowG).coerceIn(0, 255)
                b = (b + glowB).coerceIn(0, 255)

                pixels[i] = Color.argb(Color.alpha(pixel), r, g, b)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
