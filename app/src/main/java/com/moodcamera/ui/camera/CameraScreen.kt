package com.moodcamera.ui.camera

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.hilt.navigation.compose.hiltViewModel
import com.moodcamera.domain.model.AspectRatio
import com.moodcamera.domain.model.CameraMode
import com.moodcamera.domain.model.EmulationCategory
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.FlashMode
import com.moodcamera.domain.model.ToneType
import com.moodcamera.processing.enhance.CinematicLut
import com.moodcamera.processing.engine.PreviewProcessor
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack
import com.moodcamera.ui.theme.MoodOnSurfaceVariant
import com.moodcamera.ui.theme.MoodSurface
import com.moodcamera.ui.theme.MoodSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val GlassBg = Color(0x44000000)
private val GlassBgLight = Color(0x33000000)
private val GlassBorder = Color(0x18FFFFFF)

@Composable
fun CameraScreen(
    onNavigateToGallery: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    var showFilterBar by remember { mutableStateOf(false) }
    var showAdjustPanel by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var livePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(lifecycleOwner, uiState.settings.isFrontCamera) {
        viewModel.startCamera(lifecycleOwner, previewView)
    }

    LaunchedEffect(lifecycleOwner, uiState.settings.isFrontCamera) {
        kotlinx.coroutines.delay(1000)
        while (true) {
            try {
                val bmp = withContext(Dispatchers.Main) {
                    previewView.bitmap?.let {
                        if (it.width > 0 && it.height > 0) {
                            val maxDim = 320
                            val scale = maxDim.toFloat() / maxOf(it.width, it.height)
                            val tw = (it.width * scale).toInt().coerceAtLeast(1)
                            val th = (it.height * scale).toInt().coerceAtLeast(1)
                            Bitmap.createScaledBitmap(it, tw, th, true)
                        } else null
                    }
                }
                if (bmp != null) {
                    val settingsSnapshot = uiState.settings
                    val processed = withContext(Dispatchers.Default) {
                        PreviewProcessor.processPreview(bmp, settingsSnapshot)
                    }
                    bmp.recycle()
                    val old = livePreviewBitmap
                    livePreviewBitmap = processed
                    if (old != null && old !== processed) old.recycle()
                }
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(150)
        }
    }

    var pinchZoom by remember { mutableFloatStateOf(uiState.currentZoom) }
    LaunchedEffect(pinchZoom) { viewModel.setZoomRatio(pinchZoom) }

    val aspectRatio = uiState.settings.aspectRatio

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
                        pinchZoom = (pinchZoom * zoom).coerceIn(0.5f, 10f)
                    }
                }
        )

        if (uiState.settings.emulationType != EmulationType.ORIGINAL) {
            livePreviewBitmap?.let { bitmap ->
                key(bitmap) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 1f
                    )
                }
            }
        }

        if (uiState.settings.isGridEnabled) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        // Timer countdown overlay
        if (uiState.isTimerActive) {
            TimerCountdown(
                count = uiState.timerCountdown,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Auto filter status
        uiState.autoFilterStatus?.let { status ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MoodAccent.copy(alpha = 0.2f))
                    .border(0.5.dp, MoodAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MoodAccent, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(status, color = MoodAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Top bar
        TopBar(
            flashMode = uiState.settings.flashMode,
            timerDuration = uiState.settings.timerDuration,
            isGridEnabled = uiState.settings.isGridEnabled,
            aspectRatio = uiState.settings.aspectRatio,
            onFlashClick = { viewModel.cycleFlashMode() },
            onTimerClick = { viewModel.cycleTimer() },
            onGridClick = { viewModel.toggleGrid() },
            onRatioClick = { viewModel.cycleAspectRatio() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Zoom controls
        ZoomControls(
            currentZoom = uiState.zoomPreset,
            onZoomSelect = { viewModel.setZoomPreset(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 240.dp)
        )

        // Bottom section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Mode selector
            ModeSelector(
                currentMode = uiState.settings.cameraMode,
                onModeSelect = { /* Camera modes are display-only for now */ },
                modifier = Modifier.fillMaxWidth()
            )

            // Shutter + gallery + switch row
            ShutterRow(
                onCapture = { viewModel.takePhoto() },
                onGalleryClick = onNavigateToGallery,
                onSwitchCamera = { viewModel.toggleFrontCamera() },
                isProcessing = uiState.processingCount > 0,
                modifier = Modifier.fillMaxWidth()
            )

            // Bottom tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassBg)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolButton(
                    label = if (uiState.settings.emulationType == EmulationType.ORIGINAL) "No Filter" else uiState.settings.emulationType.displayName,
                    isActive = uiState.settings.emulationType != EmulationType.ORIGINAL,
                    onClick = {
                        showFilterBar = !showFilterBar
                        showAdjustPanel = false
                    }
                )
                ToolButton(
                    label = "Adjust",
                    isActive = uiState.settings.contrast != 1f || uiState.settings.brightness != 0f ||
                            uiState.settings.temperature != 0f || uiState.settings.fade > 0f,
                    onClick = {
                        showAdjustPanel = !showAdjustPanel
                        showFilterBar = false
                    }
                )
            }
        }

        // Filter bar overlay
        AnimatedVisibility(
            visible = showFilterBar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FilterBar(
                currentEmulation = uiState.settings.emulationType,
                onSelect = { viewModel.updateEmulation(it) },
                onDismiss = { showFilterBar = false }
            )
        }

        // Adjust panel overlay
        AnimatedVisibility(
            visible = showAdjustPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            AdjustPanel(
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
                onLutSelect = { viewModel.updateCinematicLut(it) },
                onLutIntensityChange = { viewModel.updateLutIntensity(it) },
                onHdToggle = { viewModel.toggleHd() },
                onHdIntensityChange = { viewModel.updateHdIntensity(it) },
                onAiEnhanceToggle = { viewModel.toggleAiEnhance() },
                onDismiss = { showAdjustPanel = false }
            )
        }

        // Exposure slider (left side)
        ExposureSlider(
            exposure = uiState.settings.exposureCompensation,
            onExposureChange = { viewModel.updateExposure(it) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp, top = 80.dp, bottom = 240.dp)
        )

        // Toasts
        uiState.savedMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(1500)
                viewModel.clearSavedMessage()
            }
            Toast(message = msg, icon = "\u2714", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 220.dp))
        }

        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            Toast(message = error, icon = "\u2716", tint = Color(0xFFCF6679), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 220.dp))
        }
    }
}

