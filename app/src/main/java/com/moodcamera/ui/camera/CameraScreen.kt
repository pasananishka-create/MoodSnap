package com.moodcamera.ui.camera

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.moodcamera.domain.model.EmulationCategory
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.ToneType
import com.moodcamera.processing.enhance.CinematicLut
import com.moodcamera.processing.engine.PreviewProcessor
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack
import com.moodcamera.ui.theme.MoodOnSurfaceVariant
import com.moodcamera.ui.theme.MoodSurface
import com.moodcamera.ui.theme.MoodSurfaceVariant
import kotlin.math.roundToInt

private val GlassBg = Color(0x55000000)
private val GlassBorder = Color(0x22FFFFFF)
private val GlassBgHeavy = Color(0x77000000)

private fun EmulationType.previewColor(): Color = when (this) {
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
    EmulationType.VELVIA -> Color(0xFFFF6600)
    EmulationType.TRI_X -> Color(0xFF808080)
    EmulationType.HP5 -> Color(0xFF787878)
    EmulationType.ARIZONA -> Color(0xFFDD9944)
    EmulationType.METRO -> Color(0xFF4466AA)
}
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
    var cachedSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(lifecycleOwner, uiState.settings.isFrontCamera) {
        viewModel.startCamera(lifecycleOwner, previewView)
    }

    LaunchedEffect(lifecycleOwner, uiState.settings.isFrontCamera) {
        kotlinx.coroutines.delay(500)
        while (true) {
            try {
                val bmp = previewView.bitmap
                if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                    val src = Bitmap.createScaledBitmap(bmp, 480, 640, true)
                    cachedSourceBitmap = src
                    val processed = withContext(Dispatchers.Default) {
                        PreviewProcessor.processPreview(src, uiState.settings)
                    }
                    livePreviewBitmap = processed
                }
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(100)
        }
    }

    LaunchedEffect(uiState.settings.emulationType, uiState.settings.toneType, uiState.settings.fade, uiState.settings.contrast, uiState.settings.brightness, uiState.settings.temperature, uiState.settings.vignette, uiState.settings.cinematicLut, uiState.settings.isGrainEnabled) {
        val src = cachedSourceBitmap ?: return@LaunchedEffect
        val processed = withContext(Dispatchers.Default) {
            PreviewProcessor.processPreview(src, uiState.settings)
        }
        livePreviewBitmap = processed
    }

    var pinchZoom by remember { mutableFloatStateOf(uiState.currentZoom) }
    LaunchedEffect(pinchZoom) { viewModel.setZoomRatio(pinchZoom) }

    val aspectRatio = uiState.settings.aspectRatio
    val screenW = with(density) { context.resources.displayMetrics.widthPixels.toDp() }
    val screenH = with(density) { context.resources.displayMetrics.heightPixels.toDp() }

    val previewHeight = when (aspectRatio) {
        AspectRatio.SIXTEEN_NINE -> screenH
        AspectRatio.FOUR_THREE -> screenH
        AspectRatio.SQUARE -> screenW
        AspectRatio.THREE_TWO -> screenH * 0.85f
        AspectRatio.XPAN -> screenH * 0.55f
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
                .fillMaxWidth()
                .height(previewHeight)
                .align(Alignment.TopCenter)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        pinchZoom = (pinchZoom * zoom).coerceIn(1f, 10f)
                    }
                }
        )

        livePreviewBitmap?.let { bitmap ->
            val imageBitmap = bitmap.asImageBitmap()
            androidx.compose.foundation.Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .align(Alignment.TopCenter),
                contentScale = ContentScale.Crop,
                alpha = 1f
            )
        }

        if (uiState.settings.isGridEnabled) {
            GridOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .align(Alignment.TopCenter)
            )
        }

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

        CameraTopBar(
            uiState = uiState,
            onFlashToggle = { viewModel.toggleFlash() },
            onAutoFilterToggle = {
                viewModel.toggleAutoFilter()
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
                onLutSelect = { viewModel.updateCinematicLut(it) },
                onLutIntensityChange = { viewModel.updateLutIntensity(it) },
                onHdToggle = { viewModel.toggleHd() },
                onHdIntensityChange = { viewModel.updateHdIntensity(it) },
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
            .background(GlassBg)
            .border(0.5.dp, GlassBorder)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFlashToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (uiState.flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (uiState.flashEnabled) MoodAccent else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onGridToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = "Grid",
                    tint = if (uiState.settings.isGridEnabled) MoodAccent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassBg)
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onAspectRatioCycle)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = uiState.settings.aspectRatio.displayName, color = MoodAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (uiState.settings.isAutoFilterEnabled) MoodAccent.copy(alpha = 0.25f) else Color.Transparent)
                    .border(0.5.dp, if (uiState.settings.isAutoFilterEnabled) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(14.dp))
                    .clickable(onClick = onAutoFilterToggle)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (uiState.settings.isAutoFilterEnabled) MoodAccent else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("AI", color = if (uiState.settings.isAutoFilterEnabled) MoodAccent else Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MoodAccent.copy(alpha = 0.15f))
                    .border(0.5.dp, MoodAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onFilterToggle)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = uiState.settings.emulationType.displayName, color = MoodAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val categories = EmulationCategory.entries
    var selectedCategory by remember { mutableStateOf(currentEmulation.category) }
    val categoryFilters = EmulationType.entries.filter { it.category == selectedCategory }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBgHeavy)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(bottom = 80.dp)
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
                        .background(if (isActive) MoodAccent.copy(alpha = 0.3f) else GlassBg)
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
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.2f) else GlassBg)
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
    onLutSelect: (CinematicLut?) -> Unit,
    onLutIntensityChange: (Float) -> Unit,
    onHdToggle: () -> Unit,
    onHdIntensityChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBgHeavy)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 100.dp)
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
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.25f) else GlassBg)
                        .border(0.5.dp, if (isSelected) MoodAccent.copy(alpha = 0.5f) else GlassBorder, RoundedCornerShape(10.dp))
                        .clickable { onToneChange(tone) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(text = tone.displayName, color = if (isSelected) MoodAccent else Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleChip("Grain", uiState.settings.isGrainEnabled, onGrainToggle, Modifier.weight(1f))
            ToggleChip("Halation", uiState.settings.isHalationEnabled, onHalationToggle, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cinema LUT section
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Cinema LUT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (uiState.settings.cinematicLut != null) {
                Text("Active", color = MoodAccent, fontSize = 10.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            item {
                val isNone = uiState.settings.cinematicLut == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isNone) MoodAccent.copy(alpha = 0.25f) else GlassBg)
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
                        .background(if (isSelected) MoodAccent.copy(alpha = 0.25f) else GlassBg)
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
            SettingsSlider("LUT Strength", uiState.settings.lutIntensity, 0f..1f, onLutIntensityChange)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // HD Enhancement section
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("HD Enhancement", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleChip("HD On", uiState.settings.isHdEnabled, onHdToggle, Modifier.weight(1f))
        }
        if (uiState.settings.isHdEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            SettingsSlider("HD Strength", uiState.settings.hdIntensity, 0f..1f, onHdIntensityChange)
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SettingsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
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
            .background(if (enabled) MoodAccent.copy(alpha = 0.25f) else GlassBg)
            .border(0.5.dp, if (enabled) MoodAccent.copy(alpha = 0.4f) else GlassBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = if (enabled) MoodAccent else Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal)
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
            .background(GlassBg)
            .border(0.5.dp, GlassBorder)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val newExposure = (uiState.settings.exposureCompensation - dragAmount * 0.1f).coerceIn(-3f, 3f)
                        onExposureChange(newExposure)
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "-3", color = MoodOnSurfaceVariant, fontSize = 9.sp)
            LinearProgressIndicator(
                progress = { ((uiState.settings.exposureCompensation + 3f) / 6f) },
                modifier = Modifier.weight(1f).height(2.dp).padding(horizontal = 8.dp),
                color = MoodAccent, trackColor = MoodSurfaceVariant
            )
            Text(text = "+3", color = MoodOnSurfaceVariant, fontSize = 9.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGalleryClick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            }

            // Premium capture button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, Color.White, CircleShape)
                    .background(Color.Transparent)
                    .clickable(enabled = !uiState.isCapturing) { onCapture() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), color = MoodAccent, strokeWidth = 2.5.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White, Color(0xFFCCCCCC)),
                                        center = Offset(size.width * 0.4f, size.height * 0.35f),
                                        radius = size.width * 0.5f
                                    )
                                )
                            }
                    )
                }
            }

            IconButton(onClick = onSettingsClick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "Adjust", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSwitchCamera, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Switch Camera", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "  |  ",
                color = MoodAccent.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        for (i in 1..2) {
            Box(modifier = Modifier.fillMaxSize().padding(start = (i * 33.33).dp).width(0.5.dp).background(Color.White.copy(alpha = 0.25f)))
        }
        for (i in 1..2) {
            Box(modifier = Modifier.fillMaxSize().padding(top = (i * 33.33).dp).height(0.5.dp).background(Color.White.copy(alpha = 0.25f)))
        }
    }
}

@Composable
private fun ProcessingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(MoodBlack.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(44.dp), color = MoodAccent, strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "Processing...", color = MoodAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GlassBgHeavy)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = message, color = Color(0xFFCF6679), fontSize = 13.sp)
    }
}

