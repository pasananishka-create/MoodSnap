package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Super Res Zoom - mimics Google Pixel's multi-frame super-resolution zoom.
 *
 * Real super-resolution: each burst frame is sampled at slightly different
 * sub-pixel positions (from natural hand shake). Frames are aligned to
 * sub-pixel precision and splatted onto a finer grid at their ACTUAL
 * fractional positions, recovering detail beyond single-frame resolution.
 */
object SuperResZoom {

    private const val TAG = "SuperResZoom"
    private const val MAX_INPUT_DIM = 1440
    private const val MAX_OUTPUT_DIM = 2880

    /**
     * Merges a burst of frames into a single higher-resolution image.
     * Frames must all be the same size. Returns null on failure.
     */
    fun mergeBurst(frames: List<Bitmap>): Bitmap? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames[0]

        val ref = frames[0]
        val w = ref.width
        val h = ref.height
        if (w < 16 || h < 16) return frames[0]

        val scale = min(2.0, MAX_OUTPUT_DIM.toDouble() / maxOf(w, h))
        val outW = (w * scale).toInt()
        val outH = (h * scale).toInt()

        // Precompute pixel buffers + sub-pixel offsets for every frame
        val refPix = IntArray(w * h)
        ref.getPixels(refPix, 0, w, 0, 0, w, h)

        val framePix = ArrayList<IntArray>(frames.size)
        val offsetsX = FloatArray(frames.size)
        val offsetsY = FloatArray(frames.size)
        framePix.add(refPix)
        for (i in 1 until frames.size) {
            val fp = IntArray(w * h)
            frames[i].getPixels(fp, 0, w, 0, 0, w, h)
            val (dx, dy) = alignSubPixel(refPix, fp, w, h)
            offsetsX[i] = dx
            offsetsY[i] = dy
            framePix.add(fp)
        }

        // Accumulators (RGB summed + count) at output resolution
        val acc = FloatArray(outW * outH * 3)
        val counts = FloatArray(outW * outH)

        for (fi in frames.indices) {
            val fp = framePix[fi]
            val dx = offsetsX[fi]
            val dy = offsetsY[fi]

            for (y in 0 until h) {
                val gy = (y + dy) * scale.toFloat()
                val y0 = floor(gy).toInt()
                val fy = (gy - y0).coerceIn(0f, 1f)
                if (y0 < 0 || y0 >= outH) continue

                for (x in 0 until w) {
                    val px = fp[y * w + x]
                    val r = Color.red(px).toFloat()
                    val g = Color.green(px).toFloat()
                    val b = Color.blue(px).toFloat()

                    val gx = (x + dx) * scale.toFloat()
                    val x0 = floor(gx).toInt()
                    val fx = (gx - x0).coerceIn(0f, 1f)
                    if (x0 < 0 || x0 >= outW) continue

                    val x1 = min(x0 + 1, outW - 1)
                    val y1 = min(y0 + 1, outH - 1)

                    val wx00 = (1f - fx) * (1f - fy)
                    val wx10 = fx * (1f - fy)
                    val wx01 = (1f - fx) * fy
                    val wx11 = fx * fy

                    var o = (y0 * outW + x0) * 3
                    acc[o] += r * wx00; acc[o + 1] += g * wx00; acc[o + 2] += b * wx00
                    counts[y0 * outW + x0] += wx00

                    o = (y0 * outW + x1) * 3
                    acc[o] += r * wx10; acc[o + 1] += g * wx10; acc[o + 2] += b * wx10
                    counts[y0 * outW + x1] += wx10

                    o = (y1 * outW + x0) * 3
                    acc[o] += r * wx01; acc[o + 1] += g * wx01; acc[o + 2] += b * wx01
                    counts[y1 * outW + x0] += wx01

                    o = (y1 * outW + x1) * 3
                    acc[o] += r * wx11; acc[o + 1] += g * wx11; acc[o + 2] += b * wx11
                    counts[y1 * outW + x1] += wx11
                }
            }
        }

