package com.example.filetracker.util

import android.content.Context
import android.os.Build

fun requestStoragePermissions(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Scoped Storage + SAF
        // Запросить разрешения через SAF (например, запуск ActivityResultContracts.OpenDocumentTree())
    } else {
        // Старые версии — стандартные permissions (READ_EXTERNAL_STORAGE/WRITE_EXTERNAL_STORAGE)
    }
}