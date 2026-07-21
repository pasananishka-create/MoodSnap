package com.moodcamera.ui.navigation

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Gallery : Screen("gallery")
    data object PhotoDetail : Screen("photo/{photoId}") {
        fun createRoute(photoId: Long) = "photo/$photoId"
    }
    data object Presets : Screen("presets")
    data object Settings : Screen("settings")
    data object PresetEditor : Screen("preset_editor/{presetId}") {
        fun createRoute(presetId: Long = -1) = "preset_editor/$presetId"
    }
}
