package com.moodcamera.processing.enhance

import android.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class CinematicLut(
    val displayName: String,
    val description: String
) {
    KODAK_VISION3("Kodak Vision3", "Hollywood cinema film stock"),
    KODAK_2383("Kodak 2383", "Classic print film emulation"),
    FUJIFILM_3513("Fuji 3513", "Fuji cinema print film"),
    BLEACH_BYPASS("Bleach Bypass", "Desaturated high contrast"),
    CROSS_PROCESS("Cross Process", "Creative color shift"),
    TEAL_ORANGE_PRO("Teal & Orange Pro", "Hollywood blockbuster grade"),
    WARM_CINEMATIC("Warm Cinema", "Warm golden film look"),
    MOODY_DARK("Moody Dark", "Dark atmospheric grade"),
    FADED_FILM("Faded Film", "Lifted matte indie look"),
    KODACHROME_64("Kodachrome 64", "Classic slide film"),
    PORTRA_400_PRO("Portra 400 Pro", "Professional portrait film"),
    FUJI_PROVIA_PRO("Provia Pro", "Balanced slide film"),
    HOLLYWOOD_BLOCKBUSTER("Blockbuster", "Studio blockbuster grade"),
    VINTAGE_CHROME("Vintage Chrome", "Retro chrome film"),
    NIGHT_CINEMA("Night Cinema", "Moody night look"),
    GOLDEN_HOUR("Golden Hour", "Warm sunset tones"),
    COLD_STEEL("Cold Steel", "Cool desaturated steel"),
    FILM_NOIR("Film Noir", "Classic B&W cinema"),
    MATTE_FADE("Matte Fade", "Indie film matte look"),
    FRENCH_COMEDY("French Cinema", "Warm European cinema")
}

object CinematicLutEngine {

    fun applyLut(r: Float, g: Float, b: Float, lut: CinematicLut): Triple<Float, Float, Float> {
        return when (lut) {
            CinematicLut.KODAK_VISION3 -> kodakVision3(r, g, b)
            CinematicLut.KODAK_2383 -> kodak2383(r, g, b)
            CinematicLut.FUJIFILM_3513 -> fuji3513(r, g, b)
            CinematicLut.BLEACH_BYPASS -> bleachBypass(r, g, b)
            CinematicLut.CROSS_PROCESS -> crossProcess(r, g, b)
            CinematicLut.TEAL_ORANGE_PRO -> tealOrangePro(r, g, b)
            CinematicLut.WARM_CINEMATIC -> warmCinematic(r, g, b)
            CinematicLut.MOODY_DARK -> moodyDark(r, g, b)
            CinematicLut.FADED_FILM -> fadedFilm(r, g, b)
            CinematicLut.KODACHROME_64 -> kodachrome64(r, g, b)
            CinematicLut.PORTRA_400_PRO -> portra400Pro(r, g, b)
            CinematicLut.FUJI_PROVIA_PRO -> fujiProviaPro(r, g, b)
            CinematicLut.HOLLYWOOD_BLOCKBUSTER -> hollywoodBlockbuster(r, g, b)
            CinematicLut.VINTAGE_CHROME -> vintageChrome(r, g, b)
            CinematicLut.NIGHT_CINEMA -> nightCinema(r, g, b)
            CinematicLut.GOLDEN_HOUR -> goldenHour(r, g, b)
            CinematicLut.COLD_STEEL -> coldSteel(r, g, b)
            CinematicLut.FILM_NOIR -> filmNoir(r, g, b)
            CinematicLut.MATTE_FADE -> matteFade(r, g, b)
            CinematicLut.FRENCH_COMEDY -> frenchCinema(r, g, b)
        }
    }

    private fun clamp(x: Float) = x.coerceIn(0f, 1f)
    private fun lum(r: Float, g: Float, b: Float) = r * 0.2126f + g * 0.7152f + b * 0.0722f

    private fun sCurve(x: Float, s: Float): Float {
        return if (x < 0.5f) 0.5f * (2f * x).pow(s) else 1f - 0.5f * (2f * (1f - x)).pow(s)
    }

    private fun filmGrain(r: Float, g: Float, b: Float, seed: Int): Triple<Float, Float, Float> {
        val noise = ((seed * 1337 + (r * 1000).toInt()) % 100) / 1000f - 0.05f
        return Triple(clamp(r + noise * 0.3f), clamp(g + noise * 0.28f), clamp(b + noise * 0.25f))
    }

