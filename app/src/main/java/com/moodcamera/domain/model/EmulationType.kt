package com.moodcamera.domain.model

enum class EmulationType(
    val displayName: String,
    val description: String,
    val category: EmulationCategory
) {
    CLARENDON(
        displayName = "Clarendon",
        description = "Vivid, high contrast, cool shadows",
        category = EmulationCategory.INSTAGRAM
    ),
    JUNO(
        displayName = "Juno",
        description = "Warm highlights, vivid reds",
        category = EmulationCategory.INSTAGRAM
    ),
    LARK(
        displayName = "Lark",
        description = "Bright, airy, desaturated reds",
        category = EmulationCategory.INSTAGRAM
    ),
    VALENCIA(
        displayName = "Valencia",
        description = "Warm faded vintage amber",
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
    GINGHAM(
        displayName = "Gingham",
        description = "Washed-out, soft, slightly warm",
        category = EmulationCategory.INSTAGRAM
    ),
    ADEN(
        displayName = "Aden",
        description = "Soft pastel, desaturated, warm",
        category = EmulationCategory.INSTAGRAM
    ),
    LUDWIG(
        displayName = "Ludwig",
        description = "Warm, slight vignette, golden",
        category = EmulationCategory.INSTAGRAM
    ),
    CREMA(
        displayName = "Crema",
        description = "Creamy warm, soft contrast",
        category = EmulationCategory.INSTAGRAM
    ),
    PORTRA(
        displayName = "Portra 400",
        description = "Soft warm skin tones, natural",
        category = EmulationCategory.FILMIC
    ),
    PORTRA_800(
        displayName = "Portra 800",
        description = "Punchy warm, high speed film",
        category = EmulationCategory.FILMIC
    ),
    GOLD_200(
        displayName = "Gold 200",
        description = "Warm golden consumer film",
        category = EmulationCategory.FILMIC
    ),
    ULTRAMAX(
        displayName = "Ultramax",
        description = "Rich saturated punchy colors",
        category = EmulationCategory.FILMIC
    ),
    EKTAR(
        displayName = "Ektar 100",
        description = "Ultra vivid saturated film",
        category = EmulationCategory.FILMIC
    ),
    FILM35(
        displayName = "35mm",
        description = "Nostalgic warm film tones",
        category = EmulationCategory.FILMIC
    ),
    FUJI_400H(
        displayName = "Fuji 400H",
        description = "Soft airy pastel film",
        category = EmulationCategory.FILMIC
    ),
    FUJI_SUPERIA(
        displayName = "Superia",
        description = "Vivid green-biased consumer film",
        category = EmulationCategory.FILMIC
    ),
    FUJI_PROVIA(
        displayName = "Provia",
        description = "Balanced slide film, punchy",
        category = EmulationCategory.FILMIC
    ),
    FUJI_NATURA(
        displayName = "Natura",
        description = "Soft natural light film",
        category = EmulationCategory.FILMIC
    ),
    CINESTILL_800T(
        displayName = "Cinestill 800T",
        description = "Tungsten cinema film, halation",
        category = EmulationCategory.CINEMATIC
    ),
    KODACHROME(
        displayName = "Kodachrome",
        description = "Classic warm slide film, punchy",
        category = EmulationCategory.CINEMATIC
    ),
    EKTACHROME(
        displayName = "Ektachrome",
        description = "Cool slide film, vivid blues",
        category = EmulationCategory.CINEMATIC
    ),
    CINEMATIC_TEAL_ORANGE(
        displayName = "Teal & Orange",
        description = "Hollywood blockbuster split-tone",
        category = EmulationCategory.CINEMATIC
    ),
    NIGHTFADE(
        displayName = "Nightfade",
        description = "Moody dark blue, lifted blacks",
        category = EmulationCategory.CINEMATIC
    ),
    ROSEWOOD(
        displayName = "Rosewood",
        description = "Deep magenta cinematic warmth",
        category = EmulationCategory.CINEMATIC
    ),
    AGFA_VISTA(
        displayName = "Agfa Vista",
        description = "Punchy red-biased color film",
        category = EmulationCategory.CINEMATIC
    ),
    BLEACH_BYPASS(
        displayName = "Bleach Bypass",
        description = "Desaturated high contrast cinema",
        category = EmulationCategory.CINEMATIC
    ),
    TECHNICOLOR(
        displayName = "Technicolor",
        description = "Vivid three-strip primary color",
        category = EmulationCategory.CINEMATIC
    ),
    NOIR(
        displayName = "Film Noir",
        description = "High contrast B&W cinema",
        category = EmulationCategory.CINEMATIC
    ),
    NEON_NOIR(
        displayName = "Neon Noir",
        description = "Cyberpunk teal-magenta neon",
        category = EmulationCategory.CINEMATIC
    ),
    VINTAGE_CHROME(
        displayName = "Vintage Chrome",
        description = "Muted retro 70s film",
        category = EmulationCategory.CINEMATIC
    ),
    ANALOG_WARM(
        displayName = "Analog Warm",
        description = "Golden 70s cinema warmth",
        category = EmulationCategory.CINEMATIC
    ),
    DAY_FOR_NIGHT(
        displayName = "Day for Night",
        description = "Blue moonlit darkness",
        category = EmulationCategory.CINEMATIC
    ),
    SILVER_RETENTION(
        displayName = "Silver Retention",
        description = "Extreme desat gritty cinema",
        category = EmulationCategory.CINEMATIC
    ),
    VELVIA(
        displayName = "Velvia 50",
        description = "Ultra vivid landscape slide film",
        category = EmulationCategory.NATURAL
    ),
    TRI_X(
        displayName = "Tri-X 400",
        description = "High contrast classic B&W",
        category = EmulationCategory.STYLISTIC
    ),
    HP5(
        displayName = "HP5+",
        description = "Versatile grainy B&W film",
        category = EmulationCategory.STYLISTIC
    ),
    ARIZONA(
        displayName = "Arizona",
        description = "Warm desert, Wes Anderson",
        category = EmulationCategory.STYLISTIC
    ),
    METRO(
        displayName = "Metro",
        description = "Cool desaturated urban tones",
        category = EmulationCategory.STYLISTIC
    )
}

enum class EmulationCategory(val label: String) {
    FILMIC("Film"),
    NATURAL("Natural"),
    CINEMATIC("Cinema"),
    STYLISTIC("Style"),
    INSTAGRAM("Social")
}
