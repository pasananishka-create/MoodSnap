package com.moodcamera.domain.model

data class CameraSettings(
    val emulationType: EmulationType = EmulationType.PORTRA,
    val toneType: ToneType = ToneType.NEUTRAL,
    val qualityType: QualityType = QualityType.ISO_200,
    val aspectRatio: AspectRatio = AspectRatio.FOUR_THREE,
    val exposureCompensation: Float = 0f,
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