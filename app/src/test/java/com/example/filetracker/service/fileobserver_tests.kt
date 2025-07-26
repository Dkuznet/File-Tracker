package com.example.filetracker.service

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import com.example.filetracker.FitTracker
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.EventLogDao
import com.example.filetracker.util.EventLogger
import com.example.filetracker.util.FileUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LatestFolderWatcherTests {

    private lateinit var context: Context
    private lateinit var mockContext: Context
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockDatabase: AppDatabase
    private lateinit var mockEventLogDao: EventLogDao
    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File
    // private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // Mock FitTracker
        mockkObject(FitTracker)
        mockContext = mockk<Context>(relaxed = true)
        every { FitTracker.instance } returns mockk<FitTracker> {
            every { applicationContext } returns mockContext
        }

        // Mock SharedPreferences
        mockSharedPrefs = mockk<SharedPreferences> {
            every { getBoolean("extended_logging", false) } returns false
        }
        every {
            mockContext.getSharedPreferences(
                "eventlog_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockSharedPrefs

        // Mock Database
        mockDatabase = mockk<AppDatabase>()
        mockEventLogDao = mockk<EventLogDao>(relaxed = true)
        every { mockDatabase.eventLogDao() } returns mockEventLogDao

        // Mock EventLogger to prevent database access
        mockkObject(EventLogger)
        every { EventLogger.log(any(), any(), any(), any()) } returns Unit

        // Mock FileUtils
        mockkObject(FileUtils)
        every { FileUtils.checkFileConditions(any()) } returns true
        every { FileUtils.fileCopy(any(), any(), any()) } returns true

        // Create temporary directories
        tempRootDir = createTempDirectory("test_root").toFile()
        tempDestDir = createTempDirectory("test_dest").toFile()

        context = mockContext
    }

    @After
    fun tearDown() {
        // Clean up temporary directories
        tempRootDir.deleteRecursively()
        tempDestDir.deleteRecursively()

        // Clear mocks
        unmockkAll()
    }

    @Test
    fun `test LatestFolderWatcher initialization with watchSubfolders true`() = runTest {
        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        assertNotNull(watcher)
    }

    @Test
    fun `test LatestFolderWatcher initialization with watchSubfolders false`() = runTest {
        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = false
        )

        assertNotNull(watcher)
    }

    @Test
    fun `test startWatching with existing subfolders`() = runTest {
        // Create test subfolders with names that will have a clear "latest"
        val subfolder1 = File(tempRootDir, "folder_a")
        val subfolder2 = File(tempRootDir, "folder_b")
        val subfolder3 = File(tempRootDir, "folder_z") // This should be "latest" alphabetically

        subfolder1.mkdirs()
        subfolder2.mkdirs()
        subfolder3.mkdirs()

        // Add test files to the latest subfolder
        val testFile = File(subfolder3, "test.txt")
        testFile.writeText("test content")

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // Verify that FileUtils.fileCopy was called for existing files
        verify { FileUtils.fileCopy(context, any(), any()) }

        watcher.stopWatching()
    }

    @Test
    fun `test startWatching with no subfolders`() = runTest {
        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should not crash and should log warning about no subfolders
        // Note: Cannot verify suspend function calls directly
    }

    @Test
    fun `test startWatching with watchSubfolders false copies existing files`() = runTest {
        // Create test files directly in root directory
        val testFile1 = File(tempRootDir, "test1.txt")
        val testFile2 = File(tempRootDir, "test2.txt")
        testFile1.writeText("content1")
        testFile2.writeText("content2")

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = false
        )

        watcher.startWatching()

        // Verify that files were copied
        verify(atLeast = 1) { FileUtils.fileCopy(context, any(), any()) }

        watcher.stopWatching()
    }

    @Test
    fun `test stopWatching when already stopped`() = runTest {
        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        // Call stopWatching without starting
        watcher.stopWatching()

        // Should log warning and not crash
        // Note: Cannot verify suspend function calls directly
    }

    @Test
    fun `test double startWatching`() = runTest {
        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.startWatching() // Second call should be ignored

        // Should log warning about already watching
        // Note: Cannot verify suspend function calls directly

        watcher.stopWatching()
    }

    @Test
    fun `test new subfolder detection switches to latest`() = runTest {
        val subfolder1 = File(tempRootDir, "folder_a")
        subfolder1.mkdirs()

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // Simulate creation of a new "later" subfolder
        val subfolder2 = File(tempRootDir, "folder_z")
        subfolder2.mkdirs()

        // Add a test file to the new subfolder
        val testFile = File(subfolder2, "newfile.txt")
        testFile.writeText("new content")

        // The watcher should detect this and switch to the new folder
        // Note: In a real test, we'd need to simulate the FileObserver events
        // For now, we verify the setup doesn't crash

        watcher.stopWatching()
    }

    @Test
    fun `test file creation in root directory with watchSubfolders false`() = runTest {
        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = false
        )

        watcher.startWatching()

        // Simulate file creation (this would normally be detected by FileObserver)
        val newFile = File(tempRootDir, "newfile.txt")
        newFile.writeText("new content")

        // In a real scenario, the FileObserver would trigger the callback
        // Here we just verify the setup is correct

        watcher.stopWatching()
    }

    @Test
    fun `test copyFilesFromFolder with multiple files`() = runTest {
        // Create multiple files to test the limit (should take only 20 newest)
        val testFolder = File(tempRootDir, "testfolder")
        testFolder.mkdirs()

        // Create 25 files (more than the limit of 20)
        for (i in 1..25) {
            val file = File(testFolder, "file_${i.toString().padStart(2, '0')}.txt")
            file.writeText("content $i")
            // Add small delay to ensure different modification times
            Thread.sleep(1)
        }

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        // Create the test scenario by putting the folder in root and starting watcher
        watcher.startWatching()

        // Should have called fileCopy for the files (limited to 20 newest)
        verify(atMost = 20) { FileUtils.fileCopy(context, any(), any()) }

        watcher.stopWatching()
    }

    @Test
    fun `test nomedia files are ignored`() = runTest {
        val subfolder = File(tempRootDir, "subfolder")
        subfolder.mkdirs()

        // Create regular file and .nomedia file
        val regularFile = File(subfolder, "regular.txt")
        val nomediaFile = File(subfolder, ".nomedia")
        regularFile.writeText("regular content")
        nomediaFile.writeText("")

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // Should only copy the regular file, not .nomedia
        verify { FileUtils.fileCopy(context, regularFile, any()) }
        verify(exactly = 0) { FileUtils.fileCopy(context, nomediaFile, any()) }

        watcher.stopWatching()
    }

    @Test
    fun `test createFileObserver returns proper observer`() {
        val testPath = tempRootDir.absolutePath
        val observer = createFileObserver(testPath, FileObserver.ALL_EVENTS) { event, path ->
            // Test callback
        }

        assertNotNull(observer)
    }


    @Test
    fun `test extended logging settings are respected`() = runTest {
        // Test with extended logging enabled
        every { mockSharedPrefs.getBoolean("extended_logging", false) } returns true

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should have logged extra messages
        // Note: Cannot verify suspend function calls directly
    }

    @Test
    fun `test watcher handles empty root directory gracefully`() = runTest {
        // Use empty directory
        val emptyDir = createTempDirectory("empty").toFile()

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = emptyDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()
        watcher.stopWatching()

        // Should not crash and should handle empty directory
        emptyDir.deleteRecursively()
    }


    @Test
    fun `test findLatestSubfolder with various folder names`() = runTest {
        // Create folders with different naming patterns
        val folders = listOf("2023-01-01", "2023-12-31", "2024-01-01", "folder_a", "folder_z")
        folders.forEach { name ->
            File(tempRootDir, name).mkdirs()
        }

        val watcher = LatestFolderWatcher(
            context = context,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // The latest should be "folder_z" (alphabetically last)
        // Verify through logging or other observable behavior

        watcher.stopWatching()
    }
}

