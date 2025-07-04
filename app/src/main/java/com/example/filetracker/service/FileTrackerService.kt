package com.example.filetracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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
    private lateinit var mediaContentObserver: MediaContentObserver

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        checkPostNotificationsPermission()
        startForegroundServiceWithNotification()
        observeTrackers()
        // Инициализируем и регистрируем MediaContentObserver
        mediaContentObserver = MediaContentObserver(this, Handler(Looper.getMainLooper()))
        registerMediaObserver()
    }

    private fun checkPostNotificationsPermission() {
        // На Android 13+ нужно разрешение на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Можно уведомить пользователя или залогировать, но сервис будет без уведомлений
                // Не запрашиваем здесь, так как это Service, а не Activity
                EventLogger.log(this, "POST_NOTIFICATIONS != PERMISSION_GRANTED")
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
            EventLogger.log(this, "Слежение за файлами включено ${observers.size}")
        }
    }

    private fun registerMediaObserver() {
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true, // Уведомлять о любых изменениях в поддереве
            mediaContentObserver
        )
        //EventLogger.log(this, "MediaContentObserver зарегистрирован")
        Log.d("FileTrackerService", "MediaContentObserver зарегистрирован")

    }

    private fun unregisterMediaObserver() {
        contentResolver.unregisterContentObserver(mediaContentObserver)
//        EventLogger.log(this, "MediaContentObserver отменён")
        Log.d("FileTrackerService", "MediaContentObserver отменён")
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        unregisterMediaObserver() // Отменяем регистрацию MediaContentObserver
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}