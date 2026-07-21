package com.moodcamera.processing.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    private const val MODEL_INPUT_SIZE = 50
    private const val SCALE_FACTOR = 4
    private const val MODEL_OUTPUT_SIZE = MODEL_INPUT_SIZE * SCALE_FACTOR

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var outputIsNormalized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(true)
            }
            interpreter = Interpreter(model, options)

            val inputShape = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()

            if (inputShape.size == 4 && inputShape[1] == MODEL_INPUT_SIZE && inputShape[2] == MODEL_INPUT_SIZE) {
                isInitialized = true
                outputIsNormalized = false
                probeModelRange()
            } else {
                isInitialized = true
                outputIsNormalized = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
        }
    }

    private fun probeModelRange() {
        val interp = interpreter ?: return
        try {
            val testInput = ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
            testInput.order(ByteOrder.nativeOrder())
            for (i in 0 until MODEL_INPUT_SIZE * MODEL_INPUT_SIZE) {
                testInput.putFloat(128f)
                testInput.putFloat(128f)
                testInput.putFloat(128f)
            }
            val testOutput = ByteBuffer.allocateDirect(1 * MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE * 3 * 4)
            testOutput.order(ByteOrder.nativeOrder())
            interp.run(testInput, testOutput)
            testOutput.rewind()
            val sampleVal = testOutput.float
            outputIsNormalized = sampleVal < 2f
        } catch (_: Exception) {
            outputIsNormalized = false
        }
    }

    fun isReady(): Boolean = isInitialized && interpreter != null

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        if (!isReady()) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        if (w < MODEL_INPUT_SIZE || h < MODEL_INPUT_SIZE) return bitmap

        val inputW = min(w, MODEL_INPUT_SIZE * 6)
        val inputH = min(h, MODEL_INPUT_SIZE * 6)

        val scaled = if (w != inputW || h != inputH) {
            Bitmap.createScaledBitmap(bitmap, inputW, inputH, true)
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val outputW = inputW * SCALE_FACTOR
        val outputH = inputH * SCALE_FACTOR
        val outputPixels = IntArray(outputW * outputH)
        val weightMap = FloatArray(outputW * outputH)

        val inputPixels = IntArray(inputW * inputH)
        scaled.getPixels(inputPixels, 0, inputW, 0, 0, inputW, inputH)

        val step = MODEL_INPUT_SIZE / 2
        val tilesX = max(1, (inputW - MODEL_INPUT_SIZE + step) / step)
        val tilesY = max(1, (inputH - MODEL_INPUT_SIZE + step) / step)

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val srcX = min(tx * step, inputW - MODEL_INPUT_SIZE)
                val srcY = min(ty * step, inputH - MODEL_INPUT_SIZE)

                val tilePixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
                for (y in 0 until MODEL_INPUT_SIZE) {
                    for (x in 0 until MODEL_INPUT_SIZE) {
                        val sx = (srcX + x).coerceIn(0, inputW - 1)
                        val sy = (srcY + y).coerceIn(0, inputH - 1)
                        tilePixels[y * MODEL_INPUT_SIZE + x] = inputPixels[sy * inputW + sx]
                    }
                }

                val srPixels = runModel(tilePixels) ?: continue

                val dstX = srcX * SCALE_FACTOR
                val dstY = srcY * SCALE_FACTOR

                for (y in 0 until MODEL_OUTPUT_SIZE) {
                    for (x in 0 until MODEL_OUTPUT_SIZE) {
                        val ox = dstX + x
                        val oy = dstY + y
                        if (ox >= outputW || oy >= outputH) continue

                        val nx = (x.toFloat() / MODEL_OUTPUT_SIZE - 0.5f) * 2f
                        val ny = (y.toFloat() / MODEL_OUTPUT_SIZE - 0.5f) * 2f
                        val dist = min(1f, sqrt(nx * nx + ny * ny))
                        val weight = (1f - dist * 0.5f).coerceIn(0.5f, 1f)

                        val outIdx = oy * outputW + ox
                        val srIdx = y * MODEL_OUTPUT_SIZE + x
                        val srPixel = srPixels[srIdx]

                        val curWeight = weightMap[outIdx]
                        val totalWeight = curWeight + weight

                        if (totalWeight > 0f) {
                            val oldR = Color.red(outputPixels[outIdx])
                            val oldG = Color.green(outputPixels[outIdx])
                            val oldB = Color.blue(outputPixels[outIdx])
                            val newR = Color.red(srPixel)
                            val newG = Color.green(srPixel)
                            val newB = Color.blue(srPixel)
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

    private fun runModel(tilePixels: IntArray): IntArray? {
        val interp = interpreter ?: return null

        return try {
            val inputBuffer = ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            for (pixel in tilePixels) {
                inputBuffer.putFloat(Color.red(pixel).toFloat())
                inputBuffer.putFloat(Color.green(pixel).toFloat())
                inputBuffer.putFloat(Color.blue(pixel).toFloat())
            }
            inputBuffer.rewind()

            val outputBuffer = ByteBuffer.allocateDirect(1 * MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE * 3 * 4)
            outputBuffer.order(ByteOrder.nativeOrder())

            interp.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()

            val srPixels = IntArray(MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE)
            val scale = if (outputIsNormalized) 255f else 1f
            for (i in srPixels.indices) {
                val r = (outputBuffer.float * scale).coerceIn(0f, 255f).roundToInt()
                val g = (outputBuffer.float * scale).coerceIn(0f, 255f).roundToInt()
                val b = (outputBuffer.float * scale).coerceIn(0f, 255f).roundToInt()
                srPixels[i] = Color.rgb(r, g, b)
            }
            srPixels
        } catch (_: Exception) {
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

        val gainR = if (enhAvgR > 0f) origAvgR / enhAvgR else 1f
        val gainG = if (enhAvgG > 0f) origAvgG / enhAvgG else 1f
        val gainB = if (enhAvgB > 0f) origAvgB / enhAvgB else 1f

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
        val luminance = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val l = Color.red(pixels[i]) * 0.299f + Color.green(pixels[i]) * 0.587f + Color.blue(pixels[i]) * 0.114f
            luminance[i] = l
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

            val detailR = (r - br) * sharpAmt
            val detailG = (g - bg) * sharpAmt
            val detailB = (b - bb) * sharpAmt

            var nr = r + detailR
            var ng = g + detailG
            var nb = b + detailB

            val l = nr * 0.299f + ng * 0.587f + nb * 0.114f
            val midPoint = 128f
            nr = midPoint + (nr - midPoint) * contrastGain
            ng = midPoint + (ng - midPoint) * contrastGain
            nb = midPoint + (nb - midPoint) * contrastGain

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
                val idx = y * w + x
                temp[idx] = Color.argb(255, sumR / count, sumG / count, sumB / count)
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
