package com.example.filetracker.util

import android.content.Context
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object EventLogger {
    fun log(context: Context, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).eventLogDao().insert(
                EventLog(timestamp = System.currentTimeMillis(), message = message)
            )
        }
    }
}