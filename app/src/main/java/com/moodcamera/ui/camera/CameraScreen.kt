package com.moodcamera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.hilt.navigation.compose.hiltViewModel
import com.moodcamera.domain.model.AspectRatio
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack
import com.moodcamera.ui.theme.MoodOnSurfaceVariant
import com.moodcamera.ui.theme.MoodSurface
import com.moodcamera.ui.theme.MoodSurfaceVariant

@Composable
fun CameraScreen(
    onNavigateToGallery: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner, uiState.settings.isFrontCamera) {
        viewModel.startCamera(lifecycleOwner, previewView)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MoodBlack)
    ) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Grid overlay
        if (uiState.settings.isGridEnabled) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        // Top bar
        CameraTopBar(
            uiState = uiState,
            onFlashToggle = { viewModel.toggleFlash() },
            onAutoFilterToggle = { viewModel.toggleAutoFilter() },
            onGridToggle = { viewModel.toggleGrid() },
            onAspectRatioClick = { ratio -> viewModel.updateAspectRatio(ratio) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Scene info overlay
        AnimatedVisibility(
            visible = uiState.showSceneInfo && uiState.sceneInfo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
        ) {
            uiState.sceneInfo?.let { scene ->
                SceneInfoCard(
                    sceneInfo = scene,
                    onApply = { viewModel.applyAutoFilter() },
                    onDismiss = { viewModel.dismissSceneInfo() }
                )
            }
        }

        // Film emulation selector
        EmulationSelector(
            currentEmulation = uiState.settings.emulationType,
            onSelect = { viewModel.updateEmulation(it) },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Bottom controls
        CameraBottomBar(
            uiState = uiState,
            onCapture = { viewModel.takePhoto() },
            onSwitchCamera = { viewModel.toggleFrontCamera() },
            onGalleryClick = onNavigateToGallery,
            onExposureChange = { viewModel.updateExposure(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Processing overlay
        if (uiState.isProcessing) {
            ProcessingOverlay()
        }

        // Error toast
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            ErrorToast(
                message = error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 160.dp)
            )
        }
    }
}

@Composable
private fun CameraTopBar(
    uiState: CameraUiState,
    onFlashToggle: () -> Unit,
    onAutoFilterToggle: () -> Unit,
    onGridToggle: () -> Unit,
    onAspectRatioClick: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MoodBlack.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFlashToggle) {
            Icon(
                imageVector = if (uiState.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (uiState.flashEnabled) MoodAccent else Color.White
            )
        }

        IconButton(onClick = onAutoFilterToggle) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Auto Filter",
                tint = if (uiState.settings.isAutoFilterEnabled) MoodAccent else MoodOnSurfaceVariant
            )
        }

        IconButton(onClick = onGridToggle) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Grid",
                tint = if (uiState.settings.isGridEnabled) MoodAccent else MoodOnSurfaceVariant
            )
        }

        AspectRatioButton(
            ratio = uiState.settings.aspectRatio,
            onClick = {
                val ratios = AspectRatio.entries
                val currentIndex = ratios.indexOf(uiState.settings.aspectRatio)
                val nextIndex = (currentIndex + 1) % ratios.size
                onAspectRatioClick(ratios[nextIndex])
            }
        )
    }
}

@Composable
private fun AspectRatioButton(
    ratio: AspectRatio,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MoodSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ratio.displayName,
            color = MoodAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmulationSelector(
    currentEmulation: EmulationType,
    onSelect: (EmulationType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 12.dp)
            .background(MoodBlack.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp)
    ) {
        EmulationType.entries.take(6).forEach { type ->
            val isSelected = type == currentEmulation
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MoodAccent.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .width(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.displayName,
                    color = if (isSelected) MoodAccent else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (EmulationType.entries.size > 6) {
            Text(
                text = "...",
                color = MoodOnSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun SceneInfoCard(
    sceneInfo: com.moodcamera.domain.model.SceneInfo,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 48.dp)
            .background(MoodSurface.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MoodAccent,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Recommended: ${sceneInfo.recommendedEmulation.displayName}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (sceneInfo.detectedLabels.isNotEmpty()) {
                Text(
                    text = sceneInfo.detectedLabels.take(3).joinToString(", ") { it.text },
                    color = MoodOnSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Dismiss",
                    color = MoodOnSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(8.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MoodAccent)
                        .clickable(onClick = onApply)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Apply",
                        color = MoodBlack,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraBottomBar(
    uiState: CameraUiState,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    onExposureChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MoodBlack.copy(alpha = 0.6f))
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Exposure slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val newExposure = (uiState.settings.exposureCompensation - dragAmount * 0.1f)
                            .coerceIn(-3f, 3f)
                        onExposureChange(newExposure)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "-3", color = MoodOnSurfaceVariant, fontSize = 10.sp)

            LinearProgressIndicator(
                progress = { ((uiState.settings.exposureCompensation + 3f) / 6f) },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .padding(horizontal = 8.dp),
                color = MoodAccent,
                trackColor = MoodSurfaceVariant
            )

            Text(text = "+3", color = MoodOnSurfaceVariant, fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Capture button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGalleryClick, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .background(if (uiState.isCapturing) MoodAccent else Color.White.copy(alpha = 0.2f))
                    .clickable(enabled = !uiState.isCapturing) { onCapture() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MoodBlack,
                        strokeWidth = 3.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            IconButton(onClick = onSwitchCamera, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = uiState.settings.emulationType.displayName,
            color = MoodAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        for (i in 1..2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = (i * 33.33).dp)
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
        for (i in 1..2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = (i * 33.33).dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
private fun ProcessingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MoodBlack.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MoodAccent,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Processing...", color = MoodAccent, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MoodSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = message, color = Color(0xFFCF6679), fontSize = 14.sp)
    }
}
