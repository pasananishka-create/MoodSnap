package com.moodcamera.processing.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

object UpscaylUpscaler {

    private const val SCALE = 2

    fun init(context: Context) {}

    fun close() {}

    fun isReady(): Boolean = true

    fun upscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return bitmap

        val outW = w * SCALE
        val outH = h * SCALE

        val upscaled = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return bitmap
        }

        val canvas = Canvas(upscaled)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, outW, outH), paint)

        return sharpen(upscaled)
    }

    private fun sharpen(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return bitmap

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(pixels.size)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = pixels[idx]
                val l = pixels[idx - 1]
                val r = pixels[idx + 1]
                val u = pixels[idx - w]
                val d = pixels[idx + w]

                val cr = Color.red(c).toFloat()
                val cg = Color.green(c).toFloat()
                val cb = Color.blue(c).toFloat()

                val lr = Color.red(l).toFloat()
                val lg = Color.green(l).toFloat()
                val lb = Color.blue(l).toFloat()

                val rr = Color.red(r).toFloat()
                val rg = Color.green(r).toFloat()
                val rb = Color.blue(r).toFloat()

                val ur = Color.red(u).toFloat()
                val ug = Color.green(u).toFloat()
                val ub = Color.blue(u).toFloat()

                val dr = Color.red(d).toFloat()
                val dg = Color.green(d).toFloat()
                val db = Color.blue(d).toFloat()

                val nr = (cr * 2f - (lr + rr + ur + dr) * 0.25f).roundToInt().coerceIn(0, 255)
                val ng = (cg * 2f - (lg + rg + ug + dg) * 0.25f).roundToInt().coerceIn(0, 255)
                val nb = (cb * 2f - (lb + rb + ub + db) * 0.25f).roundToInt().coerceIn(0, 255)

                out[idx] = Color.rgb(nr, ng, nb)
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
