package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

object HdEnhancer {

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Fast edge-preserving denoise (separable median-like filter)
        val denoised = fastDenoise(pixels, w, h)

        // 2. Combined unsharp mask + micro detail in one pass
        val blurred1 = boxBlur(denoised, w, h, 2)
        val blurred2 = boxBlur(denoised, w, h, 1)
        val sharpened = combineSharpen(denoised, blurred1, blurred2, w, h, intensity)

        // 3. Local contrast (clarity)
        val clarified = localContrast(sharpened, w, h, 0.5f * intensity)

        // 4. Dynamic range expansion
        val expanded = dynamicRangeExpansion(clarified, w, h, 0.25f * intensity)

        src.setPixels(expanded, 0, w, 0, 0, w, h)
        return src
    }

    private fun fastDenoise(pixels: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val c = pixels[idx]
                val cr = Color.red(c); val cg = Color.green(c); val cb = Color.blue(c)

                var sumR = cr; var sumG = cg; var sumB = cb; var count = 1

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                        val n = pixels[ny * w + nx]
                        val nr = Color.red(n); val ng = Color.green(n); val nb = Color.blue(n)
                        val diff = abs(cr - nr) + abs(cg - ng) + abs(cb - nb)
                        if (diff < 60) {
                            sumR += nr; sumG += ng; sumB += nb; count++
                        }
                    }
                }
                out[idx] = Color.argb(Color.alpha(c), sumR / count, sumG / count, sumB / count)
            }
        }
        return out
    }

    private fun combineSharpen(pixels: IntArray, blurred1: IntArray, blurred2: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val out = IntArray(pixels.size)
        val amount1 = 1.5f * intensity
        val amount2 = 0.8f * intensity
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val br1 = Color.red(blurred1[i]).toFloat()
            val bg1 = Color.green(blurred1[i]).toFloat()
            val bb1 = Color.blue(blurred1[i]).toFloat()
            val br2 = Color.red(blurred2[i]).toFloat()
            val bg2 = Color.green(blurred2[i]).toFloat()
            val bb2 = Color.blue(blurred2[i]).toFloat()
            val dr = r - br1; val dg = g - bg1; val db = b - bb1
            val mr = r - br2; val mg = g - bg2; val mb = b - bb2
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + dr * amount1 + mr * amount2).roundToInt().coerceIn(0, 255),
                (g + dg * amount1 + mg * amount2).roundToInt().coerceIn(0, 255),
                (b + db * amount1 + mb * amount2).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun localContrast(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val radius = maxOf(w, h) / 32
        val blurred = boxBlur(pixels, w, h, radius)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val lr = Color.red(blurred[i]).toFloat()
            val lg = Color.green(blurred[i]).toFloat()
            val lb = Color.blue(blurred[i]).toFloat()
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + (r - lr) * amount).roundToInt().coerceIn(0, 255),
                (g + (g - lg) * amount).roundToInt().coerceIn(0, 255),
                (b + (b - lb) * amount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun dynamicRangeExpansion(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val histR = IntArray(256); val histG = IntArray(256); val histB = IntArray(256)
        val total = w * h
        for (i in pixels.indices) {
            histR[Color.red(pixels[i])]++;
            histG[Color.green(pixels[i])]++;
            histB[Color.blue(pixels[i])]++;
        }
        var lowR = 0; var highR = 255
        var accum = 0
        for (i in 0..255) { accum += histR[i]; if (accum >= total * 0.005f) { lowR = i; break } }
        accum = 0
        for (i in 255 downTo 0) { accum += histR[i]; if (accum >= total * 0.005f) { highR = i; break } }

        if (highR <= lowR) return pixels

        val out = IntArray(pixels.size)
        val range = (highR - lowR).toFloat()
        for (i in pixels.indices) {
            val or = Color.red(pixels[i]).toFloat()
            val og = Color.green(pixels[i]).toFloat()
            val ob = Color.blue(pixels[i]).toFloat()
            val nr = ((or - lowR) / range * 255f).roundToInt().coerceIn(0, 255).toFloat()
            val ng = ((og - lowR) / range * 255f).roundToInt().coerceIn(0, 255).toFloat()
            val nb = ((ob - lowR) / range * 255f).roundToInt().coerceIn(0, 255).toFloat()
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (or + (nr - or) * amount).roundToInt().coerceIn(0, 255),
                (og + (ng - og) * amount).roundToInt().coerceIn(0, 255),
                (ob + (nb - ob) * amount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return pixels.copyOf()
        val out = IntArray(pixels.size)
        val r = min(radius, minOf(w, h) / 4)
        val temp = IntArray(pixels.size)
        for (y in 0 until h) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (x in -r..r) {
                val nx = x.coerceIn(0, w - 1)
                val px = pixels[y * w + nx]
                sumR += Color.red(px); sumG += Color.green(px); sumB += Color.blue(px); count++
            }
            for (x in 0 until w) {
                val idx = y * w + x
                temp[idx] = Color.argb(255, sumR / count, sumG / count, sumB / count)
                if (x + r + 1 < w) {
                    val add = pixels[y * w + x + r + 1]
                    val rem = pixels[y * w + (x - r).coerceIn(0, w - 1)]
                    sumR += Color.red(add) - Color.red(rem)
                    sumG += Color.green(add) - Color.green(rem)
                    sumB += Color.blue(add) - Color.blue(rem)
                }
            }
        }
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (y in -r..r) {
                val ny = y.coerceIn(0, h - 1)
                val px = temp[ny * w + x]
                sumR += Color.red(px); sumG += Color.green(px); sumB += Color.blue(px); count++
            }
            for (y in 0 until h) {
                val idx = y * w + x
                out[idx] = Color.argb(Color.alpha(pixels[idx]), sumR / count, sumG / count, sumB / count)
                if (y + r + 1 < h) {
                    val add = temp[(y + r + 1) * w + x]
                    val rem = temp[(y - r).coerceIn(0, h - 1) * w + x]
                    sumR += Color.red(add) - Color.red(rem)
                    sumG += Color.green(add) - Color.green(rem)
                    sumB += Color.blue(add) - Color.blue(rem)
                }
            }
        }
        return out
    }
}
