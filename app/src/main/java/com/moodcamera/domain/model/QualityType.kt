package com.moodcamera.domain.model

enum class QualityType(
    val displayName: String,
    val description: String,
    val grainMultiplier: Float,
    val sharpness: Float
) {
    DIGI(
        displayName = "DIGI",
        description = "Digital clean, minimal grain",
        grainMultiplier = 0.0f,
        sharpness = 1.0f
    ),
    ISO_100(
        displayName = "100",
        description = "Fine grain, sharp detail",
        grainMultiplier = 0.2f,
        sharpness = 0.9f
    ),
    ISO_200(
        displayName = "200",
        description = "Balanced grain and detail",
        grainMultiplier = 0.4f,
        sharpness = 0.8f
    ),
    ISO_400(
        displayName = "400",
        description = "Classic film grain",
        grainMultiplier = 0.6f,
        sharpness = 0.7f
    ),
    ISO_800(
        displayName = "800",
        description = "Noticeable grain, filmic feel",
        grainMultiplier = 0.8f,
        sharpness = 0.6f
    ),
    ISO_1600(
        displayName = "1600",
        description = "Heavy grain, atmospheric",
        grainMultiplier = 1.0f,
        sharpness = 0.5f
    ),
    ISO_3200(
        displayName = "3200",
        description = "Maximum grain, raw character",
        grainMultiplier = 1.5f,
        sharpness = 0.4f
    )
}