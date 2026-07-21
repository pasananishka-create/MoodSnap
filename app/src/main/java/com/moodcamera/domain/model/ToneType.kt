package com.moodcamera.domain.model

enum class ToneType(
    val displayName: String,
    val description: String
) {
    NEUTRAL(
        displayName = "Neutral",
        description = "Default balanced tone"
    ),
    CRUSH(
        displayName = "Crush",
        description = "Deep blacks, high contrast"
    ),
    ULTRA(
        displayName = "Ultra",
        description = "Maximum contrast and punch"
    ),
    DYNAMIC(
        displayName = "Dynamic",
        description = "Vivid with strong shadows"
    ),
    FADED(
        displayName = "Faded",
        description = "Lifted blacks, soft look"
    ),
    EXPIRED(
        displayName = "Expired",
        description = "Aged, muted film look"
    ),
    BRIGHT(
        displayName = "Bright",
        description = "Pushed exposure, preserved highlights"
    ),
    MOODY(
        displayName = "Moody",
        description = "Dark, atmospheric tones"
    )
}