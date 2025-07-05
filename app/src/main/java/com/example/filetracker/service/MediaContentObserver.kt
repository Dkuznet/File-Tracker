package com.example.filetracker.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.filetracker.util.EventLogger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MediaType(val dataField: String, val dateAddedField: String) {
    IMAGE(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED),
    AUDIO(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED),
    VIDEO(MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED)
}

class MediaContentObserver(
    private val context: Context,
    handler: Handler,
    private val mediaType: MediaType
) : ContentObserver(handler) {

    @RequiresApi(Build.VERSION_CODES.O)
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @RequiresApi(Build.VERSION_CODES.O)
    private val moscowZoneId = ZoneId.of("Europe/Moscow")

    // Кэш для хранения обработанных файлов (путь -> временная метка)
    private val processedFiles = mutableMapOf<String, Long>()

    // Время жизни записи в кэше (например, 1 минута)
    private val cacheExpirationTimeMs = 60_000L

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        EventLogger.log(context, "Изменение в MediaStore ($mediaType): $uri")
        checkNewFiles()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkNewFiles() {
        val projection = arrayOf(
            mediaType.dataField, // Путь к файлу
            mediaType.dateAddedField // Время добавления
        )

        val selection = "${mediaType.dataField} LIKE ?"
        val selectionArgs = arrayOf("%WhatsApp%") // Фильтр для папок WhatsApp

        val contentUri = when (mediaType) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            "${mediaType.dateAddedField} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(
                    cursor.getColumnIndexOrThrow(mediaType.dataField)
                )
                val dateAdded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(mediaType.dateAddedField)
                )

                // Проверяем, не был ли файл уже обработан
                if (isFileAlreadyProcessed(path, dateAdded)) {
                    EventLogger.log(context, "Файл уже обработан: $path")
                    Log.d("checkNewFiles", "Файл уже обработан: $path")
                    return
                }

                // Добавляем файл в кэш
                addFileToCache(path, dateAdded)

                // Форматируем дату
                val formattedDate = Instant.ofEpochSecond(dateAdded)
                    .atZone(moscowZoneId)
                    .format(formatter)
                EventLogger.log(
                    context,
                    "Новый файл ${mediaType.name}: $path, добавлен: $formattedDate"
                )
                Log.d(
                    "checkNewFiles",
                    "Новый файл ${mediaType.name}: $path, добавлен: $formattedDate"
                )
                handleNewFile(path)
            }
        }
    }

    private fun isFileAlreadyProcessed(path: String, dateAdded: Long): Boolean {
        val cachedDateAdded = processedFiles[path] ?: return false
        // Проверяем, не истёк ли срок действия записи в кэше
        val currentTimeMs = System.currentTimeMillis()
        val fileTimeMs = dateAdded * 1000 // Переводим секунды в миллисекунды
        if (currentTimeMs - fileTimeMs > cacheExpirationTimeMs) {
            // Если запись в кэше устарела, удаляем её
            processedFiles.remove(path)
            return false
        }
        // Файл считается уже обработанным, если временная метка совпадает
        return cachedDateAdded == dateAdded
    }

    private fun addFileToCache(path: String, dateAdded: Long) {
        processedFiles[path] = dateAdded
        // Очищаем старые записи из кэша (опционально)
        cleanUpCache()
    }

    private fun cleanUpCache() {
        val currentTimeMs = System.currentTimeMillis()
        processedFiles.entries.removeAll { entry ->
            val fileTimeMs = entry.value * 1000 // Переводим секунды в миллисекунды
            currentTimeMs - fileTimeMs > cacheExpirationTimeMs
        }
    }

    private fun handleNewFile(path: String) {
        // Здесь вызываем ту же логику, что и в FileObserverWrapper
        // Например, копируем файл в целевую папку
    }
}