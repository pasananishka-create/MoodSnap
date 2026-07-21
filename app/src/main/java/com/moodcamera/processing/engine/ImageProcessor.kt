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
        applyTone(pixels, settings.toneType)

        if (settings.fade > 0f) applyFade(pixels, settings.fade)

        src.setPixels(pixels, 0, width, 0, 0, width, height)

        var result = src

        result = applyContrastMatrix(result, settings.contrast)
        result = applyBrightnessMatrix(result, settings.brightness)
        result = applyTemperatureMatrix(result, settings.temperature, settings.tint)

        if (settings.isGrainEnabled && quality.grainMultiplier > 0f) {
            val g = FilmGrain.applyGrain(result, quality.grainMultiplier)
            if (g !== result) result.recycle()
            result = g
        }

        if (settings.isHalationEnabled && settings.halationIntensity > 0f) {
            val h = HalationEffect.applyHalation(result, settings.halationIntensity)
            if (h !== result) result.recycle()
            result = h
        }

        if (settings.vignette > 0f) {
            val v = applyVignette(result, settings.vignette)
            if (v !== result) result.recycle()
            result = v
        }

        return result
    }

    // ── Film emulations ──────────────────────────────────────────────

    private fun applyEmulation(pixels: IntArray, type: EmulationType) {
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = Color.red(px) / 255f
            val g = Color.green(px) / 255f
            val b = Color.blue(px) / 255f

            val (nr, ng, nb) = when (type) {
                // Instagram
                EmulationType.CLARENDON -> clarendon(r, g, b)
                EmulationType.JUNO -> juno(r, g, b)
                EmulationType.LARK -> lark(r, g, b)
                EmulationType.VALENCIA -> valencia(r, g, b)
                EmulationType.HUDSON -> hudson(r, g, b)
                EmulationType.REYES -> reyes(r, g, b)
                // Filmic
                EmulationType.PORTRA -> portra(r, g, b)
                EmulationType.FUJI_400H -> fuji400h(r, g, b)
                EmulationType.GOLD_200 -> gold200(r, g, b)
                EmulationType.ULTRAMAX -> ultramax(r, g, b)
                EmulationType.FILM35 -> film35(r, g, b)
                // Cinematic
                EmulationType.CINESTILL_800T -> cinestill800t(r, g, b)
                EmulationType.KODACHROME -> kodachrome(r, g, b)
                EmulationType.EKTACHROME -> ektachrome(r, g, b)
                EmulationType.CINEMATIC_TEAL_ORANGE -> tealOrange(r, g, b)
                EmulationType.NIGHTFADE -> nightfade(r, g, b)
                EmulationType.ROSEWOOD -> rosewood(r, g, b)
                EmulationType.AGFA_VISTA -> agfaVista(r, g, b)
                // Natural
                EmulationType.VELVIA -> velvia(r, g, b)
                // Stylistic
                EmulationType.TRI_X -> triX(r, g, b)
                EmulationType.HP5 -> hp5(r, g, b)
                EmulationType.ARIZONA -> arizona(r, g, b)
                EmulationType.METRO -> metro(r, g, b)
                EmulationType.EKTAR -> ektar(r, g, b)
            }

            pixels[i] = Color.argb(
                Color.alpha(px),
                (nr * 255).roundToInt().coerceIn(0, 255),
                (ng * 255).roundToInt().coerceIn(0, 255),
                (nb * 255).roundToInt().coerceIn(0, 255)
            )
        }
    }

    // ── Instagram filters ────────────────────────────────────────────

    private fun clarendon(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = ((r - 0.5f) * 1.3f + 0.5f + 0.04f).coerceIn(0f, 1f)
        val ng = ((g - 0.5f) * 1.3f + 0.5f).coerceIn(0f, 1f)
        val nb = ((b - 0.5f) * 1.2f + 0.5f + 0.06f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun juno(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.15f + 0.06f).coerceIn(0f, 1f)
        val ng = (g * 1.08f + 0.02f).coerceIn(0f, 1f)
        val nb = (b * 0.82f - 0.02f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun lark(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.85f + 0.08f).coerceIn(0f, 1f)
        val ng = (g * 1.05f + 0.04f).coerceIn(0f, 1f)
        val nb = (b * 1.1f + 0.06f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun valencia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.12f + 0.08f).coerceIn(0f, 1f)
        val ng = (g * 0.95f + 0.02f).coerceIn(0f, 1f)
        val nb = (b * 0.8f + 0.01f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun hudson(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.88f + 0.02f).coerceIn(0f, 1f)
        val ng = (g * 1.0f + 0.03f).coerceIn(0f, 1f)
        val nb = (b * 1.2f + 0.08f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun reyes(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.75f + 0.15f).coerceIn(0f, 1f)
        val ng = (g * 0.72f + 0.13f).coerceIn(0f, 1f)
        val nb = (b * 0.65f + 0.12f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    // ── Filmic emulations ────────────────────────────────────────────

    private fun portra(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.12f + 0.06f).coerceIn(0f, 1f)
        val ng = (g * 1.04f + 0.02f).coerceIn(0f, 1f)
        val nb = (b * 0.88f - 0.01f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun fuji400h(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.9f + 0.04f).coerceIn(0f, 1f)
        val ng = (g * 1.02f + 0.04f).coerceIn(0f, 1f)
        val nb = (b * 1.12f + 0.08f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun gold200(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.18f + 0.08f).coerceIn(0f, 1f)
        val ng = (g * 1.08f + 0.03f).coerceIn(0f, 1f)
        val nb = (b * 0.82f - 0.02f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun ultramax(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.2f + 0.05f).coerceIn(0f, 1f)
        val ng = (g * 1.1f + 0.02f).coerceIn(0f, 1f)
        val nb = (b * 0.85f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun film35(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.05f + 0.08f).coerceIn(0f, 1f)
        val ng = (g * 0.98f + 0.04f).coerceIn(0f, 1f)
        val nb = (b * 0.85f + 0.02f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    // ── Cinematic emulations ─────────────────────────────────────────

    private fun cinestill800t(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.95f + 0.06f).coerceIn(0f, 1f)
        val ng = (g * 0.9f + 0.01f).coerceIn(0f, 1f)
        val nb = (b * 1.25f + 0.1f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun kodachrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.18f + 0.08f).coerceIn(0f, 1f)
        val ng = (g * 1.06f + 0.02f).coerceIn(0f, 1f)
        val nb = (b * 0.8f - 0.02f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun ektachrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.88f + 0.02f).coerceIn(0f, 1f)
        val ng = (g * 1.04f + 0.03f).coerceIn(0f, 1f)
        val nb = (b * 1.2f + 0.08f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun tealOrange(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = r * 0.299f + g * 0.587f + b * 0.114f
        val nr: Float
        val ng: Float
        val nb: Float
        if (luma < 0.45f) {
            nr = (r * 0.85f + 0.02f).coerceIn(0f, 1f)
            ng = (g * 1.02f + 0.06f).coerceIn(0f, 1f)
            nb = (b * 1.15f + 0.08f).coerceIn(0f, 1f)
        } else {
            nr = (r * 1.2f + 0.08f).coerceIn(0f, 1f)
            ng = (g * 1.0f + 0.02f).coerceIn(0f, 1f)
            nb = (b * 0.8f - 0.02f).coerceIn(0f, 1f)
        }
        return Triple(nr, ng, nb)
    }

    private fun nightfade(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 0.7f + 0.08f).coerceIn(0f, 1f)
        val ng = (g * 0.75f + 0.06f).coerceIn(0f, 1f)
        val nb = (b * 0.95f + 0.1f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun rosewood(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.15f + 0.1f).coerceIn(0f, 1f)
        val ng = (g * 0.85f + 0.02f).coerceIn(0f, 1f)
        val nb = (b * 0.9f + 0.04f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    private fun agfaVista(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = (r * 1.22f + 0.06f).coerceIn(0f, 1f)
        val ng = (g * 0.95f + 0.01f).coerceIn(0f, 1f)
        val nb = (b * 0.88f).coerceIn(0f, 1f)
        return Triple(nr, ng, nb)
    }

    // ── Natural / Stylistic ──────────────────────────────────────────

    private fun velvia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.35f).coerceIn(0f, 1f),
            (g * 1.25f).coerceIn(0f, 1f),
            (b * 1.15f).coerceIn(0f, 1f)
        )
    }

    private fun triX(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = r * 0.299f + g * 0.587f + b * 0.114f
        val contrast = ((luma - 0.5f) * 1.6f + 0.5f).coerceIn(0f, 1f)
        return Triple(contrast, contrast, contrast)
    }

    private fun hp5(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val luma = r * 0.299f + g * 0.587f + b * 0.114f
        val contrast = ((luma - 0.5f) * 1.3f + 0.5f).coerceIn(0f, 1f)
        val warm = (contrast * 1.01f).coerceIn(0f, 1f)
        return Triple(warm, warm, contrast)
    }

    private fun arizona(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.2f + 0.1f).coerceIn(0f, 1f),
            (g * 1.02f + 0.03f).coerceIn(0f, 1f),
            (b * 0.78f - 0.02f).coerceIn(0f, 1f)
        )
    }

    private fun metro(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 0.78f + 0.02f).coerceIn(0f, 1f),
            (g * 0.88f + 0.04f).coerceIn(0f, 1f),
            (b * 1.15f + 0.08f).coerceIn(0f, 1f)
        )
    }

    private fun ektar(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.25f + 0.03f).coerceIn(0f, 1f),
            (g * 1.12f + 0.02f).coerceIn(0f, 1f),
            (b * 1.08f + 0.01f).coerceIn(0f, 1f)
        )
    }

    // ── Tone curves ──────────────────────────────────────────────────

    private fun applyTone(pixels: IntArray, toneType: ToneType) {
        if (toneType == ToneType.NEUTRAL) return

        for (i in pixels.indices) {
            val px = pixels[i]
            var r = Color.red(px) / 255f
            var g = Color.green(px) / 255f
            var b = Color.blue(px) / 255f

            when (toneType) {
                ToneType.NEUTRAL -> {}
                ToneType.CRUSH -> { r = crush(r); g = crush(g); b = crush(b) }
                ToneType.ULTRA -> { r = ultra(r); g = ultra(g); b = ultra(b) }
                ToneType.DYNAMIC -> { r = dynamic(r); g = dynamic(g); b = dynamic(b) }
                ToneType.FADED -> {
                    r = (r * 0.82f + 0.12f).coerceIn(0f, 1f)
                    g = (g * 0.82f + 0.12f).coerceIn(0f, 1f)
                    b = (b * 0.82f + 0.12f).coerceIn(0f, 1f)
                }
                ToneType.EXPIRED -> {
                    r = (r * 0.72f + 0.18f).coerceIn(0f, 1f)
                    g = (g * 0.68f + 0.2f).coerceIn(0f, 1f)
                    b = (b * 0.6f + 0.22f).coerceIn(0f, 1f)
                }
                ToneType.BRIGHT -> {
                    r = (r * 1.2f + 0.06f).coerceIn(0f, 1f)
                    g = (g * 1.2f + 0.06f).coerceIn(0f, 1f)
                    b = (b * 1.2f + 0.06f).coerceIn(0f, 1f)
                }
                ToneType.MOODY -> {
                    r = (r * 0.6f + 0.02f).coerceIn(0f, 1f)
                    g = (g * 0.6f + 0.03f).coerceIn(0f, 1f)
                    b = (b * 0.72f + 0.08f).coerceIn(0f, 1f)
                }
            }

            pixels[i] = Color.argb(
                Color.alpha(px),
                (r * 255).roundToInt().coerceIn(0, 255),
                (g * 255).roundToInt().coerceIn(0, 255),
                (b * 255).roundToInt().coerceIn(0, 255)
            )
        }
    }

    private fun crush(x: Float) = ((x * x * x * 0.4f + x * x * 0.6f) * 0.9f + 0.05f).coerceIn(0f, 1f)
    private fun ultra(x: Float) = if (x > 0.5f) (0.5f + (x - 0.5f) * 1.7f).coerceIn(0f, 1f) else (x * 0.35f).coerceIn(0f, 1f)
    private fun dynamic(x: Float) = if (x > 0.5f) (0.5f + (x - 0.5f) * 1.4f).coerceIn(0f, 1f) else (x * 0.65f).coerceIn(0f, 1f)

    // ── Fade ─────────────────────────────────────────────────────────

    private fun applyFade(pixels: IntArray, fade: Float) {
        val lift = (fade * 42f).roundToInt()
        for (i in pixels.indices) {
            val px = pixels[i]
            pixels[i] = Color.argb(
                Color.alpha(px),
                (Color.red(px) + lift).coerceIn(0, 255),
                (Color.green(px) + lift).coerceIn(0, 255),
                (Color.blue(px) + lift).coerceIn(0, 255)
            )
        }
    }

    // ── Hardware-accelerated adjustments ──────────────────────────────

    private fun applyContrastMatrix(bitmap: Bitmap, contrast: Float): Bitmap {
        if (contrast == 1f) return bitmap
        val t = -0.5f * contrast + 0.5f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyBrightnessMatrix(bitmap: Bitmap, brightness: Float): Bitmap {
        if (brightness == 0f) return bitmap
        val s = brightness * 255f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, s,
                0f, 1f, 0f, 0f, s,
                0f, 0f, 1f, 0f, s,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyTemperatureMatrix(bitmap: Bitmap, temp: Float, tint: Float): Bitmap {
        if (temp == 0f && tint == 0f) return bitmap
        val tr = temp * 0.08f
        val tb = -temp * 0.08f
        val tg = tint * 0.04f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                1f + tr, 0f, 0f, 0f, tr * 80f,
                0f, 1f + tg, 0f, 0f, tg * 60f,
                0f, 0f, 1f + tb, 0f, tb * 80f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // ── Vignette ─────────────────────────────────────────────────────

    private fun applyVignette(bitmap: Bitmap, intensity: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt(cx * cx + cy * cy)
        val maxSq = maxDist * maxDist
        val radSq = maxSq * 0.75f

        for (y in 0 until h) {
            val dy = y - cy
            val dySq = dy * dy
            for (x in 0 until w) {
                val dx = x - cx
                val distSq = dx * dx + dySq
                val vig = if (distSq < radSq) 1f else {
                    val t = (distSq - radSq) / (maxSq - radSq)
                    (1f - t * t * intensity).coerceIn(0f, 1f)
                }
                val i = y * w + x
                val px = pixels[i]
                pixels[i] = Color.argb(
                    Color.alpha(px),
                    (Color.red(px) * vig).roundToInt().coerceIn(0, 255),
                    (Color.green(px) * vig).roundToInt().coerceIn(0, 255),
                    (Color.blue(px) * vig).roundToInt().coerceIn(0, 255)
                )
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}
