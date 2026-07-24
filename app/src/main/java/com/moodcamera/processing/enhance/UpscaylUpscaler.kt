package com.moodcamera.processing.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object UpscaylUpscaler {

    private const val MAX_LONGEST = 4000

    fun init(context: Context) {}
    fun close() {}
    fun isReady(): Boolean = true

    fun upscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return bitmap

        val longest = maxOf(w, h)
        val scale = if (longest < MAX_LONGEST) {
            min(3f, MAX_LONGEST.toFloat() / longest)
        } else {
            1f
        }

        val result = if (scale > 1.05f) {
            val outW = (w * scale).roundToInt()
            val outH = (h * scale).roundToInt()
            val scaled = try {
                Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                return enhanceOnly(bitmap)
            }
            val c = Canvas(scaled)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isDither = true }
            c.drawBitmap(bitmap, null, Rect(0, 0, outW, outH), p)
            scaled
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap
        }

        enhanceInStrips(result)
        return result
    }

    private fun enhanceOnly(bitmap: Bitmap): Bitmap {
        val b = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap
        enhanceInStrips(b)
        return b
    }

    private fun enhanceInStrips(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return

        val stripH = 64
        var y = 1
        while (y < h - 1) {
            val yEnd = min(y + stripH, h - 1)
            val rows = yEnd - y
            val readH = rows + 2
            val topY = maxOf(y - 1, 0)
            val px = IntArray(w * readH)
            bitmap.getPixels(px, 0, w, 0, topY, w, readH)

            val out = IntArray(w * rows)

            for (row in 0 until rows) {
                val ry = row + 1
                out[row * w] = px[ry * w]
                out[row * w + w - 1] = px[ry * w + w - 1]

                for (x in 1 until w - 1) {
                    val idx = ry * w + x
                    val c = px[idx]
                    val l = px[idx - 1]
                    val r = px[idx + 1]
                    val u = px[idx - w]
                    val d = px[idx + w]

                    val cr = Color.red(c).toFloat()
                    val cg = Color.green(c).toFloat()
                    val cb = Color.blue(c).toFloat()

                    val edgeH = (abs(Color.red(r) - Color.red(l)) + abs(Color.green(r) - Color.green(l)) + abs(Color.blue(r) - Color.blue(l))).toFloat()
                    val edgeV = (abs(Color.red(u) - Color.red(d)) + abs(Color.green(u) - Color.green(d)) + abs(Color.blue(u) - Color.blue(d))).toFloat()
                    val edge = min(edgeH + edgeV, 255f) / 255f

                    val sharpenStrength = 0.15f + edge * 0.35f

                    val avgR = (Color.red(l) + Color.red(r) + Color.red(u) + Color.red(d)) / 4f
                    val avgG = (Color.green(l) + Color.green(r) + Color.green(u) + Color.green(d)) / 4f
                    val avgB = (Color.blue(l) + Color.blue(r) + Color.blue(u) + Color.blue(d)) / 4f

                    var sr = cr + (cr - avgR) * sharpenStrength
                    var sg = cg + (cg - avgG) * sharpenStrength
                    var sb = cb + (cb - avgB) * sharpenStrength

                    val lum = 0.299f * sr + 0.587f * sg + 0.114f * sb
                    val contrastFactor = 1.08f
                    sr = ((sr - 128f) * contrastFactor + 128f)
                    sg = ((sg - 128f) * contrastFactor + 128f)
                    sb = ((sb - 128f) * contrastFactor + 128f)

                    val satBoost = 1.1f
                    val gl = 0.299f * sr + 0.587f * sg + 0.114f * sb
                    sr = gl + satBoost * (sr - gl)
                    sg = gl + satBoost * (sg - gl)
                    sb = gl + satBoost * (sb - gl)

                    out[row * w + x] = Color.rgb(
                        sr.roundToInt().coerceIn(0, 255),
                        sg.roundToInt().coerceIn(0, 255),
                        sb.roundToInt().coerceIn(0, 255)
                    )
                }
            }

            bitmap.setPixels(out, 0, w, 0, y, w, rows)
            y = yEnd
        }
    }
}
