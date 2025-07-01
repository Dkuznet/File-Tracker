package com.example.filetracker.util

import android.net.Uri
import androidx.core.net.toUri

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