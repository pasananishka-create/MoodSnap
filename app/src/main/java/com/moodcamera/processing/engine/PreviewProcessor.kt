package com.moodcamera.processing.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.moodcamera.domain.model.CameraSettings
import com.moodcamera.domain.model.EmulationType
import kotlin.math.pow
import kotlin.math.roundToInt

object PreviewProcessor {

    fun processPreview(bitmap: Bitmap, settings: CameraSettings): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        val fade = settings.fade
        val fadeLift = (fade * 42f).roundToInt()
        val contrast = settings.contrast
        val brightness = settings.brightness
        val temp = settings.temperature

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
                EmulationType.PORTRA -> quickPortra(r, g, b)
                EmulationType.PORTRA_800 -> quickPortra800(r, g, b)
                EmulationType.FUJI_400H -> quickFuji400h(r, g, b)
                EmulationType.FUJI_SUPERIA -> quickFujiSuperia(r, g, b)
                EmulationType.CINESTILL_800T -> quickCinestill(r, g, b)
                EmulationType.KODACHROME -> quickKodachrome(r, g, b)
                EmulationType.CINEMATIC_TEAL_ORANGE -> quickTealOrange(r, g, b)
                EmulationType.VELVIA -> quickVelvia(r, g, b)
                EmulationType.TRI_X -> quickTriX(r, g, b)
                EmulationType.HP5 -> quickHP5(r, g, b)
                EmulationType.NIGHTFADE -> quickNightfade(r, g, b)
                else -> quickPortra(r, g, b)
            }

            var finalR = nr
            var finalG = ng
            var finalB = nb

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
    private fun fastPow(x: Float, p: Float) = Math.pow(x.toDouble(), p.toDouble()).toFloat()

    private fun quickClarendon(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(r, 0.6f); val ng = sCurve(g, 0.5f); val nb = sCurve(b, 0.55f)
        return splitTone(clamp(nr + 0.03f), ng, clamp(nb + 0.06f), Triple(0.1f, 0.5f, 1f), Triple(-0.2f, 0f, 0.4f))
    }

    private fun quickJuno(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return splitTone(clamp(r * 1.1f + 0.04f), clamp(g * 1.04f + 0.02f), clamp(b * 0.82f), Triple(0.3f, 0.2f, -0.1f), Triple(-0.1f, -0.05f, 0.15f))
    }

    private fun quickLark(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(clamp(r * 0.88f + 0.08f), clamp(g * 1.06f + 0.04f), clamp(b * 1.12f + 0.06f))
    }

    private fun quickValencia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(liftBlacks(clamp(r * 1.08f + 0.1f), 0.15f), liftBlacks(clamp(g * 0.92f + 0.03f), 0.12f), liftBlacks(clamp(b * 0.75f + 0.01f), 0.1f))
    }

    private fun quickHudson(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.86f + 0.02f); val ng = clamp(g * 0.98f + 0.04f); val nb = clamp(b * 1.22f + 0.08f)
        val (sr, sg, sb) = splitTone(nr, ng, nb, Triple(0f, 0.3f, 0.8f), Triple(-0.1f, 0f, 0.3f))
        return Triple(liftBlacks(sr, 0.12f), liftBlacks(sg, 0.1f), liftBlacks(sb, 0.08f))
    }

    private fun quickReyes(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(liftBlacks(clamp(r * 0.78f + 0.16f), 0.25f), liftBlacks(clamp(g * 0.75f + 0.14f), 0.22f), liftBlacks(clamp(b * 0.68f + 0.13f), 0.2f))
    }

    private fun quickPortra(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return splitTone(clamp(r * 1.12f + 0.05f), clamp(g * 1.05f + 0.02f), clamp(b * 0.88f - 0.01f), Triple(0.2f, 0.1f, -0.05f), Triple(-0.05f, 0f, 0.1f))
    }

    private fun quickPortra800(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return splitTone(clamp(r * 1.18f + 0.06f), clamp(g * 1.08f + 0.03f), clamp(b * 0.85f - 0.02f), Triple(0.25f, 0.15f, -0.05f), Triple(-0.08f, -0.02f, 0.12f))
    }

    private fun quickFuji400h(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return splitTone(clamp(r * 0.88f + 0.06f), clamp(g * 1.04f + 0.05f), clamp(b * 1.15f + 0.08f), Triple(0f, 0.3f, 0.5f), Triple(-0.05f, 0.05f, 0.15f))
    }

    private fun quickFujiSuperia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return splitTone(clamp(r * 0.92f + 0.04f), clamp(g * 1.12f + 0.06f), clamp(b * 0.95f + 0.02f), Triple(0.1f, 0.2f, -0.1f), Triple(-0.05f, 0.1f, 0.05f))
    }

    private fun quickCinestill(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(liftBlacks(clamp(r * 0.92f + 0.06f), 0.12f), liftBlacks(clamp(g * 0.88f + 0.01f), 0.1f), liftBlacks(clamp(b * 1.3f + 0.12f), 0.08f))
    }

    private fun quickKodachrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return splitTone(clamp(r * 1.22f + 0.08f), clamp(g * 1.08f + 0.03f), clamp(b * 0.75f - 0.03f), Triple(0.3f, 0.15f, -0.1f), Triple(-0.05f, 0.05f, 0.15f))
    }

    private fun quickTealOrange(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        return if (l < 0.45f) {
            Triple(clamp(r * 0.82f + 0.02f), clamp(g * 1.04f + 0.08f), clamp(b * 1.2f + 0.1f))
        } else {
            Triple(clamp(r * 1.25f + 0.1f), clamp(g * 1.0f + 0.03f), clamp(b * 0.75f - 0.03f))
        }
    }

    private fun quickVelvia(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(clamp(r * 1.38f), clamp(g * 1.28f), clamp(b * 1.18f))
    }

    private fun quickTriX(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val c = clamp((l - 0.5f) * 1.8f + 0.5f)
        return Triple(c, c, c)
    }

    private fun quickHP5(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val c = clamp((l - 0.5f) * 1.35f + 0.5f)
        val w = clamp(c * 1.01f)
        return Triple(w, w, c)
    }

    private fun quickNightfade(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(liftBlacks(clamp(r * 0.65f + 0.08f), 0.15f), liftBlacks(clamp(g * 0.7f + 0.06f), 0.13f), liftBlacks(clamp(b * 0.95f + 0.12f), 0.1f))
    }
}
