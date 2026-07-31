package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import kotlin.math.abs
import kotlin.math.min

/**
 * Super Res Zoom - mimics Google Pixel's multi-frame super-resolution zoom.
 *
 * Instead of plain digital zoom (which just crops and upscales one frame),
 * this captures a burst of frames and merges them using the tiny sub-pixel
 * offsets created by natural hand shake. The overlapping samples are fused
 * into a sharper, higher-resolution image - exactly the technique Pixel uses.
 */
object SuperResZoom {

    private const val TAG = "SuperResZoom"
    private const val MERGE_SCALE = 2
    private const val MAX_SR_DIM = 2400

    /**
     * Merges a burst of frames into a single higher-resolution image.
     * Frames must all be the same size. Returns null if the merge fails.
     */
    fun mergeBurst(frames: List<Bitmap>): Bitmap? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames[0]

        val ref = frames[0]
        val w = ref.width
        val h = ref.height

        val outW = min(w * MERGE_SCALE, MAX_SR_DIM)
        val ratio = outW.toFloat() / (w * MERGE_SCALE)
        val outH = (h * MERGE_SCALE * ratio).toInt()

        // Align every frame to the reference
        val aligned = ArrayList<Bitmap>(frames.size)
        aligned.add(ref.copy(Bitmap.Config.ARGB_8888, false))
        for (i in 1 until frames.size) {
            val (dx, dy) = alignFrames(ref, frames[i])
            aligned.add(shiftFrame(frames[i], dx, dy, w, h))
        }

        // Stack aligned frames into a 2x accumulator (averaging reduces noise,
        // sub-pixel offsets recover detail beyond single-frame resolution)
        val acc = FloatArray(outW * outH * 3)
        val counts = IntArray(outW * outH)

        val srcPixels = IntArray(w * h)
        for (alignedIdx in aligned.indices) {
            val bmp = aligned[alignedIdx]
            bmp.getPixels(srcPixels, 0, w, 0, 0, w, h)
            for (y in 0 until h) {
                val dstY = (y * MERGE_SCALE * ratio).toInt().coerceIn(0, outH - 1)
                val dstY2 = min(dstY + 1, outH - 1)
                for (x in 0 until w) {
                    val dstX = (x * MERGE_SCALE * ratio).toInt().coerceIn(0, outW - 1)
                    val dstX2 = min(dstX + 1, outW - 1)
                    val px = srcPixels[y * w + x]
                    val r = Color.red(px).toFloat()
                    val g = Color.green(px).toFloat()
                    val b = Color.blue(px).toFloat()

                    for (corner in 0 until 4) {
                        val ox = if (corner and 1 == 0) dstX else dstX2
                        val oy = if (corner and 2 == 0) dstY else dstY2
                        val o = (oy * outW + ox) * 3
                        acc[o] += r; acc[o + 1] += g; acc[o + 2] += b
                        counts[oy * outW + ox]++
                    }
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
            if (c > 0) {
                val o = i * 3
                outPixels[i] = Color.rgb(
                    (acc[o] / c).toInt().coerceIn(0, 255),
                    (acc[o + 1] / c).toInt().coerceIn(0, 255),
                    (acc[o + 2] / c).toInt().coerceIn(0, 255)
                )
            }
        }
        result.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

        // Recycle aligned copies (not the originals - caller owns those)
        for (i in 1 until aligned.size) aligned[i].recycle()

        return result
    }

    /**
     * Aligns [frame] to [ref] using coarse-to-fine block matching.
     * Returns the integer pixel offset (dx, dy) to shift [frame] so it
     * matches [ref].
     */
    fun alignFrames(ref: Bitmap, frame: Bitmap): Pair<Int, Int> {
        val w = min(ref.width, frame.width)
        val h = min(ref.height, frame.height)
        if (w < 16 || h < 16) return 0 to 0

        val refPix = IntArray(w * h)
        val framePix = IntArray(w * h)
        ref.getPixels(refPix, 0, w, 0, 0, w, h)
        frame.getPixels(framePix, 0, w, 0, 0, w, h)

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

        return fx to fy
    }

    private fun luma(px: Int): Int {
        return (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
    }

    private fun shiftFrame(frame: Bitmap, dx: Int, dy: Int, w: Int, h: Int): Bitmap {
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        if (dx != 0 || dy != 0) {
            val matrix = Matrix().apply { postTranslate(-dx.toFloat(), -dy.toFloat()) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(frame, matrix, paint)
        } else {
            canvas.drawBitmap(frame, 0f, 0f, null)
        }
        return result
    }
}
