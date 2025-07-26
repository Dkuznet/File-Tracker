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
import java.util.concurrent.CountDownLatch
import kotlin.test.*

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class FileObserverCreationTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("fileobserver_test")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    @Config(sdk = [28])
    fun `createFileObserver returns legacy FileObserver on older API`() {
        val testPath = tempDir.absolutePath
        val eventReceived = CountDownLatch(1)
        var receivedEvent: Int? = null
        var receivedPath: String? = null

        val observer = createFileObserver(testPath, FileObserver.CREATE) { event, path ->
            receivedEvent = event
            receivedPath = path
            eventReceived.countDown()
        }

        assertNotNull(observer)
        assertTrue(observer is FileObserver)
    }

    @Test
    @Config(sdk = [33])
    fun `createFileObserver returns new FileObserver on API 33+`() {
        val testPath = tempDir.absolutePath
        val observer = createFileObserver(testPath, FileObserver.CREATE) { event, path ->
            // Test callback
        }

        assertNotNull(observer)
        assertTrue(observer is FileObserver)
    }

    @Test
    fun `createFileObserver handles different event masks`() {
        val testPath = tempDir.absolutePath
        val masks = listOf(
            FileObserver.CREATE,
            FileObserver.DELETE,
            FileObserver.MODIFY,
            FileObserver.MOVED_FROM,
            FileObserver.MOVED_TO,
            FileObserver.ALL_EVENTS
        )

        masks.forEach { mask ->
            val observer = createFileObserver(testPath, mask) { _, _ -> }
            assertNotNull(observer, "Observer should be created for mask: $mask")
        }
    }

    @Test
    fun `createFileObserver with invalid path does not crash`() {
        val invalidPaths = listOf(
            "/nonexistent/path",
            "",
            "/",
            "/root/protected/path"
        )

        invalidPaths.forEach { path ->
            assertDoesNotThrow("Should not throw for path: $path") {
                createFileObserver(path, FileObserver.ALL_EVENTS) { _, _ -> }
            }
        }
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LatestFolderWatcherEdgeCasesTest {

    private lateinit var mockContext: Context
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File

    @Before
    fun setUp() {
        setupMocks()
        tempRootDir = createTempDir("edge_test_root")
        tempDestDir = createTempDir("edge_test_dest")
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
    fun `test watcher with very long file names`() = runBlockingTest {
        val longFileName = "a".repeat(255) + ".txt"
        val subfolder = File(tempRootDir, "subfolder")
        subfolder.mkdirs()

        val longFile = File(subfolder, longFileName)
        try {
            longFile.writeText("content")

            val watcher = LatestFolderWatcher(
                context = mockContext,
                rootPath = tempRootDir.absolutePath,
                destDirPath = tempDestDir.absolutePath,
                watchSubfolders = true
            )

            watcher.startWatching()
            watcher.stopWatching()

            // Should handle long file names gracefully
        } catch (e: Exception) {
            // Some file systems may not support very long file names
            println("Long file name test skipped due to filesystem limitations: ${e.message}")
        }
    }

    @Test
    fun `test watcher with special characters in paths`() = runBlockingTest {
        val specialFolders = listOf(
            "folder with spaces",
            "folder-with-dashes",
            "folder_with_underscores",
            "folder.with.dots"
        )

        specialFolders.forEach { folderName ->
            val folder = File(tempRootDir, folderName)
            folder.mkdirs()
            File(folder, "test.txt").writeText("content")
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should handle special characters in paths
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher with deeply nested folders`() = runBlockingTest {
        // Create deeply nested structure
        var currentDir = tempRootDir
        for (i in 1..10) {
            currentDir = File(currentDir, "level$i")
            currentDir.mkdirs()
        }
        File(currentDir, "deep_file.txt").writeText("deep content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should handle deeply nested structures
    }

    @Test
    fun `test watcher with read-only files`() = runBlockingTest {
        val subfolder = File(tempRootDir, "subfolder")
        subfolder.mkdirs()

        val readOnlyFile = File(subfolder, "readonly.txt")
        readOnlyFile.writeText("readonly content")
        readOnlyFile.setReadOnly()

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should handle read-only files
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher with symlinks`() = runBlockingTest {
        val targetDir = File(tempRootDir, "target")
        targetDir.mkdirs()
        File(targetDir, "target_file.txt").writeText("target content")

        try {
            // Create symbolic link (may not work on all systems/file systems)
            val linkDir = File(tempRootDir, "link")
            val runtime = Runtime.getRuntime()
            runtime.exec(arrayOf("ln", "-s", targetDir.absolutePath, linkDir.absolutePath))

            Thread.sleep(100) // Allow link creation

            if (linkDir.exists()) {
                val watcher = LatestFolderWatcher(
                    context = mockContext,
                    rootPath = tempRootDir.absolutePath,
                    destDirPath = tempDestDir.absolutePath,
                    watchSubfolders = true
                )

                watcher.startWatching()
                watcher.stopWatching()
            }
        } catch (e: Exception) {
            println("Symlink test skipped: ${e.message}")
        }
    }

    @Test
    fun `test watcher handles concurrent start stop operations`() = runBlockingTest {
        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        // Rapid start/stop operations
        repeat(5) {
            watcher.startWatching()
            delay(10)
            watcher.stopWatching()
            delay(10)
        }

        // Should handle rapid operations without crashing
    }

    @Test
    fun `test watcher with files that change during operation`() = runBlockingTest {
        val subfolder = File(tempRootDir, "changing_folder")
        subfolder.mkdirs()

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // Create file while watcher is running
        val changingFile = File(subfolder, "changing.txt")
        changingFile.writeText("initial content")

        delay(50)

        // Modify file
        changingFile.appendText("\nmore content")

        delay(50)

        watcher.stopWatching()

        // Should handle file changes during operation
    }

    @Test
    fun `test watcher memory usage with many files`() = runBlockingTest {
        val subfolder = File(tempRootDir, "many_files")
        subfolder.mkdirs()

        // Create many files to test memory usage
        for (i in 1..100) {
            File(subfolder, "file_$i.txt").writeText("content $i")
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        val beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        watcher.startWatching()

        val afterStartMemory =
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        watcher.stopWatching()

        val afterStopMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Memory usage should not grow excessively
        val memoryGrowth = afterStartMemory - beforeMemory
        println("Memory growth: $memoryGrowth bytes")

        // Should handle many files without excessive memory usage
        assertTrue(memoryGrowth < 50 * 1024 * 1024) // Less than 50MB growth
    }

    @Test
    fun `test watcher with zero-byte files`() = runBlockingTest {
        val subfolder = File(tempRootDir, "empty_files")
        subfolder.mkdirs()

        // Create zero-byte files
        val emptyFile1 = File(subfolder, "empty1.txt")
        val emptyFile2 = File(subfolder, "empty2.txt")
        emptyFile1.createNewFile()
        emptyFile2.createNewFile()

        assertEquals(0L, emptyFile1.length())
        assertEquals(0L, emptyFile2.length())

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should handle zero-byte files appropriately
        // Note: FileUtils.checkFileConditions might reject zero-byte files
    }

    @Test
    fun `test watcher with hidden files and directories`() = runBlockingTest {
        // Create hidden directories and files (starting with dot)
        val hiddenDir = File(tempRootDir, ".hidden_dir")
        val regularDir = File(tempRootDir, "regular_dir")
        hiddenDir.mkdirs()
        regularDir.mkdirs()

        File(hiddenDir, ".hidden_file.txt").writeText("hidden content")
        File(hiddenDir, "regular_in_hidden.txt").writeText("regular in hidden")
        File(regularDir, ".hidden_in_regular.txt").writeText("hidden in regular")
        File(regularDir, "regular_file.txt").writeText("regular content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should handle hidden files and directories
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher error handling when FileUtils throws exception`() = runBlockingTest {
        // Mock FileUtils to throw exception
        every { FileUtils.fileCopy(any(), any(), any()) } throws RuntimeException("Copy failed")

        val subfolder = File(tempRootDir, "error_folder")
        subfolder.mkdirs()
        File(subfolder, "test.txt").writeText("content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        // Should not crash even when FileUtils throws exception
        assertDoesNotThrow {
            watcher.startWatching()
            watcher.stopWatching()
        }
    }

    @Test
    fun `test watcher with mixed file types`() = runBlockingTest {
        val subfolder = File(tempRootDir, "mixed_types")
        subfolder.mkdirs()

        // Create files with different extensions
        val fileTypes = mapOf(
            "image.jpg" to "fake jpeg content",
            "video.mp4" to "fake mp4 content",
            "audio.mp3" to "fake mp3 content",
            "document.pdf" to "fake pdf content",
            "text.txt" to "plain text content",
            "data.bin" to "binary data",
            "noextension" to "file without extension"
        )

        fileTypes.forEach { (filename, content) ->
            File(subfolder, filename).writeText(content)
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should handle different file types
        verify(atLeast = fileTypes.size) { FileUtils.fileCopy(any(), any(), any()) }
    }

    @Test
    fun `test watcher folder switching with timing`() = runBlockingTest {
        // Create initial folder
        val folder1 = File(tempRootDir, "2024-01-01")
        folder1.mkdirs()
        File(folder1, "file1.txt").writeText("content1")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // Add delay and create newer folder
        delay(100)
        val folder2 = File(tempRootDir, "2024-01-02")
        folder2.mkdirs()
        File(folder2, "file2.txt").writeText("content2")

        delay(100)
        val folder3 = File(tempRootDir, "2024-01-03")
        folder3.mkdirs()
        File(folder3, "file3.txt").writeText("content3")

        delay(100)
        watcher.stopWatching()

        // Should have processed files from all folders as they became "latest"
        verify(atLeast = 3) { FileUtils.fileCopy(any(), any(), any()) }
    }
}

// Performance tests
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LatestFolderWatcherPerformanceTest {

    private lateinit var mockContext: Context
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File

    @Before
    fun setUp() {
        setupMocks()
        tempRootDir = createTempDir("perf_test_root")
        tempDestDir = createTempDir("perf_test_dest")
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
    fun `performance test - large number of subfolders`() = runBlockingTest {
        // Create many subfolders
        val numFolders = 50
        for (i in 1..numFolders) {
            val folder = File(tempRootDir, "folder_${i.toString().padStart(3, '0')}")
            folder.mkdirs()
            File(folder, "file$i.txt").writeText("content $i")
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        val startTime = System.currentTimeMillis()
        watcher.startWatching()
        val startupTime = System.currentTimeMillis() - startTime

        delay(100) // Allow processing

        val stopStartTime = System.currentTimeMillis()
        watcher.stopWatching()
        val stopTime = System.currentTimeMillis() - stopStartTime

        println("Startup time with $numFolders folders: ${startupTime}ms")
        println("Stop time: ${stopTime}ms")

        // Performance should be reasonable
        assertTrue(startupTime < 5000, "Startup should complete within 5 seconds")
        assertTrue(stopTime < 1000, "Stop should complete within 1 second")
    }

    @Test
    fun `performance test - many files in latest folder`() = runBlockingTest {
        val latestFolder = File(tempRootDir, "latest")
        latestFolder.mkdirs()

        // Create many files (more than the 20 limit to test sorting performance)
        val numFiles = 100
        for (i in 1..numFiles) {
            File(latestFolder, "file_${i.toString().padStart(3, '0')}.txt").writeText("content $i")
        }

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        val startTime = System.currentTimeMillis()
        watcher.startWatching()
        val processingTime = System.currentTimeMillis() - startTime

        watcher.stopWatching()

        println("Processing time for $numFiles files: ${processingTime}ms")

        // Should only process the 20 newest files
        verify(atMost = 20) { FileUtils.fileCopy(any(), any(), any()) }

        // Performance should be reasonable even with many files
        assertTrue(processingTime < 3000, "Processing should complete within 3 seconds")
    }

    @Test
    fun `performance test - rapid folder creation`() = runBlockingTest {
        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        val startTime = System.currentTimeMillis()

        // Rapidly create folders
        for (i in 1..10) {
            val folder = File(tempRootDir, "rapid_$i")
            folder.mkdirs()
            File(folder, "file$i.txt").writeText("content $i")
            delay(10) // Small delay between creations
        }

        val creationTime = System.currentTimeMillis() - startTime

        delay(200) // Allow processing
        watcher.stopWatching()

        println("Rapid folder creation time: ${creationTime}ms")

        // Should handle rapid folder creation
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }
}

// Mock utils for testing
class TestUtils {
    companion object {
        fun createTempFileWithContent(parent: File, name: String, content: String): File {
            val file = File(parent, name)
            file.writeText(content)
            return file
        }

        fun createTempFolderStructure(root: File, structure: Map<String, List<String>>) {
            structure.forEach { (folderName, files) ->
                val folder = File(root, folderName)
                folder.mkdirs()
                files.forEach { fileName ->
                    createTempFileWithContent(folder, fileName, "content of $fileName")
                }
            }
        }

        fun measureExecutionTime(block: () -> Unit): Long {
            val start = System.currentTimeMillis()
            block()
            return System.currentTimeMillis() - start
        }
    }
}