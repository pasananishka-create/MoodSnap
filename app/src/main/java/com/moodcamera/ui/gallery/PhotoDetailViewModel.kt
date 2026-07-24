package com.moodcamera.ui.gallery

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodcamera.data.model.PhotoEntity
import com.moodcamera.data.repository.PhotoRepository
import com.moodcamera.processing.enhance.UpscaylUpscaler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PhotoDetailUiState(
    val photo: PhotoEntity? = null,
    val isLoading: Boolean = true,
    val isUpscaling: Boolean = false,
    val upscaleProgress: String? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photoRepository: PhotoRepository,
    private val application: Application
) : ViewModel() {

    private val photoId: Long = savedStateHandle.get<Long>("photoId") ?: -1L

    private val _uiState = MutableStateFlow(PhotoDetailUiState())
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            UpscaylUpscaler.init(application)
        }
        if (photoId > 0) {
            loadPhoto()
        }
    }

    private fun loadPhoto() {
        viewModelScope.launch {
            val photo = photoRepository.getPhotoById(photoId)
            _uiState.update {
                it.copy(photo = photo, isLoading = false)
            }
        }
    }

    fun upscalePhoto() {
        val photo = _uiState.value.photo ?: return
        if (_uiState.value.isUpscaling) return

        _uiState.update { it.copy(isUpscaling = true, upscaleProgress = "Loading image...", errorMessage = null, isSaved = false) }

        viewModelScope.launch {
            try {
                if (!UpscaylUpscaler.isReady()) {
                    _uiState.update { it.copy(upscaleProgress = "Downloading AI model (64MB)...") }
                    UpscaylUpscaler.init(application)
                }

                val result = withContext(Dispatchers.IO) {
                    _uiState.update { it.copy(upscaleProgress = "Enhancing with AI...") }

                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeFile(photo.filePath, options)
                        ?: throw Exception("Failed to load image")

                    try {
                        val upscaled = UpscaylUpscaler.upscale(bitmap)
                        bitmap.recycle()

                        _uiState.update { it.copy(upscaleProgress = "Saving...") }

                        val outputFile = photoRepository.createPhotoFile()
                        outputFile.outputStream().use { fos ->
                            upscaled.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                        }
                        val w = upscaled.width
                        val h = upscaled.height
                        upscaled.recycle()

                        val updatedEntity = photo.copy(
                            filePath = outputFile.absolutePath,
                            width = w,
                            height = h
                        )
                        photoRepository.updatePhoto(updatedEntity)
                        updatedEntity
                    } catch (e: OutOfMemoryError) {
                        bitmap.recycle()
                        throw Exception("Not enough memory to upscale")
                    }
                }

                _uiState.update {
                    it.copy(
                        photo = result,
                        isUpscaling = false,
                        upscaleProgress = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUpscaling = false,
                        upscaleProgress = null,
                        errorMessage = e.message ?: "Upscale failed"
                    )
                }
            }
        }
    }

    fun saveToGallery() {
        val photo = _uiState.value.photo ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val context = application
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "MoodSnap_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MoodSnap")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { stream ->
                            val bitmap = BitmapFactory.decodeFile(photo.filePath)
                            bitmap?.compress(Bitmap.CompressFormat.JPEG, 98, stream)
                            bitmap?.recycle()
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                } catch (_: Exception) {}
            }
            _uiState.update { it.copy(isSaved = true) }
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(isSaved = false) }
        }
    }

    fun deletePhoto() {
        viewModelScope.launch {
            photoId.let { id ->
                photoRepository.deletePhotoById(id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        UpscaylUpscaler.close()
    }
}
