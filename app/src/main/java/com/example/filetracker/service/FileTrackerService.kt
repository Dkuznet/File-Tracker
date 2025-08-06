package com.example.filetracker.service

import android.Manifest
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
import com.example.filetracker.data.OutputDirRepository
import com.example.filetracker.data.Tracker
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FileTrackerService : Service() {

    private val latestFolderWatchers = mutableListOf<LatestFolderWatcher>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var imageObserver: MediaContentObserver
    private lateinit var audioObserver: MediaContentObserver
    private lateinit var videoObserver: MediaContentObserver
    private var outputDir: String? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate() {
        super.onCreate()
        EventLogger.log(
            message = "onCreate called in FileTrackerService",
            logTag = "FileTrackerService",
            extra = true
        )
        checkPostNotificationsPermission()
        startForegroundServiceWithNotification()
        initializeObservers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        EventLogger.log(
            message = "onStartCommand called: intent=$intent, flags=$flags, startId=$startId",
            logTag = "FileTrackerService",
            extra = true
        )
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
                    EventLogger.log(
                        message = "outputDir не определён",
                        logTag = "FileTrackerService",
                        log = LogLevel.ERROR,
                        extra = true
                    )
                }
            }
        }
        return START_STICKY // Сервис будет перезапущен, если система его завершит
    }

    private fun checkPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                EventLogger.log(
                    message = "POST_NOTIFICATIONS != PERMISSION_GRANTED",
                    logTag = "checkPostNotificationsPermission",
                    log = LogLevel.ERROR
                )
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "fit_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Fit Tracker Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления работы Fit Tracker"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Fit Tracker работает")
            .setContentText("Status: active")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        startForeground(1, notification)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun initializeObservers() {
        // Инициализация FileObserver
        observeTrackers()
        // Инициализация MediaContentObservers
        registerMediaObservers()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun observeTrackers() {
        val db = AppDatabase.getDatabase()
        scope.launch(Dispatchers.Main) {
            // Останавливаем и очищаем старые watcher'ы
            latestFolderWatchers.forEach { it.stopWatching() }
            latestFolderWatchers.clear()
            db.trackerDao().getAll().observeForever { trackers: List<Tracker> ->
                latestFolderWatchers.forEach { it.stopWatching() }
                latestFolderWatchers.clear()
                trackers
                    .filter { it.isActive }
                    .forEach { tracker ->
                        val watcher = LatestFolderWatcher(
                            context = this@FileTrackerService,
                            rootPath = tracker.sourceDir,
                            destDirPath = tracker.destDir,
                            watchSubfolders = tracker.watchSubfolders
                        )
                        watcher.startWatching()
                        latestFolderWatchers.add(watcher)
                    }
                EventLogger.log(
                    message = "Fit active trackers: ${latestFolderWatchers.size}",
                    logTag = "FileTrackerService"
                )
            }
        }
    }

    private fun registerMediaObservers() {
        return
//        if (outputDir == null) {
//            EventLogger.log(
//                message = "outputDir не определён, MediaContentObservers не зарегистрированы",
//                logTag = "registerMediaObservers",
//                log = LogLevel.ERROR,
//            )
//            return
//        }
//        val handler = Handler(Looper.getMainLooper())
//        EventLogger.log(
//            message = "registerMediaObservers with outputDir=$outputDir",
//            logTag = "registerMediaObservers",
//            extra = true
//        )
//
//        imageObserver = MediaContentObserver(this, handler, MediaType.IMAGE, outputDir!!)
//        audioObserver = MediaContentObserver(this, handler, MediaType.AUDIO, outputDir!!)
//        videoObserver = MediaContentObserver(this, handler, MediaType.VIDEO, outputDir!!)
//        contentResolver.registerContentObserver(
//            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//            true,
//            imageObserver
//        )
//        contentResolver.registerContentObserver(
//            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
//            true,
//            audioObserver
//        )
//        contentResolver.registerContentObserver(
//            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
//            true,
//            videoObserver
//        )
//        EventLogger.log(
//            message = "MediaContentObservers зарегистрированы",
//            logTag = "FileTrackerService"
//        )
    }

    private fun unregisterMediaObservers() {
        if (::imageObserver.isInitialized) contentResolver.unregisterContentObserver(imageObserver)
        if (::audioObserver.isInitialized) contentResolver.unregisterContentObserver(audioObserver)
        if (::videoObserver.isInitialized) contentResolver.unregisterContentObserver(videoObserver)
        EventLogger.log(
            message = "MediaContentObservers отменены",
            logTag = "FileTrackerService"
        )
    }

    override fun onDestroy() {
        EventLogger.log(
            message = "onDestroy called in FileTrackerService",
            logTag = "FileTrackerService"
        )
        latestFolderWatchers.forEach { it.stopWatching() }
        latestFolderWatchers.clear()
        unregisterMediaObservers()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}