package com.moodcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.moodcamera.ui.navigation.MoodSnapNavHost
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack

@Composable
fun MoodSnapApp(
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MoodBlack)
    ) {
        if (hasCameraPermission) {
            MoodSnapNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PermissionScreen(
                onRequestPermission = onRequestPermission,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PermissionScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MoodBlack),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Camera permission is required\nto use MoodSnap",
            color = MoodAccent,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
