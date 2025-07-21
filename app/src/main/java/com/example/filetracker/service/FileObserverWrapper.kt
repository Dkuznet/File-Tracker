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
 * LatestFolderWatcher отслеживает только самую "свежую" подпапку (по имени) внутри rootPath.
 * При появлении новой подпапки автоматически переключается на неё и отслеживает появление новых файлов только в ней.
 * Используйте startWatching()/stopWatching() для управления жизненным циклом.
 *
 * @param rootPath Путь к основной папке
 * @param mask Маска событий FileObserver (по умолчанию все события)
 * @param onFileCreated Callback для новых файлов
 */
class LatestFolderWatcher(
    private val context: Context,
    private val rootPath: String,
    private val destDirPath: String, // <--- добавили
    private val mask: Int = FileObserver.ALL_EVENTS,
    private val onFileCreated: (path: String) -> Unit
) {
    private var currentObserver: FileObserver? = null
    private var currentFolder: File? = null
    private var isWatching = false

    private val rootObserver =
        createFileObserver(rootPath, FileObserver.ALL_EVENTS) { event, relativePath ->
            val fullPath = "$rootPath/$relativePath"
            val file = File(fullPath)
            Log.d("LatestFolderWatcher", "rootObserver event=$event path=$relativePath")
            EventLogger.log(context, "rootObserver event=$event path=$relativePath")
            
            if (((event and FileObserver.CREATE != 0) || (event and FileObserver.MOVED_TO != 0)) && file.isDirectory) {
                val msgLog = "New subfolder detected: $fullPath"
                Log.d("LatestFolderWatcher", msgLog)
                EventLogger.log(context, msgLog)
                val latest = findLatestSubfolder()
                if (latest != null && latest != currentFolder) {
                    Log.d(
                        "LatestFolderWatcher",
                        "Switching to new latest subfolder: ${latest.absolutePath}"
                    )
                    EventLogger.log(
                        context,
                        "Switching to new latest subfolder: ${latest.absolutePath}"
                    )
                    switchToFolder(latest)
                }
            }
        }

    fun startWatching() {
        if (isWatching) {
            Log.d(
                "LatestFolderWatcher",
                "startWatching called, but already watching root: $rootPath"
            )
            EventLogger.log(
                context,
                "LatestFolderWatcher: startWatching called, but already watching root: $rootPath"
            )
            return
        }
        isWatching = true
        val msgLog = "Start watching root: $rootPath"
        Log.d("LatestFolderWatcher", msgLog)
        EventLogger.log(context, msgLog)
        rootObserver.startWatching()
        val latest = findLatestSubfolder()
        if (latest != null) {
            Log.d("LatestFolderWatcher", "Found latest subfolder on start: ${latest.absolutePath}")
            EventLogger.log(context, "Found latest subfolder on start: ${latest.absolutePath}")
            switchToFolder(latest)
        } else {
            Log.d("LatestFolderWatcher", "No subfolders found on start in $rootPath")
            EventLogger.log(context, "No subfolders found on start in $rootPath")
        }
    }

    fun stopWatching() {
        if (!isWatching) {
            Log.d(
                "LatestFolderWatcher",
                "stopWatching called, but was not watching root: $rootPath"
            )
            EventLogger.log(
                context,
                "LatestFolderWatcher: stopWatching called, but was not watching root: $rootPath"
            )
            return
        }
        isWatching = false
        val msgLog = "Stop watching root: $rootPath"
        Log.d("LatestFolderWatcher", msgLog)
        EventLogger.log(context, msgLog)
        rootObserver.stopWatching()
        currentObserver?.stopWatching()
        currentObserver = null
        currentFolder = null
    }

    private fun findLatestSubfolder(): File? {
        val subfolders = File(rootPath).listFiles()?.filter { it.isDirectory }
        Log.d(
            "LatestFolderWatcher",
            "findLatestSubfolder: found ${subfolders?.size ?: 0} subfolders in $rootPath"
        )
        return subfolders?.maxByOrNull { it.name }
    }

    private fun switchToFolder(folder: File) {
        Log.d(
            "LatestFolderWatcher",
            "switchToFolder: Switching to subfolder: ${folder.absolutePath}"
        )
        EventLogger.log(context, "switchToFolder: Switching to subfolder: ${folder.absolutePath}")
        currentObserver?.stopWatching()
        currentFolder = folder
        var msgLog = "Switching to subfolder: ${folder.absolutePath}"
        Log.d("LatestFolderWatcher", msgLog)
        EventLogger.log(context, msgLog)

        // --- Копируем все файлы из новой папки ---
        msgLog = "Search files in new subfolder: ${folder.absolutePath}"
        Log.d("LatestFolderWatcher", msgLog)
        EventLogger.log(context, msgLog)
        val files = folder.listFiles()?.filter { it.isFile }
        Log.d(
            "LatestFolderWatcher",
            "Found ${files?.size ?: 0} files in new subfolder ${folder.absolutePath}"
        )
        files?.forEach { file ->
            Log.d("LatestFolderWatcher", "Copying file: ${file.absolutePath}")
            EventLogger.log(context, "Copying file: ${file.absolutePath}")
            copyFileWithChecks(context, file, destDirPath)
        }

        currentObserver = createFileObserver(folder.absolutePath, mask) { event, relativePath ->
            Log.d(
                "LatestFolderWatcher",
                "currentObserver event=$event path=$relativePath in folder=${folder.absolutePath}"
            )
            if (event == FileObserver.CREATE && relativePath != null) {
                val fullPath = "${folder.absolutePath}/$relativePath"
                val msgLog = "New file in latest subfolder: $fullPath"
                Log.d("LatestFolderWatcher", msgLog)
                EventLogger.log(context, msgLog)
                onFileCreated(fullPath)
            }
        }.apply { startWatching() }
        Log.d("LatestFolderWatcher", "Started watching new subfolder: ${folder.absolutePath}")
        EventLogger.log(context, "Started watching new subfolder: ${folder.absolutePath}")
    }
}


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
) : FileObserver(listOf(File(sourceDirPath)), CREATE) {

    //private val processedFiles = mutableSetOf<String>()

    override fun onEvent(event: Int, path: String?) {
        if (event == CREATE && path != null) {

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

/**
 * Копирует файл srcFile в destDirPath с проверками и логированием, аналогично FileObserverWrapper.
 */
fun copyFileWithChecks(context: Context, srcFile: File, destDirPath: String) {
    val destDir = File(destDirPath)
    val destFile = File(destDir, srcFile.name)

    // --- ВАЖНО: создать директорию, если её нет ---
    if (!destDir.exists()) {
        destDir.mkdirs()
    }

    val timeoutMillis = 5_000L // максимальное время ожидания (например, 5 секунд)
    val pollInterval = 100L
    val start = System.currentTimeMillis()

    while (srcFile.length() == 0L && System.currentTimeMillis() - start < timeoutMillis) {
        Thread.sleep(pollInterval)
    }

    if (srcFile.length() == 0L) {
        Log.d("FileCopy", "srcFile.length() == 0L")
        EventLogger.log(context, "srcFile.length() == 0L")
        return
    }
    if (destFile.exists() && destFile.isFile) {
        Log.w("FileCopy", "Файл уже существует: $destFile")
        EventLogger.log(context, "Файл уже существует: $destFile")
        return
    }
    if (!srcFile.exists() || !srcFile.isFile) {
        Log.w("FileCopy", "Файл не найден или не является файлом: $srcFile")
        EventLogger.log(context, "Файл не найден или не является файлом: $srcFile")
        return
    }
    if (!srcFile.canRead()) {
        Log.w("FileCopy", "Нет прав на чтение файла: $srcFile")
        EventLogger.log(context, "Нет прав на чтение файла: $srcFile")
        return
    }

    EventLogger.log(context, "Найден файл: ${srcFile.name}")
    Log.d("FileCopy", "Найден файл: srcFile=$srcFile size=${srcFile.length()} destFile=$destFile")

    try {
        srcFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
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
        } else {
            Log.e("FileCopy", "Ошибка копирования: файл не существует или размеры не совпадают")
            EventLogger.log(
                context,
                "Ошибка копирования: файл не существует или размеры не совпадают"
            )
        }
    } catch (e: Exception) {
        Log.e("FileCopy", "Ошибка копирования файла", e)
        EventLogger.log(context, "Ошибка копирования файла: ${e.message}")
    }
}

fun createFileObserver(
    path: String,
    mask: Int,
    onEvent: (event: Int, path: String?) -> Unit
): FileObserver {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        object : FileObserver(listOf(File(path)), mask) {
            override fun onEvent(event: Int, path: String?) {
                onEvent(event, path)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        object : FileObserver(path, mask) {
            override fun onEvent(event: Int, path: String?) {
                onEvent(event, path)
            }
        }
    }
}

// Пример интеграции с LatestFolderWatcher:
// val watcher = LatestFolderWatcher(context, sourceDirPath, destDirPath) { fullPath ->
//     copyFileWithChecks(context, File(fullPath), destDirPath)
// }
// watcher.startWatching()
// ...
// watcher.stopWatching()