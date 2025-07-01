package com.example.filetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.Tracker
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).trackerDao()
    val trackers: LiveData<List<Tracker>> = dao.getAll()

    fun addTracker(sourceUri: String, destUri: String) {
        if (canAddTracker()) {
            viewModelScope.launch {
                dao.insert(Tracker(sourceUri = sourceUri, destUri = destUri))
            }
        }
        // можно уведомить UI если лимит превышен
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

    fun canAddTracker(): Boolean = trackers.value?.size ?: 0 < 5
}