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
    private const val TILE_SIZE = 128
    private const val MAX_INPUT_DIM = 512

    private var session: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null
    private var initFailed = false
    private var initStarted = false

    val isDownloading: Boolean get() = initStarted && session == null && !initFailed

    suspend fun init(context: Context) {
        if (session != null || initFailed || initStarted) return
        initStarted = true
        try {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            if (!modelFile.exists()) {
                Log.i("UpscaylUpscaler", "Downloading AI model (~64MB)...")
                withContext(Dispatchers.IO) {
                    URL(MODEL_URL).openStream().use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Log.i("UpscaylUpscaler", "Model downloaded: ${modelFile.length()} bytes")
            }

            val modelBytes = withContext(Dispatchers.IO) { modelFile.readBytes() }
            ortEnv = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                try { addNnapi() } catch (_: Exception) {}
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = ortEnv!!.createSession(modelBytes, options)
            Log.i("UpscaylUpscaler", "AI model loaded successfully")
        } catch (e: Exception) {
            Log.e("UpscaylUpscaler", "Init failed: ${e.message}", e)
            session = null
            initFailed = true
        }
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        try { ortEnv?.close() } catch (_: Exception) {}
        session = null
        ortEnv = null
        initStarted = false
    }

    fun isReady(): Boolean = session != null

    suspend fun upscale(bitmap: Bitmap): Bitmap {
        val sess = session
        val env = ortEnv
        if (sess == null || env == null) return withContext(Dispatchers.IO) { algorithmicUpscale(bitmap) }

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

        val result = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            if (src !== bitmap) src.recycle()
            return withContext(Dispatchers.IO) { algorithmicUpscale(bitmap) }
        }

        val resultCanvas = Canvas(result)
        val tileOverlap = 4
        val step = TILE_SIZE - tileOverlap * 2

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
                            val srcRect = Rect(oX, oY, upscaled.width - oX, upscaled.height - oY)
                            val dstRect = Rect(dstX + oX, dstY + oY,
                                dstX + tileW * SCALE - oX, dstY + tileH * SCALE - oY)
                            if (srcRect.width() > 0 && srcRect.height() > 0 &&
                                dstRect.width() > 0 && dstRect.height() > 0) {
                                resultCanvas.drawBitmap(upscaled, srcRect, dstRect, null)
                            }
                            upscaled.recycle()
                        }
                    } catch (e: Exception) {
                        Log.e("UpscaylUpscaler", "Tile error: ${e.message}")
                    }
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

            val floatArray = FloatArray(3 * h * w)
            for (i in pixels.indices) {
                val px = pixels[i]
                floatArray[i] = Color.red(px) / 255f
                floatArray[w * h + i] = Color.green(px) / 255f
                floatArray[2 * w * h + i] = Color.blue(px) / 255f
            }

            val shape = longArrayOf(1, 3, h.toLong(), w.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
            val inputName = sess.inputNames.first()
            val results = sess.run(Collections.singletonMap(inputName, inputTensor))

            @Suppress("UNCHECKED_CAST")
            val output = results[0].value as Array<Array<Array<FloatArray>>>
            val outH = output[0][0].size
            val outW = output[0][0][0].size

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
