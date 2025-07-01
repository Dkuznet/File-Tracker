package com.example.filetracker.util

import android.content.Context
import android.net.Uri
import java.io.File

// SAF-получение пути из Uri (при необходимости)
fun uriToFilePath(context: Context, uri: Uri): String? {
    // Пример для DocumentsContract/MediaStore, требуется доработка для всех случаев
    // Здесь предполагается, что пользователь выберет обычную папку в файловой системе (например, в файловом менеджере)
    // Лучше использовать сторонние библиотеки или сервисы для надёжного преобразования Uri -> File
    // Здесь просто пример для tree Uri от SAF
    val path = uri.path ?: return null
    val externalPrefix = "/tree/primary:"
    return if (path.startsWith(externalPrefix)) {
        val relPath = path.removePrefix(externalPrefix)
        "/storage/emulated/0/$relPath"
    } else null
}

/**
 * Удаляет служебные сегменты типа tree/primary:/document/ из пути и возвращает относительный путь.
 * Для обычных путей теперь просто возвращает путь, очищенный от ведущих/замыкающих слэшей.
 */
fun cleanPathFromTree(path: String): String {
    return path
    // Для совместимости с Uri-стилем (если вдруг строка содержит tree/ или document/)
//    val lastDocumentIdx = path.lastIndexOf("/document/")
//    val lastTreeIdx = path.lastIndexOf("/tree/")
//    val cutIdx = when {
//        lastDocumentIdx >= 0 -> lastDocumentIdx + "/document/".length
//        lastTreeIdx >= 0 -> lastTreeIdx + "/tree/".length
//        else -> 0
//    }
//    var clean = path.substring(cutIdx)
//    clean = clean.replace("%3A", ":").replace("%2F", "/")
//    clean = clean.removePrefix("primary:")
//    return clean.trim('/')
}

/** Возвращает строку из последних двух сегментов пути OutputDir */
fun getShortPath(path: String): String {
    val clean = cleanPathFromTree(path)
    val segments = clean.split(File.separatorChar, '/').filter { it.isNotEmpty() }
    return if (segments.size >= 2)
        segments.takeLast(2).joinToString("/")
    else
        clean
}

/**
 * Возвращает относительный путь от baseDir к targetDir (только path-сегменты).
 */
fun getRelativePath(basePath: String, targetPath: String): String {
    val baseClean = cleanPathFromTree(basePath)
    val targetClean = cleanPathFromTree(targetPath)
    return if (targetClean.startsWith(baseClean)) {
        targetClean.removePrefix(baseClean).trimStart('/', '\\')
    } else {
        ""
    }
}

/**
 * Формирует destPath для трекера: OutputDir + /<2 последних уровня из sourcePath>
 */
fun buildDestPath(outputDir: String, sourcePath: String): String {
    val clean = cleanPathFromTree(sourcePath)
    val segments = clean.split(File.separatorChar, '/').filter { it.isNotEmpty() }
    val last2 = segments.takeLast(2)
    val file = File(outputDir, last2.joinToString(File.separator))
    return file.absolutePath
}

/**
 * Создаёт папку destDir если её ещё нет (в том числе промежуточные директории).
 */
fun createDestDirIfNotExists(context: Context, outputDirPath: String, destPath: String) {
    val outputDirFile = File(outputDirPath)
    val outputPath = getRelativePath(outputDirPath, destPath)
    if (outputPath.isEmpty()) return

    // Создаём подпапки по сегментам (включая последние два)
    val fullDestDir = File(outputDirFile, outputPath)
    if (!fullDestDir.exists()) {
        fullDestDir.mkdirs()
    }
}