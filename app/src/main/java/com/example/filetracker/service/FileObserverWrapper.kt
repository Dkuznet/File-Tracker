package com.example.filetracker.service

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.FileObserver
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.filetracker.util.EventLogger
import java.io.File

/**
 * FileObserverWrapper для отслеживания появления новых файлов в настоящей файловой системе.
 * Работает только с реальными путями (например, /storage/emulated/0/...), а не с SAF/DocumentFile!
 *
 * @param context Контекст приложения
 * @param sourceDirPath Путь к папке, которую нужно отслеживать (например, "/storage/emulated/0/Download")
 * @param destDirPath Путь к папке для копирования новых файлов (например, "/storage/emulated/0/YourApp")
 */
@RequiresApi(Build.VERSION_CODES.Q)
class FileObserverWrapper(
    private val context: Context,
    private val sourceDirPath: String,
    private val destDirPath: String
) : FileObserver(listOf(File(sourceDirPath)), CLOSE_WRITE) {

    //private val processedFiles = mutableSetOf<String>()

    override fun onEvent(event: Int, path: String?) {
        if (event == CLOSE_WRITE && path != null) {

            val srcFile = File(sourceDirPath, path)
            val destDir = File(destDirPath)
            val destFile = File(destDir, srcFile.name)

            val timeoutMillis = 5_000L // максимальное время ожидания (например, 10 секунд)
            val pollInterval = 100L     // интервал проверки (100 миллисекунд)
            val start = System.currentTimeMillis()

            while (srcFile.length() == 0L && System.currentTimeMillis() - start < timeoutMillis) {
                Thread.sleep(pollInterval)
            }

            // После выхода из цикла: либо файл стал больше 0, либо вышли по таймауту
            if (srcFile.length() == 0L) {
                Log.d("FileObserverWrapper", "srcFile.length() == 0L")
                EventLogger.log(context, "srcFile.length() == 0L")
                return
            }

            // Проверяем существование файла
            if (destFile.exists() && destFile.isFile) {
                Log.w("FileObserverWrapper", "Файл уже существует: ${destFile}")
                EventLogger.log(context, "Файл уже существует: ${destFile}")
                return
            }

            // Проверяем права и существование файла
            if (!srcFile.exists() || !srcFile.isFile) {
                Log.w("FileObserverWrapper", "Файл не найден или не является файлом: ${srcFile}")
                EventLogger.log(context, "Файл не найден или не является файлом: ${srcFile}")
                return
            }
            if (!srcFile.canRead()) {
                Log.w("FileObserverWrapper", "Нет прав на чтение файла: ${srcFile}")
                EventLogger.log(context, "Нет прав на чтение файла: ${srcFile}")
                return
            }

            EventLogger.log(context, "Найден файл: $path")
            Log.d(
                "FileObserverWrapper",
                "Найден файл: srcFile=$srcFile size=${srcFile.length()} destFile=$destFile"
            )

            try {
                srcFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // Проверяем, что файл действительно скопирован
                if (destFile.exists() && destFile.isFile && srcFile.length() == destFile.length()) {
                    Log.d(
                        "FileObserverWrapper",
                        "Файл успешно скопирован: $destFile size=${destFile.length()}"
                    )
                    EventLogger.log(context, "Файл успешно скопирован: $destFile")

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        null
                    ) { path, uri ->
                        Log.d("FileObserverWrapper", "Media scan completed for: $path, uri=$uri")
                    }

                } else {
                    Log.e(
                        "FileObserverWrapper",
                        "Ошибка копирования: файл не существует или размеры не совпадают"
                    )
                    EventLogger.log(
                        context,
                        "Ошибка копирования: файл не существует или размеры не совпадают"
                    )
                }
            } catch (e: Exception) {
                Log.e("FileObserverWrapper", "Ошибка копирования файла", e)
                EventLogger.log(context, "Ошибка копирования файла: ${e.message}")
            }
        }
    }
}