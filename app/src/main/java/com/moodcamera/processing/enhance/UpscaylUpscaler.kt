package com.moodcamera.processing.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.min
import kotlin.math.roundToInt

object UpscaylUpscaler {

    private const val MAX_OUTPUT_DIM = 4000

    fun init(context: Context) {}

    fun close() {}

    fun isReady(): Boolean = true

    fun upscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return bitmap

        val longest = maxOf(w, h)
        val scale = if (longest < MAX_OUTPUT_DIM) {
            min(2f, MAX_OUTPUT_DIM.toFloat() / longest)
        } else {
            1f
        }

        if (scale <= 1.01f) {
            return sharpenInStrips(bitmap)
        }

        val outW = (w * scale).roundToInt()
        val outH = (h * scale).roundToInt()

        val upscaled = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return sharpenInStrips(bitmap)
        }

        val canvas = Canvas(upscaled)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(bitmap, null, Rect(0, 0, outW, outH), paint)

        return sharpenInStrips(upscaled)
    }

    private fun sharpenInStrips(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return bitmap

        val stripH = 64
        val srcPixels = IntArray(w * stripH)
        val outPixels = IntArray(w * stripH)

        var y = 1
        while (y < h - 1) {
            val yEnd = min(y + stripH, h - 1)
            val rows = yEnd - y

            val topY = maxOf(y - 1, 0)
            val readH = rows + 2
            val readPixels = IntArray(w * readH)
            bitmap.getPixels(readPixels, 0, w, 0, topY, w, readH)

            for (row in 0 until rows) {
                val ry = row + 1
                outPixels[row * w] = readPixels[ry * w]
                outPixels[row * w + w - 1] = readPixels[ry * w + w - 1]

                for (x in 1 until w - 1) {
                    val idx = ry * w + x
                    val c = readPixels[idx]
                    val l = readPixels[idx - 1]
                    val r = readPixels[idx + 1]
                    val u = readPixels[idx - w]
                    val d = readPixels[idx + w]

                    val cr = Color.red(c)
                    val cg = Color.green(c)
                    val cb = Color.blue(c)

                    val nr = ((cr * 2f - (Color.red(l) + Color.red(r) + Color.red(u) + Color.red(d)) * 0.25f).roundToInt()).coerceIn(0, 255)
                    val ng = ((cg * 2f - (Color.green(l) + Color.green(r) + Color.green(u) + Color.green(d)) * 0.25f).roundToInt()).coerceIn(0, 255)
                    val nb = ((cb * 2f - (Color.blue(l) + Color.blue(r) + Color.blue(u) + Color.blue(d)) * 0.25f).roundToInt()).coerceIn(0, 255)

                    outPixels[row * w + x] = Color.rgb(nr, ng, nb)
                }
            }

            bitmap.setPixels(outPixels, 0, w, 0, y, w, rows)
            y = yEnd
        }

        return bitmap
    }
}
