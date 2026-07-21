package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.roundToInt

object HdEnhancer {

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Bilateral noise reduction (edge-preserving)
        val denoised = bilateralFilter(pixels, w, h, 3, 0.08f)

        // 2. Unsharp mask sharpening
        val sharpened = unsharpMask(denoised, w, h, 2, 1.5f * intensity)

        // 3. Local contrast enhancement (clarity)
        val clarified = localContrast(sharpened, w, h, 0.6f * intensity)

        // 4. Micro detail recovery
        val detailed = microDetail(clarified, w, h, 0.4f * intensity)

        // 5. Dynamic range expansion
        val expanded = dynamicRangeExpansion(detailed, w, h, 0.3f * intensity)

        src.setPixels(expanded, 0, w, 0, 0, w, h)
        return src
    }

    private fun bilateralFilter(pixels: IntArray, w: Int, h: Int, radius: Int, sigmaSpace: Float): IntArray {
        val out = IntArray(pixels.size)
        val sigmaColor = sigmaSpace * 255f
        val kernelSize = radius * 2 + 1
        val spatialLut = FloatArray(kernelSize * kernelSize)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val dist2 = (dx * dx + dy * dy).toFloat()
                spatialLut[(dy + radius) * kernelSize + (dx + radius)] = exp(-dist2 / (2f * radius * radius))
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val cr = Color.red(pixels[idx]).toFloat()
                val cg = Color.green(pixels[idx]).toFloat()
                val cb = Color.blue(pixels[idx]).toFloat()
                var sumR = 0f; var sumG = 0f; var sumB = 0f; var sumW = 0f

                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val nIdx = ny * w + nx
                        val nr = Color.red(pixels[nIdx]).toFloat()
                        val ng = Color.green(pixels[nIdx]).toFloat()
                        val nb = Color.blue(pixels[nIdx]).toFloat()
                        val colorDist = abs(cr - nr) + abs(cg - ng) + abs(cb - nb)
                        val colorW = exp(-colorDist * colorDist / (2f * sigmaColor * sigmaColor))
                        val spW = spatialLut[(dy + radius) * kernelSize + (dx + radius)]
                        val w2 = spW * colorW
                        sumR += nr * w2; sumG += ng * w2; sumB += nb * w2; sumW += w2
                    }
                }
                if (sumW > 0f) {
                    out[idx] = Color.argb(Color.alpha(pixels[idx]),
                        (sumR / sumW).roundToInt().coerceIn(0, 255),
                        (sumG / sumW).roundToInt().coerceIn(0, 255),
                        (sumB / sumW).roundToInt().coerceIn(0, 255)
                    )
                } else {
                    out[idx] = pixels[idx]
                }
            }
        }
        return out
    }

    private fun unsharpMask(pixels: IntArray, w: Int, h: Int, radius: Int, amount: Float): IntArray {
        val blurred = boxBlur(pixels, w, h, radius)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val br = Color.red(blurred[i]).toFloat()
            val bg = Color.green(blurred[i]).toFloat()
            val bb = Color.blue(blurred[i]).toFloat()
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + (r - br) * amount).roundToInt().coerceIn(0, 255),
                (g + (g - bg) * amount).roundToInt().coerceIn(0, 255),
                (b + (b - bb) * amount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun localContrast(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val blurred = boxBlur(pixels, w, h, maxOf(w, h) / 32)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val lr = Color.red(blurred[i]).toFloat()
            val lg = Color.green(blurred[i]).toFloat()
            val lb = Color.blue(blurred[i]).toFloat()
            val dr = r - lr; val dg = g - lg; val db = b - lb
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + dr * amount).roundToInt().coerceIn(0, 255),
                (g + dg * amount).roundToInt().coerceIn(0, 255),
                (b + db * amount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun microDetail(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val blurred = boxBlur(pixels, w, h, 1)
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val br = Color.red(blurred[i]).toFloat()
            val bg = Color.green(blurred[i]).toFloat()
            val bb = Color.blue(blurred[i]).toFloat()
            val detailR = r - br; val detailG = g - bg; val detailB = b - bb
            val sharpened = 1f + amount * 2f
            out[i] = Color.argb(Color.alpha(pixels[i]),
                (r + detailR * amount).roundToInt().coerceIn(0, 255),
                (g + detailG * amount).roundToInt().coerceIn(0, 255),
                (b + detailB * amount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun dynamicRangeExpansion(pixels: IntArray, w: Int, h: Int, amount: Float): IntArray {
        // Compute histogram
        val histR = IntArray(256); val histG = IntArray(256); val histB = IntArray(256)
        val total = w * h
        for (i in pixels.indices) {
            histR[Color.red(pixels[i])]++;
            histR[Color.green(pixels[i])]++;
            histR[Color.blue(pixels[i])]++;
        }
        // Find 1% and 99% points
        var lowR = 0; var highR = 255
        var accum = 0
        for (i in 0..255) { accum += histR[i]; if (accum >= total * 0.005f) { lowR = i; break } }
        accum = 0
        for (i in 255 downTo 0) { accum += histR[i]; if (accum >= total * 0.005f) { highR = i; break } }

        if (highR <= lowR) return pixels

        val out = IntArray(pixels.size)
        val range = (highR - lowR).toFloat()
        for (i in pixels.indices) {
            val nr = ((Color.red(pixels[i]) - lowR) / range * 255f).roundToInt().coerceIn(0, 255)
            val ng = ((Color.green(pixels[i]) - lowR) / range * 255f).roundToInt().coerceIn(0, 255)
            val nb = ((Color.blue(pixels[i]) - lowR) / range * 255f).roundToInt().coerceIn(0, 255)
            val or = Color.red(pixels[i]).toFloat()
            val og = Color.green(pixels[i]).toFloat()
            val ob = Color.blue(pixels[i]).toFloat()
            out[i] = Color.argb(Color.alpha(pixels[i]),
                lerp(or, nr.toFloat(), amount).roundToInt().coerceIn(0, 255),
                lerp(og, ng.toFloat(), amount).roundToInt().coerceIn(0, 255),
                lerp(ob, nb.toFloat(), amount).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return pixels.copyOf()
        val out = IntArray(pixels.size)
        val r = min(radius, minOf(w, h) / 4)
        // Horizontal pass
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
        // Vertical pass
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
