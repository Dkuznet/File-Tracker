package com.example.filetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object AppNameRepository {
    private val APP_NAME_KEY = stringPreferencesKey("app_name")

    suspend fun saveAppName(context: Context, appName: String?) {
        context.dataStore.edit { preferences ->
            if (appName != null) {
                preferences[APP_NAME_KEY] = appName
            } else {
                preferences.remove(APP_NAME_KEY)
            }
        }
    }

    fun getAppNameFlow(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[APP_NAME_KEY]
        }
    }

    suspend fun getAppName(context: Context): String? {
        return getAppNameFlow(context).first()
    }
} 