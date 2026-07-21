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
import kotlin.math.sqrt
import kotlin.math.roundToInt

object ImageProcessor {

    private const val MAX_DIMENSION = 2048

    fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / maxOf(w, h)
        val newW = (w * scale).roundToInt()
        val newH = (h * scale).roundToInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun processImage(
        original: Bitmap,
        settings: CameraSettings,
        quality: com.moodcamera.domain.model.QualityType
    ): Bitmap {
        val src = downscaleIfNeeded(original.copy(Bitmap.Config.ARGB_8888, true))
        if (src !== original) original.recycle()

        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        applyEmulation(pixels, settings.emulationType)
        applyTone(pixels, settings.toneType, width, height)
        applySaturation(pixels, settings.emulationType, width, height)
        applyTemperature(pixels, settings.temperature, settings.tint)

        if (settings.fade > 0f) {
            applyFade(pixels, settings.fade)
        }

        src.setPixels(pixels, 0, width, 0, 0, width, height)

        var result = src

        result = applyContrastMatrix(result, settings.contrast)
        result = applyBrightnessMatrix(result, settings.brightness)

        if (settings.isGrainEnabled && quality.grainMultiplier > 0f) {
            val grainResult = FilmGrain.applyGrain(result, quality.grainMultiplier)
            if (grainResult !== result) result.recycle()
            result = grainResult
        }

        if (settings.isHalationEnabled && settings.halationIntensity > 0f) {
            val halationResult = HalationEffect.applyHalation(result, settings.halationIntensity)
            if (halationResult !== result) result.recycle()
            result = halationResult
        }

        if (settings.vignette > 0f) {
            val vignetteResult = applyVignette(result, settings.vignette)
            if (vignetteResult !== result) result.recycle()
            result = vignetteResult
        }

        return result
    }

