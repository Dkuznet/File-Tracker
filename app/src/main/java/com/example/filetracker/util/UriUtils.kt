package com.example.filetracker.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.net.URLDecoder

/**
 * Удаляет служебные сегменты типа tree/primary:/document/ из пути SAF-Uri и возвращает относительный путь
 */
fun getRelPath(uri: Uri): String {
    Log.d("FileTracker.uriToFilePath", "uri=${uri}")

    var relPath = ""
    try {
        val uri_decode = URLDecoder.decode(uri.toString(), "UTF-8")
        Log.d("FileTracker.uriToFilePath", "uri_decode=${uri_decode}")

        relPath = uri_decode.split("tree/primary:", limit = 2)[1]
        Log.d("FileTracker.uriToFilePath", "relPath=${relPath}")
    } catch (e: Exception) {
        Log.e("FileTracker.uriToFilePath", "Failed to resolve Uri: ${uri}", e)
    }
    // Log.d("FileTracker.uriToFilePath", "before return relPath=${relPath}")
    return relPath
}

/** Возвращает строку из последних двух сегментов пути OutputDir */
fun getShortPath(uriInput: Any, nParts: Int = 2): String {
    val uri = when (uriInput) {
        is Uri -> uriInput
        is String -> uriInput.toUri()
        else -> return ""
    }
    val path = getRelPath(uri)
    val segments = path.split("/").filter { it.isNotEmpty() }
    Log.d("FileTracker.getShortPath", "uri=${uri}")
    Log.d("FileTracker.getShortPath", "path=${path}")
    Log.d("FileTracker.getShortPath", "segments=${segments}")
    return if (segments.size >= nParts)
        segments.takeLast(nParts).joinToString("/")
    else
        path
}
/**
 * Возвращает относительный путь от baseUri к targetUri (только path-сегменты).
 */
fun getRelativePath(baseUri: Uri, targetUri: Uri): String {
    val baseClean = getRelPath(baseUri)
    val targetClean = getRelPath(targetUri)
    if (targetClean.startsWith(baseClean)) {
        val relative = targetClean.removePrefix(baseClean).trimStart('/')
        return relative
    }
    return ""
}
/**
 * Формирует destUri для трекера: OutputDir + /<2 последних уровня из sourceUri>
 */
fun buildDestUri(outputDir: Uri, sourceUri: Uri): Uri {
    val path = getRelPath(sourceUri)
    val segments = path.split("/").filter { it.isNotEmpty() }
    val last2 = segments.takeLast(2)
    var builder = outputDir.buildUpon()
    for (seg in last2) {
        builder = builder.appendPath(seg)
    }
    return builder.build()
}
fun createDestDirIfNotExists(context: Context, outputDirUri: Uri, destUri: Uri) {
    val outputDirDoc = DocumentFile.fromTreeUri(context, outputDirUri) ?: return

    // Получаем относительный путь от OutputDir к destUri
    val outputPath = getRelativePath(outputDirUri, destUri)
    if (outputPath.isEmpty()) return

    // Создаём подпапки по сегментам
    var currentDir = outputDirDoc
    val segments = outputPath.split("/").filter { it.isNotEmpty() }
    for (segment in segments) {
        val next = currentDir.findFile(segment)
        currentDir = if (next == null || !next.isDirectory) {
            currentDir.createDirectory(segment) ?: return
        } else {
            next
        }
    }
}
fun uriToFilePath(uriString: String): String? {
    val STORAGE_EMULATED_0 = "/storage/emulated/0/"
    val relPath = getRelPath(uriString.toUri())
    Log.d("FileTracker.uriToFilePath", "relPath $relPath")

    val fullPath = STORAGE_EMULATED_0 + relPath
    Log.d("FileTracker.uriToFilePath", "Resolved path: $fullPath from Uri: $uriString")
    return fullPath
}