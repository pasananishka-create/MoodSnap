package com.moodcamera.data.repository

import com.moodcamera.data.local.PresetDao
import com.moodcamera.data.model.PresetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao
) {
    fun getAllPresets(): Flow<List<PresetEntity>> = presetDao.getAllPresets()

    fun getPresetCount(): Flow<Int> = presetDao.getPresetCount()

    suspend fun getPresetById(id: Long): PresetEntity? = presetDao.getPresetById(id)

    suspend fun insertPreset(preset: PresetEntity): Long = presetDao.insertPreset(preset)

    suspend fun updatePreset(preset: PresetEntity) = presetDao.updatePreset(preset)

    suspend fun deletePreset(preset: PresetEntity) = presetDao.deletePreset(preset)

    suspend fun deleteCustomPresetById(id: Long) = presetDao.deleteCustomPresetById(id)
}
