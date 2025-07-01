package com.example.filetracker.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R
import com.example.filetracker.data.OutputDirRepository
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.buildDestPath
import com.example.filetracker.util.createDestDirIfNotExists
import com.example.filetracker.util.getShortPath
import com.example.filetracker.util.uriToFilePath
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val trackerViewModel: TrackerViewModel by viewModels()
    private var pickedSourcePath: String? = null
    private var outputDirPath: String? = null
    private lateinit var outputDirText: TextView

    private val pickOutputDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = uriToFilePath(this, uri)
            if (path != null) {
                outputDirPath = path
                runBlocking {
                    OutputDirRepository.saveOutputDir(this@MainActivity, path)
                }
                updateOutputDirText()
                Toast.makeText(this, "Выходная папка выбрана", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось получить путь к папке", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private val REQUEST_FOREGROUND_SERVICE_DATA_SYNC = 1001

    private val pickSourceDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null && outputDirPath != null) {
            val path = uriToFilePath(this, uri)
            if (path != null) {
                pickedSourcePath = path
                val destPath = buildDestPath(outputDirPath!!, path)
                createDestDirIfNotExists(this, outputDirPath!!, destPath)
                trackerViewModel.addTracker(path, destPath)
            } else {
                Toast.makeText(this, "Не удалось получить путь к папке", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Разрешение на уведомления выдано", Toast.LENGTH_SHORT).show()
            showNotification()
        } else {
            Toast.makeText(this, "Нет разрешения на уведомления", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        outputDirText = findViewById(R.id.outputDirText)

        requestForegroundServiceDataSyncPermissionIfNeeded()
        checkAndRequestAllFilesPermission()

        // Загрузить OutputDir (строка-путь) из DataStore при запуске
        runBlocking {
            val pathStr = OutputDirRepository.getOutputDirFlow(this@MainActivity).first()
            if (pathStr != null) outputDirPath = pathStr
        }
        updateOutputDirText()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = TrackerAdapter(
            onDeleteClick = { tracker -> trackerViewModel.removeTracker(tracker) },
            onToggleActive = { tracker, isActive -> trackerViewModel.setActive(tracker, isActive) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        trackerViewModel.trackers.observe(this, Observer { trackers ->
            trackers?.let { adapter.submitList(it) }
        })

        findViewById<View>(R.id.chooseOutputDirButton).setOnClickListener {
            pickOutputDirLauncher.launch(null)
        }

        findViewById<View>(R.id.addTrackerButton).setOnClickListener {
            if (trackerViewModel.canAddTracker()) {
                if (outputDirPath == null) {
                    Toast.makeText(this, "Сначала выберите выходную папку", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    pickSourceDirectory()
                }
            } else {
                Toast.makeText(this, "Максимум 5 трекеров", Toast.LENGTH_SHORT).show()
            }
        }

        // Открытие окна EventLog по нажатию кнопки Log
        findViewById<View>(R.id.logButton).setOnClickListener {
            startActivity(Intent(this, EventLogActivity::class.java))
        }

        // Запросить разрешение на уведомления и показать уведомление по нажатию кнопки
        findViewById<View>(R.id.okNotifyButton).setOnClickListener {
            requestNotificationPermissionIfNeededAndShow()
        }

        findViewById<View>(R.id.minimizeButton).setOnClickListener {
            moveTaskToBack(true)
        }
        findViewById<View>(R.id.startServiceButton).setOnClickListener {
            EventLogger.log(this, "Нажата кнопка 'Запустить сервис'")
            startForegroundService(
                Intent(
                    this,
                    com.example.filetracker.service.FileTrackerService::class.java
                )
            )
        }
        findViewById<View>(R.id.stopServiceButton).setOnClickListener {
            EventLogger.log(this, "Нажата кнопка 'Остановить сервис'")
            stopService(
                Intent(
                    this,
                    com.example.filetracker.service.FileTrackerService::class.java
                )
            )
        }
    }

    private fun requestForegroundServiceDataSyncPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34)
            if (checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC),
                    REQUEST_FOREGROUND_SERVICE_DATA_SYNC
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeededAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // Разрешение уже есть или не требуется
        showNotification()
    }

    private fun showNotification() {
        val channelId = "file_tracker_channel"
        // Создать канал уведомлений, если это необходимо
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "File Tracker Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления работы File Tracker"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("File Tracker")
            .setContentText("ОК")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun pickSourceDirectory() {
        pickedSourcePath = null
        pickSourceDirectoryLauncher.launch(null)
    }

    private fun updateOutputDirText() {
        if (!::outputDirText.isInitialized) return
        outputDirText.text = if (outputDirPath != null)
            "Output dir: " + getShortPath(outputDirPath!!)
        else
            "Output dir: не выбрана"
    }

    private fun checkAndRequestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(
                    this,
                    "Требуется доступ ко всем файлам для полноценной работы приложения. Включите его на следующем экране.",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    // grantUriPermission больше не нужен для String-путей

    override fun onStart() {
        super.onStart()
        // grantUriPermission больше не нужен для String-путей
    }
}