package com.moodcamera.processing.enhance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.min
import kotlin.math.roundToInt

object UpscaylUpscaler {

    private const val MODEL_URL = "https://huggingface.co/AXERA-TECH/Real-ESRGAN/resolve/main/onnx/realesrgan-x4.onnx"
    private const val MODEL_FILENAME = "realesrgan-x4.onnx"
    private const val SCALE = 4
    // This ONNX export is fixed-shape: input must be exactly 64x64 (-> 256x256).
    private const val TILE_SIZE = 64
    private const val MAX_INPUT_DIM = 512

    private var session: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var initFailed = false
    private var initStarted = false
    private var lastError: String? = null

    val isDownloading: Boolean get() = initStarted && session == null && !initFailed

    fun needsDownload(context: Context): Boolean = !File(context.filesDir, MODEL_FILENAME).exists()

    fun getLastError(): String? = lastError

    suspend fun init(context: Context, onProgress: (Long) -> Unit = {}) {
        if (session != null || initFailed || initStarted) return
        initStarted = true
        try {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                Log.i("UpscaylUpscaler", "Downloading AI model (~64MB)...")
                withContext(Dispatchers.IO) {
                    URL(MODEL_URL).openStream().use { input ->
                        modelFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var downloaded = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded)
                            }
                        }
                    }
                }
                Log.i("UpscaylUpscaler", "Model downloaded: ${modelFile.length()} bytes")
            }

            val modelBytes = withContext(Dispatchers.IO) { modelFile.readBytes() }
            ortEnv = OrtEnvironment.getEnvironment()

            // Prefer the NNAPI delegate, but many device NNAPI drivers cannot
            // run this model, so verify with a real inference and fall back to
            // CPU if the driver errors or produces a degenerate result.
            session = try {
                val nnapiOptions = OrtSession.SessionOptions().apply {
                    try { addNnapi() } catch (_: Exception) {}
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val s = ortEnv!!.createSession(modelBytes, nnapiOptions)
                try {
                    sanityCheckSession(s, ortEnv!!)
                } catch (e: Throwable) {
                    try { s.close() } catch (_: Exception) {}
                    throw e
                }
                Log.i("UpscaylUpscaler", "AI model loaded with NNAPI acceleration")
                s
            } catch (e: Throwable) {
                Log.w("UpscaylUpscaler", "NNAPI session failed (${e.message}); retrying on CPU")
                val cpuOptions = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    try {
                        setIntraOpNumThreads(maxOf(2, Runtime.getRuntime().availableProcessors() - 1))
                    } catch (_: Exception) {}
                }
                ortEnv!!.createSession(modelBytes, cpuOptions).also {
                    Log.i("UpscaylUpscaler", "AI model loaded on CPU (${it.inputNames})")
                }
            }
            lastError = null
        } catch (e: Throwable) {
            Log.e("UpscaylUpscaler", "Init failed: ${e.message}", e)
            lastError = e.message
            session = null
            initFailed = true
        }
    }

    private fun sanityCheckSession(sess: OrtSession, env: OrtEnvironment) {
        val n = TILE_SIZE * TILE_SIZE
        val floats = FloatArray(3 * n) { 0.5f }
        val shape = longArrayOf(1, 3, TILE_SIZE.toLong(), TILE_SIZE.toLong())
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floats), shape)
        try {
            val results = sess.run(Collections.singletonMap(sess.inputNames.first(), tensor))
            try {
                @Suppress("UNCHECKED_CAST")
                val output = results[0].value as Array<Array<Array<FloatArray>>>
                val outH = output[0][0].size
                val outW = output[0][0][0].size
                if (outH != TILE_SIZE * SCALE || outW != TILE_SIZE * SCALE) {
                    throw IllegalStateException("Unexpected output shape ${outW}x$outH")
                }
                val c = output[0][0][0][0]
                if (!c.isFinite()) throw IllegalStateException("Non-finite output")
            } finally {
                results.close()
            }
        } finally {
            tensor.close()
        }
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        try { ortEnv?.close() } catch (_: Exception) {}
        session = null
        ortEnv = null
        initStarted = false
        initFailed = false
        lastError = null
    }

    fun isReady(): Boolean = session != null

    suspend fun upscale(bitmap: Bitmap, maxInputDim: Int = MAX_INPUT_DIM, onProgress: (Int, Int) -> Unit = { _, _ -> }): Bitmap {
        val sess = session
        val env = ortEnv
        if (sess == null || env == null) return withContext(Dispatchers.IO) { algorithmicUpscale(bitmap) }

        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return bitmap

        val src = if (w > maxInputDim || h > maxInputDim) {
            val ratio = maxInputDim.toFloat() / maxOf(w, h)
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

        val result = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            if (src !== bitmap) src.recycle()
            return withContext(Dispatchers.IO) { algorithmicUpscale(bitmap) }
        }

        val resultCanvas = Canvas(result)
        val tileOverlap = 4
        val step = TILE_SIZE - tileOverlap * 2

        var totalTiles = 0
        var ty0 = 0
        while (ty0 < sh) {
            var tx0 = 0
            while (tx0 < sw) {
                if (min(TILE_SIZE, sw - tx0) >= 8 && min(TILE_SIZE, sh - ty0) >= 8) totalTiles++
                tx0 += step
            }
            ty0 += step
        }
        var doneTiles = 0

        withContext(Dispatchers.IO) {
            var ty = 0
            while (ty < sh) {
                var tx = 0
                while (tx < sw) {
                    val tileW = min(TILE_SIZE, sw - tx)
                    val tileH = min(TILE_SIZE, sh - ty)
                    if (tileW < 8 || tileH < 8) { tx += step; continue }

                    try {
                        val tileBitmap = Bitmap.createBitmap(src, tx, ty, tileW, tileH)
                        val upscaled = runInference(sess, env, tileBitmap)
                        tileBitmap.recycle()

                        if (upscaled != null) {
                            val dstX = tx * SCALE
                            val dstY = ty * SCALE
                            val oX = tileOverlap * SCALE
                            val oY = tileOverlap * SCALE
                            // Drop the overlap margin on interior edges so tiles
                            // stitch seamlessly; on the last column/row extend to
                            // the image edge (padded pixels replicate the border).
                            val isLastCol = tx + TILE_SIZE >= sw
                            val isLastRow = ty + TILE_SIZE >= sh
                            val sx0 = if (isLastCol) 0 else oX
                            val sy0 = if (isLastRow) 0 else oY
                            val swd = if (isLastCol) tileW * SCALE - sx0 else tileW * SCALE - 2 * oX
                            val shd = if (isLastRow) tileH * SCALE - sy0 else tileH * SCALE - 2 * oY
                            if (swd > 0 && shd > 0) {
                                val srcRect = Rect(sx0, sy0, sx0 + swd, sy0 + shd)
                                val dstRect = Rect(dstX + sx0, dstY + sy0, dstX + sx0 + swd, dstY + sy0 + shd)
                                resultCanvas.drawBitmap(upscaled, srcRect, dstRect, null)
                            }
                            upscaled.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e("UpscaylUpscaler", "Tile error: ${e.message}")
                    }
                    doneTiles++
                    onProgress(doneTiles, totalTiles)
                    tx += step
                }
                ty += step
            }
        }

        if (src !== bitmap) src.recycle()
        return result
    }

    private fun runInference(sess: OrtSession, env: OrtEnvironment, tile: Bitmap): Bitmap? {
        return try {
            val w = tile.width
            val h = tile.height
            val pixels = IntArray(w * h)
            tile.getPixels(pixels, 0, w, 0, 0, w, h)

            // The model input is fixed at TILE_SIZE x TILE_SIZE, so smaller edge
            // tiles are padded by replicating the border pixels.
            val pw = TILE_SIZE
            val ph = TILE_SIZE
            val floatArray = FloatArray(3 * ph * pw)
            for (y in 0 until ph) {
                val sy = if (y < h) y else h - 1
                for (x in 0 until pw) {
                    val sx = if (x < w) x else w - 1
                    val px = pixels[sy * w + sx]
                    val i = y * pw + x
                    floatArray[i] = Color.red(px) / 255f
                    floatArray[pw * ph + i] = Color.green(px) / 255f
                    floatArray[2 * pw * ph + i] = Color.blue(px) / 255f
                }
            }

            val shape = longArrayOf(1, 3, ph.toLong(), pw.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
            val inputName = sess.inputNames.first()
            val results = sess.run(Collections.singletonMap(inputName, inputTensor))

            @Suppress("UNCHECKED_CAST")
            val output = results[0].value as Array<Array<Array<FloatArray>>>
            val outH = output[0][0].size
            val outW = output[0][0][0].size
            if (outH != ph * SCALE || outW != pw * SCALE) {
                Log.e("UpscaylUpscaler", "Unexpected output size ${outW}x$outH")
                return null
            }

            val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val outPixels = IntArray(outW * outH)
            for (y in 0 until outH) {
                for (x in 0 until outW) {
                    val r = (output[0][0][y][x] * 255f).roundToInt().coerceIn(0, 255)
                    val g = (output[0][1][y][x] * 255f).roundToInt().coerceIn(0, 255)
                    val b = (output[0][2][y][x] * 255f).roundToInt().coerceIn(0, 255)
                    outPixels[y * outW + x] = Color.rgb(r, g, b)
                }
            }
            outBitmap.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

            inputTensor.close()
            results.close()
            outBitmap
        } catch (e: Exception) {
            Log.e("UpscaylUpscaler", "Inference failed: ${e.message}", e)
            null
        }
    }

    private fun algorithmicUpscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return bitmap

        val outW = w * 2
        val outH = h * 2

        val upscaled = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return bitmap
        }

        val canvas = Canvas(upscaled)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isDither = true }
        canvas.drawBitmap(bitmap, null, Rect(0, 0, outW, outH), paint)
        return upscaled
    }
}
