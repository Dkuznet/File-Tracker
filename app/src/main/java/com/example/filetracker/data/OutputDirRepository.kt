package com.example.filetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object OutputDirRepository {
    private val OUTPUT_DIR_KEY = stringPreferencesKey("output_dir")

    fun getOutputDirFlow(context: Context): Flow<String?> =
        context.dataStore.data.map { it[OUTPUT_DIR_KEY] }

    suspend fun saveOutputDir(context: Context, uri: String) {
        context.dataStore.edit { it[OUTPUT_DIR_KEY] = uri }
    }
}