package com.example.filetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.filetracker.R
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.uriToFilePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FileTrackerService : Service() {

    private val observers = mutableListOf<FileObserverWrapper>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        checkPostNotificationsPermission()
        startForegroundServiceWithNotification()
        observeTrackers()
    }
    private fun checkPostNotificationsPermission() {
        // На Android 13+ нужно разрешение на уведомления
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Можно уведомить пользователя или залогировать, но сервис будет без уведомлений
                // Не запрашиваем здесь, так как это Service, а не Activity
                EventLogger.log(this, ".POST_NOTIFICATIONS) != PERMISSION_GRANTED")
            }
        }
    }
    private fun startForegroundServiceWithNotification() {
        val channelId = "file_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "File Tracker Service",
                NotificationManager.IMPORTANCE_DEFAULT // Важно: DEFAULT для видимости уведомления
            ).apply {
                description = "Уведомления работы File Tracker"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("File Tracker работает")
            .setContentText("Слежение за файлами включено")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true) // Несмахиваемое уведомление
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        startForeground(1, notification)
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun observeTrackers() {
        val db = AppDatabase.getDatabase(applicationContext)
        db.trackerDao().getAll().observeForever { trackers ->
            observers.forEach { it.stopWatching() }
            observers.clear()
            trackers
                .filter { it.isActive } // следим только за активными
                .forEach { tracker ->
                    val srcPath = uriToFilePath(tracker.sourceUri)
                    val dstPath = uriToFilePath(tracker.destUri)
                    // grantUriPermission больше не нужен для путей
                    if (srcPath != null && dstPath != null) {
                        val observer = FileObserverWrapper(this, srcPath, dstPath)
                        observer.startWatching()
                        observers.add(observer)
                    } else {
                        // Логируйте или обработайте ошибку
                        EventLogger.log(this, "error: srcPath != null && dstPath != null")
                    }

                }
        }
        EventLogger.log(this, "run observeTrackers")
    }
    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}