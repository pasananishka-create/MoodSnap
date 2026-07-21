package com.moodcamera.domain.model

data class SceneInfo(
    val detectedLabels: List<SceneLabel>,
    val recommendedEmulation: EmulationType,
    val confidence: Float
)

data class SceneLabel(
    val text: String,
    val confidence: Float
)