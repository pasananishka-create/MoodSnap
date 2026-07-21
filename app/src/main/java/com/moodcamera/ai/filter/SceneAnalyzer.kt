package com.moodcamera.ai.filter

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.SceneInfo
import com.moodcamera.domain.model.SceneLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class SceneAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    suspend fun analyzeScene(bitmap: Bitmap): SceneInfo {
        val image = InputImage.fromBitmap(bitmap, 0)

        val labels = suspendCancellableCoroutine { cont ->
            labeler.process(image)
                .addOnSuccessListener { result ->
                    val sceneLabels = result.map { label ->
                        SceneLabel(
                            text = label.text.lowercase(),
                            confidence = label.confidence
                        )
                    }
                    cont.resume(sceneLabels)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

        val recommendedEmulation = mapLabelsToEmulation(labels)
        val avgConfidence = if (labels.isNotEmpty()) {
            labels.map { it.confidence }.average().toFloat()
        } else 0f

        return SceneInfo(
            detectedLabels = labels,
            recommendedEmulation = recommendedEmulation,
            confidence = avgConfidence
        )
    }

    private fun mapLabelsToEmulation(labels: List<SceneLabel>): EmulationType {
        val labelMap = mutableMapOf<String, Float>()
        labels.forEach { labelMap[it.text] = it.confidence }

        // Portrait / people detection
        val hasPerson = labelMap.keys.any { key ->
            key.contains("person") || key.contains("face") || key.contains("portrait") ||
                    key.contains("selfie") || key.contains("man") || key.contains("woman")
        }

        // Night / neon detection
        val isNight = labelMap.keys.any { key ->
            key.contains("night") || key.contains("dark") || key.contains("neon") ||
                    key.contains("light") || key.contains("lamp")
        }

        // Landscape / nature
        val isLandscape = labelMap.keys.any { key ->
            key.contains("landscape") || key.contains("mountain") || key.contains("tree") ||
                    key.contains("forest") || key.contains("nature") || key.contains("grass")
        }

        // Urban / street
        val isUrban = labelMap.keys.any { key ->
            key.contains("building") || key.contains("street") || key.contains("city") ||
                    key.contains("road") || key.contains("car") || key.contains("urban")
        }

        // Food
        val isFood = labelMap.keys.any { key ->
            key.contains("food") || key.contains("meal") || key.contains("dish") ||
                    key.contains("restaurant") || key.contains("cook")
        }

        // Indoor
        val isIndoor = labelMap.keys.any { key ->
            key.contains("room") || key.contains("indoor") || key.contains("furniture") ||
                    key.contains("table") || key.contains("chair")
        }

        // B&W scenes (high contrast subjects)
        val isMonochromeSubject = labelMap.keys.any { key ->
            key.contains("architecture") || key.contains("monochrome") ||
                    key.contains("black and white")
        }

        return when {
            isMonochromeSubject -> EmulationType.TRI_X
            hasPerson -> EmulationType.PORTRA
            isNight -> EmulationType.CINESTILL_800T
            isFood -> EmulationType.GOLD_200
            isLandscape -> EmulationType.VELVIA
            isUrban -> EmulationType.METRO
            isIndoor -> EmulationType.ARIZONA
            else -> EmulationType.PROVIA
        }
    }

    fun close() {
        labeler.close()
    }
}
