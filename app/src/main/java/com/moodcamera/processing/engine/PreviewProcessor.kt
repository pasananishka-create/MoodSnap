package com.moodcamera.processing.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.moodcamera.domain.model.CameraSettings
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.processing.enhance.CinematicLut
import com.moodcamera.processing.enhance.CinematicLutEngine
import kotlin.math.roundToInt

object PreviewProcessor {

    fun processPreview(bitmap: Bitmap, settings: CameraSettings): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        val fade = settings.fade
        val contrast = settings.contrast
        val brightness = settings.brightness
        val temp = settings.temperature
        val tint = settings.tint

        for (i in pixels.indices) {
            val px = pixels[i]
            var r = Color.red(px) / 255f
            var g = Color.green(px) / 255f
            var b = Color.blue(px) / 255f

            val (nr, ng, nb) = when (settings.emulationType) {
                EmulationType.CLARENDON -> quickClarendon(r, g, b)
                EmulationType.JUNO -> quickJuno(r, g, b)
                EmulationType.LARK -> quickLark(r, g, b)
                EmulationType.VALENCIA -> quickValencia(r, g, b)
                EmulationType.HUDSON -> quickHudson(r, g, b)
                EmulationType.REYES -> quickReyes(r, g, b)
                EmulationType.GINGHAM -> quickGingham(r, g, b)
                EmulationType.ADEN -> quickAden(r, g, b)
                EmulationType.LUDWIG -> quickLudwig(r, g, b)
                EmulationType.CREMA -> quickCrema(r, g, b)
                EmulationType.PORTRA -> quickPortra(r, g, b)
                EmulationType.PORTRA_800 -> quickPortra800(r, g, b)
                EmulationType.FUJI_400H -> quickFuji400h(r, g, b)
                EmulationType.FUJI_SUPERIA -> quickFujiSuperia(r, g, b)
                EmulationType.FUJI_PROVIA -> quickFujiProvia(r, g, b)
                EmulationType.FUJI_NATURA -> quickFujiNatura(r, g, b)
                EmulationType.GOLD_200 -> quickGold200(r, g, b)
                EmulationType.ULTRAMAX -> quickUltramax(r, g, b)
                EmulationType.FILM35 -> quickFilm35(r, g, b)
                EmulationType.CINESTILL_800T -> quickCinestill(r, g, b)
                EmulationType.KODACHROME -> quickKodachrome(r, g, b)
                EmulationType.EKTACHROME -> quickEktachrome(r, g, b)
                EmulationType.CINEMATIC_TEAL_ORANGE -> quickTealOrange(r, g, b)
                EmulationType.NIGHTFADE -> quickNightfade(r, g, b)
                EmulationType.ROSEWOOD -> quickRosewood(r, g, b)
                EmulationType.AGFA_VISTA -> quickAgfaVista(r, g, b)
                EmulationType.BLEACH_BYPASS -> quickBleachBypass(r, g, b)
                EmulationType.TECHNICOLOR -> quickTechnicolor(r, g, b)
                EmulationType.NOIR -> quickNoir(r, g, b)
                EmulationType.NEON_NOIR -> quickNeonNoir(r, g, b)
                EmulationType.VINTAGE_CHROME -> quickVintageChrome(r, g, b)
                EmulationType.ANALOG_WARM -> quickAnalogWarm(r, g, b)
                EmulationType.DAY_FOR_NIGHT -> quickDayForNight(r, g, b)
                EmulationType.SILVER_RETENTION -> quickSilverRetention(r, g, b)
                EmulationType.VELVIA -> quickVelvia(r, g, b)
                EmulationType.EKTAR -> quickEktar(r, g, b)
                EmulationType.TRI_X -> quickTriX(r, g, b)
                EmulationType.HP5 -> quickHP5(r, g, b)
                EmulationType.ARIZONA -> quickArizona(r, g, b)
                EmulationType.METRO -> quickMetro(r, g, b)
            }

            var finalR = nr
            var finalG = ng
            var finalB = nb

            settings.cinematicLut?.let { lut ->
                val (lr, lg, lb) = CinematicLutEngine.applyLut(finalR, finalG, finalB, lut)
                val intensity = settings.lutIntensity
                finalR = finalR + (lr - finalR) * intensity
                finalG = finalG + (lg - finalG) * intensity
                finalB = finalB + (lb - finalB) * intensity
            }

            if (settings.toneType != com.moodcamera.domain.model.ToneType.NEUTRAL) {
                val (tr, tg, tb) = applyQuickTone(finalR, finalG, finalB, settings.toneType)
                finalR = tr; finalG = tg; finalB = tb
            }

            if (contrast != 1f) {
                val t = -0.5f * contrast + 0.5f
                finalR = (finalR * contrast + t).coerceIn(0f, 1f)
                finalG = (finalG * contrast + t).coerceIn(0f, 1f)
                finalB = (finalB * contrast + t).coerceIn(0f, 1f)
            }
            if (brightness != 0f) {
                finalR = (finalR + brightness).coerceIn(0f, 1f)
                finalG = (finalG + brightness).coerceIn(0f, 1f)
                finalB = (finalB + brightness).coerceIn(0f, 1f)
            }
            if (temp != 0f) {
                finalR = (finalR + temp * 0.05f).coerceIn(0f, 1f)
                finalB = (finalB - temp * 0.05f).coerceIn(0f, 1f)
            }
            if (tint != 0f) {
                finalG = (finalG + tint * 0.03f).coerceIn(0f, 1f)
            }
            if (fade > 0f) {
                finalR = (finalR + fade * 0.17f).coerceIn(0f, 1f)
                finalG = (finalG + fade * 0.17f).coerceIn(0f, 1f)
                finalB = (finalB + fade * 0.17f).coerceIn(0f, 1f)
            }

            pixels[i] = Color.argb(
                Color.alpha(px),
                (finalR * 255).roundToInt().coerceIn(0, 255),
                (finalG * 255).roundToInt().coerceIn(0, 255),
                (finalB * 255).roundToInt().coerceIn(0, 255)
            )
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun clamp(x: Float) = x.coerceIn(0f, 1f)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun lum(r: Float, g: Float, b: Float) = r * 0.2126f + g * 0.7152f + b * 0.0722f
    private fun fastPow(x: Float, p: Float) = Math.pow(x.toDouble(), p.toDouble()).toFloat()

    private fun sCurve(x: Float, s: Float = 1f): Float {
        val p = 0.5f + s * 0.5f
        return if (x < 0.5f) 0.5f * fastPow(2f * x, p) else 1f - 0.5f * fastPow(2f * (1f - x), p)
    }

    private fun splitTone(r: Float, g: Float, b: Float, hi: Triple<Float, Float, Float>, sh: Triple<Float, Float, Float>): Triple<Float, Float, Float> {
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

    private fun liftBlacks(x: Float, amt: Float) = clamp(x * (1f - amt) + amt * 0.12f)

    private fun applyQuickTone(r: Float, g: Float, b: Float, tone: com.moodcamera.domain.model.ToneType): Triple<Float, Float, Float> {
        return when (tone) {
            com.moodcamera.domain.model.ToneType.NEUTRAL -> Triple(r, g, b)
            com.moodcamera.domain.model.ToneType.CRUSH -> Triple(clamp(r * r * 0.4f + r * 0.6f), clamp(g * g * 0.4f + g * 0.6f), clamp(b * b * 0.4f + b * 0.6f))
            com.moodcamera.domain.model.ToneType.ULTRA -> Triple(
                clamp(if (r > 0.5f) 0.5f + (r - 0.5f) * 1.7f else r * 0.35f),
                clamp(if (g > 0.5f) 0.5f + (g - 0.5f) * 1.7f else g * 0.35f),
                clamp(if (b > 0.5f) 0.5f + (b - 0.5f) * 1.7f else b * 0.35f)
            )
            com.moodcamera.domain.model.ToneType.DYNAMIC -> Triple(
                clamp(if (r > 0.5f) 0.5f + (r - 0.5f) * 1.4f else r * 0.65f),
                clamp(if (g > 0.5f) 0.5f + (g - 0.5f) * 1.4f else g * 0.65f),
                clamp(if (b > 0.5f) 0.5f + (b - 0.5f) * 1.4f else b * 0.65f)
            )
            com.moodcamera.domain.model.ToneType.FADED -> Triple(clamp(r * 0.82f + 0.12f), clamp(g * 0.82f + 0.12f), clamp(b * 0.82f + 0.12f))
            com.moodcamera.domain.model.ToneType.EXPIRED -> Triple(clamp(r * 0.72f + 0.18f), clamp(g * 0.68f + 0.2f), clamp(b * 0.6f + 0.22f))
            com.moodcamera.domain.model.ToneType.BRIGHT -> Triple(clamp(r * 1.2f + 0.06f), clamp(g * 1.2f + 0.06f), clamp(b * 1.2f + 0.06f))
            com.moodcamera.domain.model.ToneType.MOODY -> Triple(clamp(r * 0.6f + 0.02f), clamp(g * 0.6f + 0.03f), clamp(b * 0.72f + 0.08f))
        }
    }

    // ── Instagram filters ──────────────────────────────────────

    private fun quickClarendon(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(r, 0.6f); val ng = sCurve(g, 0.5f); val nb = sCurve(b, 0.55f)
        return splitTone(clamp(nr + 0.03f), ng, clamp(nb + 0.06f), Triple(0.1f, 0.5f, 1f), Triple(-0.2f, 0f, 0.4f))
    }

    private fun quickJuno(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.1f + 0.04f); val ng = clamp(g * 1.04f + 0.02f); val nb = clamp(b * 0.82f)
        return splitTone(sCurve(nr, 0.3f), sCurve(ng, 0.2f), sCurve(nb, 0.3f), Triple(0.3f, 0.2f, -0.1f), Triple(-0.1f, -0.05f, 0.15f))
    }

    private fun quickLark(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.88f + 0.08f); val ng = clamp(g * 1.06f + 0.04f); val nb = clamp(b * 1.12f + 0.06f)
        val l = lum(nr, ng, nb)
        return Triple(clamp(lerp(nr, nr * 1.05f, l)), clamp(ng * 1.02f), clamp(nb * 1.04f))
    }