    private fun applyEmulation(pixels: IntArray, type: EmulationType) {
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

            pixels[i] = Color.argb(
                Color.alpha(pixel),
                (newR * 255).roundToInt().coerceIn(0, 255),
                (newG * 255).roundToInt().coerceIn(0, 255),
                (newB * 255).roundToInt().coerceIn(0, 255)
            )
        }
    }

    private fun portraEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.1f + 0.05f).coerceIn(0f, 1f),
            (g * 1.02f + 0.02f).coerceIn(0f, 1f),
            (b * 0.92f).coerceIn(0f, 1f)
        )
    }

    private fun cinestillEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.05f + 0.08f).coerceIn(0f, 1f),
            (g * 0.95f).coerceIn(0f, 1f),
            (b * 1.15f + 0.05f).coerceIn(0f, 1f)
        )
    }

    private fun ektarEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.2f + 0.02f).coerceIn(0f, 1f),
            (g * 1.1f + 0.01f).coerceIn(0f, 1f),
            (b * 1.05f).coerceIn(0f, 1f)
        )
    }

    private fun fujiEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 0.95f).coerceIn(0f, 1f),
            (g * 1.0f + 0.03f).coerceIn(0f, 1f),
            (b * 1.05f + 0.05f).coerceIn(0f, 1f)
        )
    }

    private fun velviaEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.3f).coerceIn(0f, 1f),
            (g * 1.2f).coerceIn(0f, 1f),
            (b * 1.1f).coerceIn(0f, 1f)
        )
    }

    private fun proviaEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.02f).coerceIn(0f, 1f),
            (g * 1.0f).coerceIn(0f, 1f),
            (b * 1.03f).coerceIn(0f, 1f)
        )
    }

    private fun triXEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = r * 0.299f + g * 0.587f + b * 0.114f
        val contrast = ((luma - 0.5f) * 1.4f + 0.5f).coerceIn(0f, 1f)
        return Triple(contrast, contrast, contrast)
    }

    private fun hp5Emulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = r * 0.299f + g * 0.587f + b * 0.114f
        val contrast = ((luma - 0.5f) * 1.2f + 0.5f).coerceIn(0f, 1f)
        val warm = (contrast * 1.02f).coerceIn(0f, 1f)
        return Triple(warm, warm, contrast)
    }

    private fun arizonaEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.15f + 0.08f).coerceIn(0f, 1f),
            (g * 1.0f + 0.02f).coerceIn(0f, 1f),
            (b * 0.85f).coerceIn(0f, 1f)
        )
    }

    private fun metroEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 0.85f).coerceIn(0f, 1f),
            (g * 0.9f + 0.05f).coerceIn(0f, 1f),
            (b * 1.1f + 0.05f).coerceIn(0f, 1f)
        )
    }

    private fun gold200Emulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.1f + 0.05f).coerceIn(0f, 1f),
            (g * 1.05f).coerceIn(0f, 1f),
            (b * 0.9f).coerceIn(0f, 1f)
        )
    }

    private fun ultramaxEmulation(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.15f + 0.03f).coerceIn(0f, 1f),
            (g * 1.1f).coerceIn(0f, 1f),
            (b * 0.95f).coerceIn(0f, 1f)
        )
    }

    private fun applyTone(pixels: IntArray, toneType: ToneType, width: Int, height: Int) {
        if (toneType == ToneType.NEUTRAL) return

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel) / 255f
            var g = Color.green(pixel) / 255f
            var b = Color.blue(pixel) / 255f

            when (toneType) {
                ToneType.NEUTRAL -> {}
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

            pixels[i] = Color.argb(
                Color.alpha(pixel),
                (r * 255).roundToInt().coerceIn(0, 255),
                (g * 255).roundToInt().coerceIn(0, 255),
                (b * 255).roundToInt().coerceIn(0, 255)
            )
        }
    }

    private fun crushCurve(x: Float): Float = (x * x * x * 0.5f + x * x * 0.5f).coerceIn(0f, 1f)

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

    private fun applySaturation(pixels: IntArray, type: EmulationType, width: Int, height: Int) {
        val saturation = when (type) {
            EmulationType.VELVIA -> 1.4f
            EmulationType.EKTAR -> 1.3f
            EmulationType.ULTRAMAX -> 1.2f
            EmulationType.TRI_X -> 0f
            EmulationType.HP5 -> 0f
            EmulationType.FUJI_400H -> 0.8f
            EmulationType.METRO -> 0.7f
            else -> return
        }

        val invSat = 1f - saturation
        val rw = 0.2126f * invSat
        val gw = 0.7152f * invSat
        val bw = 0.0722f * invSat

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val gray = rw * r + gw * g + bw * b

            pixels[i] = Color.argb(
                Color.alpha(pixel),
                (gray + saturation * r).roundToInt().coerceIn(0, 255),
                (gray + saturation * g).roundToInt().coerceIn(0, 255),
                (gray + saturation * b).roundToInt().coerceIn(0, 255)
            )
        }
    }

    private fun applyTemperature(pixels: IntArray, temperature: Float, tint: Float) {
        if (temperature == 0f && tint == 0f) return

        val tempShift = temperature * 25.5f
        val tintShift = tint * 12.75f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)

            r = (r + tempShift).roundToInt().coerceIn(0, 255)
            b = (b - tempShift).roundToInt().coerceIn(0, 255)
            g = (g + tintShift).roundToInt().coerceIn(0, 255)

            pixels[i] = Color.argb(Color.alpha(pixel), r, g, b)
        }
    }

    private fun applyFade(pixels: IntArray, fade: Float) {
        val lift = (fade * 38f).roundToInt()

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (Color.red(pixel) + lift).coerceIn(0, 255)
            val g = (Color.green(pixel) + lift).coerceIn(0, 255)
            val b = (Color.blue(pixel) + lift).coerceIn(0, 255)
            pixels[i] = Color.argb(Color.alpha(pixel), r, g, b)
        }
    }

    private fun applyContrastMatrix(bitmap: Bitmap, contrast: Float): Bitmap {
        if (contrast == 1f) return bitmap
        val c = contrast
        val t = -0.5f * c + 0.5f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyBrightnessMatrix(bitmap: Bitmap, brightness: Float): Bitmap {
        if (brightness == 0f) return bitmap
        val shift = brightness * 255f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, shift,
                    0f, 1f, 0f, 0f, shift,
                    0f, 0f, 1f, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                ))
            )
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
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
        val maxDist = sqrt(centerX * centerX + centerY * centerY)
        val maxDistSq = maxDist * maxDist
        val radiusSq = maxDistSq * 0.8f

        for (y in 0 until height) {
            val dy = y - centerY
            val dySq = dy * dy
            for (x in 0 until width) {
                val dx = x - centerX
                val distSq = dx * dx + dySq
                val vignette = if (distSq < radiusSq) 1f else {
                    val t = (distSq - radiusSq) / (maxDistSq - radiusSq)
                    (1f - t * t * intensity).coerceIn(0f, 1f)
                }

                val i = y * width + x
                val pixel = pixels[i]
                pixels[i] = Color.argb(
                    Color.alpha(pixel),
                    (Color.red(pixel) * vignette).roundToInt().coerceIn(0, 255),
                    (Color.green(pixel) * vignette).roundToInt().coerceIn(0, 255),
                    (Color.blue(pixel) * vignette).roundToInt().coerceIn(0, 255)
                )
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
