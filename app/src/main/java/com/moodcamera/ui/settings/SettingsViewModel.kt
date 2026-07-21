package com.moodcamera.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodcamera.data.local.DataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isGrainEnabled: Boolean = true,
    val isHalationEnabled: Boolean = true,
    val isFrameEnabled: Boolean = false,
    val isGridEnabled: Boolean = false,
    val isAutoFilterEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStoreManager.cameraSettingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        isGrainEnabled = settings.isGrainEnabled,
                        isHalationEnabled = settings.isHalationEnabled,
                        isFrameEnabled = settings.isFrameEnabled,
                        isGridEnabled = settings.isGridEnabled,
                        isAutoFilterEnabled = settings.isAutoFilterEnabled
                    )
                }
            }
        }
    }

    fun toggleGrain() {
        val newState = !_uiState.value.isGrainEnabled
        _uiState.update { it.copy(isGrainEnabled = newState) }
        viewModelScope.launch { dataStoreManager.updateAutoFilter(newState) }
    }

    fun toggleHalation() {
        val newState = !_uiState.value.isHalationEnabled
        _uiState.update { it.copy(isHalationEnabled = newState) }
    }

    fun toggleFrame() {
        val newState = !_uiState.value.isFrameEnabled
        _uiState.update { it.copy(isFrameEnabled = newState) }
    }

    fun toggleGrid() {
        val newState = !_uiState.value.isGridEnabled
        _uiState.update { it.copy(isGridEnabled = newState) }
        viewModelScope.launch { dataStoreManager.updateGrid(newState) }
    }

    fun toggleAutoFilter() {
        val newState = !_uiState.value.isAutoFilterEnabled
        _uiState.update { it.copy(isAutoFilterEnabled = newState) }
        viewModelScope.launch { dataStoreManager.updateAutoFilter(newState) }
    }
}
