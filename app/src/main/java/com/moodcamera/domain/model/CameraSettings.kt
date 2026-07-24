package com.moodcamera.domain.model

import com.moodcamera.processing.enhance.CinematicLut

enum class FlashMode(val displayName: String) {
    OFF("Off"),
    ON("On"),
    AUTO("Auto")
}

enum class CameraMode(val displayName: String) {
    PHOTO("Photo"),
    PORTRAIT("Portrait"),
    NIGHT("Night")
}

enum class TimerDuration(val displayName: String, val seconds: Int) {
    OFF("Off", 0),
    THREE("3s", 3),
    TEN("10s", 10)
}

data class CameraSettings(
    val emulationType: EmulationType = EmulationType.PORTRA,
    val toneType: ToneType = ToneType.NEUTRAL,
    val qualityType: QualityType = QualityType.ISO_200,
    val aspectRatio: AspectRatio = AspectRatio.FOUR_THREE,
    val cinematicLut: CinematicLut? = null,
    val lutIntensity: Float = 0.7f,
    val isHdEnabled: Boolean = false,
    val hdIntensity: Float = 0.6f,
    val isAiEnhanceEnabled: Boolean = false,
    val exposureCompensation: Float = 0f,
    val contrast: Float = 1.0f,
    val brightness: Float = 0.0f,
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val fade: Float = 0.0f,
    val halationIntensity: Float = 0.3f,
    val vignette: Float = 0.0f,
    val isAutoFilterEnabled: Boolean = false,
    val isGrainEnabled: Boolean = false,
    val isHalationEnabled: Boolean = true,
    val isFrameEnabled: Boolean = false,
    val isGridEnabled: Boolean = false,
    val isFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val timerDuration: TimerDuration = TimerDuration.OFF,
    val cameraMode: CameraMode = CameraMode.PHOTO
) {
    fun getPresetName(): String {
        val lutName = cinematicLut?.displayName?.let { " + $it" } ?: ""
        return "${emulationType.displayName}${lutName} ${qualityType.displayName} ${toneType.displayName}"
    }
}
