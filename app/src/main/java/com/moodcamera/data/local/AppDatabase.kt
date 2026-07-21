package com.moodcamera.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodcamera.data.model.PhotoEntity
import com.moodcamera.data.model.PresetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [PhotoEntity::class, PresetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moodsnap_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(database.presetDao())
                }
            }
        }

        suspend fun populateDatabase(presetDao: PresetDao) {
            val builtInPresets = listOf(
                PresetEntity(name = "Portra", emulationType = "PORTRA", isBuiltIn = true),
                PresetEntity(name = "Cinestill 800T", emulationType = "CINESTILL_800T", halationIntensity = 0.8f, isBuiltIn = true),
                PresetEntity(name = "Ektar", emulationType = "EKTAR", saturation = 1.3f, contrast = 1.1f, isBuiltIn = true),
                PresetEntity(name = "Fuji 400H", emulationType = "FUJI_400H", saturation = 0.8f, fade = 0.2f, isBuiltIn = true),
                PresetEntity(name = "Velvia", emulationType = "VELVIA", saturation = 1.4f, contrast = 1.2f, isBuiltIn = true),
                PresetEntity(name = "Provia", emulationType = "PROVIA", isBuiltIn = true),
                PresetEntity(name = "Tri-X", emulationType = "TRI_X", saturation = 0f, contrast = 1.3f, isBuiltIn = true),
                PresetEntity(name = "HP5+", emulationType = "HP5", saturation = 0f, grainIntensity = 0.7f, isBuiltIn = true),
                PresetEntity(name = "Arizona", emulationType = "ARIZONA", temperature = 0.3f, saturation = 1.1f, isBuiltIn = true),
                PresetEntity(name = "Metro", emulationType = "METRO", saturation = 0.7f, temperature = -0.2f, isBuiltIn = true),
                PresetEntity(name = "Gold 200", emulationType = "GOLD_200", temperature = 0.2f, saturation = 1.1f, isBuiltIn = true),
                PresetEntity(name = "Ultramax", emulationType = "ULTRAMAX", saturation = 1.2f, contrast = 1.1f, isBuiltIn = true)
            )
            builtInPresets.forEach { presetDao.insertPreset(it) }
        }
    }
}
