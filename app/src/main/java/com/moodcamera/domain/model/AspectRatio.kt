package com.moodcamera.domain.model

enum class AspectRatio(
    val displayName: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    FOUR_THREE(
        displayName = "4:3",
        widthRatio = 4,
        heightRatio = 3
    ),
    SQUARE(
        displayName = "1:1",
        widthRatio = 1,
        heightRatio = 1
    ),
    SIXTEEN_NINE(
        displayName = "16:9",
        widthRatio = 16,
        heightRatio = 9
    ),
    XPAN(
        displayName = "XPan",
        widthRatio = 65,
        heightRatio = 24
    ),
    THREE_TWO(
        displayName = "3:2",
        widthRatio = 3,
        heightRatio = 2
    )
}