    private fun quickValencia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(clamp(r * 1.08f + 0.1f), 0.15f)
        val ng = liftBlacks(clamp(g * 0.92f + 0.03f), 0.12f)
        val nb = liftBlacks(clamp(b * 0.75f + 0.01f), 0.1f)
        return Triple(sCurve(nr, 0.35f), ng, nb)
    }

    private fun quickHudson(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.86f + 0.02f); val ng = clamp(g * 0.98f + 0.04f); val nb = clamp(b * 1.22f + 0.08f)
        val (sr, sg, sb) = splitTone(nr, ng, nb, Triple(0f, 0.3f, 0.8f), Triple(-0.1f, 0f, 0.3f))
        return Triple(sCurve(liftBlacks(sr, 0.12f), 0.3f), sCurve(liftBlacks(sg, 0.1f), 0.2f), sCurve(liftBlacks(sb, 0.08f), 0.25f))
    }

    private fun quickReyes(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(clamp(r * 0.78f + 0.16f), 0.25f)
        val ng = liftBlacks(clamp(g * 0.75f + 0.14f), 0.22f)
        val nb = liftBlacks(clamp(b * 0.68f + 0.13f), 0.2f)
        return Triple(clamp(nr * 0.98f), clamp(ng * 0.97f), clamp(nb * 0.96f))
    }

    private fun quickGingham(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(clamp(r * 0.92f + 0.06f), 0.18f)
        val ng = liftBlacks(clamp(g * 0.95f + 0.04f), 0.16f)
        val nb = liftBlacks(clamp(b * 0.98f + 0.05f), 0.14f)
        return Triple(clamp(sCurve(nr, 0.2f)), ng, nb)
    }

    private fun quickAden(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(clamp(r * 0.9f + 0.1f), 0.2f)
        val ng = liftBlacks(clamp(g * 0.88f + 0.08f), 0.18f)
        val nb = liftBlacks(clamp(b * 0.82f + 0.06f), 0.15f)
        return Triple(clamp(nr * 1.02f), clamp(ng * 1.01f), clamp(nb * 0.98f))
    }

    private fun quickLudwig(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.06f + 0.06f); val ng = clamp(g * 0.98f + 0.02f); val nb = clamp(b * 0.88f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f), Triple(0.25f, 0.15f, -0.05f), Triple(-0.05f, 0f, 0.1f))
    }

    private fun quickCrema(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(clamp(r * 0.95f + 0.06f), 0.12f)
        val ng = liftBlacks(clamp(g * 0.93f + 0.04f), 0.1f)
        val nb = liftBlacks(clamp(b * 0.88f + 0.02f), 0.08f)
        return Triple(sCurve(nr, 0.25f), sCurve(ng, 0.2f), sCurve(nb, 0.2f))
    }

    // ── Filmic filters ──────────────────────────────────────

    private fun quickPortra(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.12f + 0.05f); val ng = clamp(g * 1.05f + 0.02f); val nb = clamp(b * 0.88f - 0.01f)
        return splitTone(sCurve(nr, 0.35f), sCurve(ng, 0.25f), sCurve(nb, 0.3f), Triple(0.2f, 0.1f, -0.05f), Triple(-0.05f, 0f, 0.1f))
    }

    private fun quickPortra800(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.18f + 0.06f); val ng = clamp(g * 1.08f + 0.03f); val nb = clamp(b * 0.85f - 0.02f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f), Triple(0.25f, 0.15f, -0.05f), Triple(-0.08f, -0.02f, 0.12f))
    }

    private fun quickFuji400h(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.88f + 0.06f); val ng = clamp(g * 1.04f + 0.05f); val nb = clamp(b * 1.15f + 0.08f)
        return splitTone(sCurve(nr, 0.2f), sCurve(ng, 0.15f), sCurve(nb, 0.2f), Triple(0f, 0.3f, 0.5f), Triple(-0.05f, 0.05f, 0.15f))
    }

    private fun quickFujiSuperia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.92f + 0.04f); val ng = clamp(g * 1.12f + 0.06f); val nb = clamp(b * 0.95f + 0.02f)
        return splitTone(sCurve(nr, 0.35f), sCurve(ng, 0.3f), sCurve(nb, 0.3f), Triple(0.1f, 0.2f, -0.1f), Triple(-0.05f, 0.1f, 0.05f))
    }

    private fun quickFujiProvia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.05f + 0.02f); val ng = clamp(g * 1.08f + 0.03f); val nb = clamp(b * 1.1f + 0.04f)
        return Triple(sCurve(nr, 0.45f), sCurve(ng, 0.4f), sCurve(nb, 0.42f))
    }

    private fun quickFujiNatura(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.0f + 0.03f); val ng = clamp(g * 1.04f + 0.04f); val nb = clamp(b * 1.02f + 0.03f)
        return splitTone(sCurve(nr, 0.2f), sCurve(ng, 0.15f), sCurve(nb, 0.18f), Triple(0.05f, 0.1f, 0.05f), Triple(0f, 0.02f, 0.08f))
    }

    private fun quickGold200(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.2f + 0.08f); val ng = clamp(g * 1.1f + 0.04f); val nb = clamp(b * 0.78f - 0.02f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f), Triple(0.3f, 0.2f, -0.1f), Triple(0.1f, 0.05f, -0.1f))
    }

    private fun quickUltramax(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.22f + 0.05f); val ng = clamp(g * 1.12f + 0.03f); val nb = clamp(b * 0.82f)
        return Triple(sCurve(nr, 0.45f), sCurve(ng, 0.4f), sCurve(nb, 0.4f))
    }

    private fun quickFilm35(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(sCurve(clamp(r * 1.06f + 0.08f), 0.3f), 0.1f)
        val ng = liftBlacks(sCurve(clamp(g * 0.96f + 0.04f), 0.25f), 0.08f)
        val nb = liftBlacks(sCurve(clamp(b * 0.85f + 0.02f), 0.3f), 0.06f)
        return Triple(nr, ng, nb)
    }

    // ── Cinematic filters ──────────────────────────────────

    private fun quickCinestill(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(sCurve(clamp(r * 0.92f + 0.06f), 0.3f), 0.12f)
        val ng = liftBlacks(sCurve(clamp(g * 0.88f + 0.01f), 0.25f), 0.1f)
        val nb = liftBlacks(sCurve(clamp(b * 1.3f + 0.12f), 0.35f), 0.08f)
        return Triple(nr, ng, nb)
    }

    private fun quickKodachrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.22f + 0.08f); val ng = clamp(g * 1.08f + 0.03f); val nb = clamp(b * 0.75f - 0.03f)
        return splitTone(sCurve(nr, 0.55f), sCurve(ng, 0.45f), sCurve(nb, 0.5f), Triple(0.3f, 0.15f, -0.1f), Triple(-0.05f, 0.05f, 0.15f))
    }

    private fun quickEktachrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.85f + 0.02f); val ng = clamp(g * 1.06f + 0.04f); val nb = clamp(b * 1.25f + 0.1f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.35f), sCurve(nb, 0.45f), Triple(-0.1f, 0.2f, 0.8f), Triple(0.05f, 0f, 0.2f))
    }

    private fun quickTealOrange(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        return if (l < 0.45f) {
            Triple(sCurve(clamp(r * 0.82f + 0.02f), 0.45f), sCurve(clamp(g * 1.04f + 0.08f), 0.4f), sCurve(clamp(b * 1.2f + 0.1f), 0.42f))
        } else {
            Triple(sCurve(clamp(r * 1.25f + 0.1f), 0.45f), sCurve(clamp(g * 1.0f + 0.03f), 0.4f), sCurve(clamp(b * 0.75f - 0.03f), 0.42f))
        }
    }

    private fun quickNightfade(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(sCurve(clamp(r * 0.65f + 0.08f), 0.25f), 0.15f)
        val ng = liftBlacks(sCurve(clamp(g * 0.7f + 0.06f), 0.2f), 0.13f)
        val nb = liftBlacks(sCurve(clamp(b * 0.95f + 0.12f), 0.3f), 0.1f)
        return splitTone(nr, ng, nb, Triple(-0.2f, -0.1f, 0.6f), Triple(0f, 0f, 0.3f))
    }

    private fun quickRosewood(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.18f + 0.12f); val ng = clamp(g * 0.82f + 0.02f); val nb = clamp(b * 0.88f + 0.04f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f), Triple(0.4f, -0.05f, 0.1f), Triple(0.15f, -0.05f, 0.05f))
    }

    private fun quickAgfaVista(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.25f + 0.08f); val ng = clamp(g * 0.92f + 0.01f); val nb = clamp(b * 0.85f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.35f), sCurve(nb, 0.38f), Triple(0.2f, -0.1f, -0.1f), Triple(0.05f, 0f, 0f))
    }

    private fun quickBleachBypass(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val nr = clamp(lerp(r, l, 0.6f)); val ng = clamp(lerp(g, l, 0.6f)); val nb = clamp(lerp(b, l, 0.6f))
        val cr = sCurve(clamp((nr - 0.5f) * 1.6f + 0.5f), 0.5f)
        val cg = sCurve(clamp((ng - 0.5f) * 1.6f + 0.5f), 0.5f)
        val cb = sCurve(clamp((nb - 0.5f) * 1.6f + 0.5f), 0.5f)
        return Triple(clamp(cr * 0.95f + 0.02f), clamp(cg * 0.97f), clamp(cb * 0.93f + 0.01f))
    }

    private fun quickTechnicolor(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.4f + 0.05f); val ng = clamp(g * 0.85f + 0.02f); val nb = clamp(b * 0.85f + 0.08f)
        return splitTone(sCurve(nr, 0.5f), sCurve(ng, 0.4f), sCurve(nb, 0.45f), Triple(0.35f, -0.1f, 0f), Triple(0f, 0.1f, 0.2f))
    }

    private fun quickNoir(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val contrast = clamp((l - 0.5f) * 2.2f + 0.5f)
        val crushed = sCurve(contrast, 0.7f)
        return Triple(crushed, crushed * 0.99f, crushed * 0.98f)
    }

    private fun quickNeonNoir(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val nr: Float; val ng: Float; val nb: Float
        if (l < 0.35f) {
            nr = clamp(r * 0.7f + 0.05f); ng = clamp(g * 0.6f + 0.02f); nb = clamp(b * 1.3f + 0.15f)
        } else {
            nr = clamp(r * 1.3f + 0.08f); ng = clamp(g * 0.8f - 0.02f); nb = clamp(b * 0.75f + 0.1f)
        }
        return splitTone(sCurve(nr, 0.5f), sCurve(ng, 0.4f), sCurve(nb, 0.45f), Triple(0.2f, -0.15f, 0.3f), Triple(-0.1f, 0.05f, 0.25f))
    }

    private fun quickVintageChrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(sCurve(clamp(r * 1.05f + 0.1f), 0.3f), 0.12f)
        val ng = liftBlacks(sCurve(clamp(g * 0.9f + 0.04f), 0.25f), 0.1f)
        val nb = liftBlacks(sCurve(clamp(b * 0.78f + 0.02f), 0.28f), 0.08f)
        return Triple(clamp(nr * 0.98f), clamp(ng * 0.97f), clamp(nb * 0.96f))
    }

    private fun quickAnalogWarm(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = liftBlacks(sCurve(clamp(r * 1.15f + 0.12f), 0.35f), 0.1f)
        val ng = liftBlacks(sCurve(clamp(g * 1.0f + 0.06f), 0.28f), 0.08f)
        val nb = liftBlacks(sCurve(clamp(b * 0.72f - 0.02f), 0.32f), 0.06f)
        return splitTone(nr, ng, nb, Triple(0.2f, 0.1f, -0.1f), Triple(0.08f, 0.04f, -0.05f))
    }

    private fun quickDayForNight(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 0.4f + 0.03f), 0.3f)
        val ng = sCurve(clamp(g * 0.45f + 0.04f), 0.25f)
        val nb = sCurve(clamp(b * 0.75f + 0.12f), 0.4f)
        return splitTone(nr, ng, nb, Triple(-0.1f, -0.05f, 0.3f), Triple(-0.05f, 0f, 0.15f))
    }

    private fun quickSilverRetention(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val nr = clamp(lerp(r, l, 0.75f)); val ng = clamp(lerp(g, l, 0.75f)); val nb = clamp(lerp(b, l, 0.75f))
        val contrast = clamp((l - 0.5f) * 1.9f + 0.5f)
        val cr = sCurve(clamp(lerp(nr, contrast, 0.4f)), 0.6f)
        val cg = sCurve(clamp(lerp(ng, contrast, 0.4f)), 0.6f)
        val cb = sCurve(clamp(lerp(nb, contrast, 0.4f)), 0.58f)
        return Triple(clamp(cr + 0.01f), clamp(cg), clamp(cb - 0.01f))
    }

    // ── Natural / Stylistic ────────────────────────────────

    private fun quickVelvia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.38f); val ng = clamp(g * 1.28f); val nb = clamp(b * 1.18f)
        return Triple(sCurve(nr, 0.55f), sCurve(ng, 0.5f), sCurve(nb, 0.45f))
    }

    private fun quickEktar(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.3f + 0.04f); val ng = clamp(g * 1.15f + 0.03f); val nb = clamp(b * 1.1f + 0.02f)
        return Triple(sCurve(nr, 0.5f), sCurve(ng, 0.45f), sCurve(nb, 0.4f))
    }

    private fun quickTriX(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val lifted = liftBlacks(clamp((l - 0.5f) * 1.8f + 0.5f), 0.06f)
        return Triple(sCurve(lifted, 0.5f), sCurve(lifted, 0.5f), sCurve(lifted, 0.5f))
    }

    private fun quickHP5(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val lifted = liftBlacks(clamp((l - 0.5f) * 1.35f + 0.5f), 0.08f)
        val warm = clamp(lifted * 1.01f)
        return Triple(sCurve(warm, 0.35f), sCurve(warm, 0.35f), sCurve(lifted, 0.35f))
    }

    private fun quickArizona(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.22f + 0.12f); val ng = clamp(g * 1.02f + 0.04f); val nb = clamp(b * 0.72f - 0.03f)
        return splitTone(sCurve(nr, 0.4f), sCurve(ng, 0.3f), sCurve(nb, 0.35f), Triple(0.3f, 0.15f, -0.15f), Triple(0.1f, 0.05f, -0.1f))
    }

    private fun quickMetro(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.75f + 0.02f); val ng = clamp(g * 0.85f + 0.04f); val nb = clamp(b * 1.18f + 0.1f)
        return splitTone(sCurve(nr, 0.3f), sCurve(ng, 0.25f), sCurve(nb, 0.35f), Triple(-0.1f, 0.1f, 0.4f), Triple(0f, 0f, 0.15f))
    }
}
