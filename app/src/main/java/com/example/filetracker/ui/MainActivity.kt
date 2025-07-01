package com.example.filetracker.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.filetracker.R

class MainActivity : ComponentActivity() {

    private val trackerViewModel: TrackerViewModel by viewModels()
    private var pickedSourceUri: Uri? = null
    private var pickedDestUri: Uri? = null
    private val REQUEST_FOREGROUND_SERVICE_DATA_SYNC = 1001


    private val pickSourceDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            grantUriPermission(uri)
            pickedSourceUri = uri
            pickDestDirectory()
        }
    }

    private val pickDestDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            grantUriPermission(uri)
            pickedDestUri = uri
            if (pickedSourceUri != null && pickedDestUri != null) {
                trackerViewModel.addTracker(
                    pickedSourceUri.toString(),
                    pickedDestUri.toString()
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestForegroundServiceDataSyncPermissionIfNeeded()

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

        findViewById<View>(R.id.addTrackerButton).setOnClickListener {
            if (trackerViewModel.canAddTracker()) {
                pickSourceDirectory()
            } else {
                // показать сообщение, что лимит трекеров 5
            }
        }

        findViewById<View>(R.id.minimizeButton).setOnClickListener {
            moveTaskToBack(true)
        }
        findViewById<View>(R.id.startServiceButton).setOnClickListener {
            startForegroundService(
                Intent(
                    this,
                    com.example.filetracker.service.FileTrackerService::class.java
                )
            )
        }
        findViewById<View>(R.id.stopServiceButton).setOnClickListener {
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

    private fun pickSourceDirectory() {
        pickedSourceUri = null
        pickedDestUri = null
        pickSourceDirectoryLauncher.launch(null)
    }

    private fun pickDestDirectory() {
        pickDestDirectoryLauncher.launch(null)
    }

    private fun grantUriPermission(uri: Uri) {
        val takeFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
        }
    }

    override fun onStart() {
        super.onStart()
        trackerViewModel.trackers.value?.forEach { tracker ->
            try {
                grantUriPermission(Uri.parse(tracker.sourceUri))
                grantUriPermission(Uri.parse(tracker.destUri))
            } catch (_: Exception) {
            }
        }
    }
}