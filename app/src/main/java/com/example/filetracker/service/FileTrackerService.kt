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
import com.example.filetracker.data.OutputDirRepository
import com.example.filetracker.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FileTrackerService : Service() {

    private val observers = mutableListOf<FileObserverWrapper>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var imageObserver: MediaContentObserver
    private lateinit var audioObserver: MediaContentObserver
    private lateinit var videoObserver: MediaContentObserver
    private var outputDir: String? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        checkPostNotificationsPermission()
        startForegroundServiceWithNotification()
        initializeObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Получаем outputDir из Intent, если он передан
        intent?.getStringExtra("OUTPUT_DIR")?.let { newOutputDir ->
            outputDir = newOutputDir
            // Переинициализируем MediaContentObservers с новым outputDir
            unregisterMediaObservers()
            registerMediaObservers()
        }
        // Если outputDir не передан, пытаемся получить его из репозитория
        if (outputDir == null) {
            scope.launch {
                outputDir = OutputDirRepository.getOutputDir(this@FileTrackerService)
                if (outputDir != null) {
                    // Переинициализируем MediaContentObservers с полученным outputDir
                    unregisterMediaObservers()
                    registerMediaObservers()
                } else {
                    Log.e("FileTrackerService", "outputDir не определён")
                    EventLogger.log(this@FileTrackerService, "outputDir не определён")
                }
            }
        }
        return START_STICKY // Сервис будет перезапущен, если система его завершит
    }

    private fun checkPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
                NotificationManager.IMPORTANCE_DEFAULT
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
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        startForeground(1, notification)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun initializeObservers() {
        // Инициализация FileObserverWrapper
        // observeTrackers()
        // Инициализация MediaContentObservers
        registerMediaObservers()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun observeTrackers() {
        val db = AppDatabase.getDatabase(applicationContext)
        scope.launch {
            db.trackerDao().getAll().observeForever { trackers ->
                observers.forEach { it.stopWatching() }
                observers.clear()
                trackers
                    .filter { it.isActive }
                    .forEach { tracker ->
                        val observer = FileObserverWrapper(
                            this@FileTrackerService,
                            tracker.sourceDir,
                            tracker.destDir
                        )
                        observer.startWatching()
                        observers.add(observer)
                    }
                EventLogger.log(
                    this@FileTrackerService,
                    "Слежение за файлами включено ${observers.size}"
                )
            }
        }
    }

    private fun registerMediaObservers() {
        if (outputDir == null) {
            Log.e(
                "registerMediaObservers",
                "outputDir не определён, MediaContentObservers не зарегистрированы"
            )
            //EventLogger.log(this, "outputDir не определён, MediaContentObservers не зарегистрированы")
            return
        }
        val handler = Handler(Looper.getMainLooper())
        Log.d("registerMediaObservers", "outputDir=$outputDir")

        imageObserver = MediaContentObserver(this, handler, MediaType.IMAGE, outputDir!!)
        audioObserver = MediaContentObserver(this, handler, MediaType.AUDIO, outputDir!!)
        videoObserver = MediaContentObserver(this, handler, MediaType.VIDEO, outputDir!!)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            imageObserver
        )
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            audioObserver
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            videoObserver
        )
        Log.d("FileTrackerService", "MediaContentObservers зарегистрированы")
        EventLogger.log(this, "MediaContentObservers зарегистрированы")
    }

    private fun unregisterMediaObservers() {
        if (::imageObserver.isInitialized) contentResolver.unregisterContentObserver(imageObserver)
        if (::audioObserver.isInitialized) contentResolver.unregisterContentObserver(audioObserver)
        if (::videoObserver.isInitialized) contentResolver.unregisterContentObserver(videoObserver)
        Log.d("FileTrackerService", "MediaContentObservers отменены")
        EventLogger.log(this, "MediaContentObservers отменены")
    }

    override fun onDestroy() {
        observers.forEach { it.stopWatching() }
        unregisterMediaObservers()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}