package com.moodcamera.processing.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AiEnhancer {

    private const val TAG = "AiEnhancer"
    private const val MODEL_INPUT_SIZE = 50
    private const val SCALE_FACTOR = 4
    private const val MODEL_OUTPUT_SIZE = MODEL_INPUT_SIZE * SCALE_FACTOR

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var lastError: String? = null
    private var inputShape: IntArray = intArrayOf()
    private var outputShape: IntArray = intArrayOf()
    private var inputIsFloat = true
    private var outputIsFloat = true
    private var outputRange01 = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)

            val interp = interpreter!!

            inputShape = interp.getInputTensor(0).shape()
            outputShape = interp.getOutputTensor(0).shape()
            val inputDtype = interp.getInputTensor(0).dataType()
            val outputDtype = interp.getOutputTensor(0).dataType()

            inputIsFloat = inputDtype.name.contains("FLOAT")
            outputIsFloat = outputDtype.name.contains("FLOAT")

            Log.d(TAG, "Model loaded: input=$inputShape dtype=$inputDtype output=$outputShape dtype=$outputDtype")

            if (inputShape.size == 4) {
                isInitialized = true
                probeModel()
                Log.d(TAG, "AI Enhancer ready. outputRange01=$outputRange01")
            } else {
                lastError = "Unexpected input shape: ${inputShape.contentToString()}"
                Log.e(TAG, lastError!!)
            }
        } catch (e: Exception) {
            lastError = "Init failed: ${e.message}"
            Log.e(TAG, lastError!!, e)
            isInitialized = false
        }
    }

    private fun probeModel() {
        val interp = interpreter ?: return
        try {
            val inSize = inputShape[1] * inputShape[2] * inputShape[3]
            val outSize = outputShape[1] * outputShape[2] * outputShape[3]

            val inputBuf = ByteBuffer.allocateDirect(inSize * 4)
            inputBuf.order(ByteOrder.nativeOrder())
            for (i in 0 until inSize) inputBuf.putFloat(128f)
            inputBuf.rewind()

            val outputBuf = ByteBuffer.allocateDirect(outSize * 4)
            outputBuf.order(ByteOrder.nativeOrder())

            interp.run(inputBuf, outputBuf)
            outputBuf.rewind()

            val sample = outputBuf.float
            outputRange01 = sample in -2f..2f
            Log.d(TAG, "Probe result: first output float=$sample, normalized=$outputRange01")
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed: ${e.message}")
            outputRange01 = false
        }
    }

    fun isReady(): Boolean = isInitialized && interpreter != null
    fun getLastError(): String? = lastError

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        if (!isReady()) {
            Log.w(TAG, "Not ready, returning original")
            return bitmap
        }
        val w = bitmap.width
        val h = bitmap.height
        if (w < MODEL_INPUT_SIZE || h < MODEL_INPUT_SIZE) {
            Log.w(TAG, "Image too small: ${w}x${h}")
            return bitmap
        }

        val interp = interpreter ?: return bitmap

        val modelInH = inputShape[1]
        val modelInW = inputShape[2]
        val modelOutH = outputShape[1]
        val modelOutW = outputShape[2]
        val scaleH = modelOutH / modelInH
        val scaleW = modelOutW / modelInW
        val actualScale = min(scaleH, scaleW)

        val inputW = min(w, modelInW * 4)
        val inputH = min(h, modelInH * 4)

        val scaled = if (w != inputW || h != inputH) {
            Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val outputW = inputW * actualScale
        val outputH = inputH * actualScale
        val outputPixels = IntArray(outputW * outputH)
        val weightMap = FloatArray(outputW * outputH)

        val inputPixels = IntArray(inputW * inputH)
        scaled.getPixels(inputPixels, 0, inputW, 0, 0, inputW, inputH)

        val step = modelInW / 2
        val tilesX = max(1, (inputW - modelInW + step) / step)
        val tilesY = max(1, (inputH - modelInH + step) / step)

        var tilesProcessed = 0
        var tilesFailed = 0

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val srcX = min(tx * step, inputW - modelInW)
                val srcY = min(ty * step, inputH - modelInH)

                val tilePixels = IntArray(modelInW * modelInH)
                for (y in 0 until modelInH) {
                    for (x in 0 until modelInW) {
                        val sx = (srcX + x).coerceIn(0, inputW - 1)
                        val sy = (srcY + y).coerceIn(0, inputH - 1)
                        tilePixels[y * modelInW + x] = inputPixels[sy * inputW + sx]
                    }
                }

                val srPixels = runModel(interp, tilePixels, modelInW, modelInH, modelOutW, modelOutH)
                if (srPixels == null) {
                    tilesFailed++
                    continue
                }
                tilesProcessed++

                val dstX = srcX * actualScale
                val dstY = srcY * actualScale

                for (y in 0 until modelOutH) {
                    for (x in 0 until modelOutW) {
                        val ox = dstX + x
                        val oy = dstY + y
                        if (ox >= outputW || oy >= outputH) continue

                        val nx = (x.toFloat() / modelOutW - 0.5f) * 2f
                        val ny = (y.toFloat() / modelOutH - 0.5f) * 2f
                        val dist = min(1f, sqrt(nx * nx + ny * ny))
                        val weight = (1f - dist * 0.3f).coerceIn(0.7f, 1f)

                        val outIdx = oy * outputW + ox
                        val srIdx = y * modelOutW + x

                        val curWeight = weightMap[outIdx]
                        val totalWeight = curWeight + weight

                        if (totalWeight > 0f) {
                            val oldR = Color.red(outputPixels[outIdx])
                            val oldG = Color.green(outputPixels[outIdx])
                            val oldB = Color.blue(outputPixels[outIdx])
                            val newR = Color.red(srPixels[srIdx])
                            val newG = Color.green(srPixels[srIdx])
                            val newB = Color.blue(srPixels[srIdx])
                            val ratio = weight / totalWeight
                            outputPixels[outIdx] = Color.rgb(
                                (oldR + (newR - oldR) * ratio).roundToInt().coerceIn(0, 255),
                                (oldG + (newG - oldG) * ratio).roundToInt().coerceIn(0, 255),
                                (oldB + (newB - oldB) * ratio).roundToInt().coerceIn(0, 255)
                            )
                            weightMap[outIdx] = totalWeight
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Tiles: $tilesProcessed ok, $tilesFailed failed")

        if (tilesProcessed == 0) {
            lastError = "All tiles failed"
            if (scaled !== bitmap) scaled.recycle()
            return bitmap
        }

        val output = Bitmap.createBitmap(outputW, outputH, Bitmap.Config.ARGB_8888)
        output.setPixels(outputPixels, 0, outputW, 0, 0, outputW, outputH)

        var result = if (outputW != w || outputH != h) {
            Bitmap.createScaledBitmap(output, w, h, true)
        } else {
            output
        }

        result = preserveColors(bitmap, result)
        result = enhanceDetail(result, intensity)

        if (scaled !== bitmap) scaled.recycle()
        if (output !== result) output.recycle()

        if (intensity >= 0.99f) return result
        return blendWithOriginal(bitmap, result, intensity)
    }

    private fun runModel(interp: Interpreter, tilePixels: IntArray, inW: Int, inH: Int, outW: Int, outH: Int): IntArray? {
        return try {
            val pixelCount = inW * inH
            val outPixelCount = outW * outH

            if (inputIsFloat) {
                val inputBuf = ByteBuffer.allocateDirect(pixelCount * 3 * 4)
                inputBuf.order(ByteOrder.nativeOrder())
                for (pixel in tilePixels) {
                    inputBuf.putFloat(Color.red(pixel).toFloat())
                    inputBuf.putFloat(Color.green(pixel).toFloat())
                    inputBuf.putFloat(Color.blue(pixel).toFloat())
                }
                inputBuf.rewind()

                if (outputIsFloat) {
                    val outputBuf = ByteBuffer.allocateDirect(outPixelCount * 3 * 4)
                    outputBuf.order(ByteOrder.nativeOrder())
                    interp.run(inputBuf, outputBuf)
                    outputBuf.rewind()

                    val srPixels = IntArray(outPixelCount)
                    val scale = if (outputRange01) 255f else 1f
                    for (i in srPixels.indices) {
                        val r = (outputBuf.float * scale).coerceIn(0f, 255f).roundToInt()
                        val g = (outputBuf.float * scale).coerceIn(0f, 255f).roundToInt()
                        val b = (outputBuf.float * scale).coerceIn(0f, 255f).roundToInt()
                        srPixels[i] = Color.rgb(r, g, b)
                    }
                    srPixels
                } else {
                    val outputBuf = ByteBuffer.allocateDirect(outPixelCount * 3)
                    outputBuf.order(ByteOrder.nativeOrder())
                    interp.run(inputBuf, outputBuf)
                    outputBuf.rewind()

                    val srPixels = IntArray(outPixelCount)
                    for (i in srPixels.indices) {
                        val r = outputBuf.get().toInt() and 0xFF
                        val g = outputBuf.get().toInt() and 0xFF
                        val b = outputBuf.get().toInt() and 0xFF
                        srPixels[i] = Color.rgb(r, g, b)
                    }
                    srPixels
                }
            } else {
                val inputBuf = ByteBuffer.allocateDirect(pixelCount * 3)
                inputBuf.order(ByteOrder.nativeOrder())
                for (pixel in tilePixels) {
                    inputBuf.put(Color.red(pixel).toByte())
                    inputBuf.put(Color.green(pixel).toByte())
                    inputBuf.put(Color.blue(pixel).toByte())
                }
                inputBuf.rewind()

                if (outputIsFloat) {
                    val outputBuf = ByteBuffer.allocateDirect(outPixelCount * 3 * 4)
                    outputBuf.order(ByteOrder.nativeOrder())
                    interp.run(inputBuf, outputBuf)
                    outputBuf.rewind()

                    val srPixels = IntArray(outPixelCount)
                    val scale = if (outputRange01) 255f else 1f
                    for (i in srPixels.indices) {
                        val r = (outputBuf.float * scale).coerceIn(0f, 255f).roundToInt()
                        val g = (outputBuf.float * scale).coerceIn(0f, 255f).roundToInt()
                        val b = (outputBuf.float * scale).coerceIn(0f, 255f).roundToInt()
                        srPixels[i] = Color.rgb(r, g, b)
                    }
                    srPixels
                } else {
                    val outputBuf = ByteBuffer.allocateDirect(outPixelCount * 3)
                    outputBuf.order(ByteOrder.nativeOrder())
                    interp.run(inputBuf, outputBuf)
                    outputBuf.rewind()

                    val srPixels = IntArray(outPixelCount)
                    for (i in srPixels.indices) {
                        val r = outputBuf.get().toInt() and 0xFF
                        val g = outputBuf.get().toInt() and 0xFF
                        val b = outputBuf.get().toInt() and 0xFF
                        srPixels[i] = Color.rgb(r, g, b)
                    }
                    srPixels
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tile inference failed: ${e.message}")
            null
        }
    }

    private fun preserveColors(original: Bitmap, enhanced: Bitmap): Bitmap {
        val w = original.width
        val h = original.height
        val origPixels = IntArray(w * h)
        val enhPixels = IntArray(w * h)
        original.getPixels(origPixels, 0, w, 0, 0, w, h)
        enhanced.getPixels(enhPixels, 0, w, 0, 0, w, h)

        var origSumR = 0L; var origSumG = 0L; var origSumB = 0L
        var enhSumR = 0L; var enhSumG = 0L; var enhSumB = 0L
        val count = w * h

        for (i in origPixels.indices) {
            origSumR += Color.red(origPixels[i])
            origSumG += Color.green(origPixels[i])
            origSumB += Color.blue(origPixels[i])
            enhSumR += Color.red(enhPixels[i])
            enhSumG += Color.green(enhPixels[i])
            enhSumB += Color.blue(enhPixels[i])
        }

        val origAvgR = origSumR.toFloat() / count
        val origAvgG = origSumG.toFloat() / count
        val origAvgB = origSumB.toFloat() / count
        val enhAvgR = enhSumR.toFloat() / count
        val enhAvgG = enhSumG.toFloat() / count
        val enhAvgB = enhSumB.toFloat() / count

        val gainR = if (enhAvgR > 1f) origAvgR / enhAvgR else 1f
        val gainG = if (enhAvgG > 1f) origAvgG / enhAvgG else 1f
        val gainB = if (enhAvgB > 1f) origAvgB / enhAvgB else 1f

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(w * h)
        for (i in enhPixels.indices) {
            val r = (Color.red(enhPixels[i]) * gainR).roundToInt().coerceIn(0, 255)
            val g = (Color.green(enhPixels[i]) * gainG).roundToInt().coerceIn(0, 255)
            val b = (Color.blue(enhPixels[i]) * gainB).roundToInt().coerceIn(0, 255)
            outPixels[i] = Color.rgb(r, g, b)
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun enhanceDetail(bitmap: Bitmap, intensity: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val blurred = boxBlur(pixels, w, h, 2)
        val outPixels = IntArray(pixels.size)
        val sharpAmt = 0.8f * intensity
        val contrastAmt = 0.15f * intensity

        var minL = 255f; var maxL = 0f
        for (i in pixels.indices) {
            val l = Color.red(pixels[i]) * 0.299f + Color.green(pixels[i]) * 0.587f + Color.blue(pixels[i]) * 0.114f
            if (l < minL) minL = l
            if (l > maxL) maxL = l
        }
        val lRange = maxL - minL
        val contrastGain = if (lRange > 0f) 1f + contrastAmt * (1f - lRange / 255f) else 1f

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            val br = Color.red(blurred[i]).toFloat()
            val bg = Color.green(blurred[i]).toFloat()
            val bb = Color.blue(blurred[i]).toFloat()

            var nr = r + (r - br) * sharpAmt
            var ng = g + (g - bg) * sharpAmt
            var nb = b + (b - bb) * sharpAmt

            nr = 128f + (nr - 128f) * contrastGain
            ng = 128f + (ng - 128f) * contrastGain
            nb = 128f + (nb - 128f) * contrastGain

            outPixels[i] = Color.rgb(
                nr.roundToInt().coerceIn(0, 255),
                ng.roundToInt().coerceIn(0, 255),
                nb.roundToInt().coerceIn(0, 255)
            )
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return pixels.copyOf()
        val out = IntArray(pixels.size)
        val r = min(radius, minOf(w, h) / 4)
        val temp = IntArray(pixels.size)
        for (y in 0 until h) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (x in -r..r) {
                val nx = x.coerceIn(0, w - 1)
                val px = pixels[y * w + nx]
                sumR += Color.red(px); sumG += Color.green(px); sumB += Color.blue(px); count++
            }
            for (x in 0 until w) {
                temp[y * w + x] = Color.argb(255, sumR / count, sumG / count, sumB / count)
                if (x + r + 1 < w) {
                    val add = pixels[y * w + x + r + 1]
                    val rem = pixels[y * w + (x - r).coerceIn(0, w - 1)]
                    sumR += Color.red(add) - Color.red(rem)
                    sumG += Color.green(add) - Color.green(rem)
                    sumB += Color.blue(add) - Color.blue(rem)
                }
            }
        }
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
            for (y in -r..r) {
                val ny = y.coerceIn(0, h - 1)
                val px = temp[ny * w + x]
                sumR += Color.red(px); sumG += Color.green(px); sumB += Color.blue(px); count++
            }
            for (y in 0 until h) {
                val idx = y * w + x
                out[idx] = Color.argb(Color.alpha(pixels[idx]), sumR / count, sumG / count, sumB / count)
                if (y + r + 1 < h) {
                    val add = temp[(y + r + 1) * w + x]
                    val rem = temp[(y - r).coerceIn(0, h - 1) * w + x]
                    sumR += Color.red(add) - Color.red(rem)
                    sumG += Color.green(add) - Color.green(rem)
                    sumB += Color.blue(add) - Color.blue(rem)
                }
            }
        }
        return out
    }

    private fun blendWithOriginal(original: Bitmap, enhanced: Bitmap, intensity: Float): Bitmap {
        val w = original.width
        val h = original.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(enhanced, null, android.graphics.Rect(0, 0, w, h), paint)
        paint.alpha = ((1f - intensity) * 255).roundToInt()
        canvas.drawBitmap(original, 0f, 0f, paint)
        return result
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd("ESRGAN.tflite")
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}
