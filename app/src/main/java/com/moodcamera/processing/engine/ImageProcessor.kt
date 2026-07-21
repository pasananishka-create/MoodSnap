package com.moodcamera.processing.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.moodcamera.domain.model.CameraSettings
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.ToneType
import com.moodcamera.processing.grain.FilmGrain
import com.moodcamera.processing.halation.HalationEffect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

object ImageProcessor {

    fun processImage(
        original: Bitmap,
        settings: CameraSettings,
        quality: com.moodcamera.domain.model.QualityType
    ): Bitmap {
        var result = original.copy(Bitmap.Config.ARGB_8888, true)

        // Step 1: Apply film emulation color grading
        result = applyEmulation(result, settings.emulationType)

        // Step 2: Apply tone curve
        result = applyTone(result, settings.toneType)

        // Step 3: Apply saturation
        result = adjustSaturation(result, settings.emulationType)

        // Step 4: Apply contrast
        result = adjustContrast(result, settings.contrast)

        // Step 5: Apply brightness
        result = adjustBrightness(result, settings.brightness)

        // Step 6: Apply temperature/tint
        result = adjustTemperature(result, settings.temperature, settings.tint)

        // Step 7: Apply fade
        if (settings.fade > 0f) {
            result = applyFade(result, settings.fade)
        }

        // Step 8: Apply film grain
        if (settings.isGrainEnabled && quality.grainMultiplier > 0f) {
            result = FilmGrain.applyGrain(result, quality.grainMultiplier)
        }

        // Step 9: Apply halation
        if (settings.isHalationEnabled && settings.halationIntensity > 0f) {
            result = HalationEffect.applyHalation(result, settings.halationIntensity)
        }

        // Step 10: Apply vignette
        if (settings.vignette > 0f) {
            result = applyVignette(result, settings.vignette)
        }

        return result
    }

    private fun applyEmulation(bitmap: Bitmap, type: EmulationType): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f

            val (newR, newG, newB) = when (type) {
                EmulationType.PORTRA -> portraEmulation(r, g, b)
                EmulationType.CINESTILL_800T -> cinestillEmulation(r, g, b)
                EmulationType.EKTAR -> ektarEmulation(r, g, b)
                EmulationType.FUJI_400H -> fujiEmulation(r, g, b)
                EmulationType.VELVIA -> velviaEmulation(r, g, b)
                EmulationType.PROVIA -> proviaEmulation(r, g, b)
                EmulationType.TRI_X -> triXEmulation(r, g, b)
                EmulationType.HP5 -> hp5Emulation(r, g, b)
                EmulationType.ARIZONA -> arizonaEmulation(r, g, b)
                EmulationType.METRO -> metroEmulation(r, g, b)
                EmulationType.GOLD_200 -> gold200Emulation(r, g, b)
                EmulationType.ULTRAMAX -> ultramaxEmulation(r, g, b)
            }

