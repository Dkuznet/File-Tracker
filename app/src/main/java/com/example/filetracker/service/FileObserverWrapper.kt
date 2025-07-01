package com.example.filetracker.service

import android.content.Context
import android.net.Uri
import android.os.FileObserver
import androidx.documentfile.provider.DocumentFile
import com.example.filetracker.util.EventLogger

class FileObserverWrapper(
    private val context: Context,
    private val sourceUri: Uri,
    private val destUri: Uri
) : FileObserver(
    context.filesDir,
    CREATE
) { // filesDir — не используется реально, только чтобы запустить FileObserver

    override fun onEvent(event: Int, path: String?) {
        if (event == CREATE && path != null) {
            val srcDir = DocumentFile.fromTreeUri(context, sourceUri) ?: return
            val file = srcDir.findFile(path) ?: return
            val destDir = DocumentFile.fromTreeUri(context, destUri) ?: return
            EventLogger.log(context, "Найден файл: $path")
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val outFile =
                    destDir.createFile(file.type ?: "application/octet-stream", file.name ?: path)
                if (outFile != null) {
                    context.contentResolver.openOutputStream(outFile.uri)?.use { output ->
                        input.copyTo(output)
                        EventLogger.log(context, "Скопирован файл: $path")
                    }
                }
            }
        }
    }
}