package com.moodcamera.ui.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodcamera.data.model.PhotoEntity
import com.moodcamera.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoDetailUiState(
    val photo: PhotoEntity? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val photoId: Long = savedStateHandle.get<Long>("photoId") ?: -1L

    private val _uiState = MutableStateFlow(PhotoDetailUiState())
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    init {
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

    fun deletePhoto() {
        viewModelScope.launch {
            photoId.let { id ->
                photoRepository.deletePhotoById(id)
            }
        }
    }
}