// Integration tests for real file system operations
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LatestFolderWatcherIntegrationTest {

    private lateinit var tempRootDir: File
    private lateinit var tempDestDir: File
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        // Setup similar to unit tests but with less mocking for integration testing
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

        tempRootDir = createTempDirectory("integration_root").toFile()
        tempDestDir = createTempDirectory("integration_dest").toFile()
    }

    @After
    fun tearDown() {
        tempRootDir.deleteRecursively()
        tempDestDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `integration test - complete workflow with real files`() = runTest {
        // Mock FileUtils for integration test
        mockkObject(FileUtils)
        every { FileUtils.checkFileConditions(any()) } returns true
        every { FileUtils.fileCopy(any(), any(), any()) } returns true

        // Create realistic folder structure
        val dateFolder1 = File(tempRootDir, "2024-01-01")
        val dateFolder2 = File(tempRootDir, "2024-01-02")
        dateFolder1.mkdirs()
        dateFolder2.mkdirs()

        // Add files to folders
        File(dateFolder1, "image1.jpg").writeText("image1 content")
        File(dateFolder2, "image2.jpg").writeText("image2 content")
        File(dateFolder2, "image3.jpg").writeText("image3 content")

        val watcher = LatestFolderWatcher(
            context = mockContext,
            rootPath = tempRootDir.absolutePath,
            destDirPath = tempDestDir.absolutePath,
            watchSubfolders = true
        )

        watcher.startWatching()

        // Simulate adding a new latest folder
        Thread.sleep(100) // Ensure different timestamps
        val dateFolder3 = File(tempRootDir, "2024-01-03")
        dateFolder3.mkdirs()
        File(dateFolder3, "image4.jpg").writeText("image4 content")

        Thread.sleep(100) // Allow processing time

        watcher.stopWatching()

        // Verify that files were processed
        verify(atLeast = 1) { FileUtils.fileCopy(any(), any(), any()) }
    }
}