@Composable
private fun TopBar(
    flashMode: FlashMode,
    timerDuration: com.moodcamera.domain.model.TimerDuration,
    isGridEnabled: Boolean,
    aspectRatio: AspectRatio,
    onFlashClick: () -> Unit,
    onTimerClick: () -> Unit,
    onGridClick: () -> Unit,
    onRatioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GlassBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Flash
            IconButton(onClick = onFlashClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.OFF -> Icons.Default.FlashOff
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                    },
                    contentDescription = "Flash",
                    tint = when (flashMode) {
                        FlashMode.OFF -> Color.White.copy(alpha = 0.5f)
                        FlashMode.ON -> MoodAccent
                        FlashMode.AUTO -> Color(0xFFFFD54F)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            // Timer
            IconButton(onClick = onTimerClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = if (timerDuration != com.moodcamera.domain.model.TimerDuration.OFF) MoodAccent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Grid
            IconButton(onClick = onGridClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Grid",
                    tint = if (isGridEnabled) MoodAccent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Aspect Ratio
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassBgLight)
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onRatioClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = aspectRatio.displayName, color = MoodAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Timer duration indicator
            if (timerDuration != com.moodcamera.domain.model.TimerDuration.OFF) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MoodAccent.copy(alpha = 0.2f))
                        .border(0.5.dp, MoodAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = timerDuration.displayName, color = MoodAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ZoomControls(
    currentZoom: Float,
    onZoomSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(0.5f, 1f, 2f, 3f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(GlassBg)
                .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = kotlin.math.abs(currentZoom - preset) < 0.1f
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { onZoomSelect(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (preset < 1f) "${(preset * 10).toInt() / 10f}x" else "${preset.toInt()}x",
                        color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    currentMode: CameraMode,
    onModeSelect: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = CameraMode.entries
    Row(
        modifier = modifier
            .background(GlassBg)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clickable { onModeSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ShutterRow(
    onCapture: () -> Unit,
    onGalleryClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(GlassBg)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery
        IconButton(onClick = onGalleryClick, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = "Gallery",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Shutter button
        ShutterButton(
            onClick = { onCapture() },
            isProcessing = isProcessing,
            modifier = Modifier.size(76.dp)
        )

        // Switch camera
        IconButton(onClick = onSwitchCamera, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = "Switch Camera",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ShutterButton(
    onClick: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isProcessing) 0.85f else 1f,
        animationSpec = tween(100),
        label = "shutterScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(3.dp, Color.White, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = !isProcessing) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MoodAccent)
            )
        }
    }
}

@Composable
private fun ExposureSlider(
    exposure: Float,
    onExposureChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("+3", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Slider(
            value = exposure,
            onValueChange = onExposureChange,
            valueRange = -3f..3f,
            modifier = Modifier
                .height(120.dp)
                .rotate(-90f),
            colors = SliderDefaults.colors(
                thumbColor = MoodAccent,
                activeTrackColor = MoodAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Text("-3", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

@Composable
private fun ToolButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) MoodAccent.copy(alpha = 0.2f) else GlassBgLight)
            .border(0.5.dp, if (isActive) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) MoodAccent else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TimerCountdown(count: Int, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = 1.2f,
        animationSpec = tween(200),
        label = "timerScale"
    )
    Box(
        modifier = modifier
            .size(100.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$count",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun FilterBar(
    currentEmulation: EmulationType,
    onSelect: (EmulationType) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = EmulationCategory.entries
    var selectedCategory by remember { mutableStateOf(currentEmulation.category) }
    val categoryFilters = if (selectedCategory == EmulationCategory.ALL) {
        EmulationType.entries.toList()
    } else {
        listOf(EmulationType.ORIGINAL) + EmulationType.entries.filter { it.category == selectedCategory && it != EmulationType.ORIGINAL }
    }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBg.copy(alpha = 0.9f))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(bottom = 60.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories) { cat ->
                val isActive = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isActive) MoodAccent.copy(alpha = 0.3f) else GlassBgLight)
                        .border(0.5.dp, if (isActive) MoodAccent.copy(alpha = 0.6f) else GlassBorder, RoundedCornerShape(16.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = cat.label, color = if (isActive) MoodAccent else Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(categoryFilters) { type ->
                val isSelected = type == currentEmulation
                Column(
                    modifier = Modifier
                        .width(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.2f) else GlassBgLight)
                        .border(1.dp, if (isSelected) MoodAccent else Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .clickable { onSelect(type) }
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.radialGradient(listOf(type.previewColor(), type.previewColor().copy(alpha = 0.4f))))
                            .border(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) MoodAccent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = type.displayName,
                        color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
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
private fun AdjustPanel(
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
    onLutSelect: (CinematicLut?) -> Unit,
    onLutIntensityChange: (Float) -> Unit,
    onHdToggle: () -> Unit,
    onHdIntensityChange: (Float) -> Unit,
    onAiEnhanceToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBg.copy(alpha = 0.9f))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 60.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Adjustments", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(ToneType.entries.toList()) { tone ->
                val isSelected = tone == uiState.settings.toneType
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.25f) else GlassBgLight)
                        .border(0.5.dp, if (isSelected) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onToneChange(tone) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(text = tone.displayName, color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        SliderRow("Contrast", uiState.settings.contrast, 0.5f..2f, onContrastChange)
        SliderRow("Brightness", uiState.settings.brightness, -0.5f..0.5f, onBrightnessChange)
        SliderRow("Temperature", uiState.settings.temperature, -1f..1f, onTemperatureChange)
        SliderRow("Tint", uiState.settings.tint, -1f..1f, onTintChange)
        SliderRow("Fade", uiState.settings.fade, 0f..1f, onFadeChange)
        SliderRow("Vignette", uiState.settings.vignette, 0f..1f, onVignetteChange)
        SliderRow("Halation", uiState.settings.halationIntensity, 0f..1f, onHalationChange)

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleChip("Grain", uiState.settings.isGrainEnabled, onGrainToggle, Modifier.weight(1f))
            ToggleChip("Halation", uiState.settings.isHalationEnabled, onHalationToggle, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cinema LUT
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Cinema LUT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (uiState.settings.cinematicLut != null) Text("Active", color = MoodAccent, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            item {
                val isNone = uiState.settings.cinematicLut == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isNone) MoodAccent.copy(alpha = 0.25f) else GlassBgLight)
                        .border(0.5.dp, if (isNone) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onLutSelect(null) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("None", color = if (isNone) MoodAccent else Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = if (isNone) FontWeight.Bold else FontWeight.Normal)
                }
            }
            items(CinematicLut.entries.toList()) { lut ->
                val isSelected = lut == uiState.settings.cinematicLut
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.25f) else GlassBgLight)
                        .border(0.5.dp, if (isSelected) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onLutSelect(if (isSelected) null else lut) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(lut.displayName, color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (uiState.settings.cinematicLut != null) {
            Spacer(modifier = Modifier.height(4.dp))
            SliderRow("LUT Strength", uiState.settings.lutIntensity, 0f..1f, onLutIntensityChange)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Enhancement
        Text("Enhancement", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (uiState.settings.isAiEnhanceEnabled) MoodAccent.copy(alpha = 0.25f) else GlassBgLight)
                    .border(0.5.dp, if (uiState.settings.isAiEnhanceEnabled) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
                    .clickable { onAiEnhanceToggle() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AI Enhance", color = if (uiState.settings.isAiEnhanceEnabled) MoodAccent else Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Sharpness + Color", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                }
            }
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (uiState.settings.isHdEnabled && !uiState.settings.isAiEnhanceEnabled) MoodAccent.copy(alpha = 0.25f) else GlassBgLight)
                    .border(0.5.dp, if (uiState.settings.isHdEnabled && !uiState.settings.isAiEnhanceEnabled) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
                    .clickable { onHdToggle() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Classic HD", color = if (uiState.settings.isHdEnabled && !uiState.settings.isAiEnhanceEnabled) MoodAccent else Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Sharpen + Contrast", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                }
            }
        }
        if (uiState.settings.isHdEnabled || uiState.settings.isAiEnhanceEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            SliderRow("Strength", uiState.settings.hdIntensity, 0f..1f, onHdIntensityChange)
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MoodOnSurfaceVariant, fontSize = 11.sp)
            Text(String.format("%.1f", value), color = MoodAccent, fontSize = 11.sp)
        }
        Slider(
            value = value, onValueChange = onValueChange, valueRange = range,
            modifier = Modifier.fillMaxWidth().height(20.dp),
            colors = SliderDefaults.colors(thumbColor = MoodAccent, activeTrackColor = MoodAccent, inactiveTrackColor = MoodSurfaceVariant)
        )
    }
}

@Composable
private fun ToggleChip(label: String, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) MoodAccent.copy(alpha = 0.25f) else GlassBgLight)
            .border(0.5.dp, if (enabled) MoodAccent.copy(alpha = 0.4f) else GlassBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = if (enabled) MoodAccent else Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    val lineColor = Color.White.copy(alpha = 0.3f)
    Box(modifier = modifier) {
        for (i in 1..2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = (i * 33.33).dp)
                    .width(0.5.dp)
                    .background(lineColor)
            )
        }
        for (i in 1..2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = (i * 33.33).dp)
                    .height(0.5.dp)
                    .background(lineColor)
            )
        }
    }
}

@Composable
private fun Toast(message: String, icon: String = "", tint: Color = MoodAccent, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.2f))
            .border(0.5.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon.isNotEmpty()) {
                Text(text = icon, color = tint, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = message, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun EmulationType.previewColor(): Color = when (this) {
    EmulationType.ORIGINAL -> Color(0xFFCCCCCC)
    EmulationType.CLARENDON -> Color(0xFF4499DD)
    EmulationType.JUNO -> Color(0xFFFFAA33)
    EmulationType.LARK -> Color(0xFF88CCBB)
    EmulationType.VALENCIA -> Color(0xFFCC8844)
    EmulationType.HUDSON -> Color(0xFF4488CC)
    EmulationType.REYES -> Color(0xFFCCBB99)
    EmulationType.GINGHAM -> Color(0xFFBBCCBB)
    EmulationType.ADEN -> Color(0xFFCCBBAA)
    EmulationType.LUDWIG -> Color(0xFFCC9955)
    EmulationType.CREMA -> Color(0xFFCCBB88)
    EmulationType.PORTRA -> Color(0xFFFFB38A)
    EmulationType.PORTRA_800 -> Color(0xFFFFAA77)
    EmulationType.GOLD_200 -> Color(0xFFDDAA33)
    EmulationType.ULTRAMAX -> Color(0xFFCC5533)
    EmulationType.EKTAR -> Color(0xFFEE3333)
    EmulationType.FILM35 -> Color(0xFFDD9955)
    EmulationType.FUJI_400H -> Color(0xFF88CCBB)
    EmulationType.FUJI_SUPERIA -> Color(0xFF55AA55)
    EmulationType.FUJI_PROVIA -> Color(0xFF3377CC)
    EmulationType.FUJI_NATURA -> Color(0xFF88AA88)
    EmulationType.CINESTILL_800T -> Color(0xFF2244AA)
    EmulationType.KODACHROME -> Color(0xFFCC8822)
    EmulationType.EKTACHROME -> Color(0xFF2266CC)
    EmulationType.CINEMATIC_TEAL_ORANGE -> Color(0xFF2288AA)
    EmulationType.NIGHTFADE -> Color(0xFF2244BB)
    EmulationType.ROSEWOOD -> Color(0xFFAA4466)
    EmulationType.AGFA_VISTA -> Color(0xFFEE3366)
    EmulationType.BLEACH_BYPASS -> Color(0xFF888888)
    EmulationType.TECHNICOLOR -> Color(0xFFDD2244)
    EmulationType.NOIR -> Color(0xFF404040)
    EmulationType.NEON_NOIR -> Color(0xFF2244CC)
    EmulationType.VINTAGE_CHROME -> Color(0xFFAA7744)
    EmulationType.ANALOG_WARM -> Color(0xFFCC8833)
    EmulationType.DAY_FOR_NIGHT -> Color(0xFF113366)
    EmulationType.SILVER_RETENTION -> Color(0xFF666666)
    EmulationType.VELVIA -> Color(0xFFFF6600)
    EmulationType.TRI_X -> Color(0xFF808080)
    EmulationType.HP5 -> Color(0xFF787878)
    EmulationType.ARIZONA -> Color(0xFFDD9944)
    EmulationType.METRO -> Color(0xFF4466AA)
}
