package com.moodcamera.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.moodcamera.data.local.PhotoDao
import com.moodcamera.data.model.PhotoEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoDao: PhotoDao
) {
    fun getAllPhotos(): Flow<List<PhotoEntity>> = photoDao.getAllPhotos()

    fun getPhotoCount(): Flow<Int> = photoDao.getPhotoCount()

    suspend fun getPhotoById(id: Long): PhotoEntity? = photoDao.getPhotoById(id)

    suspend fun insertPhoto(photo: PhotoEntity): Long = photoDao.insertPhoto(photo)

    suspend fun updatePhoto(photo: PhotoEntity) = photoDao.updatePhoto(photo)

    suspend fun deletePhoto(photo: PhotoEntity) {
        try {
            File(photo.filePath).delete()
            photo.originalFilePath?.let { File(it).delete() }
        } catch (_: Exception) {}
        photoDao.deletePhoto(photo)
    }

    suspend fun deletePhotoById(id: Long) {
        val photo = photoDao.getPhotoById(id) ?: return
        deletePhoto(photo)
    }

    fun createPhotoFile(): File {
        val photosDir = File(context.filesDir, "photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        return File(photosDir, "IMG_${timestamp}.jpg")
    }

    fun createTempPhotoFile(): File {
        val photosDir = File(context.cacheDir, "photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        return File(photosDir, "TEMP_${timestamp}.jpg")
    }

    fun copyFileToInternal(sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val destFile = createPhotoFile()
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPhotoFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
