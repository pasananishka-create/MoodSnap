package com.moodcamera.domain.model

enum class EmulationType(
    val displayName: String,
    val description: String,
    val category: EmulationCategory
) {
    PORTRA(
        displayName = "Portra",
        description = "Soft warm tones for portraits",
        category = EmulationCategory.FILMIC
    ),
    CINESTILL_800T(
        displayName = "Cinestill 800T",
        description = "Tungsten cinema film with halation",
        category = EmulationCategory.CINEMATIC
    ),
    EKTAR(
        displayName = "Ektar",
        description = "Vivid, punchy colors",
        category = EmulationCategory.FILMIC
    ),
    FUJI_400H(
        displayName = "Fuji 400H",
        description = "Soft airy pastels",
        category = EmulationCategory.FILMIC
    ),
    VELVIA(
        displayName = "Velvia",
        description = "High saturation landscapes",
        category = EmulationCategory.NATURAL
    ),
    PROVIA(
        displayName = "Provia",
        description = "Balanced, natural tones",
        category = EmulationCategory.NATURAL
    ),
    TRI_X(
        displayName = "Tri-X",
        description = "Classic high contrast B&W",
        category = EmulationCategory.STYLISTIC
    ),
    HP5(
        displayName = "HP5+",
        description = "Gritty versatile B&W",
        category = EmulationCategory.STYLISTIC
    ),
    ARIZONA(
        displayName = "Arizona",
        description = "Warm Wes Anderson style",
        category = EmulationCategory.STYLISTIC
    ),
    METRO(
        displayName = "Metro",
        description = "Cool desaturated urban",
        category = EmulationCategory.STYLISTIC
    ),
    GOLD_200(
        displayName = "Gold 200",
        description = "Warm consumer film look",
        category = EmulationCategory.FILMIC
    ),
    ULTRAMAX(
        displayName = "Ultramax",
        description = "Rich saturated everyday film",
        category = EmulationCategory.FILMIC
    ),
    KODACHROME(
        displayName = "Kodachrome",
        description = "Classic warm slide film",
        category = EmulationCategory.CINEMATIC
    ),
    EKTACHROME(
        displayName = "Ektachrome",
        description = "Cool tones, fine grain slide",
        category = EmulationCategory.CINEMATIC
    ),
    CINESTILL_50D(
        displayName = "50D",
        description = "Daylight cinema, clean & warm",
        category = EmulationCategory.CINEMATIC
    ),
    PORTRA_160(
        displayName = "Portra 160",
        description = "Ultra smooth, fine grain",
        category = EmulationCategory.FILMIC
    ),
    FUJI_C200(
        displayName = "C200",
        description = "Everyday green-tinted film",
        category = EmulationCategory.FILMIC
    ),
    AGFA_VISTA(
        displayName = "Agfa Vista",
        description = "Punchy red-biased color",
        category = EmulationCategory.CINEMATIC
    )
}

enum class EmulationCategory {
    FILMIC,
    NATURAL,
    CINEMATIC,
    STYLISTIC
}
