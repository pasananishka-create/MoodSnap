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
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AiEnhancer {

    private const val MODEL_INPUT_SIZE = 50
    private const val SCALE_FACTOR = 4
    private const val MODEL_OUTPUT_SIZE = MODEL_INPUT_SIZE * SCALE_FACTOR
    private const val TILE_OVERLAP = 8

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isReady(): Boolean = isInitialized && interpreter != null

    fun enhance(bitmap: Bitmap, intensity: Float = 1f): Bitmap {
        if (!isReady()) return bitmap
        if (bitmap.width < 4 || bitmap.height < 4) return bitmap

        val w = bitmap.width
        val h = bitmap.height

        val inputW = min(w, MODEL_INPUT_SIZE * 8)
        val inputH = min(h, MODEL_INPUT_SIZE * 8)

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

        val step = MODEL_INPUT_SIZE - TILE_OVERLAP
        val tilesX = ((inputW - TILE_OVERLAP) + step - 1) / step
        val tilesY = ((inputH - TILE_OVERLAP) + step - 1) / step

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

                val srPixels = runModel(tilePixels)

                val dstX = srcX * SCALE_FACTOR
                val dstY = srcY * SCALE_FACTOR

                for (y in 0 until MODEL_OUTPUT_SIZE) {
                    for (x in 0 until MODEL_OUTPUT_SIZE) {
                        val ox = dstX + x
                        val oy = dstY + y
                        if (ox >= outputW || oy >= outputH) continue

                        val cx = (x - MODEL_OUTPUT_SIZE / 2).toFloat() / (MODEL_OUTPUT_SIZE / 2)
                        val cy = (y - MODEL_OUTPUT_SIZE / 2).toFloat() / (MODEL_OUTPUT_SIZE / 2)
                        val dist = min(1f, sqrt(cx * cx + cy * cy))
                        val weight = (1f - dist * dist).coerceIn(0.3f, 1f)

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

        val finalW = w * SCALE_FACTOR
        val finalH = h * SCALE_FACTOR
        val result = if (outputW != finalW || outputH != finalH) {
            Bitmap.createScaledBitmap(output, finalW, finalH, true)
        } else {
            output
        }

        if (intensity >= 0.99f) return result
        return blendWithOriginal(bitmap, result, intensity)
    }

    private fun runModel(tilePixels: IntArray): IntArray {
        val interp = interpreter ?: return tilePixels

        val inputBuffer = ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (pixel in tilePixels) {
            inputBuffer.putFloat(Color.red(pixel).toFloat())
            inputBuffer.putFloat(Color.green(pixel).toFloat())
            inputBuffer.putFloat(Color.blue(pixel).toFloat())
        }

        val outputBuffer = ByteBuffer.allocateDirect(1 * MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE * 3 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        try {
            interp.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            return tilePixels
        }

        outputBuffer.rewind()
        val srPixels = IntArray(MODEL_OUTPUT_SIZE * MODEL_OUTPUT_SIZE)
        for (i in srPixels.indices) {
            val r = outputBuffer.float.coerceIn(0f, 255f).roundToInt()
            val g = outputBuffer.float.coerceIn(0f, 255f).roundToInt()
            val b = outputBuffer.float.coerceIn(0f, 255f).roundToInt()
            srPixels[i] = Color.rgb(r, g, b)
        }
        return srPixels
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
