package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt

object AiEnhancer {

    private const val TARGET_4K = 3840

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return bitmap

        val src = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var result = edgePreservingDenoise(pixels, w, h)
        result = multiScaleSharpen(result, w, h, intensity)
        result = localContrastEnhance(result, w, h, intensity)
        result = adaptiveDynamicRange(result, w, h, intensity)
        result = textureEnhance(result, w, h, intensity)
        result = colorBoost(result, w, h, intensity)

        src.setPixels(result, 0, w, 0, 0, w, h)
        return src
    }

    fun upscaleTo4K(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w >= TARGET_4K && h >= TARGET_4K) return bitmap

        val newW: Int
        val newH: Int
        if (w >= h) {
            newW = TARGET_4K
            newH = (h.toFloat() / w * TARGET_4K).roundToInt()
        } else {
            newH = TARGET_4K
            newW = (w.toFloat() / h * TARGET_4K).roundToInt()
        }

        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        val tileSize = 512
        val tilePixels = IntArray(tileSize * tileSize)

        var ty = 0
        while (ty < newH) {
            var tx = 0
            while (tx < newW) {
                val tw = min(tileSize, newW - tx)
                val th = min(tileSize, newH - ty)

                for (dy in 0 until th) {
                    for (dx in 0 until tw) {
                        val srcX = (tx + dx).toFloat() / newW * w
                        val srcY = (ty + dy).toFloat() / newH * h

                        var sumR = 0f; var sumG = 0f; var sumB = 0f; var sumW = 0f

                        for (sy in (srcY - 2).toInt()..(srcY + 2).toInt()) {
                            for (sx in (srcX - 2).toInt()..(srcX + 2).toInt()) {
                                val px = sx.coerceIn(0, w - 1)
                                val py = sy.coerceIn(0, h - 1)
                                val weight = lanczos3(srcX - px) * lanczos3(srcY - py)
                                if (weight <= 0f) continue
                                val pixel = srcPixels[py * w + px]
                                sumR += Color.red(pixel) * weight
                                sumG += Color.green(pixel) * weight
                                sumB += Color.blue(pixel) * weight
                                sumW += weight
                            }
                        }

                        tilePixels[dy * tw + dx] = if (sumW > 0f) {
                            Color.rgb(
                                (sumR / sumW).roundToInt().coerceIn(0, 255),
                                (sumG / sumW).roundToInt().coerceIn(0, 255),
                                (sumB / sumW).roundToInt().coerceIn(0, 255)
                            )
                        } else {
                            srcPixels[srcY.toInt().coerceIn(0, h - 1) * w + srcX.toInt().coerceIn(0, w - 1)]
                        }
                    }
                }

                val tileBitmap = Bitmap.createBitmap(tilePixels, 0, tw, tw, th, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawBitmap(tileBitmap, tx.toFloat(), ty.toFloat(), null)
                tileBitmap.recycle()

                tx += tileSize
            }
            ty += tileSize
        }

        return result
    }

    private fun lanczos3(x: Float): Float {
        val ax = abs(x)
        if (ax >= 3f) return 0f
        if (ax < 0.0001f) return 1f
        return (3f * sin(PI.toFloat() * ax) * sin(PI.toFloat() * ax / 3f)) / (PI.toFloat() * PI.toFloat() * ax * ax)
    }

    fun isReady(): Boolean = true
    fun getLastError(): String? = null

    private fun edgePreservingDenoise(pixels: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val c = pixels[idx]
                val cr = Color.red(c); val cg = Color.green(c); val cb = Color.blue(c)

                var sumR = cr; var sumG = cg; var sumB = cb; var count = 1
                var edgeCount = 0

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx; val ny = y + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                        val n = pixels[ny * w + nx]
                        val nr = Color.red(n); val ng = Color.green(n); val nb = Color.blue(n)
                        val diff = abs(cr - nr) + abs(cg - ng) + abs(cb - nb)
                        if (diff > 60) edgeCount++
                        if (diff < 30) { sumR += nr; sumG += ng; sumB += nb; count++ }
                    }
                }

                if (edgeCount >= 4) {
                    out[idx] = c
                } else {
                    out[idx] = Color.argb(Color.alpha(c), sumR / count, sumG / count, sumB / count)
                }
            }
        }
        return out
    }

    private fun multiScaleSharpen(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val blur1 = boxBlur(pixels, w, h, 1)
        val blur2 = boxBlur(pixels, w, h, 3)
        val blur3 = boxBlur(pixels, w, h, 6)
        val out = IntArray(pixels.size)
        val fine = 0.8f * intensity
        val med = 0.4f * intensity
        val coarse = 0.2f * intensity

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val dr1 = r - Color.red(blur1[i]).toFloat()
            val dg1 = g - Color.green(blur1[i]).toFloat()
            val db1 = b - Color.blue(blur1[i]).toFloat()
            val dr2 = r - Color.red(blur2[i]).toFloat()
            val dg2 = g - Color.green(blur2[i]).toFloat()
            val db2 = b - Color.blue(blur2[i]).toFloat()
            val dr3 = r - Color.red(blur3[i]).toFloat()
            val dg3 = g - Color.green(blur3[i]).toFloat()
            val db3 = b - Color.blue(blur3[i]).toFloat()
            out[i] = Color.rgb(
                (r + dr1 * fine + dr2 * med + dr3 * coarse).roundToInt().coerceIn(0, 255),
                (g + dg1 * fine + dg2 * med + dg3 * coarse).roundToInt().coerceIn(0, 255),
                (b + db1 * fine + db2 * med + db3 * coarse).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun localContrastEnhance(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val r1 = max(12, maxOf(w, h) / 20)
        val r2 = max(40, maxOf(w, h) / 6)
        val blur1 = boxBlur(pixels, w, h, r1)
        val blur2 = boxBlur(pixels, w, h, r2)
        val out = IntArray(pixels.size)
        val amt1 = 0.3f * intensity
        val amt2 = 0.15f * intensity

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
            out[i] = Color.rgb(
                (r + (r - lr1) * amt1 + (r - lr2) * amt2).roundToInt().coerceIn(0, 255),
                (g + (g - lg1) * amt1 + (g - lg2) * amt2).roundToInt().coerceIn(0, 255),
                (b + (b - lb1) * amt1 + (b - lb2) * amt2).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun adaptiveDynamicRange(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val total = w * h
        val histR = IntArray(256); val histG = IntArray(256); val histB = IntArray(256)
        for (i in pixels.indices) {
            histR[Color.red(pixels[i])]++
            histG[Color.green(pixels[i])]++
            histB[Color.blue(pixels[i])]++
        }
        val loR = findCut(histR, total, 0.005f)
        val hiR = findCutHi(histR, total, 0.005f)
        val loG = findCut(histG, total, 0.005f)
        val hiG = findCutHi(histG, total, 0.005f)
        val loB = findCut(histB, total, 0.005f)
        val hiB = findCutHi(histB, total, 0.005f)
        val rangeR = max((hiR - loR), 1).toFloat()
        val rangeG = max((hiG - loG), 1).toFloat()
        val rangeB = max((hiB - loB), 1).toFloat()

        val amt = 0.2f * intensity
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val or = Color.red(pixels[i]).toFloat()
            val og = Color.green(pixels[i]).toFloat()
            val ob = Color.blue(pixels[i]).toFloat()
            val nr = ((or - loR) / rangeR * 255f)
            val ng = ((og - loG) / rangeG * 255f)
            val nb = ((ob - loB) / rangeB * 255f)
            out[i] = Color.rgb(
                (or + (nr - or) * amt).roundToInt().coerceIn(0, 255),
                (og + (ng - og) * amt).roundToInt().coerceIn(0, 255),
                (ob + (nb - ob) * amt).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun textureEnhance(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val out = IntArray(pixels.size)
        val amt = 0.15f * intensity

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]
                val r = Color.red(c).toFloat(); val g = Color.green(c).toFloat(); val b = Color.blue(c).toFloat()

                val tl = pixels[(y-1)*w+(x-1)]; val tr = pixels[(y-1)*w+(x+1)]
                val bl = pixels[(y+1)*w+(x-1)]; val br = pixels[(y+1)*w+(x+1)]

                val gx = (Color.red(tr)+Color.red(br)-Color.red(tl)-Color.red(bl)).toFloat()
                val gy = (Color.red(tl)+Color.red(tr)-Color.red(bl)-Color.red(br)).toFloat()
                val edge = sqrt(gx*gx+gy*gy) / 362f

                val gxg = (Color.green(tr)+Color.green(br)-Color.green(tl)-Color.green(bl)).toFloat()
                val gyg = (Color.green(tl)+Color.green(tr)-Color.green(bl)-Color.green(br)).toFloat()
                val edgeG = sqrt(gxg*gxg+gyg*gyg) / 362f

                val gxb = (Color.blue(tr)+Color.blue(br)-Color.blue(tl)-Color.blue(bl)).toFloat()
                val gyb = (Color.blue(tl)+Color.blue(tr)-Color.blue(bl)-Color.blue(br)).toFloat()
                val edgeB = sqrt(gxb*gxb+gyb*gyb) / 362f

                out[idx] = Color.rgb(
                    (r + r * edge * amt * 0.4f).roundToInt().coerceIn(0, 255),
                    (g + g * edgeG * amt * 0.4f).roundToInt().coerceIn(0, 255),
                    (b + b * edgeB * amt * 0.4f).roundToInt().coerceIn(0, 255)
                )
            }
        }
        for (y in intArrayOf(0, h-1)) for (x in 0 until w) out[y*w+x] = pixels[y*w+x]
        for (x in intArrayOf(0, w-1)) for (y in 0 until h) out[y*w+x] = pixels[y*w+x]
        return out
    }

    private fun colorBoost(pixels: IntArray, w: Int, h: Int, intensity: Float): IntArray {
        val out = IntArray(pixels.size)
        val satBoost = 1f + 0.06f * intensity

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            out[i] = Color.rgb(
                (lum + (r - lum) * satBoost).roundToInt().coerceIn(0, 255),
                (lum + (g - lum) * satBoost).roundToInt().coerceIn(0, 255),
                (lum + (b - lum) * satBoost).roundToInt().coerceIn(0, 255)
            )
        }
        return out
    }

    private fun findCut(hist: IntArray, total: Int, pct: Float): Int {
        var acc = 0; val target = (total * pct).toInt()
        for (i in 0..255) { acc += hist[i]; if (acc >= target) return i }
        return 0
    }

    private fun findCutHi(hist: IntArray, total: Int, pct: Float): Int {
        var acc = 0; val target = (total * pct).toInt()
        for (i in 255 downTo 0) { acc += hist[i]; if (acc >= target) return i }
        return 255
    }

    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return pixels.copyOf()
        val out = IntArray(pixels.size)
        val r = min(radius, minOf(w, h) / 4)
        val temp = IntArray(pixels.size)
        for (y in 0 until h) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (x in -r..r) {
                val nx = x.coerceIn(0, w-1)
                val px = pixels[y*w+nx]
                sumR += Color.red(px); sumG += Color.green(px); sumB += Color.blue(px); count++
            }
            for (x in 0 until w) {
                temp[y*w+x] = Color.argb(255, sumR/count, sumG/count, sumB/count)
                if (x+r+1 < w) {
                    val add = pixels[y*w+x+r+1]; val rem = pixels[y*w+(x-r).coerceIn(0,w-1)]
                    sumR += Color.red(add)-Color.red(rem); sumG += Color.green(add)-Color.green(rem); sumB += Color.blue(add)-Color.blue(rem)
                }
            }
        }
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (y in -r..r) {
                val ny = y.coerceIn(0, h-1); val px = temp[ny*w+x]
                sumR += Color.red(px); sumG += Color.green(px); sumB += Color.blue(px); count++
            }
            for (y in 0 until h) {
                val idx = y*w+x
                out[idx] = Color.argb(Color.alpha(pixels[idx]), sumR/count, sumG/count, sumB/count)
                if (y+r+1 < h) {
                    val add = temp[(y+r+1)*w+x]; val rem = temp[(y-r).coerceIn(0,h-1)*w+x]
                    sumR += Color.red(add)-Color.red(rem); sumG += Color.green(add)-Color.green(rem); sumB += Color.blue(add)-Color.blue(rem)
                }
            }
        }
        return out
    }
}