            pixels[i] = Color.rgb(
                (newR * 255).roundToInt().coerceIn(0, 255),
                (newG * 255).roundToInt().coerceIn(0, 255),
                (newB * 255).roundToInt().coerceIn(0, 255)
            )
        }

        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun portraEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.1f + 0.05f).coerceIn(0f, 1f)
        val newG = (g * 1.02f + 0.02f).coerceIn(0f, 1f)
        val newB = (b * 0.92f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun cinestillEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.05f + 0.08f).coerceIn(0f, 1f)
        val newG = (g * 0.95f).coerceIn(0f, 1f)
        val newB = (b * 1.15f + 0.05f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun ektarEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.2f + 0.02f).coerceIn(0f, 1f)
        val newG = (g * 1.1f + 0.01f).coerceIn(0f, 1f)
        val newB = (b * 1.05f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun fujiEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 0.95f).coerceIn(0f, 1f)
        val newG = (g * 1.0f + 0.03f).coerceIn(0f, 1f)
        val newB = (b * 1.05f + 0.05f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun velviaEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.3f).coerceIn(0f, 1f)
        val newG = (g * 1.2f).coerceIn(0f, 1f)
        val newB = (b * 1.1f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun proviaEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.02f).coerceIn(0f, 1f)
        val newG = (g * 1.0f).coerceIn(0f, 1f)
        val newB = (b * 1.03f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun triXEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = (r * 0.299f + g * 0.587f + b * 0.114f)
        val contrast = ((luma - 0.5f) * 1.4f + 0.5f).coerceIn(0f, 1f)
        return Triple(contrast, contrast, contrast)
    }

    private fun hp5Emulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = (r * 0.299f + g * 0.587f + b * 0.114f)
        val contrast = ((luma - 0.5f) * 1.2f + 0.5f).coerceIn(0f, 1f)
        val warm = (contrast * 1.02f).coerceIn(0f, 1f)
        return Triple(warm, warm, contrast)
    }

    private fun arizonaEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.15f + 0.08f).coerceIn(0f, 1f)
        val newG = (g * 1.0f + 0.02f).coerceIn(0f, 1f)
        val newB = (b * 0.85f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun metroEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 0.85f).coerceIn(0f, 1f)
        val newG = (g * 0.9f + 0.05f).coerceIn(0f, 1f)
        val newB = (b * 1.1f + 0.05f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun gold200Emulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.1f + 0.05f).coerceIn(0f, 1f)
        val newG = (g * 1.05f).coerceIn(0f, 1f)
        val newB = (b * 0.9f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun ultramaxEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val newR = (r * 1.15f + 0.03f).coerceIn(0f, 1f)
        val newG = (g * 1.1f).coerceIn(0f, 1f)
        val newB = (b * 0.95f).coerceIn(0f, 1f)
        return Triple(newR, newG, newB)
    }

    private fun applyTone(bitmap: Bitmap, toneType: ToneType): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel) / 255f
            var g = Color.green(pixel) / 255f
            var b = Color.blue(pixel) / 255f

            when (toneType) {
                ToneType.NEUTRAL -> { /* no change */ }
                ToneType.CRUSH -> {
                    r = crushCurve(r)
                    g = crushCurve(g)
                    b = crushCurve(b)
                }
                ToneType.ULTRA -> {
                    r = ultraCurve(r)
                    g = ultraCurve(g)
                    b = ultraCurve(b)
                }
                ToneType.DYNAMIC -> {
                    r = dynamicCurve(r)
                    g = dynamicCurve(g)
                    b = dynamicCurve(b)
                }
                ToneType.FADED -> {
                    r = (r * 0.85f + 0.1f).coerceIn(0f, 1f)
                    g = (g * 0.85f + 0.1f).coerceIn(0f, 1f)
                    b = (b * 0.85f + 0.1f).coerceIn(0f, 1f)
                }
                ToneType.EXPIRED -> {
                    r = (r * 0.8f + 0.12f).coerceIn(0f, 1f)
                    g = (g * 0.75f + 0.15f).coerceIn(0f, 1f)
                    b = (b * 0.7f + 0.18f).coerceIn(0f, 1f)
                }
                ToneType.BRIGHT -> {
                    r = (r * 1.15f + 0.05f).coerceIn(0f, 1f)
                    g = (g * 1.15f + 0.05f).coerceIn(0f, 1f)
                    b = (b * 1.15f + 0.05f).coerceIn(0f, 1f)
                }
                ToneType.MOODY -> {
                    r = (r * 0.7f).coerceIn(0f, 1f)
                    g = (g * 0.7f + 0.02f).coerceIn(0f, 1f)
                    b = (b * 0.8f + 0.05f).coerceIn(0f, 1f)
                }
            }

            pixels[i] = Color.rgb(
                (r * 255).roundToInt().coerceIn(0, 255),
                (g * 255).roundToInt().coerceIn(0, 255),
                (b * 255).roundToInt().coerceIn(0, 255)
            )
        }

        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun crushCurve(x: Float): Float {
        return x.pow(1.5f).coerceIn(0f, 1f)
    }

    private fun ultraCurve(x: Float): Float {
        return if (x > 0.5f) {
            (0.5f + (x - 0.5f) * 1.6f).coerceIn(0f, 1f)
        } else {
            (x * 0.4f).coerceIn(0f, 1f)
        }
    }

    private fun dynamicCurve(x: Float): Float {
        return if (x > 0.5f) {
            (0.5f + (x - 0.5f) * 1.3f).coerceIn(0f, 1f)
        } else {
            (x * 0.7f).coerceIn(0f, 1f)
        }
    }

    private fun adjustSaturation(bitmap: Bitmap, type: EmulationType): Bitmap {
        val saturation = when (type) {
            EmulationType.VELVIA -> 1.4f
            EmulationType.EKTAR -> 1.3f
            EmulationType.ULTRAMAX -> 1.2f
            EmulationType.TRI_X -> 0f
            EmulationType.HP5 -> 0f
            EmulationType.FUJI_400H -> 0.8f
            EmulationType.METRO -> 0.7f
            else -> 1.0f
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(saturation) }
            )
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, -0.5f * contrast + 0.5f,
                    0f, contrast, 0f, 0f, -0.5f * contrast + 0.5f,
                    0f, 0f, contrast, 0f, -0.5f * contrast + 0.5f,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        if (brightness == 0f) return bitmap
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, brightness * 255,
                    0f, 1f, 0f, 0f, brightness * 255,
                    0f, 0f, 1f, 0f, brightness * 255,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun adjustTemperature(bitmap: Bitmap, temperature: Float, tint: Float): Bitmap {
        if (temperature == 0f && tint == 0f) return bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel) / 255f
            var g = Color.green(pixel) / 255f
            var b = Color.blue(pixel) / 255f

            r = (r + temperature * 0.1f).coerceIn(0f, 1f)
            b = (b - temperature * 0.1f).coerceIn(0f, 1f)
            g = (g + tint * 0.05f).coerceIn(0f, 1f)

            pixels[i] = Color.rgb(
                (r * 255).roundToInt().coerceIn(0, 255),
                (g * 255).roundToInt().coerceIn(0, 255),
                (b * 255).roundToInt().coerceIn(0, 255)
            )
        }

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun applyFade(bitmap: Bitmap, fade: Float): Bitmap {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel) / 255f
            var g = Color.green(pixel) / 255f
            var b = Color.blue(pixel) / 255f

            r = (r + fade * 0.15f).coerceIn(0f, 1f)
            g = (g + fade * 0.15f).coerceIn(0f, 1f)
            b = (b + fade * 0.15f).coerceIn(0f, 1f)

            pixels[i] = Color.rgb(
                (r * 255).roundToInt().coerceIn(0, 255),
                (g * 255).roundToInt().coerceIn(0, 255),
                (b * 255).roundToInt().coerceIn(0, 255)
            )
        }

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun applyVignette(bitmap: Bitmap, intensity: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val centerX = width / 2f
        val centerY = height / 2f
        val maxDist = kotlin.math.sqrt(centerX * centerX + centerY * centerY)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy) / maxDist
                val vignette = 1f - (dist * dist * intensity)

                val i = y * width + x
                val pixel = pixels[i]
                val r = (Color.red(pixel) * vignette).roundToInt().coerceIn(0, 255)
                val g = (Color.green(pixel) * vignette).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(pixel) * vignette).roundToInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(r, g, b)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
