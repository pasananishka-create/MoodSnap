package com.moodcamera.domain.model

data class Photo(
    val id: Long,
    val filePath: String,
    val originalFilePath: String?,
    val presetId: Long?,
    val presetName: String?,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val width: Int,
    val height: Int
)