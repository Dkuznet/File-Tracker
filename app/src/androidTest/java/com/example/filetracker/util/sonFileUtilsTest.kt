package com.example.filetracker.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileUtilsInstrumentationTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var tempFiles: MutableList<File>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.cacheDir, "test_file_utils")
        tempFiles = mutableListOf()

        // Очищаем тестовую директорию
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
        testDir.mkdirs()

        // Мокаем EventLogger для избежания зависимостей
        mockkObject(EventLogger)
        every { EventLogger.log(any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        // Очищаем созданные файлы
        tempFiles.forEach { file ->
            if (file.exists()) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            }
        }

        // Очищаем тестовую директорию
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }

        unmockkObject(EventLogger)
    }

    private fun createTempFile(name: String, content: String = "test content"): File {
        val file = File(testDir, name)
        file.writeText(content)
        tempFiles.add(file)
        return file
    }

    private fun createTempDir(name: String): File {
        val dir = File(testDir, name)
        dir.mkdirs()
        tempFiles.add(dir)
        return dir
    }

    @Test
    fun testCreateDestDirIfNotExists_CreatesNewDirectory() {
        val newDirPath = File(testDir, "new_directory").absolutePath

        FileUtils.createDestDirIfNotExists(newDirPath)

        assertTrue("Директория должна быть создана", File(newDirPath).exists())
        assertTrue("Путь должен быть директорией", File(newDirPath).isDirectory)

        verify {
            EventLogger.log(
                message = "Директория успешно создана: $newDirPath",
                logTag = "createDestDirIfNotExists"
            )
        }
    }

    @Test
    fun testCreateDestDirIfNotExists_ExistingDirectory() {
        val existingDir = createTempDir("existing_dir")

        FileUtils.createDestDirIfNotExists(existingDir.absolutePath)

        assertTrue("Директория должна существовать", existingDir.exists())
        assertTrue("Путь должен быть директорией", existingDir.isDirectory)
    }

    @Test
    fun testCreateDestDirIfNotExists_EmptyPath() {
        FileUtils.createDestDirIfNotExists("")

        verify {
            EventLogger.log(
                message = "destDir пустой или содержит только пробелы",
                logTag = "createDestDirIfNotExists",
                log = LogLevel.ERROR,
                extra = true
            )
        }
    }

    @Test
    fun testCreateDestDirIfNotExists_BlankPath() {
        FileUtils.createDestDirIfNotExists("   ")

        verify {
            EventLogger.log(
                message = "destDir пустой или содержит только пробелы",
                logTag = "createDestDirIfNotExists",
                log = LogLevel.ERROR,
                extra = true
            )
        }
    }

    @Test
    fun testCreateDestDirIfNotExists_FileExistsAtPath() {
        val file = createTempFile("existing_file.txt")

        FileUtils.createDestDirIfNotExists(file.absolutePath)

        verify {
            EventLogger.log(
                message = "Путь существует, но не является директорией: ${file.absolutePath}",
                logTag = "createDestDirIfNotExists",
                log = LogLevel.ERROR,
                extra = true
            )
        }
    }

    @Test
    fun testBuildDestinationPath_WithAppDirInPath() {
        val appDir = "/storage/emulated/0/Android/data/com.example.app"
        val outputDir = "/storage/emulated/0/Download"
        val sourcePath = "/storage/emulated/0/Android/data/com.example.app/files/test.txt"

        val result = FileUtils.buildDestinationPath(appDir, outputDir, sourcePath)

        assertEquals("/storage/emulated/0/Download/files/test.txt", result)

        verify {
            EventLogger.log(
                message = "outputDir=$outputDir, sourcePath=$sourcePath, result=$result",
                logTag = "buildDestinationPath",
                extra = true
            )
        }
    }

    @Test
    fun testBuildDestinationPath_WithoutAppDirInPath() {
        val appDir = "/storage/emulated/0/Android/data/com.example.app"
        val outputDir = "/storage/emulated/0/Download"
        val sourcePath = "/storage/emulated/0/Pictures/test.jpg"

        val result = FileUtils.buildDestinationPath(appDir, outputDir, sourcePath)

        assertEquals("/storage/emulated/0/Download/test.jpg", result)

        verify {
            EventLogger.log(
                message = "sourcePath не содержит $appDir, используется имя файла: $sourcePath, result=$result",
                logTag = "buildDestinationPath"
            )
        }
    }

    @Test
    fun testCheckFileConditions_NonEmptyFile() {
        val file = createTempFile("test.txt", "some content")

        val result = FileUtils.checkFileConditions(file.absolutePath)

        assertTrue("Файл с содержимым должен пройти проверку", result)
    }

    @Test
    fun testCheckFileConditions_EmptyFile() {
        val file = createTempFile("empty.txt", "")

        val result = FileUtils.checkFileConditions(file.absolutePath)

        assertFalse("Пустой файл не должен пройти проверку", result)

        verify {
            EventLogger.log(
                message = "srcFile.length() == 0L",
                logTag = "FileCopy",
                log = LogLevel.WARN
            )
        }
    }

    @Test
    fun testFileCopy_SuccessfulCopy() {
        val sourceFile = createTempFile("source.txt", "test content for copy")
        val destFile = File(testDir, "destination.txt")
        tempFiles.add(destFile)

        val result = FileUtils.fileCopy(context, sourceFile, destFile)

        assertTrue("Копирование должно быть успешным", result)
        assertTrue("Файл назначения должен существовать", destFile.exists())
        assertEquals("Размеры файлов должны совпадать", sourceFile.length(), destFile.length())
        assertEquals("Содержимое должно совпадать", sourceFile.readText(), destFile.readText())

        verify {
            EventLogger.log(
                message = "Файл успешно скопирован: $destFile size=${destFile.length()}",
                logTag = "FileCopy"
            )
        }
    }

    @Test
    fun testFileCopy_SourceFileNotExists() {
        val nonExistentFile = File(testDir, "non_existent.txt")
        val destFile = File(testDir, "destination.txt")

        val result = FileUtils.fileCopy(context, nonExistentFile, destFile)

        assertFalse("Копирование несуществующего файла должно завершиться неудачей", result)

        verify {
            EventLogger.log(
                message = "Файл не найден или не является файлом: $nonExistentFile",
                logTag = "FileCopy",
                log = LogLevel.WARN
            )
        }
    }

    @Test
    fun testFileCopy_DestinationFileExists() {
        val sourceFile = createTempFile("source.txt", "source content")
        val destFile = createTempFile("destination.txt", "existing content")

        val result = FileUtils.fileCopy(context, sourceFile, destFile)

        assertFalse("Копирование в существующий файл должно завершиться неудачей", result)

        verify {
            EventLogger.log(
                message = "Файл уже существует: $destFile",
                logTag = "FileCopy",
                log = LogLevel.WARN,
                extra = true
            )
        }
    }

    @Test
    fun testFileCopy_CreatesDestinationDirectory() {
        val sourceFile = createTempFile("source.txt", "test content")
        val destDir = File(testDir, "new_subdir")
        val destFile = File(destDir, "destination.txt")
        tempFiles.add(destDir)
        tempFiles.add(destFile)

        val result = FileUtils.fileCopy(context, sourceFile, destFile)

        assertTrue("Копирование должно быть успешным", result)
        assertTrue("Директория назначения должна быть создана", destDir.exists())
        assertTrue("Файл должен быть скопирован", destFile.exists())
    }

    @Test
    fun testFileCopy_SourceIsDirectory() {
        val sourceDir = createTempDir("source_dir")
        val destFile = File(testDir, "destination.txt")

        val result = FileUtils.fileCopy(context, sourceDir, destFile)

        assertFalse("Копирование директории как файла должно завершиться неудачей", result)

        verify {
            EventLogger.log(
                message = "Файл не найден или не является файлом: $sourceDir",
                logTag = "FileCopy",
                log = LogLevel.WARN
            )
        }
    }

    @Test
    fun testFileCopy_IOExceptionHandling() {
        val sourceFile = createTempFile("source.txt", "test content")

        // Создаем файл назначения в недоступной для записи директории
        val readOnlyDir = File(testDir, "readonly")
        readOnlyDir.mkdirs()
        readOnlyDir.setWritable(false)
        val destFile = File(readOnlyDir, "destination.txt")
        tempFiles.add(readOnlyDir)

        val result = FileUtils.fileCopy(context, sourceFile, destFile)

        assertFalse("Копирование в недоступную директорию должно завершиться неудачей", result)

        // Восстанавливаем права для очистки
        readOnlyDir.setWritable(true)
    }

    @Test
    fun testFileCopy_NullParentDirectory() {
        val sourceFile = createTempFile("source.txt", "test content")

        // Создаем мок файла с null parent
        val mockDestFile = mockk<File>()
        every { mockDestFile.parent } returns null
        every { mockDestFile.absolutePath } returns "/mock/path"
        every { mockDestFile.exists() } returns false

        val result = FileUtils.fileCopy(context, sourceFile, mockDestFile)

        assertFalse("Копирование с null parent должно завершиться неудачей", result)

        verify {
            EventLogger.log(
                message = "Родительская директория не определена для /mock/path",
                logTag = "FileCopy",
                log = LogLevel.ERROR
            )
        }
    }
}