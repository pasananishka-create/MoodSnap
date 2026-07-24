package com.moodcamera.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moodcamera.domain.model.AspectRatio
import com.moodcamera.domain.model.CameraSettings
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.QualityType
import com.moodcamera.domain.model.ToneType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera_settings")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val EMULATION_TYPE = stringPreferencesKey("emulation_type")
        val TONE_TYPE = stringPreferencesKey("tone_type")
        val QUALITY_TYPE = stringPreferencesKey("quality_type")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val EXPOSURE_COMPENSATION = floatPreferencesKey("exposure_compensation")
        val IS_AUTO_FILTER_ENABLED = booleanPreferencesKey("is_auto_filter_enabled")
        val IS_GRAIN_ENABLED = booleanPreferencesKey("is_grain_enabled")
        val IS_HALATION_ENABLED = booleanPreferencesKey("is_halation_enabled")
        val IS_FRAME_ENABLED = booleanPreferencesKey("is_frame_enabled")
        val IS_GRID_ENABLED = booleanPreferencesKey("is_grid_enabled")
        val IS_FRONT_CAMERA = booleanPreferencesKey("is_front_camera")
    }

    val cameraSettingsFlow: Flow<CameraSettings> = context.dataStore.data.map { prefs ->
        CameraSettings(
            emulationType = try {
                EmulationType.valueOf(prefs[Keys.EMULATION_TYPE] ?: "ORIGINAL")
            } catch (_: Exception) { EmulationType.ORIGINAL },
            toneType = try {
                ToneType.valueOf(prefs[Keys.TONE_TYPE] ?: "NEUTRAL")
            } catch (_: Exception) { ToneType.NEUTRAL },
            qualityType = try {
                QualityType.valueOf(prefs[Keys.QUALITY_TYPE] ?: "ISO_200")
            } catch (_: Exception) { QualityType.ISO_200 },
            aspectRatio = try {
                AspectRatio.valueOf(prefs[Keys.ASPECT_RATIO] ?: "FOUR_THREE")
            } catch (_: Exception) { AspectRatio.FOUR_THREE },
            exposureCompensation = prefs[Keys.EXPOSURE_COMPENSATION] ?: 0f,
            isAutoFilterEnabled = prefs[Keys.IS_AUTO_FILTER_ENABLED] ?: false,
            isGrainEnabled = prefs[Keys.IS_GRAIN_ENABLED] ?: true,
            isHalationEnabled = prefs[Keys.IS_HALATION_ENABLED] ?: true,
            isFrameEnabled = prefs[Keys.IS_FRAME_ENABLED] ?: false,
            isGridEnabled = prefs[Keys.IS_GRID_ENABLED] ?: false,
            isFrontCamera = prefs[Keys.IS_FRONT_CAMERA] ?: false
        )
    }

    suspend fun updateSettings(settings: CameraSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EMULATION_TYPE] = settings.emulationType.name
            prefs[Keys.TONE_TYPE] = settings.toneType.name
            prefs[Keys.QUALITY_TYPE] = settings.qualityType.name
            prefs[Keys.ASPECT_RATIO] = settings.aspectRatio.name
            prefs[Keys.EXPOSURE_COMPENSATION] = settings.exposureCompensation
            prefs[Keys.IS_AUTO_FILTER_ENABLED] = settings.isAutoFilterEnabled
            prefs[Keys.IS_GRAIN_ENABLED] = settings.isGrainEnabled
            prefs[Keys.IS_HALATION_ENABLED] = settings.isHalationEnabled
            prefs[Keys.IS_FRAME_ENABLED] = settings.isFrameEnabled
            prefs[Keys.IS_GRID_ENABLED] = settings.isGridEnabled
            prefs[Keys.IS_FRONT_CAMERA] = settings.isFrontCamera
        }
    }

    suspend fun updateEmulationType(type: EmulationType) {
        context.dataStore.edit { it[Keys.EMULATION_TYPE] = type.name }
    }

    suspend fun updateToneType(type: ToneType) {
        context.dataStore.edit { it[Keys.TONE_TYPE] = type.name }
    }

    suspend fun updateQualityType(type: QualityType) {
        context.dataStore.edit { it[Keys.QUALITY_TYPE] = type.name }
    }

    suspend fun updateAspectRatio(ratio: AspectRatio) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = ratio.name }
    }

    suspend fun updateExposureCompensation(value: Float) {
        context.dataStore.edit { it[Keys.EXPOSURE_COMPENSATION] = value }
    }

    suspend fun updateAutoFilter(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_AUTO_FILTER_ENABLED] = enabled }
    }

    suspend fun updateGrid(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_ENABLED] = enabled }
    }

    suspend fun updateFrontCamera(front: Boolean) {
        context.dataStore.edit { it[Keys.IS_FRONT_CAMERA] = front }
    }
}
