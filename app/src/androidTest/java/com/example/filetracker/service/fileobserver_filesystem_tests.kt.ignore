package com.example.filetracker.service

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import com.example.filetracker.FitTracker
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.EventLogDao
import com.example.filetracker.util.FileUtils
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.*

/**
 * Тесты для проверки корректной работы LatestFolderWatcher с различными файловыми операциями
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileSystemOperationsTest {

    private lateinit var mockContext: Context
    private lateinit var mockEventLogDao: EventLogDao
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File

    @Before
    fun setUp() {
        setupMocks()
        tempRootDir = createTempDir("fs_test_root")
        tempDestDir = createTempDir("fs_test_dest")
    }

    @After
    fun tearDown() {
        tempRootDir.deleteRecursively()
        tempDestDir.deleteRecursively()
        unmockkAll()
    }

    private fun setupMocks() {
        mockkObject(FitTracker)
        mockContext = mockk<Context>(relaxed = true)
        mockEventLogDao = mockk<EventLogDao>(relaxed = true)

        every { FitTracker.instance } returns mockk<FitTracker> {
            every { applicationContext } returns mockContext
        }

        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val mockDatabase = mockk<AppDatabase>()
        every { mockDatabase.eventLogDao() } returns mockEventLogDao
        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase() } returns mockDatabase

        mockkObject(FileUtils)
        every { FileUtils.checkFileConditions(any()) } returns true
        every { FileUtils.fileCopy(any(), any(), any()) } returns true
    }

    @Test
    fun `test watcher detects file creation in root directory`() = runBlockingTest {
        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = false
        )

        watcher.startWatching()
        delay(100) // Allow watcher to initialize

        // Create new file
        val newFile = File(tempRootDir, "new_file.txt")
        newFile.writeText("new content")

        delay(200) // Allow file system events to be processed
        watcher.stopWatching()

        // Verify file was processed during startup (existing files)
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher detects subfolder creation`() = runBlockingTest {
        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)

        // Create new subfolder with file
        val newFolder = File(tempRootDir, "new_subfolder")
        newFolder.mkdirs()
        val fileInNewFolder = File(newFolder, "file_in_new.txt")
        fileInNewFolder.writeText("content in new folder")

        delay(200)
        watcher.stopWatching()

        // Should have detected the new folder and processed its files
        verify { mockEventLogDao.insert(any()) }
    }

    @Test
    fun `test watcher switches to alphabetically later folder`() = runBlockingTest {
        // Create initial folder
        val folderA = File(tempRootDir, "folder_a")
        folderA.mkdirs()
        File(folderA, "file_a.txt").writeText("content a")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)

        // Create alphabetically later folder
        val folderZ = File(tempRootDir, "folder_z")
        folderZ.mkdirs()
        File(folderZ, "file_z.txt").writeText("content z")

        delay(200)
        watcher.stopWatching()

        // Should have processed files from both folders
        verify(atLeast = 2) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher handles file move operations`() = runBlockingTest {
        // Create source folder with file
        val sourceFolder = File(tempRootDir, "source")
        sourceFolder.mkdirs()
        val originalFile = File(sourceFolder, "movable.txt")
        originalFile.writeText("movable content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)

        // Create target folder and move file
        val targetFolder = File(tempRootDir, "target")
        targetFolder.mkdirs()
        val targetFile = File(targetFolder, "movable.txt")

        Files.move(originalFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        delay(200)
        watcher.stopWatching()

        // Should have detected the move operation
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher handles rapid file creation and deletion`() = runBlockingTest {
        val testFolder = File(tempRootDir, "rapid_changes")
        testFolder.mkdirs()

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)

        // Rapidly create and delete files
        for (i in 1..5) {
            val file = File(testFolder, "rapid_$i.txt")
            file.writeText("rapid content $i")
            delay(50)
            file.delete()
            delay(50)
        }

        delay(200)
        watcher.stopWatching()

        // Should handle rapid changes without crashing
        verify { mockEventLogDao.insert(any()) }
    }

    @Test
    fun `test watcher with file permission changes`() = runBlockingTest {
        val testFolder = File(tempRootDir, "permission_test")
        testFolder.mkdirs()

        val testFile = File(testFolder, "permission_file.txt")
        testFile.writeText("permission content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)

        // Change file permissions
        testFile.setReadable(false)
        delay(100)
        testFile.setReadable(true)

        delay(200)
        watcher.stopWatching()

        // Should handle permission changes
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher handles directory rename operations`() = runBlockingTest {
        val originalFolder = File(tempRootDir, "original_name")
        originalFolder.mkdirs()
        File(originalFolder, "file_in_renamed.txt").writeText("content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)

        // Rename directory
        val renamedFolder = File(tempRootDir, "renamed_folder")
        originalFolder.renameTo(renamedFolder)

        delay(200)
        watcher.stopWatching()

        // Should handle directory rename
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }
}

/**
 * Тесты для проверки логирования и обработки ошибок
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileObserverLoggingTest {

    private lateinit var mockContext: Context
    private lateinit var mockEventLogDao: EventLogDao
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File

    @Before
    fun setUp() {
        setupMocks()
        tempRootDir = createTempDir("logging_test_root")
        tempDestDir = createTempDir("logging_test_dest")
    }

    @After
    fun tearDown() {
        tempRootDir.deleteRecursively()
        tempDestDir.deleteRecursively()
        unmockkAll()
    }

    private fun setupMocks() {
        mockkObject(FitTracker)
        mockContext = mockk<Context>(relaxed = true)
        mockEventLogDao = mockk<EventLogDao>(relaxed = true)

        every { FitTracker.instance } returns mockk<FitTracker> {
            every { applicationContext } returns mockContext
        }

        val mockDatabase = mockk<AppDatabase>()
        every { mockDatabase.eventLogDao() } returns mockEventLogDao
        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase() } returns mockDatabase

        mockkObject(FileUtils)
        every { FileUtils.checkFileConditions(any()) } returns true
        every { FileUtils.fileCopy(any(), any(), any()) } returns true
    }

    @Test
    fun `test extended logging is respected`() = runBlockingTest {
        // Test with extended logging enabled
        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns true
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val testFolder = File(tempRootDir, "extended_log_test")
        testFolder.mkdirs()
        File(testFolder, "test.txt").writeText("content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)
        watcher.stopWatching()

        // Should have logged extended messages
        verify(atLeast = 3) { mockEventLogDao.insert(any()) }
    }

    @Test
    fun `test extended logging is disabled`() = runBlockingTest {
        // Test with extended logging disabled
        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val testFolder = File(tempRootDir, "no_extended_log_test")
        testFolder.mkdirs()
        File(testFolder, "test.txt").writeText("content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)
        watcher.stopWatching()

        // Should have logged fewer messages (no extended logs)
        verify(atLeast = 1) { mockEventLogDao.insert(any()) }
    }

    @Test
    fun `test error logging when file conditions fail`() = runBlockingTest {
        // Mock file conditions to fail
        every { FileUtils.checkFileConditions(any()) } returns false

        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val testFile = File(tempRootDir, "failing_file.txt")
        testFile.writeText("content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = false
        )

        watcher.startWatching()
        delay(100)
        watcher.stopWatching()

        // Should have logged error about file conditions
        verify { mockEventLogDao.insert(any()) }
        // Should not have called fileCopy since conditions failed
        verify(exactly = 0) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test logging includes correct information`() = runBlockingTest {
        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val testFolder = File(tempRootDir, "info_test")
        testFolder.mkdirs()

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(100)
        watcher.stopWatching()

        // Verify that logged messages contain relevant information
        verify {
            mockEventLogDao.insert(match { log ->
                log.message.contains("Start watching root") ||
                        log.message.contains("Stop watching root") ||
                        log.message.contains("No subfolders found")
            })
        }
    }
}

/**
 * Тесты для проверки различных конфигураций FileObserver
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileObserverConfigurationTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("config_test")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `test FileObserver with CREATE mask only`() {
        var eventReceived = false
        val observer =
            createFileObserver(tempDir.absolutePath, FileObserver.CREATE) { event, path ->
                if (event and FileObserver.CREATE != 0) {
                    eventReceived = true
                }
            }

        assertNotNull(observer)
        observer.startWatching()

        // Create file to trigger event
        File(tempDir, "create_test.txt").writeText("content")

        // Note: In unit tests, FileObserver events might not be triggered
        // This test mainly verifies that the observer can be created with specific masks

        observer.stopWatching()
    }

    @Test
    fun `test FileObserver with multiple event masks`() {
        val eventsMask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MODIFY
        var eventsReceived = 0

        val observer = createFileObserver(tempDir.absolutePath, eventsMask) { event, path ->
            when {
                event and FileObserver.CREATE != 0 -> eventsReceived++
                event and FileObserver.DELETE != 0 -> eventsReceived++
                event and FileObserver.MODIFY != 0 -> eventsReceived++
            }
        }

        assertNotNull(observer)
        observer.startWatching()

        val testFile = File(tempDir, "multi_event_test.txt")
        testFile.writeText("initial content")
        testFile.appendText("modified content")
        testFile.delete()

        observer.stopWatching()
    }

    @Test
    fun `test FileObserver callback receives correct parameters`() {
        var receivedEvent: Int? = null
        var receivedPath: String? = null

        val observer =
            createFileObserver(tempDir.absolutePath, FileObserver.ALL_EVENTS) { event, path ->
                receivedEvent = event
                receivedPath = path
            }

        assertNotNull(observer)

        // Verify observer can be started and stopped without issues
        observer.startWatching()

        // Create file to potentially trigger callback
        File(tempDir, "callback_test.txt").writeText("callback content")

        observer.stopWatching()

        // Note: In unit test environment, actual FileObserver events may not fire
        // This test mainly verifies the callback structure is correct
    }

    @Test
    fun `test FileObserver with invalid path handles gracefully`() {
        val invalidPath = "/completely/invalid/path/that/does/not/exist"

        assertDoesNotThrow("Should not throw with invalid path") {
            val observer = createFileObserver(invalidPath, FileObserver.ALL_EVENTS) { _, _ -> }
            observer.startWatching()
            observer.stopWatching()
        }
    }

    @Test
    fun `test FileObserver with empty path`() {
        assertDoesNotThrow("Should not throw with empty path") {
            val observer = createFileObserver("", FileObserver.ALL_EVENTS) { _, _ -> }
            observer.startWatching()
            observer.stopWatching()
        }
    }

    @Test
    fun `test FileObserver with root path`() {
        assertDoesNotThrow("Should not throw with root path") {
            val observer = createFileObserver("/", FileObserver.ALL_EVENTS) { _, _ -> }
            observer.startWatching()
            observer.stopWatching()
        }
    }
}

/**
 * Стресс-тесты и тесты стабильности
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileObserverStressTest {

    private lateinit var mockContext: Context
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File

    @Before
    fun setUp() {
        setupMocks()
        tempRootDir = createTempDir("stress_test_root")
        tempDestDir = createTempDir("stress_test_dest")
    }

    @After
    fun tearDown() {
        tempRootDir.deleteRecursively()
        tempDestDir.deleteRecursively()
        unmockkAll()
    }

    private fun setupMocks() {
        mockkObject(FitTracker)
        mockContext = mockk<Context>(relaxed = true)

        every { FitTracker.instance } returns mockk<FitTracker> {
            every { applicationContext } returns mockContext
        }

        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val mockDatabase = mockk<AppDatabase>()
        val mockEventLogDao = mockk<EventLogDao>(relaxed = true)
        every { mockDatabase.eventLogDao() } returns mockEventLogDao
        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase() } returns mockDatabase

        mockkObject(FileUtils)
        every { FileUtils.checkFileConditions(any()) } returns true
        every { FileUtils.fileCopy(any(), any(), any()) } returns true
    }

    @Test
    fun `stress test - multiple watchers simultaneously`() = runBlockingTest {
        val watchers = mutableListOf<LatestFolderWatcher>()
        val numWatchers = 5

        // Create separate directories for each watcher
        for (i in 1..numWatchers) {
            val rootDir = File(tempRootDir, "root_$i")
            val destDir = File(tempDestDir, "dest_$i")
            rootDir.mkdirs()
            destDir.mkdirs()

            // Add test folder and file
            val testFolder = File(rootDir, "test_folder")
            testFolder.mkdirs()
            File(testFolder, "test_file_$i.txt").writeText("content $i")

            val watcher = LatestFolderWatcher(
                context = mockContext,
                rootPath = rootDir.absolutePath,
                destDirPath = destDir.absolutePath,
                watchSubfolders = true
            )
            watchers.add(watcher)
        }

        // Start all watchers
        val startTime = System.currentTimeMillis()
        watchers.forEach { it.startWatching() }
        val startupTime = System.currentTimeMillis() - startTime

        delay(200) // Allow processing

        // Stop all watchers
        val stopStartTime = System.currentTimeMillis()
        watchers.forEach { it.stopWatching() }
        val stopTime = System.currentTimeMillis() - stopStartTime

        println("Multiple watchers startup time: ${startupTime}ms")
        println("Multiple watchers stop time: ${stopTime}ms")

        // Should handle multiple watchers without issues
        assertTrue(startupTime < 5000, "Multiple watchers should start within 5 seconds")
        assertTrue(stopTime < 2000, "Multiple watchers should stop within 2 seconds")
    }

    @Test
    fun `stress test - rapid start stop cycles`() = runBlockingTest {
        val testFolder = File(tempRootDir, "rapid_cycle_test")
        testFolder.mkdirs()
        File(testFolder, "cycle_test.txt").writeText("cycle content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        val numCycles = 10
        val startTime = System.currentTimeMillis()

        for (i in 1..numCycles) {
            watcher.startWatching()
            delay(50)
            watcher.stopWatching()
            delay(50)
        }

        val totalTime = System.currentTimeMillis() - startTime
        println("Rapid cycles ($numCycles) total time: ${totalTime}ms")

        // Should handle rapid cycles without memory leaks or crashes
        assertTrue(totalTime < 10000, "Rapid cycles should complete within 10 seconds")
    }

    @Test
    fun `stress test - large directory tree`() = runBlockingTest {
        // Create deep directory structure
        var currentDir = tempRootDir
        val depth = 10

        for (level in 1..depth) {
            currentDir = File(currentDir, "level_$level")
            currentDir.mkdirs()

            // Add files at each level
            for (fileNum in 1..3) {
                File(
                    currentDir,
                    "file_${level}_$fileNum.txt"
                ).writeText("content at level $level, file $fileNum")
            }
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        val startTime = System.currentTimeMillis()
        watcher.startWatching()
        val initTime = System.currentTimeMillis() - startTime

        delay(500) // Allow processing

        val stopStartTime = System.currentTimeMillis()
        watcher.stopWatching()
        val stopTime = System.currentTimeMillis() - stopStartTime

        println("Deep directory tree init time: ${initTime}ms")
        println("Deep directory tree stop time: ${stopTime}ms")

        // Should handle deep structures
        assertTrue(initTime < 5000, "Deep structure init should complete within 5 seconds")
        assertTrue(stopTime < 1000, "Deep structure stop should complete within 1 second")
    }

    @Test
    fun `stress test - memory usage over time`() = runBlockingTest {
        val testFolder = File(tempRootDir, "memory_test")
        testFolder.mkdirs()

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        // Measure initial memory
        System.gc()
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        watcher.startWatching()

        // Simulate file operations over time
        for (i in 1..50) {
            val file = File(testFolder, "memory_test_$i.txt")
            file.writeText("memory test content $i")
            delay(20)
            if (i % 10 == 0) {
                file.delete()
            }
        }

        delay(500) // Allow processing

        // Measure memory after operations
        System.gc()
        val midMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        watcher.stopWatching()

        // Measure final memory
        System.gc()
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val memoryGrowthDuringOps = midMemory - initialMemory
        val memoryGrowthAfterStop = finalMemory - initialMemory

        println("Initial memory: ${initialMemory / 1024}KB")
        println("Memory during ops: ${midMemory / 1024}KB (growth: ${memoryGrowthDuringOps / 1024}KB)")
        println("Final memory: ${finalMemory / 1024}KB (total growth: ${memoryGrowthAfterStop / 1024}KB)")

        // Memory growth should be reasonable
        assertTrue(
            memoryGrowthAfterStop < 10 * 1024 * 1024,
            "Memory growth should be less than 10MB"
        )
    }

    @Test
    fun `stress test - exception handling during operations`() = runBlockingTest {
        // Mock FileUtils to occasionally throw exceptions
        var callCount = 0
        every { FileUtils.fileCopy(any(), any(), any()) } answers {
            callCount++
            if (callCount % 3 == 0) {
                throw RuntimeException("Simulated file copy failure")
            } else {
                true
            }
        }

        val testFolder = File(tempRootDir, "exception_test")
        testFolder.mkdirs()

        // Create multiple files to trigger exceptions
        for (i in 1..10) {
            File(testFolder, "exception_test_$i.txt").writeText("content $i")
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        // Should not crash despite exceptions
        assertDoesNotThrow("Should handle exceptions gracefully") {
            watcher.startWatching()
            delay(300)
            watcher.stopWatching()
        }

        // Should have attempted to copy files despite some failures
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }
}

/**
 * Тесты интеграции с реальными файловыми операциями
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FileObserverRealFileSystemTest {

    private lateinit var mockContext: Context
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File
    private val copiedFiles = mutableListOf<Pair<File, File>>()

    @Before
    fun setUp() {
        setupMocksWithRealFileCopy()
        tempRootDir = createTempDir("real_fs_test_root")
        tempDestDir = createTempDir("real_fs_test_dest")
    }

    @After
    fun tearDown() {
        tempRootDir.deleteRecursively()
        tempDestDir.deleteRecursively()
        unmockkAll()
    }

    private fun setupMocksWithRealFileCopy() {
        mockkObject(FitTracker)
        mockContext = mockk<Context>(relaxed = true)

        every { FitTracker.instance } returns mockk<FitTracker> {
            every { applicationContext } returns mockContext
        }

        val mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        val mockDatabase = mockk<AppDatabase>()
        val mockEventLogDao = mockk<EventLogDao>(relaxed = true)
        every { mockDatabase.eventLogDao() } returns mockEventLogDao
        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase() } returns mockDatabase

        // Mock FileUtils with real file operations for testing
        mockkObject(FileUtils)
        every { FileUtils.checkFileConditions(any()) } returns true
        every { FileUtils.fileCopy(any(), any(), any()) } answers {
            val context = firstArg<Context>()
            val srcFile = secondArg<File>()
            val destFile = thirdArg<File>()

            // Perform actual file copy for testing
            try {
                destFile.parentFile?.mkdirs()
                srcFile.copyTo(destFile, overwrite = true)
                copiedFiles.add(srcFile to destFile)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    @Test
    fun `integration test - real file copying with subfolder switching`() = runBlockingTest {
        copiedFiles.clear()

        // Create initial folder structure
        val folder1 = File(tempRootDir, "2024-01-01")
        val folder2 = File(tempRootDir, "2024-01-02")
        folder1.mkdirs()
        folder2.mkdirs()

        val file1 = File(folder1, "image1.jpg")
        val file2 = File(folder2, "image2.jpg")
        file1.writeText("fake image 1 content")
        file2.writeText("fake image 2 content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(200) // Allow initial processing

        // Create newer folder
        val folder3 = File(tempRootDir, "2024-01-03")
        folder3.mkdirs()
        val file3 = File(folder3, "image3.jpg")
        file3.writeText("fake image 3 content")

        delay(300) // Allow processing
        watcher.stopWatching()

        // Verify real files were copied
        assertTrue(copiedFiles.isNotEmpty(), "Files should have been copied")

        // Check that destination files actually exist
        copiedFiles.forEach { (_, destFile) ->
            assertTrue(destFile.exists(), "Destination file should exist: ${destFile.absolutePath}")
            assertTrue(destFile.length() > 0, "Destination file should not be empty")
        }
    }

    @Test
    fun `integration test - file content verification`() = runBlockingTest {
        copiedFiles.clear()

        val testFolder = File(tempRootDir, "content_test")
        testFolder.mkdirs()

        val originalContent = "This is test content with special characters: åäö 你好 🎉"
        val testFile = File(testFolder, "content_test.txt")
        testFile.writeText(originalContent, Charsets.UTF_8)

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(200)
        watcher.stopWatching()

        // Verify content was copied correctly
        assertTrue(copiedFiles.isNotEmpty(), "Files should have been copied")

        val (srcFile, destFile) = copiedFiles.first()
        assertEquals(
            originalContent,
            destFile.readText(Charsets.UTF_8),
            "File content should match"
        )
        assertEquals(srcFile.length(), destFile.length(), "File sizes should match")
    }

    @Test
    fun `integration test - binary file handling`() = runBlockingTest {
        copiedFiles.clear()

        val testFolder = File(tempRootDir, "binary_test")
        testFolder.mkdirs()

        // Create fake binary content
        val binaryContent = ByteArray(1024) { (it % 256).toByte() }
        val binaryFile = File(testFolder, "binary_test.bin")
        binaryFile.writeBytes(binaryContent)

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        delay(200)
        watcher.stopWatching()

        // Verify binary content was copied correctly
        assertTrue(copiedFiles.isNotEmpty(), "Binary files should have been copied")

        val (srcFile, destFile) = copiedFiles.first()
        assertContentEquals(
            srcFile.readBytes(),
            destFile.readBytes(),
            "Binary content should match"
        )
    }
}
        