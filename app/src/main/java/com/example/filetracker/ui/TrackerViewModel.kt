package com.example.filetracker.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.Tracker
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase().trackerDao()
    val trackers: LiveData<List<Tracker>> = dao.getAll()

    fun addTracker(sourceDir: String?, destDir: String?) {
        if (canAddTracker()) {
            if (sourceDir == null) {
                // Логируем ошибку или уведомляем UI
                Log.e("addTracker", "sourceDir не может быть null")
                return
            }
            if (destDir == null) {
                // Логируем ошибку или уведомляем UI
                Log.e("addTracker", "destDir не может быть null")
                return
            }
            viewModelScope.launch {
                dao.insert(Tracker(sourceDir = sourceDir, destDir = destDir))
            }
        } else {
            // Уведомляем UI, если лимит превышен
            Log.w("addTracker", "Достигнут лимит трекеров")
        }
    }
    fun removeTracker(tracker: Tracker) {
        viewModelScope.launch {
            dao.delete(tracker)
        }
    }

    fun setActive(tracker: Tracker, isActive: Boolean) {
        viewModelScope.launch {
            dao.setActive(tracker.id, isActive)
        }
    }

    fun canAddTracker(): Boolean = trackers.value?.size ?: 0 < 10
}