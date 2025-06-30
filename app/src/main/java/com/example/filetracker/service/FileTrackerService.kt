package com.example.filetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.filetracker.R
import com.example.filetracker.data.AppDatabase
import kotlinx.coroutines.*

class FileTrackerService : Service() {

    private val observers = mutableListOf<FileObserverWrapper>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        observeTrackers()
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "file_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "File Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("File Tracker работает")
            .setContentText("Слежение за файлами включено")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        startForeground(1, notification)
    }

    private fun observeTrackers() {
        val db = AppDatabase.getDatabase(applicationContext)
        // Предполагается, что getAll() возвращает LiveData<List<Tracker>>
        db.trackerDao().getAll().observeForever { trackers ->
            // Останавливаем все старые наблюдатели
            observers.forEach { it.stopWatching() }
            observers.clear()
            // Запускаем новых наблюдателей
            trackers.forEach { tracker ->
                val observer = FileObserverWrapper(
                    context = this,
                    sourceUri = Uri.parse(tracker.sourceUri),
                    destUri = Uri.parse(tracker.destUri)
                )
                observer.startWatching()
                observers.add(observer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observers.forEach { it.stopWatching() }
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}