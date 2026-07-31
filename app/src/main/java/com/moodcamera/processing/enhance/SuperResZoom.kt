package com.moodcamera.processing.enhance

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Super Res Zoom - faithful implementation of the multi-frame super-resolution
 * merge used by Google's Pixel "Super Res Zoom" (Wronski et al., SIGGRAPH 2019,
 * "Handheld Multi-Frame Super-Resolution").
 *
 * Pipeline:
 *  1. The sharpest burst frame is chosen as the reference.
 *  2. LOCAL sub-pixel motion is estimated for every frame via coarse-to-fine
 *     block matching (quarter-scale search + full-res refine + parabolic
 *     sub-pixel fit). Unlike a single global translation, this handles the
 *     per-patch offsets produced by hand shake, parallax and rolling shutter.
 *  3. All frames are fused directly onto a higher-resolution grid with an
 *     ANISOTROPIC GAUSSIAN kernel steered by the local structure tensor: wide
 *     along edges and in flat areas (strong multi-frame noise averaging) and
 *     narrow across edges (high-frequency detail preserved). The reference is
 *     given a mild bias so the average stays crisp without losing the noise
 *     reduction that only multi-frame averaging can give.
 *  4. Robust weights gate every contribution: alignment confidence (patch
 *     match cost) and a radiance/deghosting weight (color similarity to the
 *     reference), so moving objects and misaligned regions do not ghost.
 */
object SuperResZoom {

    private const val TAG = "SuperResZoom"
    private const val MAX_INPUT_DIM = 1920
    private const val MAX_OUTPUT_DIM = 2560

    // Local motion estimation
    private const val PATCH = 32
    private const val PATCH_HALF = PATCH / 2
    private const val SEARCH_Q = 4            // quarter-px search radius (-> 16 source px)
    private const val SMALL_HALF = 4          // 8x8 window at quarter scale == 32px patch
    private const val REFINE_SEARCH = 2       // full-res integer refine radius
    private const val SAD_STRIDE = 2

    // Anisotropic merge kernel
    private const val KERNEL_HALF = 2
    private const val TAPS = (KERNEL_HALF * 2 + 1) * (KERNEL_HALF * 2 + 1)
    private const val NUM_ANGLE_BINS = 32
    private const val ANISO_LEVELS = 16
    private const val SIGMA_BASE = 0.7f
    private const val ALONG_BOOST = 0.8f
    private const val ACROSS_DROP = 0.55f
    private const val MIN_KERNEL = 0.02f
    // Mild bias toward the (sharpest) reference so the fused average stays
    // crisp, while all frames still average through the kernel for noise.
    private const val REFERENCE_WEIGHT = 1.3f

    // Robustness weights
    private const val COST_SIGMA = 5f
    private const val RADIANCE_SIGMA = 35f
    private const val MIN_CONF = 0.02f

    fun mergeBurst(frames: List<Bitmap>): Bitmap? {
        return try {
            mergeBurstInternal(frames)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during Super Res merge", e)
            frames[0]
        } catch (e: Exception) {
            Log.e(TAG, "Super Res merge failed: ${e.message}", e)
            frames[0]
        }
    }

