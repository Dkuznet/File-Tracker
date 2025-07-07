package com.example.filetracker.util

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import java.net.URLDecoder


object UriUtils {
    fun getRelPath(uri: Uri): String {
        Log.d("getRelPath", "uri=${uri}")

        var relPath = ""
        try {
            val uri_decode = URLDecoder.decode(uri.toString(), "UTF-8")
            Log.d("getRelPath", "uri_decode=${uri_decode}")

            relPath = uri_decode.split("tree/primary:", limit = 2)[1]
            Log.d("getRelPath", "relPath=${relPath}")
        } catch (e: Exception) {
            Log.e("getRelPath", "Failed to resolve Uri: ${uri}", e)
        }
        // Log.d("FileTracker.uriToFilePath", "before return relPath=${relPath}")
        return relPath
    }

    /** Возвращает строку из последних двух сегментов пути OutputDir */
    fun getShortPath(path: String?, nParts: Int = 2): String? {
        if (path.isNullOrEmpty()) return path // Проверяем null и пустую строку сразу
        val segments = path.split("/").filter { it.isNotBlank() }
        return if (segments.size >= nParts) {
            segments.takeLast(nParts).joinToString("/")
        } else {
            segments.joinToString("/") // Возвращаем весь путь без ведущих слэшей
        }.also { result ->
            Log.d("getShortPath", "path=$path, segments=$segments, result=$result")
        }
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
     * Формирует путь к директории назначения: outputDir + /<2 последних уровня из sourceDir>
     * @param outputDir Путь к выходной директории (может быть null)
     * @param sourceDir Путь к исходной директории (может быть null)
     * @return Путь к директории назначения в виде строки, или пустая строка, если входные данные некорректны
     */
    fun buildDestDir(outputDir: String?, sourceDir: String?): String {
        // Если outputDir или sourceDir равны null, возвращаем пустую строку
        if (outputDir.isNullOrBlank() || sourceDir.isNullOrBlank()) {
            return ""
        }

        val segments =
            sourceDir.split("/").filter { it.isNotBlank() } // Используем isNotBlank для надёжности
        val last2 =
            if (segments.size >= 2) segments.takeLast(2) else segments // Берем последние 2 сегмента, если они есть
        return buildString {
            append(outputDir.trimEnd('/')) // Убираем trailing slash у outputDir
            if (last2.isNotEmpty()) {
                append('/')
                append(last2.joinToString("/"))
            }
        }
    }


    /**
     * Создаёт директорию назначения, если она не существует.
     * @param context Контекст приложения
     * @param destDir Путь к директории назначения
     */


    fun uriToFilePath(uriString: String): String? {
        val STORAGE_EMULATED_0 = "/storage/emulated/0/"
        val relPath = getRelPath(uriString.toUri())
        Log.d("UriUtils.uriToFilePath", "relPath $relPath")

        val fullPath = STORAGE_EMULATED_0 + relPath
        Log.d("UriUtils.uriToFilePath", "Resolved path: $fullPath from Uri: $uriString")
        return fullPath
    }
}