package com.moodcamera.domain.model

enum class EmulationType(
    val displayName: String,
    val description: String,
    val category: EmulationCategory
) {
    CLARENDON(
        displayName = "Clarendon",
        description = "Bright, vivid, high contrast",
        category = EmulationCategory.INSTAGRAM
    ),
    JUNO(
        displayName = "Juno",
        description = "Warm highlights, cool shadows",
        category = EmulationCategory.INSTAGRAM
    ),
    LARK(
        displayName = "Lark",
        description = "Bright, desaturated reds, airy",
        category = EmulationCategory.INSTAGRAM
    ),
    VALENCIA(
        displayName = "Valencia",
        description = "Warm faded vintage tone",
        category = EmulationCategory.INSTAGRAM
    ),
    HUDSON(
        displayName = "Hudson",
        description = "Cool misty, teal shadows",
        category = EmulationCategory.INSTAGRAM
    ),
    REYES(
        displayName = "Reyes",
        description = "Dusty vintage, washed out",
        category = EmulationCategory.INSTAGRAM
    ),
    PORTRA(
        displayName = "Portra",
        description = "Soft warm skin tones",
        category = EmulationCategory.FILMIC
    ),
    CINESTILL_800T(
        displayName = "Cinestill",
        description = "Tungsten cinema, halation glow",
        category = EmulationCategory.CINEMATIC
    ),
    KODACHROME(
        displayName = "Kodachrome",
        description = "Classic warm slide film",
        category = EmulationCategory.CINEMATIC
    ),
    GOLD_200(
        displayName = "Gold",
        description = "Warm golden consumer film",
        category = EmulationCategory.FILMIC
    ),
    ULTRAMAX(
        displayName = "Ultramax",
        description = "Rich saturated punchy film",
        category = EmulationCategory.FILMIC
    ),
    EKTAR(
        displayName = "Ektar",
        description = "Vivid saturated colors",
        category = EmulationCategory.FILMIC
    ),
    VELVIA(
        displayName = "Velvia",
        description = "Ultra vivid landscapes",
        category = EmulationCategory.NATURAL
    ),
    TRI_X(
        displayName = "Tri-X",
        description = "High contrast B&W",
        category = EmulationCategory.STYLISTIC
    ),
    HP5(
        displayName = "HP5+",
        description = "Gritty versatile B&W",
        category = EmulationCategory.STYLISTIC
    ),
    ARIZONA(
        displayName = "Arizona",
        description = "Warm desert Wes Anderson",
        category = EmulationCategory.STYLISTIC
    ),
    METRO(
        displayName = "Metro",
        description = "Cool desaturated urban",
        category = EmulationCategory.STYLISTIC
    ),
    CINEMATIC_TEAL_ORANGE(
        displayName = "Teal & Orange",
        description = "Hollywood blockbuster look",
        category = EmulationCategory.CINEMATIC
    ),
    NIGHTFADE(
        displayName = "Nightfade",
        description = "Moody dark blue, lifted blacks",
        category = EmulationCategory.CINEMATIC
    ),
    ROSEWOOD(
        displayName = "Rosewood",
        description = "Deep red warm cinematic",
        category = EmulationCategory.CINEMATIC
    ),
    FILM35(
        displayName = "35mm",
        description = "Nostalgic warm film grain",
        category = EmulationCategory.FILMIC
    ),
    FUJI_400H(
        displayName = "Fuji 400H",
        description = "Soft airy pastels",
        category = EmulationCategory.FILMIC
    ),
    EKTACHROME(
        displayName = "Ektachrome",
        description = "Cool slide, punchy blues",
        category = EmulationCategory.CINEMATIC
    ),
    AGFA_VISTA(
        displayName = "Agfa",
        description = "Punchy red-biased color",
        category = EmulationCategory.CINEMATIC
    )
}

enum class EmulationCategory {
    FILMIC,
    NATURAL,
    CINEMATIC,
    STYLISTIC,
    INSTAGRAM
}
