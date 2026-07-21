package com.moodcamera.domain.model

data class CameraSettings(
    val emulationType: EmulationType = EmulationType.PORTRA,
    val toneType: ToneType = ToneType.NEUTRAL,
    val qualityType: QualityType = QualityType.ISO_200,
    val aspectRatio: AspectRatio = AspectRatio.FOUR_THREE,
    val exposureCompensation: Float = 0f,
    val contrast: Float = 1.0f,
    val brightness: Float = 0.0f,
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val fade: Float = 0.0f,
    val halationIntensity: Float = 0.3f,
    val vignette: Float = 0.0f,
    val isAutoFilterEnabled: Boolean = false,
    val isGrainEnabled: Boolean = true,
    val isHalationEnabled: Boolean = true,
    val isFrameEnabled: Boolean = false,
    val isGridEnabled: Boolean = false,
    val isFrontCamera: Boolean = false
) {
    fun getPresetName(): String {
        return "${emulationType.displayName} ${qualityType.displayName} ${toneType.displayName}"
    }
}