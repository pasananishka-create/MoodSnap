package com.moodcamera.di

import android.content.Context
import androidx.room.Room
import com.moodcamera.data.local.AppDatabase
import com.moodcamera.data.local.DataStoreManager
import com.moodcamera.data.local.PhotoDao
import com.moodcamera.data.local.PresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun providePhotoDao(database: AppDatabase): PhotoDao {
        return database.photoDao()
    }

    @Provides
    fun providePresetDao(database: AppDatabase): PresetDao {
        return database.presetDao()
    }
}
