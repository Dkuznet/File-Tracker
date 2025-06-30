package com.example.filetracker.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.filetracker.data.*
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).trackerDao()
    val trackers: LiveData<List<Tracker>> = dao.getAll()

    fun addTracker(sourceUri: String, destUri: String) {
        viewModelScope.launch {
            dao.insert(Tracker(sourceUri = sourceUri, destUri = destUri))
        }
    }

    fun removeTracker(tracker: Tracker) {
        viewModelScope.launch {
            dao.delete(tracker)
        }
    }
}