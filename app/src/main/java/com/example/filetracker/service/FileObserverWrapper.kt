package com.example.filetracker.service

import android.content.Context
import android.net.Uri
import android.os.FileObserver
import androidx.documentfile.provider.DocumentFile

class FileObserverWrapper(
    private val context: Context,
    private val sourceUri: Uri,
    private val destUri: Uri
) : FileObserver(getFilePathFromUri(context, sourceUri), CREATE) {

    override fun onEvent(event: Int, path: String?) {
        if (event == CREATE && path != null) {
            val srcDir = DocumentFile.fromTreeUri(context, sourceUri) ?: return
            val file = srcDir.findFile(path) ?: return
            val destDir = DocumentFile.fromTreeUri(context, destUri) ?: return
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val outFile =
                    destDir.createFile(file.type ?: "application/octet-stream", file.name ?: path)
                if (outFile != null) {
                    context.contentResolver.openOutputStream(outFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    companion object {
        fun getFilePathFromUri(context: Context, uri: Uri): String? {
            // Реализуйте получение физического пути, если возможно, или используйте SAF
            return null // Для SAF путь не обязателен, можно реализовать через DocumentFile API
        }
    }
}