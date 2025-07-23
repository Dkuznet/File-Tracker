package com.example.filetracker.service

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.filetracker.data.AppNameRepository
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.FileUtils
import com.example.filetracker.util.LogLevel
import kotlinx.coroutines.runBlocking
import java.io.File
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
    private val mediaType: MediaType,
    private val outputDir: String
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
        EventLogger.log(
            message = "Изменение в MediaStore ($mediaType): $uri",
            logTag = "MediaContentObserver.onChange",
            extra = true
        )
        checkNewFiles(uri)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkNewFiles(uri: Uri?) {
        val projection = arrayOf(
            mediaType.dataField, // Путь к файлу
            mediaType.dateAddedField, // Время добавления
            MediaStore.MediaColumns._ID // ID файла
        )

        val contentUri = when (mediaType) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        // Если передан uri, извлекаем _id из него с защитой от ошибок
        val uriId = try {
            uri?.let { ContentUris.parseId(it) }
        } catch (e: NumberFormatException) {
            EventLogger.log(
                message = "parseId failed for uri: $uri ${e.message}",
                logTag = "MediaContentObserver",
                log = LogLevel.ERROR
            )
            -1L
        } ?: -1L

        // Формируем условия выборки
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // Получаем app_name из DataStore
        val appName = runBlocking { AppNameRepository.getAppName(context) } ?: " "
        if (appName.isNotBlank()) {
            selectionBuilder.append("${mediaType.dataField} LIKE ?")
            selectionArgs.add("%$appName%")
        } else {
            selectionBuilder.append("1=1") // не фильтруем по app_name
        }

        // добавляем фильтр по _id
        selectionBuilder.append(" AND ${MediaStore.MediaColumns._ID} = ?")
        selectionArgs.add(uriId.toString())

        context.contentResolver.query(
            contentUri,
            projection,
            selectionBuilder.toString(),
            selectionArgs.toTypedArray(),
            "${mediaType.dateAddedField} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sourcePath = cursor.getString(
                    cursor.getColumnIndexOrThrow(mediaType.dataField)
                )
                val dateAdded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(mediaType.dateAddedField)
                )

                // Проверяем, не был ли файл уже обработан
                if (isFileAlreadyProcessed(sourcePath, dateAdded)) {
                    EventLogger.log(
                        message = "Файл уже обработан: $sourcePath",
                        logTag = "checkNewFiles",
                        log = LogLevel.WARN,
                        extra = true
                    )
                    return
                }

                // Добавляем файл в кэш
                addFileToCache(sourcePath, dateAdded)

                // Форматируем дату
                val formattedDate = Instant.ofEpochSecond(dateAdded)
                    .atZone(moscowZoneId)
                    .format(formatter)
                EventLogger.log(
                    message = "Новый файл ${mediaType.name}: $sourcePath, добавлен: $formattedDate",
                    logTag = "checkNewFiles"
                )
                handleNewFile(context, sourcePath, appName)
            } else {
                EventLogger.log(
                    message = "Файл не найден для uri: $uri",
                    logTag = "checkNewFiles",
                    log = LogLevel.ERROR,
                    extra = true
                )
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

    private fun handleNewFile(context: Context, sourcePath: String, appName: String) {
        val appDir = "$appName/"
        if (!sourcePath.contains(appDir)) {
            EventLogger.log(
                message = "не найден $appDir в пути $sourcePath",
                logTag = "handleNewFile",
                log = LogLevel.ERROR
            )
            return
        }
        // Формируем путь назначения
        val destPath = FileUtils.buildDestinationPath(context, appDir, outputDir, sourcePath)

        // Проверяем условия для файла
        if (!FileUtils.checkFileConditions(context, sourcePath)) {
            EventLogger.log(
                message = "Файл не прошёл проверку условий: $sourcePath",
                logTag = "handleNewFile",
                log = LogLevel.ERROR
            )
            return
        }

        // Вызываем функцию FileCopy
        FileUtils.fileCopy(
            context = context,
            srcFile = File(sourcePath),
            destFile = File(destPath)
        )

    }
}