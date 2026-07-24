package com.moodcamera.processing.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

object UpscaylUpscaler {

    private const val SCALE = 4
    private const val MAX_INPUT_DIM = 512

    fun init(context: Context) {
        // No-op: using CPU-based Lanczos upscaling (no ONNX dependency)
    }

    fun close() {}

    fun isReady(): Boolean = true

    fun upscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return bitmap

        val src = if (w > MAX_INPUT_DIM || h > MAX_INPUT_DIM) {
            val ratio = MAX_INPUT_DIM.toFloat() / maxOf(w, h)
            val nw = (w * ratio).roundToInt().coerceAtLeast(8)
            val nh = (h * ratio).roundToInt().coerceAtLeast(8)
            Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap
        }

        val sw = src.width
        val sh = src.height
        val outW = sw * SCALE
        val outH = sh * SCALE

        val srcPixels = IntArray(sw * sh)
        src.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)

        val outPixels = IntArray(outW * outH)

        for (oy in 0 until outH) {
            for (ox in 0 until outW) {
                val srcX = ox.toFloat() / SCALE
                val srcY = oy.toFloat() / SCALE
                outPixels[oy * outW + ox] = sampleLanczos(srcPixels, sw, sh, srcX, srcY)
            }
        }

        val upscaled = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        upscaled.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

        if (src !== bitmap) src.recycle()

        return sharpen(upscaled)
    }

    private fun sampleLanczos(pixels: IntArray, w: Int, h: Int, cx: Float, cy: Float): Int {
        val a = 3
        var rSum = 0f; var gSum = 0f; var bSum = 0f; var wSum = 0f

        val x0 = cx.toInt().coerceIn(0, w - 1)
        val y0 = cy.toInt().coerceIn(0, h - 1)

        val minX = (cx - a).roundToInt().coerceIn(0, w - 1)
        val maxX = (cx + a).roundToInt().coerceIn(0, w - 1)
        val minY = (cy - a).roundToInt().coerceIn(0, h - 1)
        val maxY = (cy + a).roundToInt().coerceIn(0, h - 1)

        for (iy in minY..maxY) {
            val wy = lanczosKernel(cy - iy, a)
            if (abs(wy) < 0.001f) continue
            for (ix in minX..maxX) {
                val wx = lanczosKernel(cx - ix, a)
                if (abs(wx) < 0.001f) continue
                val weight = wx * wy
                val px = pixels[iy * w + ix]
                rSum += Color.red(px) * weight
                gSum += Color.green(px) * weight
                bSum += Color.blue(px) * weight
                wSum += weight
            }
        }

        if (wSum < 0.001f) return pixels[y0 * w + x0]

        val r = (rSum / wSum).roundToInt().coerceIn(0, 255)
        val g = (gSum / wSum).roundToInt().coerceIn(0, 255)
        val b = (bSum / wSum).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun lanczosKernel(x: Float, a: Int): Float {
        if (abs(x) < 0.001f) return 1f
        if (abs(x) >= a) return 0f
        val piX = Math.PI * x
        return (a * sin(piX) * sin(piX / a) / (piX * piX)).toFloat()
    }

    private fun sharpen(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(pixels.size)

        val kernel = floatArrayOf(
            0f, -0.5f, 0f,
            -0.5f, 3f, -0.5f,
            0f, -0.5f, 0f
        )

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var r = 0f; var g = 0f; var b = 0f
                var ki = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val px = pixels[(y + dy) * w + (x + dx)]
                        val k = kernel[ki++]
                        r += Color.red(px) * k
                        g += Color.green(px) * k
                        b += Color.blue(px) * k
                    }
                }
                out[y * w + x] = Color.rgb(
                    r.roundToInt().coerceIn(0, 255),
                    g.roundToInt().coerceIn(0, 255),
                    b.roundToInt().coerceIn(0, 255)
                )
            }
        }

        out[0] = pixels[0]
        out[w - 1] = pixels[w - 1]
        out[(h - 1) * w] = pixels[(h - 1) * w]
        out[h * w - 1] = pixels[h * w - 1]
        for (x in 1 until w - 1) {
            out[x] = pixels[x]
            out[(h - 1) * w + x] = pixels[(h - 1) * w + x]
        }
        for (y in 1 until h - 1) {
            out[y * w] = pixels[y * w]
            out[y * w + w - 1] = pixels[y * w + w - 1]
        }

        bitmap.setPixels(out, 0, w, 0, 0, w, h)
        return bitmap
    }
}
