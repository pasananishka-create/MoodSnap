package com.moodcamera.processing.halation

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

object HalationEffect {

    fun applyHalation(bitmap: Bitmap, intensity: Float): Bitmap {
        if (intensity <= 0f) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = (255 * (0.8f - intensity * 0.2f)).toInt()
        val highlightMask = FloatArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val luma = Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
            if (luma > threshold) {
                highlightMask[i] = ((luma - threshold) / (255f - threshold)).coerceIn(0f, 1f)
            }
        }

        val radius = (intensity * 12f).toInt().coerceIn(1, 15)
        val blurredMask = boxBlur2D(highlightMask, width, height, radius)

        val glowStrength = intensity * 0.7f
        for (i in pixels.indices) {
            val mask = blurredMask[i]
            if (mask > 0.01f) {
                val pixel = pixels[i]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val glowR = (mask * glowStrength * 70f).roundToInt()
                val glowG = (mask * glowStrength * 15f).roundToInt()
                val glowB = (mask * glowStrength * 2f).roundToInt()

                pixels[i] = Color.argb(
                    Color.alpha(pixel),
                    (r + glowR).coerceIn(0, 255),
                    (g + glowG).coerceIn(0, 255),
                    (b + glowB).coerceIn(0, 255)
                )
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun boxBlur2D(input: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val temp = FloatArray(input.size)
        val out = FloatArray(input.size)
        val kernelSize = 2 * radius + 1
        val invKernel = 1f / kernelSize

        for (y in 0 until height) {
            var sum = 0f
            for (x in -radius..radius) {
                val xi = x.coerceIn(0, width - 1)
                sum += input[y * width + xi]
            }
            for (x in 0 until width) {
                temp[y * width + x] = sum * invKernel
                val removeX = (x - radius).coerceIn(0, width - 1)
                val addX = (x + radius + 1).coerceIn(0, width - 1)
                sum -= input[y * width + removeX]
                sum += input[y * width + addX]
            }
        }

        for (x in 0 until width) {
            var sum = 0f
            for (y in -radius..radius) {
                val yi = y.coerceIn(0, height - 1)
                sum += temp[yi * width + x]
            }
            for (y in 0 until height) {
                out[y * width + x] = sum * invKernel
                val removeY = (y - radius).coerceIn(0, height - 1)
                val addY = (y + radius + 1).coerceIn(0, height - 1)
                sum -= temp[removeY * width + x]
                sum += temp[addY * width + x]
            }
        }

        return out
    }
}
