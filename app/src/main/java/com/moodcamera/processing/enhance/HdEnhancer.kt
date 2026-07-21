package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object HdEnhancer {

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Edge-preserving denoise (smooth flat areas, keep edges sharp)
        val denoised = edgePreservingDenoise(pixels, w, h)

        // 2. Strong multi-radius unsharp mask (captures both fine and medium detail)
        val blur1 = boxBlur(denoised, w, h, 1)
        val blur2 = boxBlur(denoised, w, h, 3)
        val blur3 = boxBlur(denoised, w, h, 6)
        val sharpened = multiRadiusSharpen(denoised, blur1, blur2, blur3, w, h, intensity)

        // 3. Strong local contrast / clarity
        val clarified = strongLocalContrast(sharpened, w, h, intensity)

        // 4. Adaptive contrast (histogram stretch per channel)
        val contrasted = adaptiveContrast(clarified, w, h, intensity)

        // 5. Edge enhancement (structure / texture boost)
        val structured = edgeEnhance(contrasted, w, h, intensity)

        src.setPixels(structured, 0, w, 0, 0, w, h)
        return src
    }

    private fun edgePreservingDenoise(pixels: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val c = pixels[idx]
                val cr = Color.red(c); val cg = Color.green(c); val cb = Color.blue(c)

                var sumR = cr; var sumG = cg; var sumB = cb; var count = 1
                var edgeStrength = 0

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                        val n = pixels[ny * w + nx]
                        val nr = Color.red(n); val ng = Color.green(n); val nb = Color.blue(n)
                        val diff = abs(cr - nr) + abs(cg - ng) + abs(cb - nb)
                        if (diff > 80) edgeStrength++
                        if (diff < 40) {
                            sumR += nr; sumG += ng; sumB += nb; count++
                        }
                    }
                }
                if (edgeStrength >= 3) {
                    out[idx] = pixels[idx]
                } else {
                    out[idx] = Color.argb(Color.alpha(c), sumR / count, sumG / count, sumB / count)
                }
            }
        }
        return out
    }

    private fun multiRadiusSharpen(pixels: IntArray, b1: IntArray, b2: IntArray, b3: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val out = IntArray(pixels.size)
        val fineAmt = 2.0f * intensity
        val medAmt = 1.2f * intensity
        val coarseAmt = 0.6f * intensity

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()

            val d1r = r - Color.red(b1[i]).toFloat()
            val d1g = g - Color.green(b1[i]).toFloat()
            val d1b = b - Color.blue(b1[i]).toFloat()

            val d2r = r - Color.red(b2[i]).toFloat()
            val d2g = g - Color.green(b2[i]).toFloat()
            val d2b = b - Color.blue(b2[i]).toFloat()

            val d3r = r - Color.red(b3[i]).toFloat()
            val d3g = g - Color.green(b3[i]).toFloat()
            val d3b = b - Color.blue(b3[i]).toFloat()

            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + d1r * fineAmt + d2r * medAmt + d3r * coarseAmt).roundToInt().coerceIn(0, 255),
                (g + d1g * fineAmt + d2g * medAmt + d3g * coarseAmt).roundToInt().coerceIn(0, 255),
                (b + d1b * fineAmt + d2b * medAmt + d3b * coarseAmt).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun strongLocalContrast(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val radius1 = max(16, maxOf(w, h) / 24)
        val radius2 = max(48, maxOf(w, h) / 8)
        val blur1 = boxBlur(pixels, w, h, radius1)
        val blur2 = boxBlur(pixels, w, h, radius2)

        val out = IntArray(pixels.size)
        val amount = 0.8f * intensity
        val largeAmount = 0.4f * intensity

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()

            val lr1 = Color.red(blur1[i]).toFloat()
            val lg1 = Color.green(blur1[i]).toFloat()
            val lb1 = Color.blue(blur1[i]).toFloat()

            val lr2 = Color.red(blur2[i]).toFloat()
            val lg2 = Color.green(blur2[i]).toFloat()
            val lb2 = Color.blue(blur2[i]).toFloat()

            val dr1 = r - lr1; val dg1 = g - lg1; val db1 = b - lb1
            val dr2 = r - lr2; val dg2 = g - lg2; val db2 = b - lb2

            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + dr1 * amount + dr2 * largeAmount).roundToInt().coerceIn(0, 255),
                (g + dg1 * amount + dg2 * largeAmount).roundToInt().coerceIn(0, 255),
                (b + db1 * amount + db2 * largeAmount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun adaptiveContrast(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val total = w * h
        val histR = IntArray(256); val histG = IntArray(256); val histB = IntArray(256)
        val histL = IntArray(256)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
            histR[r]++; histG[g]++; histB[b]++
            val l = (r * 0.299 + g * 0.587 + b * 0.114).roundToInt().coerceIn(0, 255)
            histL[l]++
        }

        val low = findPercentile(histL, total, 0.01f)
        val high = findPercentile(histL, total, 0.99f)
        if (high <= low) return pixels

        val lutR = buildChannelLUT(histR, total, 0.005f, 0.995f)
        val lutG = buildChannelLUT(histG, total, 0.005f, 0.995f)
        val lutB = buildChannelLUT(histB, total, 0.005f, 0.995f)

        val out = IntArray(pixels.size)
        val amt = 0.6f * intensity

        for (i in pixels.indices) {
            val or = Color.red(pixels[i])
            val og = Color.green(pixels[i])
            val ob = Color.blue(pixels[i])
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (or + (lutR[or] - or) * amt).roundToInt().coerceIn(0, 255),
                (og + (lutG[og] - og) * amt).roundToInt().coerceIn(0, 255),
                (ob + (lutB[ob] - ob) * amt).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun edgeEnhance(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val out = IntArray(pixels.size)
        val amount = 1.5f * intensity

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]
                val r = Color.red(c).toFloat()
                val g = Color.green(c).toFloat()
                val b = Color.blue(c).toFloat()

                val tl = pixels[(y - 1) * w + (x - 1)]
                val tr = pixels[(y - 1) * w + (x + 1)]
                val bl = pixels[(y + 1) * w + (x - 1)]
                val br = pixels[(y + 1) * w + (x + 1)]

                val gx = (Color.red(tr) + Color.red(br) - Color.red(tl) - Color.red(bl)).toFloat()
                val gy = (Color.red(tl) + Color.red(tr) - Color.red(bl) - Color.red(br)).toFloat()
                val edge = sqrt(gx * gx + gy * gy) / 362f

                val gxg = (Color.green(tr) + Color.green(br) - Color.green(tl) - Color.green(bl)).toFloat()
                val gyg = (Color.green(tl) + Color.green(tr) - Color.green(bl) - Color.green(br)).toFloat()
                val edgeG = sqrt(gxg * gxg + gyg * gyg) / 362f

                val gxb = (Color.blue(tr) + Color.blue(br) - Color.blue(tl) - Color.blue(bl)).toFloat()
                val gyb = (Color.blue(tl) + Color.blue(tr) - Color.blue(bl) - Color.blue(br)).toFloat()
                val edgeB = sqrt(gxb * gxb + gyb * gyb) / 362f

                out[idx] = Color.argb(Color.alpha(c),
                    (r + r * edge * amount * 0.3f).roundToInt().coerceIn(0, 255),
                    (g + g * edgeG * amount * 0.3f).roundToInt().coerceIn(0, 255),
                    (b + b * edgeB * amount * 0.3f).roundToInt().coerceIn(0, 255)
                )
            }
        }
        for (y in intArrayOf(0, h - 1)) {
            for (x in 0 until w) { out[y * w + x] = pixels[y * w + x] }
        }
        for (x in intArrayOf(0, w - 1)) {
            for (y in 0 until h) { out[y * w + x] = pixels[y * w + x] }
        }
        return out
    }

    private fun findPercentile(hist: IntArray, total: Int, pct: Float): Int {
        var accum = 0
        val target = (total * pct).toInt()
        for (i in 0..255) {
            accum += hist[i]
            if (accum >= target) return i
        }
        return 255
    }

    private fun buildChannelLUT(hist: IntArray, total: Int, lowPct: Float, highPct: Float): IntArray {
        val low = findPercentile(hist, total, lowPct)
        val high = findPercentile(hist, total, highPct)
        val range = (high - low).toFloat()
        if (range < 1f) return IntArray(256) { it }
        return IntArray(256) { i ->
            (((i - low) / range * 255f).roundToInt()).coerceIn(0, 255)
        }
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
