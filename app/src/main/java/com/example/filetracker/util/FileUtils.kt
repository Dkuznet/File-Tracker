package com.example.filetracker.util

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File

object FileUtils {

    fun createDestDirIfNotExists(destDir: String) {
        // Проверяем, что destDir не пустой
        if (destDir.isBlank()) { // Используем isBlank вместо isEmpty для надёжности
            EventLogger.log(
                message = "destDir пустой или содержит только пробелы",
                logTag = "createDestDirIfNotExists",
                log = LogLevel.ERROR,
                extra = true
            )
            return
        }

        // Создаём File для destDir
        val destDirFile = File(destDir)

        // Проверяем, существует ли директория
        if (destDirFile.exists()) {
            if (!destDirFile.isDirectory) {
                EventLogger.log(
                    message = "Путь существует, но не является директорией: $destDir",
                    logTag = "createDestDirIfNotExists",
                    log = LogLevel.ERROR,
                    extra = true
                )
                return
            }
            // Директория уже существует, ничего делать не нужно
            return
        }

        // Создаём директорию и все родительские директории
        if (!destDirFile.mkdirs()) {
            EventLogger.log(
                message = "Не удалось создать директорию: $destDir",
                logTag = "createDestDirIfNotExists",
                log = LogLevel.ERROR,
                extra = true
            )
            return
        }

        EventLogger.log(
            message = "Директория успешно создана: $destDir",
            logTag = "createDestDirIfNotExists"
        )
    }


    fun buildDestinationPath(
        context: Context,
        appDir: String,
        outputDir: String,
        sourcePath: String
    ): String {
        val srcFile = File(sourcePath)
        return sourcePath.split(appDir, limit = 2).takeIf { it.size > 1 }?.let { parts ->
            File(outputDir, parts[1]).absolutePath.also { result ->
                EventLogger.log(
                    message = "outputDir=$outputDir, sourcePath=$sourcePath, result=$result",
                    logTag = "buildDestinationPath"
                )
            }
        } ?: File(outputDir, srcFile.name).absolutePath.also { result ->
            EventLogger.log(
                message = "sourcePath не содержит $appDir, используется имя файла: $sourcePath, result=$result",
                logTag = "buildDestinationPath"
            )
        }
    }

    fun checkFileConditions(context: Context, sourcePath: String): Boolean {
        val srcFile = File(sourcePath)

        val timeoutMillis = 5_000L // максимальное время ожидания (например, 5 секунд)
        val pollInterval = 100L     // интервал проверки (100 миллисекунд)
        val start = System.currentTimeMillis()

        while (srcFile.length() == 0L && System.currentTimeMillis() - start < timeoutMillis) {
            Thread.sleep(pollInterval)
        }

        // После выхода из цикла: либо файл стал больше 0, либо вышли по таймауту
        if (srcFile.length() == 0L) {
            EventLogger.log(
                message = "srcFile.length() == 0L",
                logTag = "FileCopy",
                log = LogLevel.WARN
            )
            return false
        }
        return true
    }

    fun fileCopy(context: Context, srcFile: File, destFile: File): Boolean {
        // Проверяем права и существование файла
        if (!srcFile.exists() || !srcFile.isFile) {
            EventLogger.log(
                message = "Файл не найден или не является файлом: $srcFile",
                logTag = "FileCopy",
                log = LogLevel.WARN
            )
            return false
        }
        if (!srcFile.canRead()) {
            EventLogger.log(
                message = "Нет прав на чтение файла: $srcFile",
                logTag = "FileCopy",
                log = LogLevel.ERROR
            )
            return false
        }

        // Проверяем существование файла назначения
        if (destFile.exists() && destFile.isFile) {
            EventLogger.log(
                message = "Файл уже существует: $destFile",
                logTag = "FileCopy",
                log = LogLevel.WARN,
                extra = true
            )
            return false
        }

        val destDirPath = destFile.parent
        val destPath = destFile.absolutePath
        if (destDirPath == null) {
            EventLogger.log(
                message = "Родительская директория не определена для $destPath",
                logTag = "FileCopy",
                log = LogLevel.ERROR
            )
            return false
        }

        createDestDirIfNotExists(destDirPath)


        try {
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Проверяем, что файл действительно скопирован
            if (destFile.exists() && destFile.isFile && srcFile.length() == destFile.length()) {
                EventLogger.log(
                    message = "Файл успешно скопирован: $destFile size=${destFile.length()}",
                    logTag = "FileCopy"
                )

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    null
                ) { path, uri ->
                    EventLogger.log(
                        message = "Media scan completed for: $path, uri=$uri",
                        logTag = "FileCopy",
                        extra = true
                    )
                }
                return true
            } else {
                EventLogger.log(
                    message = "Ошибка копирования: файл не существует или размеры не совпадают",
                    logTag = "FileCopy",
                    log = LogLevel.ERROR
                )
                return false
            }
        } catch (e: Exception) {
            EventLogger.log(
                message = "Ошибка копирования файла: ${e.message}",
                logTag = "FileCopy",
                log = LogLevel.ERROR
            )
            return false
        }
    }
}