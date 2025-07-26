package com.example.filetracker.service

import android.content.Context
import android.os.Build
import android.os.FileObserver
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.FileUtils
import com.example.filetracker.util.LogLevel
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
    private val destDirPath: String,
    private val watchSubfolders: Boolean = true, // true = следить за подпапками, false = следить за файлами в rootPath
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
            var msgLog =
                "rootObserver event=$event path=$relativePath watchSubfolders=$watchSubfolders"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher", extra = true)

            if (watchSubfolders) {
                // Режим слежения за подпапками
                if (((event and FileObserver.CREATE != 0) || (event and FileObserver.MOVED_TO != 0)) && file.isDirectory) {
                    msgLog = "New subfolder detected: $fullPath"
                    EventLogger.log(msgLog, logTag = "rootObserver")
                    val latest = findLatestSubfolder()
                    if (latest != null && latest != currentFolder) {
                        msgLog = "Switching to new latest subfolder: ${latest.absolutePath}"
                        EventLogger.log(msgLog, logTag = "rootObserver")
                        switchToFolder(latest)
                    }
                }
            } else {
                // Режим слежения за файлами в самой rootPath
                if (((event and FileObserver.CREATE != 0) || (event and FileObserver.MOVED_TO != 0)) && file.isFile && relativePath != null) {
                    msgLog = "New file in root folder: $fullPath"
                    EventLogger.log(msgLog, logTag = "rootObserver")

                    // Проверяем условия для файла
                    if (!FileUtils.checkFileConditions(file.absolutePath)) {
                        EventLogger.log(
                            message = "Файл не прошёл проверку условий: $file.absolutePath",
                            logTag = "rootObserver",
                            log = LogLevel.ERROR
                        )
                    } else {
                        FileUtils.fileCopy(
                            context = context,
                            srcFile = file,
                            destFile = File(destDirPath, file.name)
                        )
                    }
                }
            }
        }

    fun startWatching() {
        var msgLog = ""
        if (isWatching) {
            msgLog = "startWatching called, but already watching root: $rootPath"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher", log = LogLevel.WARN)
            return
        }
        isWatching = true
        msgLog = "Start watching root: $rootPath, watchSubfolders=$watchSubfolders"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
        rootObserver.startWatching()

        if (watchSubfolders) {
            // Режим слежения за подпапками
            val latest = findLatestSubfolder()
            if (latest != null) {
                msgLog = "Found latest subfolder on start: ${latest.absolutePath}"
                EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
                switchToFolder(latest)
            } else {
                msgLog = "No subfolders found on start in $rootPath"
                EventLogger.log(msgLog, logTag = "LatestFolderWatcher", log = LogLevel.WARN)
            }
        } else {
            // Режим слежения за файлами в rootPath - копируем существующие файлы
            msgLog = "Copying existing files from root folder: $rootPath"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
            val files = File(rootPath).listFiles()?.filter { it.isFile }
            msgLog = "Found ${files?.size ?: 0} files in root folder $rootPath"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
            files?.forEach { file ->
                FileUtils.fileCopy(
                    context = context,
                    srcFile = file,
                    destFile = File(destDirPath, file.name)
                )
            }
        }
    }

    fun stopWatching() {
        var msgLog = ""
        if (!isWatching) {
            msgLog = "stopWatching called, but was not watching root: $rootPath"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher", log = LogLevel.WARN)
            return
        }
        isWatching = false
        msgLog = "Stop watching root: $rootPath"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
        rootObserver.stopWatching()
        currentObserver?.stopWatching()
        currentObserver = null
        currentFolder = null
    }

    private fun findLatestSubfolder(): File? {
        val subfolders = File(rootPath).listFiles()?.filter { it.isDirectory }
        val msgLog = "findLatestSubfolder: found ${subfolders?.size ?: 0} subfolders in $rootPath"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
        return subfolders?.maxByOrNull { it.name }
    }

    private fun switchToFolder(folder: File) {
        var msgLog = "switchToFolder: Switching to subfolder: ${folder.absolutePath}"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
        currentObserver?.stopWatching()
        currentFolder = folder
        msgLog = "Switching to subfolder: ${folder.absolutePath}"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher", extra = true)

        // --- Копируем все файлы из новой папки ---
        msgLog = "Search files in new subfolder: ${folder.absolutePath}"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
        val files = folder.listFiles()?.filter { it.isFile }
        msgLog = "Found ${files?.size ?: 0} files in new subfolder ${folder.absolutePath}"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
        files?.forEach { file ->
            msgLog = "Copying file: ${file.absolutePath}"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
            FileUtils.fileCopy(
                context = context,
                srcFile = file,
                destFile = File(destDirPath, file.name)
            )
        }

        currentObserver = createFileObserver(folder.absolutePath, mask) { event, relativePath ->
            var msgLog =
                "currentObserver event=$event path=$relativePath in folder=${folder.absolutePath}"
            EventLogger.log(msgLog, logTag = "LatestFolderWatcher", extra = true)
            if (((event and FileObserver.CREATE != 0) || (event and FileObserver.MOVED_TO != 0)) && relativePath != null) {
                val fullPath = "${folder.absolutePath}/$relativePath"
                msgLog = "New file in latest subfolder: $fullPath"
                EventLogger.log(msgLog, logTag = "LatestFolderWatcher")
                FileUtils.fileCopy(
                    context = context,
                    srcFile = File(fullPath),
                    destFile = File(destDirPath, File(fullPath).name)
                )
            }
        }.apply { startWatching() }
        msgLog = "Started watching new subfolder: ${folder.absolutePath}"
        EventLogger.log(msgLog, logTag = "LatestFolderWatcher", extra = true)
    }
}


/**
 * Копирует файл srcFile в destDirPath с проверками и логированием, аналогично FileObserverWrapper.
 */
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
