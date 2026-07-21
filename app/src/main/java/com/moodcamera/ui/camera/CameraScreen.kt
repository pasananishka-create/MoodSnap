package com.moodcamera.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.hilt.navigation.compose.hiltViewModel
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.ToneType
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack
import com.moodcamera.ui.theme.MoodOnSurfaceVariant
import com.moodcamera.ui.theme.MoodSurface
import com.moodcamera.ui.theme.MoodSurfaceVariant

private fun EmulationType.overlayColor(): Color = when (this) {
    // Instagram - strong tints
    EmulationType.CLARENDON -> Color(0x20FF9966)
    EmulationType.JUNO -> Color(0x20FFAA33)
    EmulationType.LARK -> Color(0x1888DDAA)
    EmulationType.VALENCIA -> Color(0x20DD8844)
    EmulationType.HUDSON -> Color(0x204488CC)
    EmulationType.REYES -> Color(0x25CCBB99)
    // Filmic
    EmulationType.PORTRA -> Color(0x18FFB38A)
    EmulationType.GOLD_200 -> Color(0x20DDAA33)
    EmulationType.ULTRAMAX -> Color(0x18DD6644)
    EmulationType.FUJI_400H -> Color(0x1588CCBB)
    EmulationType.FILM35 -> Color(0x18DD9955)
    // Cinematic
    EmulationType.CINESTILL_800T -> Color(0x202244AA)
    EmulationType.KODACHROME -> Color(0x20CC8822)
    EmulationType.EKTACHROME -> Color(0x184488CC)
    EmulationType.CINEMATIC_TEAL_ORANGE -> Color(0x222288AA)
    EmulationType.NIGHTFADE -> Color(0x202244BB)
    EmulationType.ROSEWOOD -> Color(0x20AA4466)
    EmulationType.AGFA_VISTA -> Color(0x20FF3366)
    // Natural
    EmulationType.VELVIA -> Color(0x18FF8800)
    // Stylistic
    EmulationType.TRI_X -> Color(0x38808080)
    EmulationType.HP5 -> Color(0x28787878)
    EmulationType.ARIZONA -> Color(0x20DD9944)
    EmulationType.METRO -> Color(0x184466AA)
    EmulationType.EKTAR -> Color(0x18FF4444)
}

private fun EmulationType.overlayBlendColor(): Color = when (this) {
    EmulationType.TRI_X -> Color(0x28000000)
    EmulationType.HP5 -> Color(0x20000000)
    EmulationType.NIGHTFADE -> Color(0x18000022)
    EmulationType.CINESTILL_800T -> Color(0x15000033)
    EmulationType.HUDSON -> Color(0x12000022)
    EmulationType.REYES -> Color(0x15000000)
    EmulationType.METRO -> Color(0x12000033)
    else -> Color.Transparent
}

