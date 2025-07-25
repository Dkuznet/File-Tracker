package com.example.filetracker.util

import android.content.Context
import android.media.MediaScannerConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileUtilsTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        tempDir = File(context.filesDir, "test_dir_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        mockkStatic(EventLogger::class)
        every {
            EventLogger.log(
                any<String>(),
                any(),
                any<LogLevel>(),
                any<Boolean>()
            )
        } returns Unit
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun createDestDirIfNotExists_emptyDir_logsErrorAndReturns() {
        val emptyDir = ""

        FileUtils.createDestDirIfNotExists(emptyDir)

        verify {
            EventLogger.log(
                "destDir пустой или содержит только пробелы",
                "createDestDirIfNotExists",
                LogLevel.ERROR,
                true
            )
        }

        Assert.assertFalse(File(emptyDir).exists())
    }

    @Test
    fun createDestDirIfNotExists_existingFileNotDir_logsError() {
        val testFile = File(tempDir, "not_a_dir.txt")
        testFile.createNewFile()

        FileUtils.createDestDirIfNotExists(testFile.absolutePath)

        verify {
            EventLogger.log(
                match { it.contains("Путь существует, но не является директорией") },
                "createDestDirIfNotExists",
                LogLevel.ERROR,
                true
            )
        }

        Assert.assertTrue(testFile.exists() && testFile.isFile)
    }

    @Test
    fun createDestDirIfNotExists_nonExistingDir_createsDir() {
        val newDir = File(tempDir, "new_dir").absolutePath

        FileUtils.createDestDirIfNotExists(newDir)

        Assert.assertTrue(File(newDir).exists() && File(newDir).isDirectory)

        verify {
            EventLogger.log(
                match { it.contains("Директория успешно создана") },
                "createDestDirIfNotExists",
                any(),
                any()
            )
        }
    }

    @Test
    fun createDestDirIfNotExists_existingDir_doesNothing() {
        val existingDir = File(tempDir, "existing_dir")
        existingDir.mkdirs()

        FileUtils.createDestDirIfNotExists(existingDir.absolutePath)

        verify(exactly = 0) {
            EventLogger.log(any(), any(), any(), any())
        }

        Assert.assertTrue(existingDir.exists() && existingDir.isDirectory)
    }

    @Test
    fun buildDestinationPath_sourceContainsAppDir_buildsRelativePath() {
        val appDir = "/app/base"
        val outputDir = "/output"
        val sourcePath = "/app/base/sub/dir/file.txt"

        val result = FileUtils.buildDestinationPath(appDir, outputDir, sourcePath)

        Assert.assertEquals("/output/sub/dir/file.txt", result)

        verify {
            EventLogger.log(
                match { it.contains("result=/output/sub/dir/file.txt") },
                "buildDestinationPath",
                any(),
                true
            )
        }
    }

    @Test
    fun buildDestinationPath_sourceDoesNotContainAppDir_usesFileName() {
        val appDir = "/app/base"
        val outputDir = "/output"
        val sourcePath = "/other/path/file.txt"

        val result = FileUtils.buildDestinationPath(appDir, outputDir, sourcePath)

        Assert.assertEquals("/output/file.txt", result)

        verify {
            EventLogger.log(
                match { it.contains("sourcePath не содержит /app/base") },
                "buildDestinationPath",
                any(),
                any()
            )
        }
    }

    @Test
    fun checkFileConditions_fileSizeGreaterThanZero_returnsTrue() {
        val testFile = File(tempDir, "test_file.txt")
        testFile.writeText("content")

        val result = FileUtils.checkFileConditions(testFile.absolutePath)

        Assert.assertTrue(result)

        verify(exactly = 0) {
            EventLogger.log(any(), any(), LogLevel.WARN, any())
        }
    }

    @Test
    fun checkFileConditions_fileSizeZeroAfterTimeout_returnsFalse() {
        val testFile = File(tempDir, "empty_file.txt")
        testFile.createNewFile() // Size 0

        mockkStatic(System::class)
        var currentTime = System.currentTimeMillis()
        every { System.currentTimeMillis() } answers { currentTime }

        mockkStatic(Thread::class)
        every { Thread.sleep(any()) } answers {
            currentTime += it.invocation.args[0] as Long
        }

        val result = FileUtils.checkFileConditions(testFile.absolutePath)

        Assert.assertFalse(result)

        verify {
            EventLogger.log(
                "srcFile.length() == 0L",
                "FileCopy",
                LogLevel.WARN,
                any()
            )
        }
    }

    @Test
    fun fileCopy_successfulCopy_returnsTrueAndScansMedia() {
        val srcFile = File(tempDir, "src.txt")
        srcFile.writeText("content")
        val destFile = File(tempDir, "dest.txt")

        mockkStatic(MediaScannerConnection::class)
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } returns Unit

        val result = FileUtils.fileCopy(context, srcFile, destFile)

        Assert.assertTrue(result)
        Assert.assertTrue(destFile.exists() && destFile.length() == srcFile.length())

        verify {
            EventLogger.log(
                match { it.contains("Файл успешно скопирован") },
                "FileCopy",
                any(),
                any()
            )
        }

        verify {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                null,
                any()
            )
        }
    }

    @Test
    fun fileCopy_sourceNotExists_returnsFalse() {
        val srcFile = File(tempDir, "nonexistent.txt")
        val destFile = File(tempDir, "dest.txt")

        val result = FileUtils.fileCopy(context, srcFile, destFile)

        Assert.assertFalse(result)

        verify {
            EventLogger.log(
                match { it.contains("Файл не найден или не является файлом") },
                "FileCopy",
                LogLevel.WARN,
                any()
            )
        }
    }

    @Test
    fun fileCopy_destExists_returnsFalse() {
        val srcFile = File(tempDir, "src.txt")
        srcFile.writeText("content")
        val destFile = File(tempDir, "dest.txt")
        destFile.createNewFile()

        val result = FileUtils.fileCopy(context, srcFile, destFile)

        Assert.assertFalse(result)

        verify {
            EventLogger.log(
                match { it.contains("Файл уже существует") },
                "FileCopy",
                LogLevel.WARN,
                true
            )
        }
    }

    @Test
    fun fileCopy_copyError_returnsFalse() {
        val destFile = File(tempDir, "dest.txt")

        val srcFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { canRead() } returns true
            every { length() } returns 7L
            every { inputStream() } throws RuntimeException("Copy error")
            every { absolutePath } returns "mock_path"
        }

        val result = FileUtils.fileCopy(context, srcFile, destFile)

        Assert.assertFalse(result)

        verify {
            EventLogger.log(
                match { it.contains("Ошибка копирования файла") },
                "FileCopy",
                LogLevel.ERROR,
                any()
            )
        }
    }
}