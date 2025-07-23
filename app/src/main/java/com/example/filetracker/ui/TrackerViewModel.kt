package com.example.filetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.Tracker
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.LogLevel
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase().trackerDao()
    val trackers: LiveData<List<Tracker>> = dao.getAll()

    fun addTracker(sourceDir: String?, destDir: String?) {
        if (canAddTracker()) {
            if (sourceDir == null) {
                // Логируем ошибку или уведомляем UI
                EventLogger.log(
                    message = "sourceDir не может быть null",
                    logTag = "addTracker",
                    log = LogLevel.ERROR
                )
                return
            }
            if (destDir == null) {
                EventLogger.log(
                    message = "destDir не может быть null",
                    logTag = "addTracker",
                    log = LogLevel.ERROR
                )
                return
            }
            viewModelScope.launch {
                dao.insert(Tracker(sourceDir = sourceDir, destDir = destDir))
            }
        } else {
            EventLogger.log(
                message = "Достигнут лимит трекеров",
                logTag = "addTracker",
                log = LogLevel.WARN
            )
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