package com.moodcamera

import android.app.Application
import com.moodcamera.processing.enhance.AiEnhancer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MoodSnapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AiEnhancer.init(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        AiEnhancer.close()
    }
}
