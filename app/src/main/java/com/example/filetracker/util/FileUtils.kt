package com.example.filetracker.util

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import java.io.File

object FileUtils {

    fun createDestDirIfNotExists(context: Context, destDir: String) {
        // Проверяем, что destDir не пустой
        if (destDir.isBlank()) { // Используем isBlank вместо isEmpty для надёжности
            Log.e("createDestDirIfNotExists", "destDir пустой или содержит только пробелы")
            EventLogger.log(context, "destDir пустой или содержит только пробелы")
            return
        }

        // Создаём File для destDir
        val destDirFile = File(destDir)

        // Проверяем, существует ли директория
        if (destDirFile.exists()) {
            if (!destDirFile.isDirectory) {
                Log.e(
                    "createDestDirIfNotExists",
                    "Путь существует, но не является директорией: $destDir"
                )
                EventLogger.log(context, "Путь существует, но не является директорией: $destDir")
                return
            }
            // Директория уже существует, ничего делать не нужно
            return
        }

        // Создаём директорию и все родительские директории
        if (!destDirFile.mkdirs()) {
            Log.e("createDestDirIfNotExists", "Не удалось создать директорию: $destDir")
            EventLogger.log(context, "Не удалось создать директорию: $destDir")
            return
        }

        Log.d("createDestDirIfNotExists", "Директория успешно создана: $destDir")
        EventLogger.log(context, "Директория успешно создана: $destDir")
    }


    /**
     * Формирует путь назначения, добавляя к outputDir относительный путь после /WhatsApp/ из sourcePath.
     * Если /WhatsApp/ отсутствует, возвращает путь с именем файла.
     * @param outputDir Путь к выходной директории
     * @param sourcePath Путь к исходному файлу
     * @return Путь назначения в виде строки
     */
    fun buildDestinationPath(appDir: String, outputDir: String, sourcePath: String): String {
        val srcFile = File(sourcePath)
        return sourcePath.split(appDir, limit = 2).takeIf { it.size > 1 }?.let { parts ->
            File(outputDir, parts[1]).absolutePath.also { result ->
                Log.d(
                    "buildDestinationPath",
                    "outputDir=$outputDir, sourcePath=$sourcePath, result=$result"
                )
            }
        } ?: File(outputDir, srcFile.name).absolutePath.also { result ->
            Log.d(
                "buildDestinationPath",
                "sourcePath не содержит $appDir, используется имя файла: $sourcePath, result=$result"
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
            Log.d("FileCopy", "srcFile.length() == 0L")
            EventLogger.log(context, "srcFile.length() == 0L")
            return false
        }
        return true
    }

    fun fileCopy(context: Context, sourcePath: String, destPath: String): Boolean {
        val srcFile = File(sourcePath)
        val destFile = File(destPath)

        // Проверяем права и существование файла
        if (!srcFile.exists() || !srcFile.isFile) {
            Log.w("FileCopy", "Файл не найден или не является файлом: $srcFile")
            EventLogger.log(context, "Файл не найден или не является файлом: $srcFile")
            return false
        }
        if (!srcFile.canRead()) {
            Log.w("FileCopy", "Нет прав на чтение файла: $srcFile")
            EventLogger.log(context, "Нет прав на чтение файла: $srcFile")
            return false
        }

        // Проверяем существование файла назначения
        if (destFile.exists() && destFile.isFile) {
            Log.w("FileCopy", "Файл уже существует: $destFile")
            EventLogger.log(context, "Файл уже существует: $destFile")
            return false
        }

        val destDirPath = destFile.parent
        if (destDirPath == null) {
            Log.e("FileCopy", "Родительская директория не определена для $destPath")
            EventLogger.log(context, "Родительская директория не определена для $destPath")
            return false
        }

        createDestDirIfNotExists(context, destDirPath)


        try {
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Проверяем, что файл действительно скопирован
            if (destFile.exists() && destFile.isFile && srcFile.length() == destFile.length()) {
                Log.d("FileCopy", "Файл успешно скопирован: $destFile size=${destFile.length()}")
                EventLogger.log(context, "Файл успешно скопирован: $destFile")

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    null
                ) { path, uri ->
                    Log.d("FileCopy", "Media scan completed for: $path, uri=$uri")
                }
                return true
            } else {
                Log.e("FileCopy", "Ошибка копирования: файл не существует или размеры не совпадают")
                EventLogger.log(
                    context,
                    "Ошибка копирования: файл не существует или размеры не совпадают"
                )
                return false
            }
        } catch (e: Exception) {
            Log.e("FileCopy", "Ошибка копирования файла", e)
            EventLogger.log(context, "Ошибка копирования файла: ${e.message}")
            return false
        }
    }
}