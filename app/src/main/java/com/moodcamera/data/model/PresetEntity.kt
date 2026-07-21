package com.moodcamera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val emulationType: String,
    val grainIntensity: Float = 0.5f,
    val halationIntensity: Float = 0.3f,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f,
    val brightness: Float = 0.0f,
    val temperature: Float = 0.0f,
    val tint: Float = 0.0f,
    val fade: Float = 0.0f,
    val vignette: Float = 0.0f,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)