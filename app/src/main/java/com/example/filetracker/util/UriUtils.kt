package com.example.filetracker.util

import android.net.Uri
import androidx.core.net.toUri
import java.net.URLDecoder


object UriUtils {
    fun getRelPath(uri: Uri): String {
        EventLogger.log("uri=${uri}", logTag = "getRelPath", extra = true)

        var relPath = ""
        try {
            val uri_decode = URLDecoder.decode(uri.toString(), "UTF-8")
            EventLogger.log(
                message = "uri_decode=${uri_decode}",
                logTag = "getRelPath",
                extra = true
            )

            relPath = uri_decode.split("tree/primary:", limit = 2)[1]
            EventLogger.log(message = "relPath=${relPath}", logTag = "getRelPath", extra = true)
        } catch (e: Exception) {
            EventLogger.log(
                message = "Failed to resolve Uri: ${uri}",
                logTag = "getRelPath",
                log = LogLevel.ERROR
            )
        }
        // EventLogger.log(null, "before return relPath=${relPath}", logcatTag = "FileTracker.uriToFilePath")
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
            EventLogger.log(
                message = "path=$path, segments=$segments, result=$result",
                logTag = "getShortPath",
                extra = true
            )
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
        EventLogger.log("relPath $relPath", logTag = "UriUtils.uriToFilePath", extra = true)

        val fullPath = STORAGE_EMULATED_0 + relPath
        EventLogger.log(
            message = "Resolved path: $fullPath from Uri: $uriString",
            logTag = "UriUtils.uriToFilePath",
            extra = true
        )
        return fullPath
    }
}