@Composable
fun CameraScreen(
    onNavigateToGallery: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showFilterBar by remember { mutableStateOf(false) }
    var showAdjustPanel by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner, uiState.settings.isFrontCamera) {
        viewModel.startCamera(lifecycleOwner, previewView)
    }

    var pinchZoom by remember { mutableStateOf(uiState.currentZoom) }

    LaunchedEffect(pinchZoom) {
        viewModel.setZoomRatio(pinchZoom)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MoodBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newZoom = (pinchZoom * zoom).coerceIn(1f, 10f)
                        pinchZoom = newZoom
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(uiState.settings.emulationType.overlayColor())
        )
        val blend = uiState.settings.emulationType.overlayBlendColor()
        if (blend != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(blend)
            )
        }

        if (uiState.settings.isGridEnabled) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = uiState.showSceneInfo && uiState.sceneInfo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            uiState.sceneInfo?.let { scene ->
                SceneInfoCard(
                    sceneInfo = scene,
                    onApply = { viewModel.applyAutoFilter() },
                    onDismiss = { viewModel.dismissSceneInfo() }
                )
            }
        }

        CameraTopBar(
            uiState = uiState,
            onFlashToggle = { viewModel.toggleFlash() },
            onAutoFilterToggle = {
                viewModel.toggleAutoFilter()
                if (!uiState.settings.isAutoFilterEnabled) {
                    viewModel.captureForAnalysis(previewView)
                }
            },
            onGridToggle = { viewModel.toggleGrid() },
            onFilterToggle = {
                showFilterBar = !showFilterBar
                showAdjustPanel = false
            },
            onAspectRatioCycle = { viewModel.cycleAspectRatio() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        AnimatedVisibility(
            visible = showFilterBar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            FilterBar(
                currentEmulation = uiState.settings.emulationType,
                onSelect = { viewModel.updateEmulation(it) },
                onDismiss = { showFilterBar = false }
            )
        }

        AnimatedVisibility(
            visible = showAdjustPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SettingsPanel(
                uiState = uiState,
                onToneChange = { viewModel.updateTone(it) },
                onContrastChange = { viewModel.updateContrast(it) },
                onBrightnessChange = { viewModel.updateBrightness(it) },
                onTemperatureChange = { viewModel.updateTemperature(it) },
                onTintChange = { viewModel.updateTint(it) },
                onFadeChange = { viewModel.updateFade(it) },
                onVignetteChange = { viewModel.updateVignette(it) },
                onHalationChange = { viewModel.updateHalationIntensity(it) },
                onGrainToggle = { viewModel.toggleGrain() },
                onHalationToggle = { viewModel.toggleHalation() },
                onDismiss = { showAdjustPanel = false }
            )
        }

        if (!showFilterBar && !showAdjustPanel) {
            CameraBottomBar(
                uiState = uiState,
                onCapture = { viewModel.takePhoto() },
                onSwitchCamera = { viewModel.toggleFrontCamera() },
                onGalleryClick = onNavigateToGallery,
                onExposureChange = { viewModel.updateExposure(it) },
                onSettingsClick = {
                    showAdjustPanel = true
                    showFilterBar = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (uiState.isProcessing) {
            ProcessingOverlay()
        }

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
    onFilterToggle: () -> Unit,
    onAspectRatioCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MoodBlack.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFlashToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (uiState.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (uiState.flashEnabled) MoodAccent else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(onClick = onGridToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Grid",
                    tint = if (uiState.settings.isGridEnabled) MoodAccent else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MoodAccent.copy(alpha = 0.2f))
                    .clickable(onClick = onAspectRatioCycle)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.settings.aspectRatio.displayName,
                    color = MoodAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (uiState.settings.isAutoFilterEnabled) MoodAccent.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .clickable(onClick = onAutoFilterToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (uiState.settings.isAutoFilterEnabled) MoodAccent else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Auto",
                        color = if (uiState.settings.isAutoFilterEnabled) MoodAccent else Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MoodAccent.copy(alpha = 0.2f))
                    .clickable(onClick = onFilterToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = uiState.settings.emulationType.displayName,
                    color = MoodAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterBar(
    currentEmulation: EmulationType,
    onSelect: (EmulationType) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoodBlack.copy(alpha = 0.85f))
            .padding(top = 12.dp, bottom = 80.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(EmulationType.entries) { type ->
                val isSelected = type == currentEmulation
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MoodAccent.copy(alpha = 0.25f)
                            else MoodSurfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable {
                            onSelect(type)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(type.overlayColor())
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = type.displayName,
                        color = if (isSelected) MoodAccent else Color.White,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    uiState: CameraUiState,
    onToneChange: (ToneType) -> Unit,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTintChange: (Float) -> Unit,
    onFadeChange: (Float) -> Unit,
    onVignetteChange: (Float) -> Unit,
    onHalationChange: (Float) -> Unit,
    onGrainToggle: () -> Unit,
    onHalationToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoodBlack.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 100.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Adjustments", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ToneType.entries.toList()) { tone ->
                val isSelected = tone == uiState.settings.toneType
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.3f) else MoodSurfaceVariant.copy(alpha = 0.5f))
                        .clickable { onToneChange(tone) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = tone.displayName,
                        color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsSlider("Contrast", uiState.settings.contrast, 0.5f..2f, onContrastChange)
        SettingsSlider("Brightness", uiState.settings.brightness, -0.5f..0.5f, onBrightnessChange)
        SettingsSlider("Temperature", uiState.settings.temperature, -1f..1f, onTemperatureChange)
        SettingsSlider("Tint", uiState.settings.tint, -1f..1f, onTintChange)
        SettingsSlider("Fade", uiState.settings.fade, 0f..1f, onFadeChange)
        SettingsSlider("Vignette", uiState.settings.vignette, 0f..1f, onVignetteChange)
        SettingsSlider("Halation", uiState.settings.halationIntensity, 0f..1f, onHalationChange)

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToggleChip("Grain", uiState.settings.isGrainEnabled, onGrainToggle, Modifier.weight(1f))
            ToggleChip("Halation", uiState.settings.isHalationEnabled, onHalationToggle, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MoodOnSurfaceVariant, fontSize = 11.sp)
            Text(String.format("%.1f", value), color = MoodAccent, fontSize = 11.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = MoodAccent,
                activeTrackColor = MoodAccent,
                inactiveTrackColor = MoodSurfaceVariant
            )
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) MoodAccent.copy(alpha = 0.25f) else MoodSurfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) MoodAccent else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
        )
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
                    Text(text = "Apply", color = MoodBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MoodBlack.copy(alpha = 0.55f))
            .padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    .height(3.dp)
                    .padding(horizontal = 8.dp),
                color = MoodAccent,
                trackColor = MoodSurfaceVariant
            )
            Text(text = "+3", color = MoodOnSurfaceVariant, fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGalleryClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .background(if (uiState.isCapturing) MoodAccent else Color.White.copy(alpha = 0.15f))
                    .clickable(enabled = !uiState.isCapturing) { onCapture() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MoodBlack,
                        strokeWidth = 3.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            IconButton(onClick = onSettingsClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Adjust",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSwitchCamera, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Switch Camera",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${uiState.settings.emulationType.displayName}  |  ${uiState.settings.toneType.displayName}",
                color = MoodAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