    private fun mergeBurstInternal(frames: List<Bitmap>): Bitmap? {
        if (frames.isEmpty()) return null
        if (frames.size == 1) return frames[0]

        val ref = frames[0]
        val w = ref.width
        val h = ref.height
        if (w < 16 || h < 16) return frames[0]

        val scale = min(2.0, MAX_OUTPUT_DIM.toDouble() / maxOf(w, h)).toFloat()
        val outW = (w * scale).toInt()
        val outH = (h * scale).toInt()
        val invScale = 1f / scale

        val count = frames.size
        val allPix = ArrayList<IntArray>(count)
        for (f in frames) {
            val p = IntArray(w * h)
            f.getPixels(p, 0, w, 0, 0, w, h)
            allPix.add(p)
        }

        // Pick the sharpest frame as the reference.
        var refIdx = 0
        var bestSharp = -1f
        for (i in allPix.indices) {
            val s = sharpness(allPix[i], w, h)
            if (s > bestSharp) { bestSharp = s; refIdx = i }
        }
        val order = IntArray(count)
        var k = 0
        order[k++] = refIdx
        for (i in allPix.indices) if (i != refIdx) order[k++] = i

        val framePix = ArrayList<IntArray>(count)
        for (i in 0 until count) framePix.add(allPix[order[i]])
        val refPix = framePix[0]

        // Global sub-pixel alignment seeds the local patch search.
        val baseX = FloatArray(count)
        val baseY = FloatArray(count)
        for (i in 1 until count) {
            val (dx, dy) = alignSubPixel(refPix, framePix[i], w, h)
            baseX[i] = dx
            baseY[i] = dy
        }

        // Per-patch local motion + confidence.
        val cols = (w + PATCH - 1) / PATCH
        val rows = (h + PATCH - 1) / PATCH
        val motionX = ArrayList<FloatArray>(count - 1)
        val motionY = ArrayList<FloatArray>(count - 1)
        val motionCost = ArrayList<FloatArray>(count - 1)
        for (i in 1 until count) {
            val mx = FloatArray(cols * rows)
            val my = FloatArray(cols * rows)
            val mc = FloatArray(cols * rows)
            estimatePatchMotion(refPix, framePix[i], w, h, baseX[i], baseY[i], cols, rows, mx, my, mc)
            motionX.add(mx)
            motionY.add(my)
            motionCost.add(mc)
        }

        val frameConf = FloatArray(count)
        val gain = FloatArray(count)
        frameConf[0] = 1f
        gain[0] = 1f
        val refMean = meanLuma(refPix, w, h)
        for (i in 1 until count) {
            var s = 0f
            val c = motionCost[i - 1]
            for (v in c) s += v
            val mean = s / c.size
            val cr = mean / COST_SIGMA
            frameConf[i] = exp(-cr * cr)
            val fm = meanLuma(framePix[i], w, h)
            gain[i] = (if (fm > 1f) refMean / fm else 1f).coerceIn(0.85f, 1.18f)
        }

        // Structure tensor of the reference -> orientation + anisotropy maps.
        val angle = ByteArray(w * h)
        val aniso = ByteArray(w * h)
        structureTensor(refPix, w, h, angle, aniso)

        val kernelTable = buildKernelTables()
        val tapU = IntArray(TAPS)
        val tapV = IntArray(TAPS)
        var t = 0
        for (ty in -KERNEL_HALF..KERNEL_HALF) {
            for (tx in -KERNEL_HALF..KERNEL_HALF) {
                tapU[t] = tx
                tapV[t] = ty
                t++
            }
        }

        val outPixels = IntArray(outW * outH)
        val accR = FloatArray(outW * outH)
        val accG = FloatArray(outW * outH)
        val accB = FloatArray(outW * outH)
        val accW = FloatArray(outW * outH)
        val radNorm = 1f / (2f * RADIANCE_SIGMA * RADIANCE_SIGMA)
        val confNorm = 1f / (2f * COST_SIGMA * COST_SIGMA)

        try {
            for (Y in 0 until outH) {
                val py = Y * invScale
                if (Y % 512 == 0) Log.d(TAG, "merging row $Y/$outH")
                for (X in 0 until outW) {
                    val px = X * invScale
                    val xi = px.toInt().coerceIn(0, w - 1)
                    val yi = py.toInt().coerceIn(0, h - 1)
                    val bin = angle[yi * w + xi].toInt() and 0xFF
                    val aLev = aniso[yi * w + xi].toInt() and 0xFF
                    val kernBase = (bin * ANISO_LEVELS + aLev) * TAPS

                    val refC = sampleBilinear(refPix, w, h, px, py)
                    val rr = (refC ushr 16) and 0xFF
                    val rg = (refC ushr 8) and 0xFF
                    val rb = refC and 0xFF

                    var sR = 0f
                    var sG = 0f
                    var sB = 0f
                    var sWt = 0f

                    for (fi in 0 until count) {
                        val fp = framePix[fi]
                        var fx = px
                        var fy = py
                        var conf = 1f
                        if (fi > 0) {
                            val m = sampleMotion(motionX[fi - 1], motionY[fi - 1], motionCost[fi - 1], cols, rows, px, py)
                            fx += m.first
                            fy += m.second
                            val cr = m.third
                            conf = frameConf[fi] * exp(-cr * cr * confNorm)
                            if (conf < MIN_CONF) continue
                        }

                        var deghost = 1f
                        if (fi > 0) {
                            val fc = sampleBilinear(fp, w, h, fx, fy)
                            val dr = rr - ((fc ushr 16) and 0xFF)
                            val dg = rg - ((fc ushr 8) and 0xFF)
                            val db = rb - (fc and 0xFF)
                            deghost = exp(-(dr * dr + dg * dg + db * db) * radNorm)
                        }

                        val wMul = conf * deghost * (if (fi == 0) REFERENCE_WEIGHT else 1f)
                        if (wMul < MIN_CONF) continue

                        val gf = gain[fi]
                        for (tt in 0 until TAPS) {
                            val kw = kernelTable[kernBase + tt]
                            if (kw < MIN_KERNEL) continue
                            val tx = fx + tapU[tt] * invScale
                            val ty = fy + tapV[tt] * invScale
                            if (tx < 0f || ty < 0f || tx >= w || ty >= h) continue
                            val c = sampleBilinear(fp, w, h, tx, ty)
                            val wgt = kw * wMul
                            sR += ((c ushr 16) and 0xFF) * wgt * gf
                            sG += ((c ushr 8) and 0xFF) * wgt * gf
                            sB += (c and 0xFF) * wgt * gf
                            sWt += wgt
                        }
                    }

                    val oi = Y * outW + X
                    if (sWt > 0.001f) {
                        outPixels[oi] = Color.argb(
                            255,
                            (sR / sWt).roundToInt().coerceIn(0, 255),
                            (sG / sWt).roundToInt().coerceIn(0, 255),
                            (sB / sWt).roundToInt().coerceIn(0, 255)
                        )
                    } else {
                        outPixels[oi] = refC
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during merge", e)
            return frames[0]
        }

        val result = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM creating result", e)
            return frames[0]
        }
        result.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        Log.d(TAG, "merge done ${w}x$h -> $outW x $outH")
        return result
    }

    /**
     * Per-patch local motion via coarse-to-fine block matching.
     * Fills [mx]/[my] with the offset to ADD to a reference coordinate to get
     * the corresponding frame coordinate, and [cost] with the normalized patch
     * match error (confidence).
     */
    private fun estimatePatchMotion(
        refPix: IntArray, framePix: IntArray, w: Int, h: Int,
        baseX: Float, baseY: Float,
        cols: Int, rows: Int,
        mx: FloatArray, my: FloatArray, cost: FloatArray
    ) {
        val sw = w / 4
        val sh = h / 4
        val refS = IntArray(sw * sh)
        val frameS = IntArray(sw * sh)
        for (y in 0 until sh) {
            val ro = y * 4 * w
            val so = y * sw
            for (x in 0 until sw) {
                refS[so + x] = luma(refPix[ro + x * 4])
                frameS[so + x] = luma(framePix[ro + x * 4])
            }
        }

        for (r in 0 until rows) {
            val cy = r * PATCH + PATCH_HALF
            for (c in 0 until cols) {
                val cx = c * PATCH + PATCH_HALF
                val scx = cx shr 2
                val scy = cy shr 2

                var bestM = Float.MAX_VALUE
                var bestDx = 0
                var bestDy = 0
                var quarterUsed = false
                if (sw >= SMALL_HALF * 2 + 1 && sh >= SMALL_HALF * 2 + 1) {
                    quarterUsed = true
                    val bqx = scx + Math.round(baseX / 4f)
                    val bqy = scy + Math.round(baseY / 4f)
                    for (dy in -SEARCH_Q..SEARCH_Q) {
                        for (dx in -SEARCH_Q..SEARCH_Q) {
                            val sad = smallSad(refS, frameS, sw, sh, scx, scy, bqx + dx, bqy + dy)
                            if (sad < bestM) { bestM = sad; bestDx = bqx + dx; bestDy = bqy + dy }
                        }
                    }
                } else {
                    val bx = Math.round(baseX)
                    val by = Math.round(baseY)
                    for (dy in -REFINE_SEARCH * 4..REFINE_SEARCH * 4) {
                        for (dx in -REFINE_SEARCH * 4..REFINE_SEARCH * 4) {
                            val sad = patchSad(refPix, framePix, w, h, cx, cy, bx + dx, by + dy)
                            if (sad < bestM) { bestM = sad; bestDx = bx + dx; bestDy = by + dy }
                        }
                    }
                }

                // Convert search result to a full-res offset (frame = ref + m).
                val estX: Int
                val estY: Int
                if (quarterUsed) {
                    estX = (bestDx - scx) * 4
                    estY = (bestDy - scy) * 4
                } else {
                    estX = bestDx
                    estY = bestDy
                }

                // Full-res integer refine.
                var bestSad = Float.MAX_VALUE
                var bxr = estX
                var byr = estY
                for (dy in -REFINE_SEARCH..REFINE_SEARCH) {
                    for (dx in -REFINE_SEARCH..REFINE_SEARCH) {
                        val sad = patchSad(refPix, framePix, w, h, cx, cy, estX + dx, estY + dy)
                        if (sad < bestSad) { bestSad = sad; bxr = estX + dx; byr = estY + dy }
                    }
                }

                // Sub-pixel parabolic refinement on x.
                val sxm = patchSad(refPix, framePix, w, h, cx, cy, bxr - 1, byr)
                val sxp = patchSad(refPix, framePix, w, h, cx, cy, bxr + 1, byr)
                var subX = 0f
                val denomX = sxm - 2f * bestSad + sxp
                if (abs(denomX) > 0.001f) {
                    subX = (0.5f * (sxm - sxp) / denomX).coerceIn(-0.75f, 0.75f)
                }

                // Sub-pixel parabolic refinement on y.
                val sym = patchSad(refPix, framePix, w, h, cx, cy, bxr, byr - 1)
                val syp = patchSad(refPix, framePix, w, h, cx, cy, bxr, byr + 1)
                var subY = 0f
                val denomY = sym - 2f * bestSad + syp
                if (abs(denomY) > 0.001f) {
                    subY = (0.5f * (sym - syp) / denomY).coerceIn(-0.75f, 0.75f)
                }

                mx[r * cols + c] = bxr + subX
                my[r * cols + c] = byr + subY
                cost[r * cols + c] = bestSad
            }
        }
    }

    /**
     * Structure tensor of the reference luminance.
     * Fills [angle] with the gradient orientation bin (0..NUM_ANGLE_BINS-1)
     * and [aniso] with the anisotropy level (0..ANISO_LEVELS-1).
     */
    private fun structureTensor(refPix: IntArray, w: Int, h: Int, angle: ByteArray, aniso: ByteArray) {
        val n = w * h

        // 3x3 box-blurred luma. Gradient + tensor are computed from the blurred
        // luma, which gives stable orientation without storing any tensor buffers.
        val blurred = IntArray(n)
        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                val idx = ro + x
                var s = 0
                s += luma(refPix[idx - w - 1]); s += luma(refPix[idx - w]); s += luma(refPix[idx - w + 1])
                s += luma(refPix[idx - 1]); s += luma(refPix[idx]); s += luma(refPix[idx + 1])
                s += luma(refPix[idx + w - 1]); s += luma(refPix[idx + w]); s += luma(refPix[idx + w + 1])
                blurred[idx] = s / 9
            }
        }

        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                val idx = ro + x
                val tl = blurred[idx - w - 1].toFloat()
                val tc = blurred[idx - w].toFloat()
                val tr = blurred[idx - w + 1].toFloat()
                val ml = blurred[idx - 1].toFloat()
                val mr = blurred[idx + 1].toFloat()
                val bl = blurred[idx + w - 1].toFloat()
                val bc = blurred[idx + w].toFloat()
                val br = blurred[idx + w + 1].toFloat()
                val gx = (tr + 2f * mr + br) - (tl + 2f * ml + bl)
                val gy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)
                val xx = gx * gx
                val yy = gy * gy
                val xy = gx * gy
                val trace = xx + yy
                if (trace < 0.01f) {
                    angle[idx] = 0
                    aniso[idx] = 0
                    continue
                }
                val det = sqrt(0.25f * (xx - yy) * (xx - yy) + xy * xy)
                val l1 = 0.5f * trace + det
                val l2 = 0.5f * trace - det
                var theta = 0.5f * atan2(2f * xy, xx - yy)
                if (theta < 0f) theta += PI.toFloat()
                val bin = (theta / PI.toFloat() * NUM_ANGLE_BINS).toInt() % NUM_ANGLE_BINS
                val a = (l1 - l2) / (l1 + l2)
                angle[idx] = bin.toByte()
                aniso[idx] = (a * (ANISO_LEVELS - 1)).roundToInt().coerceIn(0, ANISO_LEVELS - 1).toByte()
            }
        }
    }

    /**
     * Precomputed anisotropic Gaussian kernel weights.
     * Table index: ((angleBin * ANISO_LEVELS) + anisoLevel) * TAPS + tap.
     * Tap (du, dv) in output pixels is rotated by the edge orientation and
     * scaled by sigma_along (along edge, denoising) vs sigma_across (across
     * edge, detail). theta points along the gradient (across the edge).
     */
    private fun buildKernelTables(): FloatArray {
        val table = FloatArray(NUM_ANGLE_BINS * ANISO_LEVELS * TAPS)
        for (b in 0 until NUM_ANGLE_BINS) {
            val theta = (b + 0.5f) * (PI.toFloat() / NUM_ANGLE_BINS)
            val cosT = cos(theta)
            val sinT = sin(theta)
            for (a in 0 until ANISO_LEVELS) {
                val an = a / (ANISO_LEVELS - 1f)
                val sAlong = SIGMA_BASE * (1f + ALONG_BOOST * an)
                val sAcross = SIGMA_BASE * (1f - ACROSS_DROP * an)
                val sAlong2 = 2f * sAlong * sAlong
                val sAcross2 = 2f * sAcross * sAcross
                var t = 0
                for (ty in -KERNEL_HALF..KERNEL_HALF) {
                    for (tx in -KERNEL_HALF..KERNEL_HALF) {
                        // theta points along the gradient (across the edge), so u
                        // is across the edge (narrow sigma) and v along it (wide).
                        val u = tx * cosT + ty * sinT
                        val v = -tx * sinT + ty * cosT
                        val w = exp(-(u * u / sAcross2 + v * v / sAlong2))
                        table[((b * ANISO_LEVELS) + a) * TAPS + t] = w
                        t++
                    }
                }
            }
        }
        return table
    }

    /**
     * Bilinear interpolation of [mx]/[my]/[cost] patch grids at (x, y).
     * Returns (motionX, motionY, confidenceCost).
     */
    private fun sampleMotion(
        mx: FloatArray, my: FloatArray, cost: FloatArray,
        cols: Int, rows: Int, x: Float, y: Float
    ): Triple<Float, Float, Float> {
        val gx = (x - PATCH_HALF) / PATCH
        val gy = (y - PATCH_HALF) / PATCH
        var j0 = floor(gx).toInt()
        var i0 = floor(gy).toInt()
        var fx = gx - j0
        var fy = gy - i0
        i0 = i0.coerceIn(0, rows - 1)
        j0 = j0.coerceIn(0, cols - 1)
        fx = fx.coerceIn(0f, 1f)
        fy = fy.coerceIn(0f, 1f)
        val i1 = min(i0 + 1, rows - 1)
        val j1 = min(j0 + 1, cols - 1)
        val w00 = (1f - fx) * (1f - fy)
        val w01 = (1f - fx) * fy
        val w10 = fx * (1f - fy)
        val w11 = fx * fy
        val i00 = i0 * cols + j0
        val i01 = i1 * cols + j0
        val i10 = i0 * cols + j1
        val i11 = i1 * cols + j1
        return Triple(
            mx[i00] * w00 + mx[i01] * w01 + mx[i10] * w10 + mx[i11] * w11,
            my[i00] * w00 + my[i01] * w01 + my[i10] * w10 + my[i11] * w11,
            cost[i00] * w00 + cost[i01] * w01 + cost[i10] * w10 + cost[i11] * w11
        )
    }

    /** Bilinear sample of a pixel array at (x, y), clamped to bounds. */
    private fun sampleBilinear(pix: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val xx = x.coerceIn(0f, w - 0.001f)
        val yy = y.coerceIn(0f, h - 0.001f)
        val x0 = floor(xx).toInt()
        val y0 = floor(yy).toInt()
        val fx = xx - x0
        val fy = yy - y0
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val p00 = pix[y0 * w + x0]
        val p10 = pix[y0 * w + x1]
        val p01 = pix[y1 * w + x0]
        val p11 = pix[y1 * w + x1]
        val w00 = (1f - fx) * (1f - fy)
        val w10 = fx * (1f - fy)
        val w01 = (1f - fx) * fy
        val w11 = fx * fy
        val r = (((p00 ushr 16) and 0xFF) * w00 + ((p10 ushr 16) and 0xFF) * w10 +
            ((p01 ushr 16) and 0xFF) * w01 + ((p11 ushr 16) and 0xFF) * w11).roundToInt().coerceIn(0, 255)
        val g = (((p00 ushr 8) and 0xFF) * w00 + ((p10 ushr 8) and 0xFF) * w10 +
            ((p01 ushr 8) and 0xFF) * w01 + ((p11 ushr 8) and 0xFF) * w11).roundToInt().coerceIn(0, 255)
        val b = ((p00 and 0xFF) * w00 + (p10 and 0xFF) * w10 +
            (p01 and 0xFF) * w01 + (p11 and 0xFF) * w11).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * SAD of the ref patch centered at (cx, cy) vs the frame patch shifted by
     * (dx, dy) at quarter scale. Returns the mean absolute luma difference.
     */
    private fun smallSad(
        refS: IntArray, frameS: IntArray, sw: Int, sh: Int,
        cx: Int, cy: Int, dx: Int, dy: Int
    ): Float {
        var sum = 0
        var n = 0
        for (y in (cy - SMALL_HALF)..(cy + SMALL_HALF)) {
            val sy = y + dy
            if (sy < 0 || sy >= sh) continue
            val ro = y * sw
            val so = sy * sw
            for (x in (cx - SMALL_HALF)..(cx + SMALL_HALF)) {
                val sx = x + dx
                if (sx < 0 || sx >= sw) continue
                sum += abs(refS[ro + x] - frameS[so + sx])
                n++
            }
        }
        return if (n > 0) sum.toFloat() / n else Float.MAX_VALUE
    }

    /**
     * SAD of the ref patch centered at (cx, cy) vs the frame patch shifted by
     * (dx, dy) at full resolution (sampled with stride [SAD_STRIDE]).
     */
    private fun patchSad(
        ref: IntArray, frame: IntArray, w: Int, h: Int,
        cx: Int, cy: Int, dx: Int, dy: Int
    ): Float {
        var sum = 0
        var n = 0
        val x0 = cx - PATCH_HALF
        val y0 = cy - PATCH_HALF
        for (y in y0 until y0 + PATCH step SAD_STRIDE) {
            val sy = y + dy
            if (sy < 0 || sy >= h) continue
            val ro = y * w
            val so = sy * w
            for (x in x0 until x0 + PATCH step SAD_STRIDE) {
                val sx = x + dx
                if (sx < 0 || sx >= w) continue
                sum += abs(luma(ref[ro + x]) - luma(frame[so + sx]))
                n++
            }
        }
        return if (n > 0) sum.toFloat() / n else Float.MAX_VALUE
    }

    /** Coarse-to-fine sub-pixel alignment of [framePix] to [refPix]. */
    private fun alignSubPixel(refPix: IntArray, framePix: IntArray, w: Int, h: Int): Pair<Float, Float> {
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

    /**
     * Noise-robust sharpness - used to pick the reference frame. Gradients are
     * measured on a 3x3 box-blurred luminance so sensor noise (which looks like
     * detail to a raw gradient metric) does not win the selection.
     */
    private fun sharpness(pix: IntArray, w: Int, h: Int): Float {
        val n = w * h
        val lumaArr = IntArray(n)
        for (i in 0 until n) lumaArr[i] = luma(pix[i])
        val blurred = IntArray(n)
        for (y in 1 until h - 1) {
            val ro = y * w
            for (x in 1 until w - 1) {
                val idx = ro + x
                blurred[idx] = (lumaArr[idx - w - 1] + lumaArr[idx - w] + lumaArr[idx - w + 1] +
                    lumaArr[idx - 1] + lumaArr[idx] + lumaArr[idx + 1] +
                    lumaArr[idx + w - 1] + lumaArr[idx + w] + lumaArr[idx + w + 1]) / 9
            }
        }
        var sum = 0f
        var count = 0
        for (y in 1 until h - 1 step 4) {
            val ro = y * w
            for (x in 1 until w - 1 step 4) {
                val idx = ro + x
                val tl = blurred[idx - w - 1].toFloat()
                val tc = blurred[idx - w].toFloat()
                val tr = blurred[idx - w + 1].toFloat()
                val ml = blurred[idx - 1].toFloat()
                val mr = blurred[idx + 1].toFloat()
                val bl = blurred[idx + w - 1].toFloat()
                val bc = blurred[idx + w].toFloat()
                val br = blurred[idx + w + 1].toFloat()
                val gx = (tr + 2f * mr + br) - (tl + 2f * ml + bl)
                val gy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)
                sum += abs(gx) + abs(gy)
                count++
            }
        }
        return if (count > 0) sum / count else 0f
    }

    private fun meanLuma(pix: IntArray, w: Int, h: Int): Float {
        var sum = 0L
        var n = 0
        for (y in 0 until h step 4) {
            val ro = y * w
            for (x in 0 until w step 4) {
                sum += luma(pix[ro + x])
                n++
            }
        }
        return if (n > 0) sum.toFloat() / n else 1f
    }

    private fun luma(px: Int): Int {
        return (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
    }
}
