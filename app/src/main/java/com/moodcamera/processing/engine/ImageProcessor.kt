package com.moodcamera.processing.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.moodcamera.domain.model.CameraSettings
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.QualityType
import com.moodcamera.domain.model.ToneType
import com.moodcamera.processing.grain.FilmGrain
import com.moodcamera.processing.halation.HalationEffect
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
        quality: QualityType
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

    // ── Color science helpers ──────────────────────────────────────

    private fun clamp(x: Float) = x.coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lum(r: Float, g: Float, b: Float) = r * 0.2126f + g * 0.7152f + b * 0.0722f

    private fun sCurve(x: Float, strength: Float = 1f): Float {
        val s = 0.5f + strength * 0.5f
        return if (x < 0.5f) {
            0.5f * (2f * x).pow(s)
        } else {
            1f - 0.5f * (2f * (1f - x)).pow(s)
        }
    }

    private fun liftBlacks(x: Float, amt: Float) = clamp(x * (1f - amt) + amt * 0.12f)

    private fun splitTone(
        r: Float, g: Float, b: Float,
        hi: Triple<Float, Float, Float>,
        sh: Triple<Float, Float, Float>
    ): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val sw = (1f - l).coerceIn(0f, 1f)
        val hw = l.coerceIn(0f, 1f)
        val total = sw + hw
        if (total < 0.001f) return Triple(r, g, b)
        return Triple(
            clamp(r + (sh.first * sw + hi.first * hw) / total * 0.15f),
            clamp(g + (sh.second * sw + hi.second * hw) / total * 0.15f),
            clamp(b + (sh.third * sw + hi.third * hw) / total * 0.15f)
        )
    }

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
                EmulationType.GINGHAM -> gingham(r, g, b)
                EmulationType.ADEN -> aden(r, g, b)
                EmulationType.LUDWIG -> ludwig(r, g, b)
                EmulationType.CREMA -> crema(r, g, b)
                // Filmic
                EmulationType.PORTRA -> portra(r, g, b)
                EmulationType.PORTRA_800 -> portra800(r, g, b)
                EmulationType.FUJI_400H -> fuji400h(r, g, b)
                EmulationType.FUJI_SUPERIA -> fujiSuperia(r, g, b)
                EmulationType.FUJI_PROVIA -> fujiProvia(r, g, b)
                EmulationType.FUJI_NATURA -> fujiNatura(r, g, b)
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
                EmulationType.EKTAR -> ektar(r, g, b)
                // Stylistic
                EmulationType.TRI_X -> triX(r, g, b)
                EmulationType.HP5 -> hp5(r, g, b)
                EmulationType.ARIZONA -> arizona(r, g, b)
                EmulationType.METRO -> metro(r, g, b)
            }

            pixels[i] = Color.argb(
                Color.alpha(px),
                (nr * 255).roundToInt().coerceIn(0, 255),
                (ng * 255).roundToInt().coerceIn(0, 255),
                (nb * 255).roundToInt().coerceIn(0, 255)
            )
        }
    }

    // ── Instagram filters (cinematic) ──────────────────────────────

    private fun clarendon(r: Float, g: Float, b: Float) = run {
        val nr = sCurve(r, 0.6f)
        val ng = sCurve(g, 0.5f)
        val nb = sCurve(b, 0.55f)
        splitTone(clamp(nr + 0.03f), ng, clamp(nb + 0.06f),
            Triple(0.1f, 0.5f, 1f), Triple(-0.2f, 0f, 0.4f))
    }

    private fun juno(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.1f + 0.04f)
        val ng = clamp(g * 1.04f + 0.02f)
        val nb = clamp(b * 0.82f)
        splitTone(sCurve(nr, 0.3f), sCurve(ng, 0.2f), sCurve(nb, 0.3f),
            Triple(0.3f, 0.2f, -0.1f), Triple(-0.1f, -0.05f, 0.15f))
    }

    private fun lark(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.88f + 0.08f)
        val ng = clamp(g * 1.06f + 0.04f)
        val nb = clamp(b * 1.12f + 0.06f)
        val l = lum(nr, ng, nb)
        Triple(clamp(lerp(nr, nr * 1.05f, l)), clamp(ng * 1.02f), clamp(nb * 1.04f))
    }

    private fun valencia(r: Float, g: Float, b: Float) = run {
        val nr = liftBlacks(clamp(r * 1.08f + 0.1f), 0.15f)
        val ng = liftBlacks(clamp(g * 0.92f + 0.03f), 0.12f)
        val nb = liftBlacks(clamp(b * 0.75f + 0.01f), 0.1f)
        Triple(sCurve(nr, 0.35f), ng, nb)
    }

    private fun hudson(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.86f + 0.02f)
        val ng = clamp(g * 0.98f + 0.04f)
        val nb = clamp(b * 1.22f + 0.08f)
        val (sr, sg, sb) = splitTone(nr, ng, nb, Triple(0f, 0.3f, 0.8f), Triple(-0.1f, 0f, 0.3f))
        Triple(sCurve(liftBlacks(sr, 0.12f), 0.3f), sCurve(liftBlacks(sg, 0.1f), 0.2f),
            sCurve(liftBlacks(sb, 0.08f), 0.25f))
    }

    private fun reyes(r: Float, g: Float, b: Float) = run {
        val nr = liftBlacks(clamp(r * 0.78f + 0.16f), 0.25f)
        val ng = liftBlacks(clamp(g * 0.75f + 0.14f), 0.22f)
        val nb = liftBlacks(clamp(b * 0.68f + 0.13f), 0.2f)
        Triple(clamp(nr * 0.98f), clamp(ng * 0.97f), clamp(nb * 0.96f))
    }

    private fun gingham(r: Float, g: Float, b: Float) = run {
        val nr = liftBlacks(clamp(r * 0.92f + 0.06f), 0.18f)
        val ng = liftBlacks(clamp(g * 0.95f + 0.04f), 0.16f)
        val nb = liftBlacks(clamp(b * 0.98f + 0.05f), 0.14f)
        Triple(clamp(sCurve(nr, 0.2f)), ng, nb)
    }

    private fun aden(r: Float, g: Float, b: Float) = run {
        val nr = liftBlacks(clamp(r * 0.9f + 0.1f), 0.2f)
        val ng = liftBlacks(clamp(g * 0.88f + 0.08f), 0.18f)
        val nb = liftBlacks(clamp(b * 0.82f + 0.06f), 0.15f)
        Triple(clamp(nr * 1.02f), clamp(ng * 1.01f), clamp(nb * 0.98f))
    }

    private fun ludwig(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.06f + 0.06f)
        val ng = clamp(g * 0.98f + 0.02f)
        val nb = clamp(b * 0.88f)
        splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f),
            Triple(0.25f, 0.15f, -0.05f), Triple(-0.05f, 0f, 0.1f))
    }

    private fun crema(r: Float, g: Float, b: Float) = run {
        val nr = liftBlacks(clamp(r * 0.95f + 0.06f), 0.12f)
        val ng = liftBlacks(clamp(g * 0.93f + 0.04f), 0.1f)
        val nb = liftBlacks(clamp(b * 0.88f + 0.02f), 0.08f)
        Triple(sCurve(nr, 0.25f), sCurve(ng, 0.2f), sCurve(nb, 0.2f))
    }

    // ── Filmic emulations (cinematic) ──────────────────────────────

    private fun portra(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.12f + 0.05f)
        val ng = clamp(g * 1.05f + 0.02f)
        val nb = clamp(b * 0.88f - 0.01f)
        splitTone(sCurve(nr, 0.35f), sCurve(ng, 0.25f), sCurve(nb, 0.3f),
            Triple(0.2f, 0.1f, -0.05f), Triple(-0.05f, 0f, 0.1f))
    }

    private fun portra800(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.18f + 0.06f)
        val ng = clamp(g * 1.08f + 0.03f)
        val nb = clamp(b * 0.85f - 0.02f)
        val nr2 = sCurve(nr, 0.4f)
        val ng2 = sCurve(ng, 0.3f)
        val nb2 = sCurve(nb, 0.35f)
        splitTone(nr2, ng2, nb2, Triple(0.25f, 0.15f, -0.05f), Triple(-0.08f, -0.02f, 0.12f))
    }

    private fun fuji400h(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.88f + 0.06f)
        val ng = clamp(g * 1.04f + 0.05f)
        val nb = clamp(b * 1.15f + 0.08f)
        splitTone(sCurve(nr, 0.2f), sCurve(ng, 0.15f), sCurve(nb, 0.2f),
            Triple(0f, 0.3f, 0.5f), Triple(-0.05f, 0.05f, 0.15f))
    }

    private fun fujiSuperia(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.92f + 0.04f)
        val ng = clamp(g * 1.12f + 0.06f)
        val nb = clamp(b * 0.95f + 0.02f)
        splitTone(sCurve(nr, 0.35f), sCurve(ng, 0.3f), sCurve(nb, 0.3f),
            Triple(0.1f, 0.2f, -0.1f), Triple(-0.05f, 0.1f, 0.05f))
    }

    private fun fujiProvia(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.05f + 0.02f)
        val ng = clamp(g * 1.08f + 0.03f)
        val nb = clamp(b * 1.1f + 0.04f)
        sCurve(nr, 0.45f).let { Triple(it, sCurve(ng, 0.4f), sCurve(nb, 0.42f)) }
    }

    private fun fujiNatura(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.0f + 0.03f)
        val ng = clamp(g * 1.04f + 0.04f)
        val nb = clamp(b * 1.02f + 0.03f)
        splitTone(sCurve(nr, 0.2f), sCurve(ng, 0.15f), sCurve(nb, 0.18f),
            Triple(0.05f, 0.1f, 0.05f), Triple(0f, 0.02f, 0.08f))
    }

    private fun gold200(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.2f + 0.08f)
        val ng = clamp(g * 1.1f + 0.04f)
        val nb = clamp(b * 0.78f - 0.02f)
        splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f),
            Triple(0.3f, 0.2f, -0.1f), Triple(0.1f, 0.05f, -0.1f))
    }

    private fun ultramax(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.22f + 0.05f)
        val ng = clamp(g * 1.12f + 0.03f)
        val nb = clamp(b * 0.82f)
        sCurve(nr, 0.45f).let { Triple(it, sCurve(ng, 0.4f), sCurve(nb, 0.4f)) }
    }

    private fun film35(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.06f + 0.08f)
        val ng = clamp(g * 0.96f + 0.04f)
        val nb = clamp(b * 0.85f + 0.02f)
        val nr2 = liftBlacks(sCurve(nr, 0.3f), 0.1f)
        val ng2 = liftBlacks(sCurve(ng, 0.25f), 0.08f)
        val nb2 = liftBlacks(sCurve(nb, 0.3f), 0.06f)
        Triple(nr2, ng2, nb2)
    }

    // ── Cinematic emulations ───────────────────────────────────────

    private fun cinestill800t(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.92f + 0.06f)
        val ng = clamp(g * 0.88f + 0.01f)
        val nb = clamp(b * 1.3f + 0.12f)
        val nr2 = liftBlacks(sCurve(nr, 0.3f), 0.12f)
        val ng2 = liftBlacks(sCurve(ng, 0.25f), 0.1f)
        val nb2 = liftBlacks(sCurve(nb, 0.35f), 0.08f)
        Triple(nr2, ng2, nb2)
    }

    private fun kodachrome(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.22f + 0.08f)
        val ng = clamp(g * 1.08f + 0.03f)
        val nb = clamp(b * 0.75f - 0.03f)
        splitTone(sCurve(nr, 0.55f), sCurve(ng, 0.45f), sCurve(nb, 0.5f),
            Triple(0.3f, 0.15f, -0.1f), Triple(-0.05f, 0.05f, 0.15f))
    }

    private fun ektachrome(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.85f + 0.02f)
        val ng = clamp(g * 1.06f + 0.04f)
        val nb = clamp(b * 1.25f + 0.1f)
        splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.35f), sCurve(nb, 0.45f),
            Triple(-0.1f, 0.2f, 0.8f), Triple(0.05f, 0f, 0.2f))
    }

    private fun tealOrange(r: Float, g: Float, b: Float) = run {
        val l = lum(r, g, b)
        val nr: Float
        val ng: Float
        val nb: Float
        if (l < 0.45f) {
            nr = clamp(r * 0.82f + 0.02f)
            ng = clamp(g * 1.04f + 0.08f)
            nb = clamp(b * 1.2f + 0.1f)
        } else {
            nr = clamp(r * 1.25f + 0.1f)
            ng = clamp(g * 1.0f + 0.03f)
            nb = clamp(b * 0.75f - 0.03f)
        }
        sCurve(nr, 0.45f).let { Triple(it, sCurve(ng, 0.4f), sCurve(nb, 0.42f)) }
    }

    private fun nightfade(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.65f + 0.08f)
        val ng = clamp(g * 0.7f + 0.06f)
        val nb = clamp(b * 0.95f + 0.12f)
        splitTone(liftBlacks(sCurve(nr, 0.25f), 0.15f), liftBlacks(sCurve(ng, 0.2f), 0.13f),
            liftBlacks(sCurve(nb, 0.3f), 0.1f),
            Triple(-0.2f, -0.1f, 0.6f), Triple(0f, 0f, 0.3f))
    }

    private fun rosewood(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.18f + 0.12f)
        val ng = clamp(g * 0.82f + 0.02f)
        val nb = clamp(b * 0.88f + 0.04f)
        splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f),
            Triple(0.4f, -0.05f, 0.1f), Triple(0.15f, -0.05f, 0.05f))
    }

    private fun agfaVista(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.25f + 0.08f)
        val ng = clamp(g * 0.92f + 0.01f)
        val nb = clamp(b * 0.85f)
        splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.35f), sCurve(nb, 0.38f),
            Triple(0.2f, -0.1f, -0.1f), Triple(0.05f, 0f, 0f))
    }

    // ── Natural / Stylistic ────────────────────────────────────────

    private fun velvia(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.38f)
        val ng = clamp(g * 1.28f)
        val nb = clamp(b * 1.18f)
        sCurve(nr, 0.55f).let { Triple(it, sCurve(ng, 0.5f), sCurve(nb, 0.45f)) }
    }

    private fun ektar(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.3f + 0.04f)
        val ng = clamp(g * 1.15f + 0.03f)
        val nb = clamp(b * 1.1f + 0.02f)
        sCurve(nr, 0.5f).let { Triple(it, sCurve(ng, 0.45f), sCurve(nb, 0.4f)) }
    }

    private fun triX(r: Float, g: Float, b: Float) = run {
        val l = lum(r, g, b)
        val contrast = clamp((l - 0.5f) * 1.8f + 0.5f)
        val lifted = liftBlacks(contrast, 0.06f)
        Triple(sCurve(lifted, 0.5f), sCurve(lifted, 0.5f), sCurve(lifted, 0.5f))
    }

    private fun hp5(r: Float, g: Float, b: Float) = run {
        val l = lum(r, g, b)
        val contrast = clamp((l - 0.5f) * 1.35f + 0.5f)
        val lifted = liftBlacks(contrast, 0.08f)
        val warm = clamp(lifted * 1.01f)
        Triple(sCurve(warm, 0.35f), sCurve(warm, 0.35f), sCurve(lifted, 0.35f))
    }

    private fun arizona(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 1.22f + 0.12f)
        val ng = clamp(g * 1.02f + 0.04f)
        val nb = clamp(b * 0.72f - 0.03f)
        splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f),
            Triple(0.3f, 0.15f, -0.15f), Triple(0.1f, 0.05f, -0.1f))
    }

    private fun metro(r: Float, g: Float, b: Float) = run {
        val nr = clamp(r * 0.75f + 0.02f)
        val ng = clamp(g * 0.85f + 0.04f)
        val nb = clamp(b * 1.18f + 0.1f)
        splitTone(sCurve(nr, 0.3f), sCurve(ng, 0.25f), sCurve(nb, 0.35f),
            Triple(-0.1f, 0.1f, 0.4f), Triple(0f, 0f, 0.15f))
    }

    // ── Tone curves ────────────────────────────────────────────────

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

    // ── Hardware-accelerated adjustments ───────────────────────────

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