    // ── Kodak Vision3: Rich shadows, warm skin, cinema tungsten ────
    private fun kodakVision3(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.08f + 0.04f)
        val ng = clamp(g * 1.02f + 0.01f)
        val nb = clamp(b * 0.92f - 0.01f)
        val l = lum(nr, ng, nb)
        val s = clamp((l - 0.5f) * 1.1f + 0.5f)
        val lifted = clamp(s * 0.97f + 0.03f)
        return Triple(clamp(nr * 0.95f + lifted * 0.05f), clamp(ng * 0.97f + lifted * 0.03f), clamp(nb * 0.93f + lifted * 0.07f))
    }

    // ── Kodak 2383: Print film with rich contrast and warm midtones ──
    private fun kodak2383(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 1.1f + 0.03f), 0.5f)
        val ng = sCurve(clamp(g * 1.05f + 0.01f), 0.45f)
        val nb = sCurve(clamp(b * 0.95f), 0.48f)
        return Triple(clamp(nr + 0.02f), clamp(ng + 0.01f), clamp(nb - 0.01f))
    }

    // ── Fuji 3513: Cooler shadows, green bias, cinema print ────────
    private fun fuji3513(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 1.02f + 0.01f), 0.4f)
        val ng = sCurve(clamp(g * 1.08f + 0.03f), 0.42f)
        val nb = sCurve(clamp(b * 1.0f + 0.02f), 0.4f)
        val l = lum(nr, ng, nb)
        return Triple(clamp(nr * 0.96f + l * 0.04f), clamp(ng * 0.98f + l * 0.02f), clamp(nb * 1.02f + l * 0.01f))
    }

    // ── Bleach Bypass: Silver retention, desaturated, high contrast ──
    private fun bleachBypass(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val nr = clamp(lerp(r, l, 0.55f))
        val ng = clamp(lerp(g, l, 0.55f))
        val nb = clamp(lerp(b, l, 0.55f))
        return Triple(sCurve(nr, 0.65f), sCurve(ng, 0.65f), sCurve(nb, 0.65f))
    }

    // ── Cross Process: Shifted channels, creative chemistry ─────────
    private fun crossProcess(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(b * 0.6f + g * 0.4f + 0.05f)
        val ng = clamp(r * 0.3f + g * 0.7f + 0.03f)
        val nb = clamp(r * 0.4f + b * 0.6f - 0.02f)
        return Triple(sCurve(nr, 0.4f), sCurve(ng, 0.35f), sCurve(nb, 0.4f))
    }

    // ── Teal & Orange Pro: Proper Hollywood split-tone ─────────────
    private fun tealOrangePro(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val nr: Float; val ng: Float; val nb: Float
        if (l < 0.4f) {
            nr = clamp(r * 0.88f - 0.02f)
            ng = clamp(g * 1.06f + 0.06f)
            nb = clamp(b * 1.18f + 0.1f)
        } else {
            nr = clamp(r * 1.2f + 0.08f)
            ng = clamp(g * 1.02f + 0.02f)
            nb = clamp(b * 0.82f - 0.03f)
        }
        return Triple(sCurve(nr, 0.5f), sCurve(ng, 0.45f), sCurve(nb, 0.48f))
    }

    // ── Warm Cinema: Golden shadows, warm highlights ───────────────
    private fun warmCinematic(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.12f + 0.06f)
        val ng = clamp(g * 1.04f + 0.02f)
        val nb = clamp(b * 0.85f - 0.02f)
        val l = lum(nr, ng, nb)
        return Triple(clamp(nr * 0.96f + l * 0.04f), clamp(ng * 0.98f + l * 0.02f), clamp(nb * 1.02f))
    }

    // ── Moody Dark: Deep shadows, desaturated, atmospheric ─────────
    private fun moodyDark(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 0.72f + 0.04f), 0.55f)
        val ng = sCurve(clamp(g * 0.75f + 0.03f), 0.5f)
        val nb = sCurve(clamp(b * 0.82f + 0.06f), 0.52f)
        val l = lum(nr, ng, nb)
        return Triple(clamp(lerp(nr, l, 0.2f)), clamp(lerp(ng, l, 0.2f)), clamp(lerp(nb, l, 0.15f)))
    }

    // ── Faded Film: Lifted blacks, matte finish ────────────────────
    private fun fadedFilm(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val lift = 0.12f
        val nr = clamp(r * 0.88f + lift)
        val ng = clamp(g * 0.88f + lift)
        val nb = clamp(b * 0.85f + lift + 0.02f)
        return Triple(sCurve(nr, 0.3f), sCurve(ng, 0.28f), sCurve(nb, 0.3f))
    }

    // ── Kodachrome 64: Rich, punchy, warm shadows ──────────────────
    private fun kodachrome64(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 1.2f + 0.06f), 0.55f)
        val ng = sCurve(clamp(g * 1.08f + 0.02f), 0.48f)
        val nb = sCurve(clamp(b * 0.88f - 0.02f), 0.5f)
        return Triple(clamp(nr + 0.02f), ng, clamp(nb - 0.01f))
    }

    // ── Portra 400 Pro: Soft, warm skin, natural ───────────────────
    private fun portra400Pro(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.1f + 0.05f)
        val ng = clamp(g * 1.04f + 0.02f)
        val nb = clamp(b * 0.9f)
        val l = lum(nr, ng, nb)
        return Triple(sCurve(clamp(nr + l * 0.03f), 0.35f), sCurve(clamp(ng + l * 0.01f), 0.3f), sCurve(clamp(nb + l * 0.02f), 0.32f))
    }

    // ── Provia Pro: Balanced, punchy, fine grain ────────────────────
    private fun fujiProviaPro(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 1.08f + 0.02f), 0.42f)
        val ng = sCurve(clamp(g * 1.1f + 0.03f), 0.4f)
        val nb = sCurve(clamp(b * 1.06f + 0.02f), 0.42f)
        return Triple(nr, ng, nb)
    }

    // ── Hollywood Blockbuster: Studio polish ────────────────────────
    private fun hollywoodBlockbuster(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val nr: Float; val ng: Float; val nb: Float
        if (l < 0.45f) {
            nr = clamp(r * 0.9f + 0.01f)
            ng = clamp(g * 1.02f + 0.04f)
            nb = clamp(b * 1.12f + 0.06f)
        } else {
            nr = clamp(r * 1.15f + 0.06f)
            ng = clamp(g * 1.0f + 0.01f)
            nb = clamp(b * 0.88f - 0.02f)
        }
        return Triple(sCurve(nr, 0.45f), sCurve(ng, 0.4f), sCurve(nb, 0.42f))
    }

    // ── Vintage Chrome: Retro, warm, slightly faded ─────────────────
    private fun vintageChrome(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.05f + 0.08f)
        val ng = clamp(g * 0.95f + 0.03f)
        val nb = clamp(b * 0.82f + 0.01f)
        val lifted = 0.08f
        return Triple(sCurve(clamp(nr + lifted), 0.35f), sCurve(clamp(ng + lifted), 0.3f), sCurve(clamp(nb + lifted), 0.32f))
    }

    // ── Night Cinema: Dark, blue shadows, moody ────────────────────
    private fun nightCinema(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = sCurve(clamp(r * 0.68f + 0.05f), 0.5f)
        val ng = sCurve(clamp(g * 0.72f + 0.04f), 0.45f)
        val nb = sCurve(clamp(b * 0.88f + 0.1f), 0.55f)
        val l = lum(nr, ng, nb)
        return Triple(clamp(lerp(nr, l, 0.15f)), clamp(lerp(ng, l, 0.1f)), clamp(nb * 1.05f + l * 0.05f))
    }

    // ── Golden Hour: Warm sunset, soft contrast ────────────────────
    private fun goldenHour(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.18f + 0.1f)
        val ng = clamp(g * 1.06f + 0.04f)
        val nb = clamp(b * 0.8f - 0.03f)
        return Triple(sCurve(nr, 0.35f), sCurve(ng, 0.3f), sCurve(nb, 0.32f))
    }

    // ── Cold Steel: Cool desaturated, metallic ─────────────────────
    private fun coldSteel(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 0.88f)
        val ng = clamp(g * 0.95f + 0.02f)
        val nb = clamp(b * 1.15f + 0.08f)
        val l = lum(nr, ng, nb)
        return Triple(sCurve(clamp(lerp(nr, l, 0.35f)), 0.4f), sCurve(clamp(lerp(ng, l, 0.3f)), 0.38f), sCurve(nb, 0.45f))
    }

    // ── Film Noir: High contrast B&W with slight tone ──────────────
    private fun filmNoir(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val l = lum(r, g, b)
        val contrast = clamp((l - 0.5f) * 1.9f + 0.5f)
        val tone = clamp(contrast * 0.99f + 0.01f)
        return Triple(sCurve(tone, 0.6f), sCurve(tone * 0.99f, 0.6f), sCurve(tone * 0.98f, 0.58f))
    }

    // ── Matte Fade: Lifted blacks, soft contrast, indie ────────────
    private fun matteFade(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val lift = 0.15f
        val nr = clamp(r * 0.82f + lift)
        val ng = clamp(g * 0.82f + lift)
        val nb = clamp(b * 0.8f + lift + 0.01f)
        return Triple(sCurve(nr, 0.25f), sCurve(ng, 0.22f), sCurve(nb, 0.24f))
    }

    // ── French Cinema: Warm European, soft, romantic ────────────────
    private fun frenchCinema(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val nr = clamp(r * 1.08f + 0.06f)
        val ng = clamp(g * 1.0f + 0.02f)
        val nb = clamp(b * 0.88f)
        val l = lum(nr, ng, nb)
        return Triple(sCurve(clamp(nr + l * 0.02f), 0.3f), sCurve(clamp(ng + l * 0.01f), 0.28f), sCurve(clamp(nb + l * 0.03f), 0.3f))
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
