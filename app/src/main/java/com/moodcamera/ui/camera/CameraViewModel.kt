package com.moodcamera.ui.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.moodcamera.ai.filter.SceneAnalyzer
import com.moodcamera.data.model.PhotoEntity
import com.moodcamera.data.repository.PhotoRepository
import com.moodcamera.data.repository.PresetRepository
import com.moodcamera.domain.model.AspectRatio
import com.moodcamera.domain.model.CameraSettings
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.QualityType
import com.moodcamera.domain.model.SceneInfo
import com.moodcamera.processing.engine.ImageProcessor
import com.moodcamera.processing.enhance.AiEnhancer
import com.moodcamera.processing.enhance.HdEnhancer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class CameraUiState(
    val settings: CameraSettings = CameraSettings(),
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val lastPhotoPath: String? = null,
    val sceneInfo: SceneInfo? = null,
    val showSceneInfo: Boolean = false,
    val currentZoom: Float = 1f,
    val hasFlashUnit: Boolean = false,
    val flashEnabled: Boolean = false,
    val photoCount: Int = 0,
    val errorMessage: String? = null,
    val showSettings: Boolean = false,
    val autoFilterStatus: String? = null
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val photoRepository: PhotoRepository,
    private val presetRepository: PresetRepository,
    private val sceneAnalyzer: SceneAnalyzer
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    init {
        viewModelScope.launch {
            photoRepository.getPhotoCount().collect { count ->
                _uiState.update { it.copy(photoCount = count) }
            }
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val context = getApplication<Application>()

        val preview = Preview.Builder().build()
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera(lifecycleOwner, preview, previewView)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        preview: Preview,
        previewView: PreviewView
    ) {
        val provider = cameraProvider ?: return
        val capture = imageCapture ?: return

        val cameraSelector = if (_uiState.value.settings.isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            provider.unbindAll()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )

            camera?.let { cam ->
                _uiState.update {
                    it.copy(
                        hasFlashUnit = cam.cameraInfo.hasFlashUnit(),
                        currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    )
                }

                cam.cameraInfo.zoomState.observeForever { zoomState ->
                    _uiState.update { it.copy(currentZoom = zoomState.zoomRatio) }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Camera bind failed: ${e.message}") }
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (_uiState.value.isCapturing) return

        _uiState.update { it.copy(isCapturing = true) }

        val tempFile = photoRepository.createTempPhotoFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(getApplication()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        processAndSavePhoto(tempFile.absolutePath)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    tempFile.delete()
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            errorMessage = "Capture failed: ${exception.message}"
                        )
                    }
                }
            }
        )
    }

    private suspend fun processAndSavePhoto(tempPath: String) {
        withContext(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isProcessing = true) }

                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val originalBitmap = BitmapFactory.decodeFile(tempPath, options) ?: run {
                    File(tempPath).delete()
                    _uiState.update {
                        it.copy(isCapturing = false, isProcessing = false, errorMessage = "Failed to decode image")
                    }
                    return@withContext
                }

                val settings = _uiState.value.settings
                var processedBitmap = ImageProcessor.processImage(
                    original = originalBitmap,
                    settings = settings,
                    quality = settings.qualityType
                )

                if (settings.isAiEnhanceEnabled && AiEnhancer.isReady()) {
                    try {
                        val aiResult = AiEnhancer.enhance(processedBitmap, settings.hdIntensity)
                        if (aiResult !== processedBitmap) {
                            processedBitmap.recycle()
                            processedBitmap = aiResult
                        }
                    } catch (_: Exception) {
                        if (settings.isHdEnabled) {
                            val hdResult = HdEnhancer.enhance(processedBitmap, settings.hdIntensity)
                            if (hdResult !== processedBitmap) {
                                processedBitmap.recycle()
                                processedBitmap = hdResult
                            }
                        }
                    }
                } else if (settings.isHdEnabled) {
                    val hdResult = HdEnhancer.enhance(processedBitmap, settings.hdIntensity)
                    if (hdResult !== processedBitmap) {
                        processedBitmap.recycle()
                        processedBitmap = hdResult
                    }
                }

                val processedFile = photoRepository.createPhotoFile()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, processedFile.outputStream())

                val photoEntity = PhotoEntity(
                    filePath = processedFile.absolutePath,
                    originalFilePath = "",
                    presetName = settings.getPresetName(),
                    width = processedBitmap.width,
                    height = processedBitmap.height
                )
                photoRepository.insertPhoto(photoEntity)

                saveToGallery(processedBitmap)

                originalBitmap.recycle()
                processedBitmap.recycle()
                File(tempPath).delete()

                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessing = false,
                        lastPhotoPath = processedFile.absolutePath
                    )
                }
            } catch (e: Exception) {
                File(tempPath).delete()
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessing = false,
                        errorMessage = "Processing failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val context = getApplication<Application>()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val filename = "MoodSnap_${timestamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MoodSnap")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            } catch (_: Exception) {}
        }
    }

    fun analyzeScene(bitmap: Bitmap) {
        if (!_uiState.value.settings.isAutoFilterEnabled) return
        viewModelScope.launch {
            try {
                val sceneInfo = sceneAnalyzer.analyzeScene(bitmap)
                _uiState.update {
                    it.copy(
                        sceneInfo = sceneInfo,
                        showSceneInfo = false,
                        autoFilterStatus = "Detected: ${sceneInfo.detectedLabels.firstOrNull()?.text ?: "scene"}"
                    )
                }
                applyAutoFilter(sceneInfo)
            } catch (_: Exception) {
            }
        }
    }

    private fun applyAutoFilter(sceneInfo: SceneInfo) {
        _uiState.update {
            it.copy(
                settings = it.settings.copy(emulationType = sceneInfo.recommendedEmulation),
                showSceneInfo = false
            )
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(autoFilterStatus = null) }
        }
    }

    fun dismissSceneInfo() {
        _uiState.update { it.copy(showSceneInfo = false) }
    }

    fun updateEmulation(type: EmulationType) {
        _uiState.update { it.copy(settings = it.settings.copy(emulationType = type)) }
    }

    fun updateTone(type: com.moodcamera.domain.model.ToneType) {
        _uiState.update { it.copy(settings = it.settings.copy(toneType = type)) }
    }

    fun updateExposure(value: Float) {
        camera?.cameraControl?.setExposureCompensationIndex(value.toInt())
        _uiState.update { it.copy(settings = it.settings.copy(exposureCompensation = value)) }
    }

    fun updateContrast(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(contrast = value)) }
    }

    fun updateBrightness(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(brightness = value)) }
    }

    fun updateTemperature(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(temperature = value)) }
    }

    fun updateTint(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(tint = value)) }
    }

    fun updateFade(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(fade = value)) }
    }

    fun updateVignette(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(vignette = value)) }
    }

    fun updateHalationIntensity(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(halationIntensity = value)) }
    }

    fun toggleGrain() {
        _uiState.update { it.copy(settings = it.settings.copy(isGrainEnabled = !it.settings.isGrainEnabled)) }
    }

    fun toggleHalation() {
        _uiState.update { it.copy(settings = it.settings.copy(isHalationEnabled = !it.settings.isHalationEnabled)) }
    }

    fun toggleFlash() {
        val newState = !_uiState.value.flashEnabled
        camera?.cameraControl?.enableTorch(newState)
        _uiState.update { it.copy(flashEnabled = newState) }
    }

    fun cycleZoom() {
        val cam = camera ?: return
        val current = _uiState.value.currentZoom
        val minZoom = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
        val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 5f
        val newZoom = if (current >= maxZoom) minZoom else current * 2f
        cam.cameraControl.setZoomRatio(newZoom)
    }

    fun toggleFrontCamera() {
        _uiState.update { it.copy(settings = it.settings.copy(isFrontCamera = !it.settings.isFrontCamera)) }
    }

    fun toggleAutoFilter() {
        val newState = !_uiState.value.settings.isAutoFilterEnabled
        _uiState.update {
            it.copy(
                settings = it.settings.copy(isAutoFilterEnabled = newState),
                sceneInfo = if (!newState) null else it.sceneInfo,
                showSceneInfo = false,
                autoFilterStatus = if (newState) "AI Active" else null
            )
        }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(settings = it.settings.copy(isGridEnabled = !it.settings.isGridEnabled)) }
    }

    fun cycleAspectRatio() {
        val entries = AspectRatio.entries
        val current = _uiState.value.settings.aspectRatio
        val nextIndex = (entries.indexOf(current) + 1) % entries.size
        _uiState.update { it.copy(settings = it.settings.copy(aspectRatio = entries[nextIndex])) }
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val minZoom = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
        val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 10f
        cam.cameraControl.setZoomRatio(ratio.coerceIn(minZoom, maxZoom))
    }

    fun updateCinematicLut(lut: com.moodcamera.processing.enhance.CinematicLut?) {
        _uiState.update { it.copy(settings = it.settings.copy(cinematicLut = lut)) }
    }

    fun updateLutIntensity(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(lutIntensity = value)) }
    }

    fun toggleHd() {
        _uiState.update { it.copy(settings = it.settings.copy(isHdEnabled = !it.settings.isHdEnabled)) }
    }

    fun updateHdIntensity(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(hdIntensity = value)) }
    }

    fun toggleAiEnhance() {
        _uiState.update { it.copy(settings = it.settings.copy(isAiEnhanceEnabled = !it.settings.isAiEnhanceEnabled)) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        sceneAnalyzer.close()
    }
}