        val result = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM creating result", e)
            return frames[0]
        }

        val outPixels = IntArray(outW * outH)
        for (i in outPixels.indices) {
            val c = counts[i]
            if (c > 0.001f) {
                val o = i * 3
                outPixels[i] = Color.rgb(
                    (acc[o] / c).toInt().coerceIn(0, 255),
                    (acc[o + 1] / c).toInt().coerceIn(0, 255),
                    (acc[o + 2] / c).toInt().coerceIn(0, 255)
                )
            }
        }
        result.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

        return result
    }

    /**
     * Coarse-to-fine sub-pixel alignment of [framePix] to [refPix].
     * Returns the fractional pixel offset (dx, dy) to add to frame pixels
     * so they line up with the reference.
     */
    fun alignSubPixel(refPix: IntArray, framePix: IntArray, w: Int, h: Int): Pair<Float, Float> {
        // Coarse search at 1/4 scale
        val cw = w / 4
        val ch = h / 4
        val refSmall = IntArray(cw * ch)
        val frameSmall = IntArray(cw * ch)
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                refSmall[y * cw + x] = luma(refPix[(y * 4) * w + (x * 4)])
                frameSmall[y * cw + x] = luma(framePix[(y * 4) * w + (x * 4)])
            }
        }

        var bestDx = 0
        var bestDy = 0
        var bestScore = Int.MAX_VALUE
        val search = 8
        val margin = 24
        for (dy in -search..search) {
            for (dx in -search..search) {
                var score = 0
                var counted = 0
                for (y in margin until ch - margin) {
                    val ry = (y + dy).coerceIn(0, ch - 1)
                    for (x in margin until cw - margin) {
                        val rx = (x + dx).coerceIn(0, cw - 1)
                        score += abs(refSmall[y * cw + x] - frameSmall[ry * cw + rx])
                        counted++
                    }
                }
                if (counted > 0 && score < bestScore) {
                    bestScore = score
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        // Refine at full resolution around coarse estimate
        var fx = bestDx * 4
        var fy = bestDy * 4
        var bestFull = Int.MAX_VALUE
        val fullSearch = 2
        val marginFull = 96
        for (dy in -fullSearch..fullSearch) {
            for (dx in -fullSearch..fullSearch) {
                var score = 0
                var counted = 0
                for (y in marginFull until h - marginFull step 2) {
                    val ry = (y + fy + dy).coerceIn(0, h - 1)
                    for (x in marginFull until w - marginFull step 2) {
                        val rx = (x + fx + dx).coerceIn(0, w - 1)
                        score += abs(luma(refPix[y * w + x]) - luma(framePix[ry * w + rx]))
                        counted++
                    }
                }
                if (counted > 0 && score < bestFull) {
                    bestFull = score
                    fx = bestDx * 4 + dx
                    fy = bestDy * 4 + dy
                }
            }
        }

        // Sub-pixel refinement: parabolic fit on the SAD curve around best x
        val sadXm1 = sadAt(refPix, framePix, w, h, fx - 1, fy, 128)
        val sadX0 = sadAt(refPix, framePix, w, h, fx, fy, 128)
        val sadXp1 = sadAt(refPix, framePix, w, h, fx + 1, fy, 128)
        var subX = 0f
        val denomX = sadXm1 - 2f * sadX0 + sadXp1
        if (abs(denomX) > 0.001f) {
            subX = 0.5f * (sadXm1 - sadXp1) / denomX
            subX = subX.coerceIn(-0.75f, 0.75f)
        }

        val sadYm1 = sadAt(refPix, framePix, w, h, fx, fy - 1, 128)
        val sadY0 = sadAt(refPix, framePix, w, h, fx, fy, 128)
        val sadYp1 = sadAt(refPix, framePix, w, h, fx, fy + 1, 128)
        var subY = 0f
        val denomY = sadYm1 - 2f * sadY0 + sadYp1
        if (abs(denomY) > 0.001f) {
            subY = 0.5f * (sadYm1 - sadYp1) / denomY
            subY = subY.coerceIn(-0.75f, 0.75f)
        }

        return (fx + subX) to (fy + subY)
    }

    private fun sadAt(ref: IntArray, frame: IntArray, w: Int, h: Int, dx: Int, dy: Int, step: Int): Float {
        var score = 0f
        var counted = 0
        val margin = 96
        for (y in margin until h - margin step step) {
            val ry = (y + dy).coerceIn(0, h - 1)
            for (x in margin until w - margin step step) {
                val rx = (x + dx).coerceIn(0, w - 1)
                score += abs(luma(ref[y * w + x]) - luma(frame[ry * w + rx]))
                counted++
            }
        }
        return if (counted > 0) score / counted else 0f
    }

    private fun luma(px: Int): Int {
        return (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
    }
}
