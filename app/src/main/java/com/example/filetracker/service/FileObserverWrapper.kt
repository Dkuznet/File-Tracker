package com.example.filetracker.service

import android.content.Context
import android.os.Build
import android.os.FileObserver
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
) : FileObserver(sourceDirPath, CREATE) {

    override fun onEvent(event: Int, path: String?) {
        if (event == CREATE && path != null) {
            val srcFile = File(sourceDirPath, path)
            val destDir = File(destDirPath)
            val destFile = File(destDir, srcFile.name)
            if (srcFile.exists() && srcFile.isFile) {
                EventLogger.log(context, "Найден файл: $path")
                try {
                    srcFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    EventLogger.log(context, "Скопирован файл: $path")
                } catch (e: Exception) {
                    EventLogger.log(context, "Ошибка копирования файла: $path, ${e.message}")
                }
            }
        }
    }
}