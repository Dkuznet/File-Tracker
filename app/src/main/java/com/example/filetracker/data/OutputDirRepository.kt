package com.example.filetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


object OutputDirRepository {

    private val OUTPUT_DIR_KEY = stringPreferencesKey("output_dir")

    // Сохранение outputDir
    suspend fun saveOutputDir(context: Context, outputDir: String?) {
        context.dataStore.edit { preferences ->
            if (outputDir != null) {
                preferences[OUTPUT_DIR_KEY] = outputDir
            } else {
                preferences.remove(OUTPUT_DIR_KEY) // Удаляем ключ, если outputDir == null
            }
        }
    }

    // Получение outputDir как Flow
    fun getOutputDirFlow(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[OUTPUT_DIR_KEY]
        }
    }

    // Получение outputDir синхронно (для случаев, когда нельзя использовать корутины)
    suspend fun getOutputDir(context: Context): String? {
        return getOutputDirFlow(context).first()
    }
}