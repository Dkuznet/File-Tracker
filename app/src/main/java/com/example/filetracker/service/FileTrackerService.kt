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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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

    private fun grantUriPermission(uri: Uri) {
        val takeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
        }
    }

    private fun observeTrackers() {
        val db = AppDatabase.getDatabase(applicationContext)
        db.trackerDao().getAll().observeForever { trackers ->
            observers.forEach { it.stopWatching() }
            observers.clear()
            trackers.forEach { tracker ->
                val srcUri = Uri.parse(tracker.sourceUri)
                val dstUri = Uri.parse(tracker.destUri)
                grantUriPermission(srcUri)
                grantUriPermission(dstUri)
                val observer = FileObserverWrapper(this, srcUri, dstUri)
                observer.startWatching()
                observers.add(observer)
            }
        }
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}