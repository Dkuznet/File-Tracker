package com.example.filetracker.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile

/**
 * Удаляет служебные сегменты типа tree/primary:/document/ из пути SAF-Uri и возвращает относительный путь
 */
fun cleanPathFromTree(uri: Uri): String {
    val encodedPath = uri.encodedPath ?: return ""
    val lastDocumentIdx = encodedPath.lastIndexOf("/document/")
    val lastTreeIdx = encodedPath.lastIndexOf("/tree/")
    val cutIdx = when {
        lastDocumentIdx >= 0 -> lastDocumentIdx + "/document/".length
        lastTreeIdx >= 0 -> lastTreeIdx + "/tree/".length
        else -> 0
    }
    var clean = encodedPath.substring(cutIdx)
    clean = clean.replace("%3A", ":").replace("%2F", "/")
    clean = clean.removePrefix("primary:")
    return clean.trim('/')
}
/** Возвращает строку из последних двух сегментов пути OutputDir */
fun getShortPath(uriInput: Any): String {
    val uri = when (uriInput) {
        is Uri -> uriInput
        is String -> uriInput.toUri()
        else -> return ""
    }
    val path = cleanPathFromTree(uri)
    val segments = path.split("/").filter { it.isNotEmpty() }
    return if (segments.size >= 2)
        segments.takeLast(2).joinToString("/")
    else
        path
}
/**
 * Возвращает относительный путь от baseUri к targetUri (только path-сегменты).
 */
fun getRelativePath(baseUri: Uri, targetUri: Uri): String {
    val baseClean = cleanPathFromTree(baseUri)
    val targetClean = cleanPathFromTree(targetUri)
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
    val path = cleanPathFromTree(sourceUri)
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

/**
 * Преобразует SAF Uri к файловому пути только для primary storage.
 * Возвращает абсолютный путь или null, если не удалось распознать.
 *
 * Работает для Uri формата: content://com.android.externalstorage.documents/tree/primary:folder1/folder2
 * Возвращает: /storage/emulated/0/folder1/folder2
 */
fun uriToFilePath(uriString: String): String? {
    val STORAGE_EMULATED_0 = "/storage/emulated/0/"
    return try {
        val parts = uriString.split("tree/primary%3A", limit = 2)
        Log.d("FileTracker.uriToFilePath", "uriString $uriString")
//        Log.d("FileTracker.uriToFilePath", "uri $uri")
        Log.d("FileTracker.uriToFilePath", "parts $parts")

        if (parts.size == 2) {
            val relPath = parts[1].replace("%2F", "/").trimStart('/')
            val fullPath = STORAGE_EMULATED_0 + relPath
            Log.d("FileTracker.uriToFilePath", "Resolved path: $fullPath from Uri: $uriString")
            fullPath
        } else {
            Log.w("FileTracker.uriToFilePath", "Uri is not primary storage: $uriString")
            null
        }
    } catch (e: Exception) {
        Log.e("FileTracker.uriToFilePath", "Failed to resolve Uri: $uriString", e)
        null
    }
}