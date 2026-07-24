package com.moodcamera.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodcamera.domain.model.EmulationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PresetsUiState(
    val selectedEmulation: EmulationType = EmulationType.ORIGINAL
)

@HiltViewModel
class PresetsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PresetsUiState())
    val uiState: StateFlow<PresetsUiState> = _uiState.asStateFlow()

    fun selectEmulation(type: EmulationType) {
        _uiState.update { it.copy(selectedEmulation = type) }
    }
}
