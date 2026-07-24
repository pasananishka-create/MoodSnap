package com.moodcamera.ui.gallery

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodcamera.data.model.PhotoEntity
import com.moodcamera.data.repository.PhotoRepository
import com.moodcamera.processing.enhance.AiEnhancer
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
    val upscaleProgress: String? = null
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
        UpscaylUpscaler.init(application)
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

        _uiState.update { it.copy(isUpscaling = true, upscaleProgress = "Loading image...") }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    _uiState.update { it.copy(upscaleProgress = "Upscaling with AI...") }

                    val bitmap = BitmapFactory.decodeFile(photo.filePath)
                        ?: throw Exception("Failed to load image")

                    val upscaled = UpscaylUpscaler.upscale(bitmap)
                    bitmap.recycle()

                    _uiState.update { it.copy(upscaleProgress = "Saving...") }

                    val outputFile = photoRepository.createPhotoFile()
                    outputFile.outputStream().use { fos ->
                        upscaled.compress(Bitmap.CompressFormat.JPEG, 98, fos)
                    }
                    upscaled.recycle()

                    val updatedEntity = photo.copy(
                        filePath = outputFile.absolutePath,
                        width = 0,
                        height = 0
                    )
                    photoRepository.updatePhoto(updatedEntity)
                    updatedEntity
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
                        upscaleProgress = null
                    )
                }
            }
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
