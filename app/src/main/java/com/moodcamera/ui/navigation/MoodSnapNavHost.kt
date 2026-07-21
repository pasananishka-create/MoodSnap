package com.moodcamera.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moodcamera.ui.camera.CameraScreen
import com.moodcamera.ui.gallery.GalleryScreen
import com.moodcamera.ui.gallery.PhotoDetailScreen
import com.moodcamera.ui.presets.PresetsScreen

@Composable
fun MoodSnapNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route,
        modifier = modifier
    ) {
        composable(Screen.Camera.route) {
            CameraScreen(
                onNavigateToGallery = {
                    navController.navigate(Screen.Gallery.route)
                }
            )
        }

        composable(Screen.Gallery.route) {
            GalleryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { photoId ->
                    navController.navigate(Screen.PhotoDetail.createRoute(photoId))
                }
            )
        }

        composable(
            route = Screen.PhotoDetail.route,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType })
        ) {
            PhotoDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Presets.route) {
            PresetsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            com.moodcamera.ui.settings.SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
